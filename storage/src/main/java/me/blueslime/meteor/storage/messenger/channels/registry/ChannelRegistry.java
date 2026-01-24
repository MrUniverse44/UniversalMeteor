package me.blueslime.meteor.storage.messenger.channels.registry;

import me.blueslime.meteor.storage.messenger.channels.Channel;
import me.blueslime.meteor.storage.messenger.channels.parameter.ChannelMessageEvent;
import me.blueslime.meteor.storage.messenger.channels.listener.ChannelListener;
import me.blueslime.meteor.storage.messenger.Messenger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChannelRegistry {
    private final Logger logger;
    
    private final Map<String, Channel> channelsById = new ConcurrentHashMap<>();
    
    private final Map<String, List<ListenerInvoker>> listenersByChannelId = new ConcurrentHashMap<>();
    
    private final List<Messenger> messengers = Collections.synchronizedList(new ArrayList<>());
    
    private final Map<String, Map<Messenger, String>> subscriptions = new ConcurrentHashMap<>();

    public ChannelRegistry(Logger logger) {
        this.logger = logger;
    }

    public void addMessenger(Messenger messenger) {
        Objects.requireNonNull(messenger, "messenger");
        synchronized (messengers) {
            messengers.add(messenger);
        }
        
        for (Channel ch : channelsById.values()) {
            subscribeChannelOnMessenger(ch, messenger);
        }
    }

    public void removeMessenger(Messenger messenger) {
        synchronized (messengers) {
            messengers.remove(messenger);
        }
        
        for (String channelId : new ArrayList<>(subscriptions.keySet())) {
            Map<Messenger, String> map = subscriptions.get(channelId);
            if (map != null) {
                String subId = map.remove(messenger);
                if (subId != null) {
                    try { messenger.unsubscribe(subId); } catch (Exception e) { logger.warning("Failed to unsubscribe: " + e.getMessage()); }
                }
            }
        }
    }

    public void register(Channel channel) {
        String id = channel.getId();
        channelsById.put(id, channel);
        scanAndRegisterListeners(channel);
        logger.info("Registered channel: " + id + " class=" + channel.getClass().getName());

        
        synchronized (messengers) {
            for (Messenger m : messengers) subscribeChannelOnMessenger(channel, m);
        }
    }

    public void unregister(Channel channel) {
        String id = channel.getId();
        channelsById.remove(id);
        listenersByChannelId.remove(id);

        
        Map<Messenger, String> map = subscriptions.remove(id);
        if (map != null) {
            for (Map.Entry<Messenger, String> e : map.entrySet()) {
                try { e.getKey().unsubscribe(e.getValue()); } catch (Exception ex) { logger.warning("Failed unsubscribe: " + ex.getMessage()); }
            }
        }
    }

    private void subscribeChannelOnMessenger(Channel channel, Messenger messenger) {
        String channelId = channel.getId();
        
        subscriptions.computeIfAbsent(channelId, k -> new ConcurrentHashMap<>());
        Map<Messenger, String> map = subscriptions.get(channelId);
        if (map.containsKey(messenger)) return;

        
        try {
            String sid = messenger.subscribe(channelId, (ChannelMessageEvent evt) -> {
                try {
                    this.route(evt);
                } catch (Throwable t) {
                    logger.severe("Error routing event for channel " + channelId + " : " + t.getMessage());
                }
            });
            if (sid != null) map.put(messenger, sid);
        } catch (Exception e) {
            logger.warning("Failed to subscribe channel=" + channelId + " on messenger=" + messenger.getClass().getSimpleName() + " -> " + e.getMessage());
        }
    }

    private static List<Method> getAllMethodsIncludingSuperclasses(Class<?> clazz) {
        List<Method> methods = new ArrayList<>();
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            Method[] declared = c.getDeclaredMethods();
            Collections.addAll(methods, declared);
        }
        return methods;
    }

    private void scanAndRegisterListeners(Channel channel) {
        Class<?> clazz = channel.getClass();
        List<Method> methods = getAllMethodsIncludingSuperclasses(clazz);
        List<ListenerInvoker> invokers = new ArrayList<>();
        for (Method m : methods) {
            if (!m.isAnnotationPresent(ChannelListener.class)) continue;
            
            
            if (m.getParameterCount() != 1) {
                logger.warning("@ChannelListener method must have single parameter of type ChannelMessageEvent or subclass: " + m);
                continue;
            }
            Class<?> param = m.getParameterTypes()[0];
            if (!ChannelMessageEvent.class.isAssignableFrom(param)) {
                logger.warning("@ChannelListener parameter must extend ChannelMessageEvent: " + m);
                continue;
            }
            m.setAccessible(true);
            invokers.add(new ListenerInvoker(channel, m));
        }
        listenersByChannelId.put(channel.getId(), invokers);
    }

    public void route(ChannelMessageEvent event) {
        String id = event.getChannelId();
        List<ListenerInvoker> invs = listenersByChannelId.get(id);
        if (invs == null || invs.isEmpty()) return;
        for (ListenerInvoker inv : invs) {
            try {
                inv.invoke(event);
            } catch (Exception e) {
                logger.log(Level.SEVERE,"Error invoking listener for channel " + id + ": " + e.getMessage(), e);
            }
        }
    }

    private record ListenerInvoker(Object target, Method method) {

        void invoke(ChannelMessageEvent event) throws Exception {
                method.invoke(target, event);
            }
        }
}