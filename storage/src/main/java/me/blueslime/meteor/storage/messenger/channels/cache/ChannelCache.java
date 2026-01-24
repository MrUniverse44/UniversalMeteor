package me.blueslime.meteor.storage.messenger.channels.cache;

import java.util.Map;
import java.util.concurrent.*;

public class ChannelCache {
    private final Map<String, Object> map = new ConcurrentHashMap<>(); 
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final Map<String, ScheduledFuture<?>> expirations = new ConcurrentHashMap<>();

    public void put(String key, Object value) {
        put(key, value, 0L);
    }

    public void put(String key, Object value, long ttlMillis) {
        map.put(key, value);
        
        ScheduledFuture<?> prev = expirations.remove(key);
        if (prev != null) prev.cancel(false);
        if (ttlMillis > 0) {
            ScheduledFuture<?> f = scheduler.schedule(() -> {
                map.remove(key);
                expirations.remove(key);
            }, ttlMillis, TimeUnit.MILLISECONDS);
            expirations.put(key, f);
        }
    }

    public Object get(String key) {
        return map.get(key);
    }

    public Object remove(String key) {
        ScheduledFuture<?> f = expirations.remove(key);
        if (f != null) f.cancel(false);
        return map.remove(key);
    }

    public boolean contains(String key) {
        return map.containsKey(key);
    }

    public void clear() {
        for (ScheduledFuture<?> f : expirations.values()) f.cancel(false);
        expirations.clear();
        map.clear();
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}

