package me.blueslime.meteor.storage.messenger.types;

import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.messenger.Messenger;
import me.blueslime.meteor.storage.messenger.channels.parameter.ChannelMessageEvent;
import me.blueslime.meteor.storage.messenger.channels.parameter.types.ChannelMessageWithObjectEvent;
import me.blueslime.meteor.storage.messenger.channels.parameter.types.ChannelMessageWithoutObjectEvent;
import com.rabbitmq.client.*;
import org.bson.Document;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.List;

public class RabbitMQMessenger implements Messenger {

    private final ConnectionFactory factory;
    private final ObjectMapper objectMapper;
    private final ExecutorService processingExecutor;
    private final ScheduledExecutorService reconnectScheduler;

    private volatile Connection connection;
    private volatile Channel channel;
    private final ConcurrentHashMap<String, SubscriptionHandle> subscriptions = new ConcurrentHashMap<>();

    public RabbitMQMessenger(String host, ObjectMapper mapper, int processingThreads) {
        this.factory = new ConnectionFactory();
        this.factory.setHost(host);
        this.objectMapper = mapper;
        this.processingExecutor = Executors.newFixedThreadPool(processingThreads, r -> {
            Thread t = new Thread(r, "rabbit-processor");
            t.setDaemon(true);
            return t;
        });
        this.reconnectScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rabbit-reconnect");
            t.setDaemon(true);
            return t;
        });

        connectAsync();
    }

    private void connectAsync() {
        reconnectScheduler.submit(() -> {
            try {
                if (connection != null && connection.isOpen()) return;
                connection = factory.newConnection();
                channel = connection.createChannel();
            } catch (Exception e) {
                getLogger().error(e, "RabbitMQ connect failed: " + e.getMessage());
                reconnectScheduler.schedule(this::connectAsync, 2, TimeUnit.SECONDS);
            }
        });
    }

    @Override
    public void publish(String channelId, String payload) {
        try {
            if (channel == null || !channel.isOpen()) connectAsync();
            String exchange = "umeteor_channels";
            channel.exchangeDeclare(exchange, BuiltinExchangeType.TOPIC, true);
            channel.basicPublish(exchange, channelId, null, payload.getBytes());
        } catch (Exception e) {
            getLogger().error(e, "RabbitMQ publish error: " + e.getMessage());
            connectAsync();
        }
    }

    @Override
    public String subscribe(String channelId, Consumer<ChannelMessageEvent> consumer) {
        return subscribe(channelId, consumer, "umeteor_channels", true);
    }

    public String subscribe(String channelId, Consumer<ChannelMessageEvent> consumer, String exchange, boolean autoAck) {
        String sid = UUID.randomUUID().toString();
        SubscriptionHandle handle = new SubscriptionHandle(sid, channelId, exchange, autoAck);
        subscriptions.put(sid, handle);

        if (connection == null || !connection.isOpen() || channel == null || !channel.isOpen()) connectAsync();
        reconnectScheduler.submit(() -> setupConsumer(handle, consumer));
        return sid;
    }

    private void setupConsumer(SubscriptionHandle handle, Consumer<ChannelMessageEvent> consumer) {
        try {
            if (connection == null || !connection.isOpen()) {
                reconnectScheduler.schedule(() -> setupConsumer(handle, consumer), 1, TimeUnit.SECONDS);
                return;
            }
            if (channel == null || !channel.isOpen()) channel = connection.createChannel();

            channel.exchangeDeclare(handle.exchange, BuiltinExchangeType.TOPIC, true);
            String q = channel.queueDeclare().getQueue();
            channel.queueBind(q, handle.exchange, handle.channelId);

            handle.consumerTag = channel.basicConsume(q, handle.autoAck, new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    String payload = new String(body);
                    processingExecutor.submit(() -> {
                        try {
                            Document map = Document.parse(payload);

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
                                        getLogger().error(e, "Failed reconstruct object for channel " + handle.channelId + ": " + e.getMessage());
                                    }
                                }

                                List<String> msgs = map.getList("messages", String.class);
                                String[] arr = msgs == null ? new String[0] : msgs.toArray(new String[0]);

                                ChannelMessageWithObjectEvent ev = new ChannelMessageWithObjectEvent(handle.channelId, destiny, obj, arr);
                                try { consumer.accept(ev); } catch (Throwable t) { getLogger().error(t, "Listener threw: " + t.getMessage()); }
                            } else {
                                List<String> msgs = map.getList("messages", String.class);
                                String[] arr = msgs == null ? new String[0] : msgs.toArray(new String[0]);

                                ChannelMessageWithoutObjectEvent ev = new ChannelMessageWithoutObjectEvent(handle.channelId, destiny, arr);
                                try { consumer.accept(ev); } catch (Throwable t) { getLogger().error(t, "Listener threw: " + t.getMessage()); }
                            }

                            if (!handle.autoAck) {
                                try { channel.basicAck(envelope.getDeliveryTag(), false); }
                                catch (Exception ackEx) { getLogger().error(ackEx, "Failed ack: " + ackEx.getMessage()); }
                            }
                        } catch (Throwable ex) {
                            if (!handle.autoAck) {
                                try { channel.basicAck(envelope.getDeliveryTag(), false); } catch (Exception ignored) {}
                            }
                            getLogger().error(ex, "Error processing message (Raw?): " + ex.getMessage());
                        }
                    });
                }
            });
        } catch (Exception e) {
            getLogger().error(e, "Failed to setup RabbitMQ consumer for " + handle.channelId + ": " + e.getMessage());
            reconnectScheduler.schedule(() -> setupConsumer(handle, consumer), 2, TimeUnit.SECONDS);
        }
    }

    @Override
    public void unsubscribe(String subscriptionId) {
        SubscriptionHandle h = subscriptions.remove(subscriptionId);
        if (h == null) return;
        h.stopped.set(true);
        try {
            if (channel != null && h.consumerTag != null) channel.basicCancel(h.consumerTag);
        } catch (Exception e) {
            getLogger().error(e, "Error cancelling consumer: " + e.getMessage());
        }
    }

    @Override
    public void shutdown() {
        for (String k : subscriptions.keySet()) unsubscribe(k);
        subscriptions.clear();
        try { if (channel != null) channel.close(); } catch (Exception ignored) {}
        try { if (connection != null) connection.close(); } catch (Exception ignored) {}
        safeShutdownExecutor(processingExecutor, "rabbit-processing");
        safeShutdownExecutor(reconnectScheduler, "rabbit-reconnect");
    }

    private void safeShutdownExecutor(ExecutorService ex, String name) {
        try {
            ex.shutdown();
            if (!ex.awaitTermination(5, TimeUnit.SECONDS)) ex.shutdownNow();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            ex.shutdownNow();
        }
    }

    private static class SubscriptionHandle {
        final String id;
        final String channelId;
        final String exchange;
        final boolean autoAck;
        volatile String consumerTag;
        final AtomicBoolean stopped = new AtomicBoolean(false);

        SubscriptionHandle(String id, String channelId, String exchange, boolean autoAck) {
            this.id = id;
            this.channelId = channelId;
            this.exchange = exchange;
            this.autoAck = autoAck;
        }
    }
}