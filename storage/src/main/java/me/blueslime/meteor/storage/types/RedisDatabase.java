package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;
import me.blueslime.meteor.storage.references.ReferencedObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;

/**
 * Redis-backed implementation optimized for the new ObjectMapper.
 * Uses native JSON serialization provided by the mapper.
 */
@SuppressWarnings("unused")
public class RedisDatabase extends StorageDatabase {

    private final JedisPool jedisPool;
    private final ExecutorService executor;
    private final String prefix;

    public RedisDatabase(JedisPool jedisPool, ExecutorService executor, String prefix) {
        this.jedisPool = jedisPool;
        this.executor = executor != null ? executor :
                Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.prefix = (prefix == null || prefix.isEmpty()) ? "storage" : prefix;
    }

    public RedisDatabase(JedisPool jedisPool) {
        this(jedisPool, null, "storage");
    }

    private String keyFor(Class<?> clazz, String identifier) {
        return prefix + ":" + clazz.getSimpleName() + ":" + identifier;
    }
    private String idsKeyFor(Class<?> clazz) {
        return prefix + ":ids:" + clazz.getSimpleName();
    }
    private String namingKeyFor(Class<?> clazz, String extraLower) {
        return prefix + ":" + clazz.getSimpleName() + ":StringNaming:" + extraLower;
    }
    private String extrasIndexKeyFor(Class<?> clazz, String identifier) {
        return prefix + ":ids:" + clazz.getSimpleName() + ":extras:" + identifier;
    }

    private void ensurePool() {
        if (jedisPool == null) throw new IllegalStateException("JedisPool is null. Call connect() or provide a pool.");
    }

    @Override
    public CompletableFuture<Void> saveOrUpdateAsync(StorageObject obj) {
        return runAsync(() -> saveOrUpdateSync(obj));
    }

    @Override
    public void saveOrUpdateSync(StorageObject obj) {
        if (obj == null) return;
        ensurePool();
        Class<?> clazz = obj.getClass();

        String identifier = extractIdentifier(obj);
        if (identifier == null || identifier.isEmpty()) {
            identifier = UUID.randomUUID().toString();
            injectIdentifier(obj, identifier);
        }

        String json = mapper().toJson(obj);

        try (Jedis j = jedisPool.getResource()) {
            String key = keyFor(clazz, identifier);
            j.set(key, json);
            j.sadd(idsKeyFor(clazz), identifier);

            updateExtraIdentifiers(j, obj, identifier);
        } catch (Exception e) {
            logError("Failed saveOrUpdateSync for " + clazz.getSimpleName(), e);
        }
    }

