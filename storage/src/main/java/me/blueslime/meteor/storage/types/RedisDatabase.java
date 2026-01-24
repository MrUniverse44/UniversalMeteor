package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;

import me.blueslime.meteor.storage.references.ReferencedObject;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Redis-backed implementation of StorageDatabase that uses annotations:
 * - @StorageIdentifier on the field that contains the primary identifier
 * - @StorageExtraIdentifier on fields that must be indexable as extra identifiers
 * <p>
 * Keys used:
 * - object key: {prefix}:{ClassSimpleName}:{identifier} -> JSON (mapper.toJsonCompatible)
 * - ids set:   {prefix}:ids:{ClassSimpleName} -> set of identifiers
 * - naming:    {prefix}:{ClassSimpleName}:StringNaming:{extraLower} -> identifier (string)
 * - extras idx per object: {prefix}:ids:{ClassSimpleName}:extras:{identifier} -> set of extras (lowercase)
 * <p>
 * Async methods run on provided executor.
 */
@SuppressWarnings("unused")
public class RedisDatabase extends StorageDatabase {

    private final JedisPool jedisPool;
    private final ExecutorService executor;
    private final String prefix;

    public RedisDatabase(Logger logger, JedisPool jedisPool, ExecutorService executor, String prefix) {
        super(logger);
        this.jedisPool = jedisPool;
        this.executor = executor != null ? executor :
                Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        this.prefix = (prefix == null || prefix.isEmpty()) ? "storage" : prefix;
    }

