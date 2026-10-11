package me.blueslime.meteor.storage.database;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.mapper.ObjectMapper;
import me.blueslime.meteor.storage.query.StorageQuery;
import me.blueslime.meteor.storage.references.ReferencedObject;

import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public abstract class StorageDatabase implements PlatformService {

    private static final long EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS = 10L;

    private final ObjectMapper mapper;

    private final Object executorLock = new Object();

    private volatile ExecutorService asyncExecutor;

    protected StorageDatabase() {
        this.mapper = new ObjectMapper();

        initializeAsyncExecutor();
    }

    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraIdentifier) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(extraIdentifier, "extraIdentifier");

        return supplyAsync(
                () -> loadByExtraIdentifierSync(
                        clazz,
                        extraIdentifier
                )
        );
    }

    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String identifier) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(identifier, "identifier");

        return supplyAsync(
                () -> loadByIdSync(
                        clazz,
                        identifier
                )
        );
    }

    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String identifier) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(identifier, "identifier");

        return runAsync(
                () -> deleteByIdSync(
                        clazz,
                        identifier
                )
        );
    }

    public <T extends StorageObject> CompletableFuture<Set<T>> loadAllAsync(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");

        return supplyAsync(
                () -> loadAllSync(clazz)
        );
    }

    public CompletableFuture<Void> saveOrUpdateAsync(StorageObject object) {
        Objects.requireNonNull(object, "object");

        return runAsync(
                () -> saveOrUpdateSync(object)
        );
    }

    public <T extends StorageObject> CompletableFuture<Set<T>> matchAsync(Class<T> clazz, StorageQuery query) {
        Objects.requireNonNull(clazz, "clazz");

        return supplyAsync(
                () -> matchSync(
                        clazz,
                        query
                )
        );
    }

    public <T extends StorageObject> CompletableFuture<Long> countAsync(Class<T> clazz, StorageQuery query) {
        Objects.requireNonNull(clazz, "clazz");

        return supplyAsync(
            () -> countSync(
                clazz,
                query
            )
        );
    }

    public <T extends StorageObject> CompletableFuture<Long> countAsync(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");

        return supplyAsync(
            () -> countSync(clazz)
        );
    }

    public <T extends StorageObject> CompletableFuture<Boolean> deleteExtraIdentifierAsync(Class<T> clazz, String extraIdentifier) {
        Objects.requireNonNull(clazz, "clazz");
        Objects.requireNonNull(extraIdentifier, "extraIdentifier");

        return supplyAsync(
            () -> deleteExtraIdentifierSync(
                clazz,
                extraIdentifier
            )
        );
    }

    public <T extends StorageObject> CompletableFuture<Void> deleteAllAsync(Class<T> clazz) {
        Objects.requireNonNull(clazz, "clazz");

        return runAsync(
            () -> deleteAllSync(clazz)
        );
    }

    public <T extends StorageObject> CompletableFuture<Void> deleteAllAsync(Class<T> clazz, StorageQuery query) {
        Objects.requireNonNull(clazz, "clazz");

        return runAsync(
            () -> deleteAllSync(
                clazz,
                query
            )
        );
    }

    public abstract <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier);

    public abstract <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier);

    public abstract <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier);

    public abstract <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz);

    public abstract <T extends StorageObject> Set<T> matchSync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> long countSync(Class<T> clazz, StorageQuery query);

    public abstract <T extends StorageObject> boolean deleteExtraIdentifierSync(Class<T> clazz, String extraIdentifier);

    public abstract <T extends StorageObject> long countSync(Class<T> clazz);

    public abstract <T extends StorageObject> void deleteAllSync(Class<T> clazz);

    public abstract <T extends StorageObject> void deleteAllSync(Class<T> clazz, StorageQuery query);

    public abstract void saveOrUpdateSync(StorageObject object);

    public abstract void connect();

    public abstract void closeConnection();

    protected final void initializeAsyncExecutor() {
        synchronized (executorLock) {
            if (
                asyncExecutor != null &&
                !asyncExecutor.isShutdown()
            ) {
                return;
            }

            ThreadFactory factory =
                    Thread
                            .ofVirtual()
                            .name(
                                    "meteor-storage-",
                                    0
                            )
                            .factory();

            asyncExecutor =
                    Executors.newThreadPerTaskExecutor(
                            factory
                    );
        }
    }

    protected final void shutdownAsyncExecutor() {
        ExecutorService executor;

        synchronized (executorLock) {
            executor = asyncExecutor;
            asyncExecutor = null;
        }

        if (executor == null) {
            return;
        }

        executor.shutdown();

        try {
            if (
                !executor.awaitTermination(
                    EXECUTOR_SHUTDOWN_TIMEOUT_SECONDS,
                    TimeUnit.SECONDS
                )
            ) {
                executor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            executor.shutdownNow();

            Thread.currentThread().interrupt();
        }
    }

    protected final ExecutorService asyncExecutor() {
        ExecutorService executor = asyncExecutor;

        if (
            executor == null ||
            executor.isShutdown()
        ) {
            throw new IllegalStateException(
                    "Storage async executor is not available"
            );
        }

        return executor;
    }

    protected final <T> CompletableFuture<T> supplyAsync(
            Supplier<T> supplier
    ) {
        Objects.requireNonNull(
                supplier,
                "supplier"
        );

        return CompletableFuture.supplyAsync(
                supplier,
                asyncExecutor()
        );
    }

    protected final CompletableFuture<Void> runAsync(
            Runnable runnable
    ) {
        Objects.requireNonNull(
                runnable,
                "runnable"
        );

        return CompletableFuture.runAsync(
                runnable,
                asyncExecutor()
        );
    }

    public boolean is(Class<?> type) {
        return false;
    }

    public <T> T to(Class<T> type) {
        return null;
    }


    public static Class<?> primitiveToWrapper(Class<?> primitive) {
        if (!primitive.isPrimitive()) {
            return primitive;
        }

        if (primitive == boolean.class) {
            return Boolean.class;
        }

        if (primitive == byte.class) {
            return Byte.class;
        }

        if (primitive == char.class) {
            return Character.class;
        }

        if (primitive == short.class) {
            return Short.class;
        }

        if (primitive == int.class) {
            return Integer.class;
        }

        if (primitive == long.class) {
            return Long.class;
        }

        if (primitive == float.class) {
            return Float.class;
        }

        if (primitive == double.class) {
            return Double.class;
        }

        if (primitive == void.class) {
            return Void.class;
        }

        return primitive;
    }

    protected void logError(
            String message,
            Exception exception
    ) {
        if (exception == null) {
            getLogger().error(message);
            return;
        }

        getLogger().error(
                exception,
                message
        );
    }

    public ObjectMapper mapper() {
        return mapper;
    }
}