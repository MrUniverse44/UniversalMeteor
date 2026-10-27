package me.blueslime.meteor.storage.types;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.database.AtomicDocumentStorage;
import me.blueslime.meteor.storage.database.AtomicMutation;
import me.blueslime.meteor.storage.interfaces.StorageExtraIdentifier;
import me.blueslime.meteor.storage.interfaces.StorageIdentifier;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.query.StorageQuery;
import me.blueslime.meteor.storage.references.ReferencedObject;
import org.bson.Document;
import org.bson.codecs.DocumentCodec;
import org.bson.codecs.EncoderContext;
import org.bson.BsonBinaryWriter;
import org.bson.io.BasicOutputBuffer;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.mongodb.client.model.Filters.eq;

@SuppressWarnings("unused")
public class MongoDatabase extends StorageDatabase implements AtomicDocumentStorage {

    private volatile MongoClient mongoClient;
    private volatile com.mongodb.client.MongoDatabase database;

    private final String uri;
    private final String databaseName;

    public MongoDatabase(String uri, String databaseName) {
        this.uri = uri;
        this.databaseName = databaseName;
    }

    @Override
    public synchronized void connect() {
        if (
            mongoClient != null
            && database != null
        ) {
            return;
        }

        initializeAsyncExecutor();

        MongoClientSettings settings =
            MongoClientSettings.builder()
                .applyConnectionString(
                    new ConnectionString(
                        uri
                    )
                )
                .build();

        MongoClient client =
                MongoClients.create(
                        settings
                );

        this.mongoClient =
                client;

        this.database =
                client.getDatabase(
                        databaseName
                );
    }

    @Override
    public synchronized void closeConnection() {
        /*
         * Wait for already-running async operations before closing the
         * synchronous MongoDB driver underneath them.
         */
        shutdownAsyncExecutor();

        MongoClient client =
                mongoClient;

        mongoClient = null;
        database = null;

        if (
                client != null
        ) {
            client.close();
        }
    }

    private void ensureConnected() {
        if (
            mongoClient == null
            || database == null
        ) {
            throw new IllegalStateException(
                    "MongoDatabase is not connected; call connect() first."
            );
        }
    }

    @Override
    public <T extends StorageObject> Set<T> matchSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();

        StorageQuery effectiveQuery =
                query == null
                        ? new StorageQuery()
                        : query;

        Set<T> results =
                new LinkedHashSet<>();

        MongoCollection<Document> collection =
                database.getCollection(
                        clazz.getSimpleName()
                );

        Document filter =
                buildBsonFilter(
                        effectiveQuery
                );

        var iterable =
                collection.find(
                        filter
                );

        if (
                effectiveQuery.getSortBy() != null
        ) {
            iterable.sort(
                    new Document(
                            effectiveQuery.getSortBy(),
                            effectiveQuery.isSortDescending()
                                    ? -1
                                    : 1
                    )
            );
        }

        if (
                effectiveQuery.getLimit() != null
        ) {
            iterable.limit(
                    effectiveQuery.getLimit()
            );
        }

        for (
            Document document :
            iterable
        ) {
            T object =
                    mapper().fromDocument(
                            clazz,
                            document
                    );

            if (
                    object != null
            ) {
                results.add(
                        object
                );
            }
        }

