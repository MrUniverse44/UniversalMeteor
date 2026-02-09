package me.blueslime.meteor.storage.messenger.types;

import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.messenger.Messenger;
import me.blueslime.meteor.storage.messenger.redis.RedisMessengerConfig;
import me.blueslime.meteor.storage.messenger.channels.parameter.ChannelMessageEvent;
import me.blueslime.meteor.storage.messenger.channels.parameter.types.ChannelMessageWithObjectEvent;
import me.blueslime.meteor.storage.messenger.channels.parameter.types.ChannelMessageWithoutObjectEvent;
import redis.clients.jedis.*;
import redis.clients.jedis.params.XReadGroupParams;
import redis.clients.jedis.resps.StreamEntry;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@SuppressWarnings("deprecation")
public class RedisMessenger implements Messenger {

    public enum SubscribeMode { PUBSUB, STREAMS }

    private final JedisPool pool;
    private final ObjectMapper objectMapper;
    private final RedisMessengerConfig config;

    private final ExecutorService publisherExecutor;
    private final ExecutorService subscriberExecutor;
    private final ExecutorService processingExecutor;
    private final ScheduledExecutorService reconnectScheduler;
    private final ConcurrentHashMap<String, SubscriptionHandle> subscriptions = new ConcurrentHashMap<>();

    public RedisMessenger(String redisUri, ObjectMapper objectMapper, RedisMessengerConfig cfg) {
        this.pool = new JedisPool(redisUri);
        this.objectMapper = objectMapper;
        this.config = cfg != null ? cfg : RedisMessengerConfig.builder();
        this.publisherExecutor = Executors.newFixedThreadPool(this.config.getPublisherThreads(), r -> {
            Thread t = new Thread(r, "redis-pub");
            t.setDaemon(true);
            return t;
        });
        this.subscriberExecutor = Executors.newFixedThreadPool(this.config.getSubscriberThreads(), r -> {
            Thread t = new Thread(r, "redis-sub");
            t.setDaemon(true);
            return t;
        });
        this.processingExecutor = Executors.newFixedThreadPool(this.config.getProcessingThreads(), r -> {
            Thread t = new Thread(r, "redis-processor");
            t.setDaemon(true);
            return t;
        });
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "redis-reconnect");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void publish(String channelId, String payload) {
        publish(channelId, payload, config.isAsyncPublish());
    }

    public void publish(String channelId, String payload, boolean async) {
        if (async) {
            try {
                publisherExecutor.submit(() -> safePublish(channelId, payload));
            } catch (RejectedExecutionException rex) {
                getLogger().error(rex, "Publisher executor rejected task, falling back to sync publish");
                safePublish(channelId, payload);
            }
        } else {
            safePublish(channelId, payload);
        }
    }

    private void safePublish(String channelId, String payload) {
        try (Jedis j = pool.getResource()) {
            j.publish(channelId, payload);
            String streamKey = config.getStreamPrefix() + channelId;
            Map<String, String> entry = Map.of("payload", payload);
            j.xadd(streamKey, StreamEntryID.NEW_ENTRY, entry);
        } catch (Exception e) {
            getLogger().error(e, "Redis publish failed for channel=" + channelId + " : " + e.getMessage());
        }
    }

    @Override
    public String subscribe(String channelId, Consumer<ChannelMessageEvent> consumer) {
        return subscribe(channelId, consumer, null, config.getSubscriberMode());
    }

    public String subscribe(String channelId, Consumer<ChannelMessageEvent> consumer, String consumerGroupName, SubscribeMode mode) {
        String sid = UUID.randomUUID().toString();
        SubscriptionHandle handle = new SubscriptionHandle(sid, channelId, mode, consumerGroupName);
        subscriptions.put(sid, handle);
        if (mode == SubscribeMode.PUBSUB) {
            startPubSub(handle, consumer);
        } else {
            startStreamConsumer(handle, consumer);
        }
        return sid;
    }