    public RedisDatabase(Logger logger, JedisPool jedisPool) {
        this(logger, jedisPool, null, "storage");
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

    
    
    
    private String extractIdentifier(StorageObject obj) {
        if (obj == null) return null;
        Class<?> clazz = obj.getClass();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(StorageIdentifier.class)) {
                f.setAccessible(true);
                try {
                    Object v = f.get(obj);
                    return v != null ? v.toString() : null;
                } catch (Exception e) {
                    logError("Failed to extract identifier from field " + f.getName(), e);
                    return null;
                }
            }
        }
        return null;
    }

    private Set<String> extractExtraIdentifier(StorageObject obj) {
        Set<String> extras = new HashSet<>();
        if (obj == null) return extras;
        Class<?> clazz = obj.getClass();
        for (Field f : clazz.getDeclaredFields()) {
            if (f.isAnnotationPresent(StorageExtraIdentifier.class)) {
                f.setAccessible(true);
                try {
                    Object v = f.get(obj);
                    if (v != null) extras.add(v.toString());
                } catch (Exception e) {
                    logError("Failed to extract extra identifier from field " + f.getName(), e);
                }
            }
        }
        return extras;
    }

    
    
    
    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensurePool();
        if (extraIdentifier == null) return Optional.empty();
        String extraLower = extraIdentifier.toLowerCase(Locale.ENGLISH);
        try (Jedis j = jedisPool.getResource()) {
            String namingKey = namingKeyFor(clazz, extraLower);
            String referenced = j.get(namingKey);
            if (referenced != null) {
                
                return Optional.of(new ReferencedObject(extraIdentifier, referenced));
            }

            
            Set<String> ids = j.smembers(idsKeyFor(clazz));
            for (String id : ids) {
                String json = j.get(keyFor(clazz, id));
                if (json == null) continue;
                Object parsed = mapper().fromJsonCompatible(json);
                if (parsed instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = new HashMap<>((Map<String, Object>) parsed);
                    
                    if (map.values().stream().anyMatch(v -> v != null && extraIdentifier.equals(v.toString()))) {
                        return Optional.of(new ReferencedObject(extraIdentifier, id));
                    }
                    
                    for (Object v : map.values()) {
                        if (v instanceof Collection<?> coll) {
                            for (Object e : coll) if (e != null && extraIdentifier.equals(e.toString()))
                                return Optional.of(new ReferencedObject(extraIdentifier, id));
                        } else if (v instanceof Map<?, ?> inner) {
                            if (inner.values().stream().anyMatch(x -> x != null && extraIdentifier.equals(x.toString()))) {
                                return Optional.of(new ReferencedObject(extraIdentifier, id));
                            }
                        }
                    }
                } else {
                    if (extraIdentifier.equals(String.valueOf(parsed))) {
                        return Optional.of(new ReferencedObject(extraIdentifier, id));
                    }
                }
            }
        } catch (Exception e) {
            logError("Failed loadByExtraIdentifierSync for " + clazz.getName(), e);
        }
        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraIdentifier) {
        return supplyAsync(() -> loadByExtraIdentifierSync(clazz, extraIdentifier));
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String identifier) {
        return supplyAsync(() -> loadByIdSync(clazz, identifier));
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier) {
        ensurePool();
        try (Jedis j = jedisPool.getResource()) {
            String key = keyFor(clazz, identifier);
            String json = j.get(key);
            if (json == null) return Optional.empty();
            Object parsed = mapper().fromJsonCompatible(json);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = new HashMap<>((Map<String, Object>) parsed);
                T obj = mapper().fromMap(clazz, map, identifier);
                return Optional.ofNullable(obj);
            } else {
                logger.log(Level.WARNING, "Stored JSON for key " + key + " is not an object.");
                return Optional.empty();
            }
        } catch (Exception e) {
            logError("Failed loadByIdSync for " + clazz.getName() + " id: " + identifier, e);
            return Optional.empty();
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String identifier) {
        return runAsync(() -> deleteByIdSync(clazz, identifier));
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier) {
        ensurePool();
        try (Jedis j = jedisPool.getResource()) {
            String key = keyFor(clazz, identifier);
            j.del(key);
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
            logError("Failed deleteByIdSync for " + clazz.getName() + " id: " + identifier, e);
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
            if (ids == null || ids.isEmpty()) return out;
            for (String id : ids) {
                loadByIdSync(clazz, id).ifPresent(out::add);
            }
        } catch (Exception e) {
            logError("Failed loadAllSync for " + clazz.getName(), e);
        }
        return out;
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
        try (Jedis j = jedisPool.getResource()) {
            String identifier = extractIdentifier(obj);
            boolean createdId = false;
            if (identifier == null || identifier.isEmpty()) {
                
                identifier = UUID.randomUUID().toString();
                
                try {
                    for (Field f : clazz.getDeclaredFields()) {
                        if (f.isAnnotationPresent(StorageIdentifier.class) || "id".equalsIgnoreCase(f.getName()) || "identifier".equalsIgnoreCase(f.getName())) {
                            f.setAccessible(true);
                            Object cur = f.get(obj);
                            if (cur == null) {
                                f.set(obj, identifier);
                            }
                            break;
                        }
                    }
                } catch (Exception e) {
                    logger.log(Level.FINE, "Could not set generated identifier back to object", e);
                }
            }

            
            String json = mapper().toJsonCompatible(obj);

            
            String key = keyFor(clazz, identifier);
            j.set(key, json);
            j.sadd(idsKeyFor(clazz), identifier);

            
            Set<String> newExtras = new HashSet<>();
            for (String ex : extractExtraIdentifier(obj)) {
                if (ex == null) continue;
                newExtras.add(ex.toLowerCase(Locale.ENGLISH));
            }

            String extrasIndexKey = extrasIndexKeyFor(clazz, identifier);
            
            Set<String> prevExtras = j.smembers(extrasIndexKey);
            if (prevExtras == null) prevExtras = Collections.emptySet();

            
            for (String prev : prevExtras) {
                if (!newExtras.contains(prev)) {
                    try {
                        j.del(namingKeyFor(clazz, prev));
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to remove stale naming key for " + prev, e);
                    }
                }
            }

            
            if (!newExtras.isEmpty()) {
                for (String exLower : newExtras) {
                    try {
                        j.set(namingKeyFor(clazz, exLower), identifier);
                        j.sadd(extrasIndexKey, exLower);
                    } catch (Exception e) {
                        logger.log(Level.WARNING, "Failed to set naming key for " + exLower, e);
                    }
                }
            } else {
                
                if (!prevExtras.isEmpty()) {
                    j.del(extrasIndexKey);
                }
            }
        } catch (Exception e) {
            logError("Failed saveOrUpdateSync for " + obj.getClass().getName(), e);
        }
    }

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
            logger.log(Level.WARNING, "Error closing JedisPool", e);
        }
        try {
            if (executor != null) {
                executor.shutdown();
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    
    
    
    private <U> CompletableFuture<U> supplyAsync(SupplierWithException<U> supplier) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return supplier.get();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    private CompletableFuture<Void> runAsync(RunnableWithException runnable) {
        return CompletableFuture.runAsync(() -> {
            try {
                runnable.run();
            } catch (RuntimeException re) {
                throw re;
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, executor);
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get() throws Exception;
    }

    @FunctionalInterface
    private interface RunnableWithException {
        void run() throws Exception;
    }
}