        return results;
    }

    private Document buildBsonFilter(StorageQuery query) {
        if (
            query == null
            || query
                .getConditions()
                .isEmpty()
        ) {
            return new Document();
        }

        List<Document> conditions =
                new ArrayList<>();

        for (
            StorageQuery.Condition condition :
            query.getConditions()
        ) {
            String key = condition.key();

            Object value = normalizeMongoValue(condition.value());

            Document predicate =
                    switch (
                        condition.operator()
                    ) {
                        case EQUALS ->
                                new Document(
                                        key,
                                        value
                                );

                        case GREATER_THAN ->
                                new Document(
                                        key,
                                        new Document(
                                                "$gt",
                                                value
                                        )
                                );

                        case LESS_THAN ->
                                new Document(
                                        key,
                                        new Document(
                                                "$lt",
                                                value
                                        )
                                );

                        case CONTAINS ->
                                new Document(
                                        key,
                                        new Document(
                                                "$regex",
                                                ".*"
                                                        + java.util.regex.Pattern.quote(
                                                            String.valueOf(
                                                                    condition.value()
                                                            )
                                                        )
                                                        + ".*"
                                        ).append(
                                                "$options",
                                                "i"
                                        )
                                );

                        case STARTS_WITH ->
                                new Document(
                                        key,
                                        new Document(
                                                "$regex",
                                                "^"
                                                        + java.util.regex.Pattern.quote(
                                                            String.valueOf(
                                                                    condition.value()
                                                            )
                                                        )
                                        ).append(
                                                "$options",
                                                "i"
                                        )
                                );

                        case ENDS_WITH ->
                                new Document(
                                        key,
                                        new Document(
                                                "$regex",
                                                java.util.regex.Pattern.quote(
                                                        String.valueOf(
                                                                condition.value()
                                                        )
                                                )
                                                        + "$"
                                        ).append(
                                                "$options",
                                                "i"
                                        )
                                );

                        case PRESENT ->
                                new Document(
                                        key,
                                        new Document(
                                                "$exists",
                                                Boolean.TRUE.equals(
                                                        condition.value()
                                                )
                                        )
                                );
                    };

            conditions.add(
                    predicate
            );
        }

        if (
                conditions.size() == 1
        ) {
            return conditions.getFirst();
        }

        return new Document(
                "$and",
                conditions
        );
    }

    private static Object normalizeMongoValue(
            Object value
    ) {
        if (
                value instanceof Enum<?> enumValue
        ) {
            return enumValue.toString();
        }

        return value;
    }

    @Override
    public <T extends StorageObject> long countSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();

        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName());

        return collection.countDocuments(buildBsonFilter(query));
    }

    @Override
    public <T extends StorageObject> long countSync(Class<T> clazz) {
        return countSync(
            clazz,
            null
        );
    }

    @Override
    public void saveOrUpdateSync(StorageObject object) {
        if (object == null) {
            return;
        }

        ensureConnected();

        Document document = mapper().toDocument(object);

        String collectionName = object.getClass().getSimpleName();

        String identifier = extractIdentifier(object);

        MongoCollection<Document> collection = database.getCollection(collectionName);

        ReplaceOptions options = new ReplaceOptions().upsert(true);

        if (identifier != null) {
            collection.replaceOne(
                eq(
                    "_id",
                    identifier
                ),
                document,
                options
            );
        } else {
            collection.insertOne(
                    document
            );
        }

        /*
         * Legacy support only. New code should use StorageQuery and real
         * database indexes instead of StorageExtraIdentifier.
         */
        Set<String> extraIdentifiers = extractExtraIdentifier(object);

        if (
            identifier != null
            && !extraIdentifiers.isEmpty()
        ) {
            MongoCollection<Document> namingCollection = database.getCollection(collectionName + "-StringNaming");

            Document reference = new Document("referenced", identifier);

            for (
                String extra :
                extraIdentifiers
            ) {
                Document completed =
                    new Document()
                        .append(
                            "extra",
                            extra
                        )
                        .append(
                            "data",
                            reference
                        );

                namingCollection.replaceOne(
                    eq(
                        "_id",
                        extra.toLowerCase(
                            Locale.ENGLISH
                        )
                    ),
                    completed,
                    options
                );
            }
        }
    }

    /**
     * One-document operations work on a standalone MongoDB without a replica set.
     * This is NOT a multi-document transaction API.
     */
    @Override
    public boolean insertIfAbsentSync(StorageObject entity) {
        Objects.requireNonNull(entity, "entity");
        ensureConnected();
        String id = extractIdentifier(entity);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("insertIfAbsentSync requires @StorageIdentifier");
        }
        Document doc = mapper().toDocument(entity);
        if (!id.equals(String.valueOf(doc.get("_id")))) {
            throw new IllegalArgumentException("Entity mapper must serialize its identifier as _id");
        }
        checkAtomicPayloadSize(doc);
        MongoCollection<Document> collection = database.getCollection(entity.getClass().getSimpleName());
        try {
            collection.insertOne(doc);
            return true;
        } catch (MongoWriteException ex) {
            if (ex.getError().getCode() != 11000) throw ex;
            // Different unique-index violations MUST NOT be confused with a
            // repeated idempotency key / already-existing primary key.
            if (collection.find(eq("_id", id)).first() == null) throw ex;
            return false;
        }
    }

    @Override
    public <T extends StorageObject> Optional<T> updateIfSync(
            Class<T> type, String identifier, StorageQuery preconditions, AtomicMutation mutation) {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(identifier, "identifier");
        Objects.requireNonNull(mutation, "mutation");
        if (identifier.isBlank()) throw new IllegalArgumentException("identifier cannot be blank");
        ensureConnected();
        Document predicate = new Document("_id", identifier);
        if (preconditions != null && !preconditions.isEmpty()) {
            // StorageQuery predates AtomicDocumentStorage; do not allow
            // untrusted callers to smuggle special BSON field names.
            for (StorageQuery.Condition condition : preconditions.getConditions()) {
                if (!condition.key().matches("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")) {
                    throw new IllegalArgumentException("Invalid atomic predicate field: " + condition.key());
                }
            }
            predicate = new Document("$and", List.of(predicate, buildBsonFilter(preconditions)));
        }
        Document patch = new Document();
        if (!mutation.sets().isEmpty()) {
            patch.put("$set", new Document(mutation.sets()));
        }
        if (!mutation.increments().isEmpty()) {
            patch.put("$inc", new Document(mutation.increments()));
        }
        if (!mutation.unsets().isEmpty()) {
            Document unsets = new Document();
            for (String field : mutation.unsets()) unsets.put(field, "");
            patch.put("$unset", unsets);
        }
        checkAtomicPayloadSize(patch);
        // Atomic on the server: read/modify/write is ONE MongoDB command.
        // No upsert, no separate read, no Java synchronized blocks.
        Document updated = database.getCollection(type.getSimpleName())
                .findOneAndUpdate(predicate, patch,
                        new FindOneAndUpdateOptions().upsert(false)
                                .returnDocument(ReturnDocument.AFTER));
        return Optional.ofNullable(mapper().fromDocument(type, updated));
    }

    private static void checkAtomicPayloadSize(Document document) {
        try (BasicOutputBuffer buffer = new BasicOutputBuffer();
             BsonBinaryWriter writer = new BsonBinaryWriter(buffer)) {
            new DocumentCodec().encode(writer, document, EncoderContext.builder().build());
            if (buffer.getSize() > 4 * 1024 * 1024) {
                throw new IllegalArgumentException("Atomic MongoDB payload exceeds 4 MiB");
            }
        }
    }

    public CompletableFuture<Boolean> insertIfAbsentAsync(StorageObject entity) {
        return supplyAsync(() -> insertIfAbsentSync(entity));
    }

    public <T extends StorageObject> CompletableFuture<Optional<T>> updateIfAsync(
            Class<T> type, String identifier, StorageQuery preconditions, AtomicMutation mutation) {
        return supplyAsync(() -> updateIfSync(type, identifier, preconditions, mutation));
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier) {
        ensureConnected();

        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName());

        Document document = collection.find(eq("_id", identifier)).first();

        if (document == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(mapper().fromDocument(clazz, document));
    }

    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensureConnected();

        Set<T> results = new LinkedHashSet<>();

        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName());

        for (
            Document document :
            collection.find()
        ) {
            T object = mapper().fromDocument(clazz, document);

            if (
                object != null
            ) {
                results.add(
                        object
                );
            }
        }

        return results;
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier) {
        ensureConnected();

        String collectionName =
                clazz.getSimpleName();

        database.getCollection(collectionName + "-StringNaming").deleteMany(eq("data.referenced", identifier));

        database.getCollection(collectionName).deleteOne(eq("_id", identifier));
    }

    @Override
    public <T extends StorageObject> void deleteAllSync(Class<T> clazz) {
        ensureConnected();

        String collectionName = clazz.getSimpleName();

        database.getCollection(collectionName).deleteMany(new Document());

        database.getCollection(collectionName + "-StringNaming").deleteMany(new Document());
    }

    @Override
    public <T extends StorageObject> void deleteAllSync(Class<T> clazz, StorageQuery query) {
        if (
            query == null
            || query.isEmpty()
        ) {
            deleteAllSync(
                    clazz
            );

            return;
        }

        Set<T> matching = matchSync(clazz, query);

        for (
            T object :
            matching
        ) {
            String identifier = extractIdentifier(object);

            if (
                identifier != null
            ) {
                deleteByIdSync(
                    clazz,
                    identifier
                );
            }
        }
    }

    /*
     * ============================================================
     * LEGACY EXTRA IDENTIFIERS
     * ============================================================
     */

    @Deprecated(forRemoval = false)
    public <T extends StorageObject> boolean checkExtraIdentifierExistsSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName() + "-StringNaming");

        return collection.find(eq("_id", extraIdentifier.toLowerCase(Locale.ENGLISH))).first() != null;
    }

    @Deprecated(forRemoval = false)
    public <T extends StorageObject> CompletableFuture<Boolean> checkExtraIdentifierExistsAsync(Class<T> clazz, String extraIdentifier) {
        return supplyAsync(
            () -> checkExtraIdentifierExistsSync(
                clazz,
                extraIdentifier
            )
        );
    }

    @Override
    public <T extends StorageObject> boolean deleteExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName() + "-StringNaming");

        DeleteResult result = collection.deleteOne(eq("_id", extraIdentifier.toLowerCase(Locale.ENGLISH)));

        return result.getDeletedCount()
                > 0;
    }

    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        String normalized = extraIdentifier.toLowerCase(Locale.ENGLISH);

        MongoCollection<Document> collection =
                database.getCollection(
                        clazz.getSimpleName()
                                + "-StringNaming"
                );

        Document document = collection.find(eq("_id", normalized)).first();

        if (
            document == null
        ) {
            return Optional.empty();
        }

        String original = document.getString("extra");

        Document data = document.get("data", Document.class);

        if (
            data == null
        ) {
            return Optional.empty();
        }

        String reference = data.getString("referenced");

        if (
            reference == null
        ) {
            return Optional.empty();
        }

        return Optional.of(
            new ReferencedObject(
                original,
                reference
            )
        );
    }

    @Override
    public boolean is(Class<?> type) {
        Object handle = database;

        if (
            handle == null
            || type == null
        ) {
            return false;
        }

        if (
            type == Object.class
        ) {
            return true;
        }

        Class<?> check =
            type.isPrimitive()
                ? primitiveToWrapper(
                    type
                )
                : type;

        return check.isInstance(
            handle
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T to(
        Class<T> type
    ) {
        return is(type)
            ? (T) database
            : null;
    }

    private String extractIdentifier(StorageObject object) {
        for (
            Class<?> current = object.getClass();

            current != null && current != Object.class;

            current = current.getSuperclass()
        ) {
            for (
                Field field :
                current.getDeclaredFields()
            ) {
                if (
                    !field.isAnnotationPresent(
                        StorageIdentifier.class
                    )
                ) {
                    continue;
                }

                field.setAccessible(
                    true
                );

                try {
                    Object value = field.get(object);

                    return value == null
                        ? null
                        : value.toString();

                } catch (ReflectiveOperationException exception) {
                    logError(
                        "Failed to extract identifier",
                        exception
                    );

                    return null;
                }
            }
        }

        return null;
    }

    private Set<String> extractExtraIdentifier(StorageObject object) {
        Set<String> extraIdentifiers = new HashSet<>();

        for (
            Class<?> current = object.getClass();

            current != null && current != Object.class;

            current = current.getSuperclass()
        ) {
            for (
                Field field :
                current.getDeclaredFields()
            ) {
                if (!field.isAnnotationPresent(StorageExtraIdentifier.class)) {
                    continue;
                }

                field.setAccessible(
                        true
                );

                try {
                    Object value = field.get(object);

                    if (
                        value != null
                    ) {
                        extraIdentifiers.add(value.toString());
                    }

                } catch (ReflectiveOperationException exception) {
                    logError(
                        "Failed to extract extra identifier",
                        exception
                    );
                }
            }
        }

        return extraIdentifiers;
    }
}