    private void startPubSub(SubscriptionHandle handle, Consumer<ChannelMessageEvent> consumer) {
        handle.future = subscriberExecutor.submit(() -> {
            if (handle.stopped.get()) return;
            try (Jedis j = pool.getResource()) {
                JedisPubSub sub = new JedisPubSub() {
                    @Override
                    public void onMessage(String channel, String message) {
                        dispatchParsedPayload(channel, message, consumer, handle);
                    }
                };
                handle.pubSub = sub;
                try {
                    j.subscribe(sub, handle.channelId);
                } catch (Exception ex) {
                    if (!handle.stopped.get()) {
                        getLogger().error(ex, "PubSub subscribe on channel " + handle.channelId + " ended unexpectedly: " + ex.getMessage());
                        reconnectScheduler.schedule(() -> startPubSub(handle, consumer), config.getReconnectBackoff().toMillis(), TimeUnit.MILLISECONDS);
                    }
                }
            } catch (Exception e) {
                if (!handle.stopped.get()) {
                    getLogger().error(e, "Error in pubsub loop for channel " + handle.channelId + ": " + e.getMessage());
                    reconnectScheduler.schedule(() -> startPubSub(handle, consumer), config.getReconnectBackoff().toMillis(), TimeUnit.MILLISECONDS);
                }
            }
        });
    }

    private void startStreamConsumer(SubscriptionHandle handle, Consumer<ChannelMessageEvent> consumer) {
        handle.future = subscriberExecutor.submit(() -> {
            final String channelStream = config.getStreamPrefix() + handle.channelId;
            final String group = (handle.consumerGroupName != null && !handle.consumerGroupName.isEmpty())
                    ? handle.consumerGroupName
                    : config.getConsumerGroupPrefix() + handle.channelId;
            final String consumerName = "consumer-" + UUID.randomUUID();

            try (Jedis j = pool.getResource()) {
                try {
                    j.xgroupCreate(channelStream, group, StreamEntryID.LAST_ENTRY, true);
                } catch (Exception ex) {
                    getLogger().error(ex, "Consumer group may already exist for " + channelStream + ":" + group + " -> " + ex.getMessage());
                }
            } catch (Exception e) {
                getLogger().error(e, "Could not ensure consumer group exists for " + channelStream + " : " + e.getMessage());
            }

            while (!handle.stopped.get()) {
                try (Jedis j = pool.getResource()) {
                    XReadGroupParams params = new XReadGroupParams();
                    params.count(config.getStreamReadCount());
                    params.block((int) config.getStreamBlockMillis());
                    List<Map.Entry<String, List<StreamEntry>>> responses = j.xreadGroup(
                            group, consumerName, params,
                            Collections.singletonMap(channelStream, StreamEntryID.UNRECEIVED_ENTRY)
                    );
                    if (responses == null || responses.isEmpty()) continue;

                    for (Map.Entry<String, List<StreamEntry>> streamResponse : responses) {
                        List<StreamEntry> entries = streamResponse.getValue();
                        for (StreamEntry entry : entries) {
                            String id = entry.getID().toString();
                            Map<String, String> fields = entry.getFields();
                            String payload = fields.get("payload");
                            if (payload == null) {
                                if (config.isAckAfterProcessing()) safeXAck(handle, j, channelStream, group, id);
                                continue;
                            }
                            final String entryId = id;
                            processingExecutor.submit(() -> {
                                boolean processedOk = false;
                                try {
                                    dispatchParsedPayload(handle.channelId, payload, consumer, handle);
                                    processedOk = true;
                                } catch (Exception ex) {
                                    getLogger().error(ex, "Error processing stream entry " + entryId + " for " + channelStream + ": " + ex.getMessage());
                                } finally {
                                    if (processedOk && config.isAckAfterProcessing()) {
                                        try (Jedis j2 = pool.getResource()) {
                                            safeXAck(handle, j2, channelStream, group, entryId);
                                        } catch (Exception ackEx) {
                                            getLogger().error(ackEx, "Failed XACK entry " + entryId + " : " + ackEx.getMessage());
                                        }
                                    }
                                }
                            });
                        }
                    }
                } catch (Exception e) {
                    if (!handle.stopped.get()) {
                        getLogger().error(e, "Error in stream consumer loop for channel " + handle.channelId + " : " + e.getMessage());
                        reconnectScheduler.schedule(() -> startStreamConsumer(handle, consumer), config.getReconnectBackoff().toMillis(), TimeUnit.MILLISECONDS);
                        return;
                    }
                }
            }
        });
    }

    @SuppressWarnings("unused")
    private void safeXAck(SubscriptionHandle handle, Jedis j, String streamKey, String group, String entryId) {
        try {
            j.xack(streamKey, group, new StreamEntryID(entryId));
        } catch (Exception e) {
            getLogger().error(e, "Failed XACK " + entryId + " for " + streamKey + " group=" + group + " : " + e.getMessage());
        }
    }

