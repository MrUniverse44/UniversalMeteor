package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;
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