package me.blueslime.meteor.storage.database;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.query.StorageQuery;
import me.blueslime.meteor.storage.references.ReferencedObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public abstract class StorageDatabase implements PlatformService {

    private final ObjectMapper mapper;

    public StorageDatabase() {
        this.mapper = new ObjectMapper();
    }

    public abstract <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraIdentifier);

    public abstract <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier);

    public abstract <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String identifier);

    public abstract <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier);

    public abstract <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String identifier);

    public abstract <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier);

    public abstract <T extends StorageObject> CompletableFuture<Set<T>> loadAllAsync(Class<T> clazz);

    public abstract <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz);

    public abstract CompletableFuture<Void> saveOrUpdateAsync(StorageObject obj);

    public abstract <T extends StorageObject> CompletableFuture<Set<T>> matchAsync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> Set<T> matchSync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> CompletableFuture<Long> countAsync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> long countSync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> CompletableFuture<Long> countAsync(Class<T> clazz);

    public abstract <T extends StorageObject> long countSync(Class<T> clazz);

    public abstract <T extends StorageObject> CompletableFuture<Void> deleteAllAsync(Class<T> clazz);

    public abstract <T extends StorageObject> void deleteAllSync(Class<T> clazz);

    public abstract <T extends StorageObject> CompletableFuture<Void> deleteAllAsync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> void deleteAllSync(Class<T> clazz, StorageQuery query);

    public abstract void saveOrUpdateSync(StorageObject obj);

    public abstract void connect();

    public abstract void closeConnection();

    /* Helper to map primitive types to their wrapper classes. */
    public static Class<?> primitiveToWrapper(Class<?> primitive) {
        if (!primitive.isPrimitive()) return primitive;
        if (primitive == boolean.class) return Boolean.class;
        if (primitive == byte.class)    return Byte.class;
        if (primitive == char.class)    return Character.class;
        if (primitive == short.class)   return Short.class;
        if (primitive == int.class)     return Integer.class;
        if (primitive == long.class)    return Long.class;
        if (primitive == float.class)   return Float.class;
        if (primitive == double.class)  return Double.class;
        if (primitive == void.class)    return Void.class;
        return primitive; // fallback (shouldn't happen)
    }

    /**
     * Checks whether the underlying handle is an instance of the given type.
     *
     * @param type The class to check compatibility with.
     * @return true if the handle is non-null and can be cast to {@code type}, false otherwise.
     */
    public boolean is(Class<?> type) {
        return false;
    }

    /**
     * Casts the underlying handle to a specific class type if compatible.
     * <p>
     * If the handle is not compatible with {@code type} this method returns {@code null}
     * (no ClassCastException will be thrown).
     *
     * @param type The class to cast the handle to.
     * @param <T>  The type of the class.
     * @return The cast handle, or {@code null} if not compatible.
     */
    public <T> T to(Class<T> type) {
        return null;
    }

    protected void logError(String message, Exception e) {
        if (e == null) {
            getLogger().error(message);
            return;
        }
        getLogger().error(e, message);
    }

    public ObjectMapper mapper() {
        return mapper;
    }

}