    private void dispatchParsedPayload(String channel, String message, Consumer<ChannelMessageEvent> consumer, SubscriptionHandle handle) {
        Runnable job = () -> {
            try {
                Document map = Document.parse(message);

                String destiny = map.getString("destiny");
                String type = map.getString("type");

                if ("object".equals(type)) {
                    String className = map.getString("class");
                    Object obj = null;
                    Object data = map.get("data");

                    if (data instanceof Document docData) {
                        try {
                            Class<?> clazz = Class.forName(className);
                            obj = objectMapper.fromDocument(clazz, docData);
                        } catch (Exception e) {
                            getLogger().error(e, "Failed to parse object from document " + className + " : " + e.getMessage());
                        }
                    }

                    List<String> msgsList = map.getList("messages", String.class);
                    String[] arr = msgsList == null ? new String[0] : msgsList.toArray(new String[0]);

                    ChannelMessageWithObjectEvent ev = new ChannelMessageWithObjectEvent(channel, destiny, obj, arr);
                    safeInvokeConsumer(ev, consumer);
                } else {
                    List<String> msgsList = map.getList("messages", String.class);
                    String[] arr = msgsList == null ? new String[0] : msgsList.toArray(new String[0]);

                    ChannelMessageWithoutObjectEvent ev = new ChannelMessageWithoutObjectEvent(channel, destiny, arr);
                    safeInvokeConsumer(ev, consumer);
                }
            } catch (Throwable t) {
                if (!message.trim().startsWith("{")) {
                    ChannelMessageWithoutObjectEvent ev = new ChannelMessageWithoutObjectEvent(channel, null, new String[]{message});
                    safeInvokeConsumer(ev, consumer);
                } else {
                    getLogger().error(t, "Error while parsing/dispatching payload for channel " + channel + " : " + t.getMessage());
                }
            }
        };

        if (config.isAsyncProcess()) {
            try {
                processingExecutor.submit(job);
            } catch (RejectedExecutionException rex) {
                getLogger().error(rex, "Processing executor rejected task; running inline to avoid message loss");
                job.run();
            }
        } else {
            job.run();
        }
    }

    private void safeInvokeConsumer(ChannelMessageEvent ev, Consumer<ChannelMessageEvent> consumer) {
        try {
            consumer.accept(ev);
        } catch (Throwable t) {
            getLogger().error(t, "Listener threw exception for channel " + ev.getChannelId() + " : " + t.getMessage());
        }
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        SubscriptionHandle h = subscriptions.remove(subscriptionId);
        if (h == null) return;
        h.stopped.set(true);
        if (h.pubSub != null) {
            try { h.pubSub.unsubscribe(); } catch (Exception e) { getLogger().error(e, "Error unsubscribing pubsub for " + h.channelId + " : " + e.getMessage()); }
        }
        if (h.future != null) h.future.cancel(true);
    }

    @Override
    public void shutdown() {
        for (Map.Entry<String, SubscriptionHandle> e : subscriptions.entrySet()) unsubscribe(e.getKey());
        subscriptions.clear();
        safeShutdownExecutor(publisherExecutor, "publisherExecutor");
        safeShutdownExecutor(subscriberExecutor, "subscriberExecutor");
        safeShutdownExecutor(processingExecutor, "processingExecutor");
        safeShutdownExecutor(reconnectScheduler, "reconnectScheduler");
        pool.close();
    }

    private void safeShutdownExecutor(ExecutorService ex, String name) {
        try {
            ex.shutdown();
            if (!ex.awaitTermination(5, TimeUnit.SECONDS)) ex.shutdownNow();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ex.shutdownNow();
        } catch (Throwable t) {
            getLogger().error(t, "Error shutting down " + name + ": " + t.getMessage());
        }
    }

    private static class SubscriptionHandle {
        final String id;
        final String channelId;
        final SubscribeMode mode;
        final String consumerGroupName;
        final AtomicBoolean stopped = new AtomicBoolean(false);
        volatile JedisPubSub pubSub = null;
        volatile Future<?> future = null;

        SubscriptionHandle(String id, String channelId, SubscribeMode mode, String consumerGroupName) {
            this.id = id;
            this.channelId = channelId;
            this.mode = mode;
            this.consumerGroupName = consumerGroupName;
        }
    }
}