    private void updateExtraIdentifiers(Jedis j, StorageObject obj, String identifier) {
        Class<?> clazz = obj.getClass();
        String extrasIndexKey = extrasIndexKeyFor(clazz, identifier);

        Set<String> newExtras = new HashSet<>();
        for (String ex : extractExtraIdentifier(obj)) {
            if (ex != null) newExtras.add(ex.toLowerCase(Locale.ENGLISH));
        }

        Set<String> prevExtras = j.smembers(extrasIndexKey);
        if (prevExtras == null) prevExtras = Collections.emptySet();

        for (String prev : prevExtras) {
            if (!newExtras.contains(prev)) {
                j.del(namingKeyFor(clazz, prev));
            }
        }

        if (!newExtras.isEmpty()) {
            for (String exLower : newExtras) {
                j.set(namingKeyFor(clazz, exLower), identifier);
                j.sadd(extrasIndexKey, exLower);
            }
        } else if (!prevExtras.isEmpty()) {
            j.del(extrasIndexKey);
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String identifier) {
        return supplyAsync(() -> loadByIdSync(clazz, identifier));
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier) {
        ensurePool();
        try (Jedis j = jedisPool.getResource()) {
            String json = j.get(keyFor(clazz, identifier));
            if (json == null) return Optional.empty();

            return Optional.ofNullable(mapper().fromJson(json, clazz));
        } catch (Exception e) {
            logError("Failed loadByIdSync for " + clazz.getSimpleName(), e);
            return Optional.empty();
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraIdentifier) {
        return supplyAsync(() -> loadByExtraIdentifierSync(clazz, extraIdentifier));
    }

    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensurePool();
        if (extraIdentifier == null) return Optional.empty();

        String extraLower = extraIdentifier.toLowerCase(Locale.ENGLISH);
        try (Jedis j = jedisPool.getResource()) {
            String namingKey = namingKeyFor(clazz, extraLower);
            String referencedId = j.get(namingKey);

            if (referencedId != null) {
                return Optional.of(new ReferencedObject(extraIdentifier, referencedId));
            }
        } catch (Exception e) {
            logError("Failed loadByExtraIdentifierSync for " + clazz.getSimpleName(), e);
        }
        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String identifier) {
        return runAsync(() -> deleteByIdSync(clazz, identifier));
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier) {
        ensurePool();
        try (Jedis j = jedisPool.getResource()) {
            j.del(keyFor(clazz, identifier));
            j.srem(idsKeyFor(clazz), identifier);

            String extrasIndexKey = extrasIndexKeyFor(clazz, identifier);
            Set<String> extras = j.smembers(extrasIndexKey);
            if (extras != null) {
                for (String extraLower : extras) {
                    j.del(namingKeyFor(clazz, extraLower));
                }
            }
            j.del(extrasIndexKey);
        } catch (Exception e) {
            logError("Failed deleteByIdSync for " + clazz.getSimpleName(), e);
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Set<T>> loadAllAsync(Class<T> clazz) {
        return supplyAsync(() -> loadAllSync(clazz));
    }

    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensurePool();
        Set<T> out = new HashSet<>();
        try (Jedis j = jedisPool.getResource()) {
            Set<String> ids = j.smembers(idsKeyFor(clazz));
            if (ids != null) {
                for (String id : ids) {
                    loadByIdSync(clazz, id).ifPresent(out::add);
                }
            }
        } catch (Exception e) {
            logError("Failed loadAllSync for " + clazz.getSimpleName(), e);
        }
        return out;
    }

    // --- CONNECTION & HELPERS ---

    @Override
    public void connect() {
        ensurePool();
        try (Jedis j = jedisPool.getResource()) {
            j.ping();
        } catch (Exception e) {
            logError("Redis connect failed", e);
            throw (RuntimeException) e;
        }
    }

    @Override
    public void closeConnection() {
        try {
            if (jedisPool != null) jedisPool.close();
        } catch (Exception e) {
            getLogger().error(e, "Error closing JedisPool");
        }
        try {
            if (executor != null) executor.shutdown();
        } catch (Exception ignored) {}
    }

    private String extractIdentifier(StorageObject obj) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(StorageIdentifier.class)) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(obj);
                        return v != null ? v.toString() : null;
                    } catch (Exception e) { return null; }
                }
            }
        }
        return null;
    }

    private void injectIdentifier(StorageObject obj, String id) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(StorageIdentifier.class)) {
                    f.setAccessible(true);
                    try { f.set(obj, id); } catch (Exception ignored) {}
                    return;
                }
            }
        }
    }

    private Set<String> extractExtraIdentifier(StorageObject obj) {
        Set<String> extras = new HashSet<>();
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(StorageExtraIdentifier.class)) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(obj);
                        if (v != null) extras.add(v.toString());
                    } catch (Exception ignored) {}
                }
            }
        }
        return extras;
    }

    private <U> CompletableFuture<U> supplyAsync(SupplierWithException<U> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try { return supplier.get(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    private CompletableFuture<Void> runAsync(RunnableWithException runnable) {
        return CompletableFuture.runAsync(() -> {
            try { runnable.run(); }
            catch (Exception e) { throw new CompletionException(e); }
        }, executor);
    }

    @FunctionalInterface private interface SupplierWithException<T> { T get() throws Exception; }
    @FunctionalInterface private interface RunnableWithException { void run() throws Exception; }
}