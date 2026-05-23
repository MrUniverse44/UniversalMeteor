package me.blueslime.meteor.storage.types;

import com.mongodb.client.result.DeleteResult;
import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;
import me.blueslime.meteor.storage.query.StorageQuery;
import me.blueslime.meteor.storage.references.ReferencedObject;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.bson.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.mongodb.client.model.Filters.eq;

@SuppressWarnings("unused")
public class MongoDatabase extends StorageDatabase {

    private MongoClient mongoClient;
    private com.mongodb.client.MongoDatabase database;

    private final String uri;
    private final String databaseName;

    public MongoDatabase(String uri, String databaseName) {
        this.uri = uri;
        this.databaseName = databaseName;
    }

    @Override
    public void connect() {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .build();
        this.mongoClient = MongoClients.create(settings);
        this.database = mongoClient.getDatabase(databaseName);
    }

    @Override
    public void closeConnection() {
        if (mongoClient != null) mongoClient.close();
    }

    private void ensureConnected() {
        if (database == null) {
            throw new IllegalStateException("MongoDatabase is null; call connect() first.");
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Set<T>> matchAsync(Class<T> clazz, StorageQuery query) {
        ensureConnected();
        return CompletableFuture.supplyAsync(() -> matchSync(clazz, query));
    }

    @SuppressWarnings("unchecked")
    private Document buildBsonFilter(StorageQuery query) {
        Document bsonFilter = new Document();
        if (query != null && query.getFilters() != null) {
            for (Map.Entry<String, Object> entry : query.getFilters().entrySet()) {
                Object val = entry.getValue();
                if (val instanceof Map) {
                    bsonFilter.put(entry.getKey(), new Document((Map<String, Object>) val));
                } else {
                    bsonFilter.put(entry.getKey(), val);
                }
            }
        }
        return bsonFilter;
    }

    @Override
    public <T extends StorageObject> Set<T> matchSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();

        Set<T> results = new LinkedHashSet<>();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());

        Document bsonFilter = buildBsonFilter(query);

        var findIterable = coll.find(bsonFilter);

        if (query.getSortBy() != null) {
            findIterable.sort(new Document(query.getSortBy(), query.isSortDescending() ? -1 : 1));
        }

        if (query.getLimit() != null) {
            findIterable.limit(query.getLimit());
        }

        for (Document doc : findIterable) {
            T obj = mapper().fromDocument(clazz, doc);
            if (obj != null) results.add(obj);
        }

        return results;
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Long> countAsync(Class<T> clazz, StorageQuery query) {
        ensureConnected();
        return CompletableFuture.supplyAsync(() -> countSync(clazz, query));
    }

    @Override
    public <T extends StorageObject> long countSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());

        Document bsonFilter = buildBsonFilter(query);

        return coll.countDocuments(bsonFilter);
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Long> countAsync(Class<T> clazz) {
        ensureConnected();
        return CompletableFuture.supplyAsync(() -> countSync(clazz, null));
    }

    @Override
    public <T extends StorageObject> long countSync(Class<T> clazz) {
        return countSync(clazz, null);
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteAllAsync(Class<T> clazz) {
        return CompletableFuture.runAsync(() -> deleteAllSync(clazz));
    }

    /**
     * Verify async if an extra identifier is being used
     * @param clazz Entity class
     * @param extraIdentifier text to verify
     * @return true if already exists otherwise returns else
     */
    public <T extends StorageObject> boolean checkExtraIdentifierExistsSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        String extra = extraIdentifier.toLowerCase(Locale.ENGLISH);
        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName() + "-StringNaming");

        Document doc = collection.find(eq("_id", extra)).first();

