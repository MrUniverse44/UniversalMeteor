package me.blueslime.meteor.storage.database;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.references.ReferencedObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    public abstract void saveOrUpdateSync(StorageObject obj);

    public abstract void connect();

    public abstract void closeConnection();

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