        return doc != null;
    }

    /**
     * Verify async if an extra identifier is being used
     */
    public <T extends StorageObject> CompletableFuture<Boolean> checkExtraIdentifierExistsAsync(Class<T> clazz, String extraIdentifier) {
        return CompletableFuture.supplyAsync(() -> checkExtraIdentifierExistsSync(clazz, extraIdentifier));
    }

    public <T extends StorageObject> boolean deleteExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        String extra = extraIdentifier.toLowerCase(Locale.ENGLISH);
        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName() + "-StringNaming");
        DeleteResult result = collection.deleteOne(eq("_id", extra));

        return result.getDeletedCount() > 0;
    }

    public <T extends StorageObject> CompletableFuture<Boolean> deleteExtraIdentifierAsync(Class<T> clazz, String extraIdentifier) {
        return CompletableFuture.supplyAsync(() -> {
            return deleteExtraIdentifierSync(clazz, extraIdentifier);
        });
    }

    @Override
    public <T extends StorageObject> void deleteAllSync(Class<T> clazz) {
        ensureConnected();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());
        coll.deleteMany(new Document());
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteAllAsync(Class<T> clazz, StorageQuery query) {
        return CompletableFuture.runAsync(() -> deleteAllSync(clazz, query));
    }

    @Override
    public <T extends StorageObject> void deleteAllSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());

        Document bsonFilter = buildBsonFilter(query);

        coll.deleteMany(bsonFilter);
    }

    /**
     * Checks whether the underlying handle is an instance of the given type.
     *
     * @param type The class to check compatibility with.
     * @return true if the handle is non-null and can be cast to {@code type}, false otherwise.
     */
    @Override
    public boolean is(Class<?> type) {
        Object handle = database;
        if (handle == null || type == null) return false;
        if (type == Object.class) return true;

        Class<?> check = type.isPrimitive() ? primitiveToWrapper(type) : type;
        return check.isInstance(handle);
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
    @Override
    @SuppressWarnings("unchecked")
    public <T> T to(Class<T> type) {
        return is(type) ? (T) database : null;
    }

    @Override
    public CompletableFuture<Void> saveOrUpdateAsync(StorageObject obj) {
        return CompletableFuture.runAsync(() -> saveOrUpdateSync(obj));
    }

    @Override
    public void saveOrUpdateSync(StorageObject obj) {
        ensureConnected();

        Document doc = mapper().toDocument(obj);

        String collectionName = obj.getClass().getSimpleName();
        String id = extractIdentifier(obj);

        MongoCollection<Document> coll = database.getCollection(collectionName);
        ReplaceOptions opts = new ReplaceOptions().upsert(true);

        if (id != null) {
            coll.replaceOne(eq("_id", id), doc, opts);
        } else {
            coll.insertOne(doc);
        }

        Set<String> extraId = extractExtraIdentifier(obj);
        if (id != null && !extraId.isEmpty()) {
            MongoCollection<Document> collectionNaming = database.getCollection(collectionName + "-StringNaming");

            Document idFetch = new Document("referenced", id);

            for (String extra : extraId) {
                Document completed = new Document();
                completed.append("extra", extra);
                completed.append("data", idFetch);

                collectionNaming.replaceOne(
                        eq("_id", extra.toLowerCase(Locale.ENGLISH)),
                        completed,
                        opts
                );
            }
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();
        return CompletableFuture.supplyAsync(() -> loadByExtraIdentifierSync(clazz, extraIdentifier));
    }

    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        String extra = extraIdentifier.toLowerCase(Locale.ENGLISH);

        MongoCollection<Document> collection = database.getCollection(clazz.getSimpleName() + "-StringNaming");
        Document doc = collection.find(eq("_id", extra)).first();

        if (doc != null) {
            String original = doc.getString("extra");
            Document document = (Document) doc.get("data");

            if (document != null) {
                String reference = document.getString("referenced");
                return Optional.of(new ReferencedObject(original, reference));
            }
        }
        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String identifier) {
        ensureConnected();
        return CompletableFuture.supplyAsync(() -> loadByIdSync(clazz, identifier));
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier) {
        ensureConnected();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());

        Document doc = coll.find(eq("_id", identifier)).first();
        if (doc == null) return Optional.empty();

        T obj = mapper().fromDocument(clazz, doc);

        return Optional.ofNullable(obj);
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String identifier) {
        return CompletableFuture.runAsync(() -> deleteByIdSync(clazz, identifier));
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier) {
        ensureConnected();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());
        coll.deleteOne(eq("_id", identifier));
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Set<T>> loadAllAsync(Class<T> clazz) {
        ensureConnected();
        return CompletableFuture.supplyAsync(() -> loadAllSync(clazz));
    }

    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensureConnected();
        Set<T> results = new HashSet<>();
        MongoCollection<Document> coll = database.getCollection(clazz.getSimpleName());

        for (Document doc : coll.find()) {
            T obj = mapper().fromDocument(clazz, doc);
            if (obj != null) results.add(obj);
        }
        return results;
    }

    private String extractIdentifier(StorageObject obj) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(StorageIdentifier.class)) {
                    field.setAccessible(true);
                    try {
                        Object val = field.get(obj);
                        return val != null ? val.toString() : null;
                    } catch (Exception e) {
                        logError("Failed to extract identifier", e);
                        return null;
                    }
                }
            }
        }
        return null;
    }

    private Set<String> extractExtraIdentifier(StorageObject obj) {
        Set<String> extraIdentifiers = new HashSet<>();
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(StorageExtraIdentifier.class)) {
                    field.setAccessible(true);
                    try {
                        Object val = field.get(obj);
                        if (val != null) {
                            extraIdentifiers.add(val.toString());
                        }
                    } catch (Exception e) {
                        logError("Failed to extract extra identifier", e);
                    }
                }
            }
        }
        return extraIdentifiers;
    }
}