package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;
import me.blueslime.meteor.storage.references.ReferencedObject;
import org.bson.Document;

import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.*;

/**
 * YamlDatabase supported
 */
@SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
public class YamlDatabase extends StorageDatabase {

    private final File rootFolder;
    private final ExecutorService executor;

    /**
     * @param rootFolder Root folder for storage
     */
    public YamlDatabase(File rootFolder) {
        this.rootFolder = rootFolder;
        this.executor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
    }

    @Override
    public void connect() {
        if (!rootFolder.exists()) {
            if (rootFolder.mkdirs()) {
                getLogger().info("Created root storage folder: " + rootFolder.getName());
            }
        }
    }

    @Override
    public void closeConnection() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(2, TimeUnit.SECONDS)) executor.shutdownNow();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }


    protected void ensureConnected() {
        if (!rootFolder.exists()) rootFolder.mkdirs();
    }

    @Override
    public CompletableFuture<Void> saveOrUpdateAsync(StorageObject obj) {
        return CompletableFuture.runAsync(() -> saveOrUpdateSync(obj), executor);
    }

    @Override
    public void saveOrUpdateSync(StorageObject obj) {
        if (obj == null) return;
        ensureConnected();

        Class<?> clazz = obj.getClass();
        String id = extractIdentifier(obj);

        if (id == null || id.isEmpty()) {
            id = UUID.randomUUID().toString();
            injectIdentifier(obj, id);
        }

        Document doc = mapper().toDocument(obj);

        doc.put("_id", id);
        Set<String> extraIds = extractExtraIdentifier(obj);
        doc.put("_extras", extraIds);

        File folder = new File(rootFolder, clazz.getSimpleName());
        if (!folder.exists()) folder.mkdirs();

        File file = new File(folder, id + (id.endsWith(".yml") ? "" : ".yml"));

        ConfigurationHandle handle = getConfigurationProvider().load(file);

        for (String key : handle.getKeys(false)) {
            handle.set(key, null);
        }

        writeDocumentToHandle(handle, "", doc);

        handle.save();

        saveExtraIndices(folder, id, extraIds);
    }

    /**
     * Convert Structure of Document to calls using handle#set()
     */
    private void writeDocumentToHandle(ConfigurationHandle handle, String parentPath, Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String path = parentPath.isEmpty() ? key : parentPath + "." + key;
                writeDocumentToHandle(handle, path, entry.getValue());
            }
        } else if (value instanceof List) {
            handle.set(parentPath, value);
        } else {
            handle.set(parentPath, value);
        }
    }

    private void saveExtraIndices(File folder, String realId, Set<String> extraIds) {
        File indexFolder = new File(folder, ".index");
        if (!indexFolder.exists()) indexFolder.mkdirs();
        for (String extra : extraIds) {
            File indexFile = new File(indexFolder, extra.toLowerCase(Locale.ENGLISH) + ".idx");
            try (FileWriter w = new FileWriter(indexFile)) {
                w.write(realId);
            } catch (IOException ignored) {}
        }
    }


    @Override
    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String id) {
        return CompletableFuture.supplyAsync(() -> loadByIdSync(clazz, id), executor);
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String id) {
        ensureConnected();
        File folder = new File(rootFolder, clazz.getSimpleName());
        File file = new File(folder, id + (id.endsWith(".yml") ? "" : ".yml"));

        if (!file.exists()) return Optional.empty();

        ConfigurationHandle handle = getConfigurationProvider().load(file);

        Document doc = readDocumentFromHandle(handle);

        return Optional.ofNullable(mapper().fromDocument(clazz, doc));
    }

    /**
     * Reconstruye un Documento BSON leyendo todas las claves del ConfigurationHandle.
     */
    private Document readDocumentFromHandle(ConfigurationHandle handle) {
        Document doc = new Document();

        Set<String> keys = handle.getKeys(true);

        for (String key : keys) {
            Object value = handle.get(key);

            if (isConfigurationSection(value)) continue;

            placeValueInDocument(doc, key, value);
        }
        return doc;
    }

    /**
     * Determina si un valor es una sección de configuración (depende de la implementación,
     * pero generalmente si getKeys devuelve algo, es sección).
     * Aquí asumimos que si el valor no es primitivo ni lista, es sección.
     */
    private boolean isConfigurationSection(Object value) {
        return value != null && !(value instanceof String || value instanceof Number ||
                                  value instanceof Boolean || value instanceof List || value instanceof Character);
    }

    private void placeValueInDocument(Document root, String path, Object value) {
        String[] parts = path.split("\\.");
        Document current = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            Object existing = current.get(part);

            if (existing instanceof Document) {
                current = (Document) existing;
            } else {
                Document newDoc = new Document();
                current.put(part, newDoc);
                current = newDoc;
            }
        }

        current.put(parts[parts.length - 1], value);
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String id) {
        return CompletableFuture.runAsync(() -> deleteByIdSync(clazz, id), executor);
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String id) {
        File folder = new File(rootFolder, clazz.getSimpleName());
        File file = new File(folder, id + (id.endsWith(".yml") ? "" : ".yml"));

        if (file.exists()) {
            ConfigurationHandle handle = getConfigurationProvider().load(file);
            List<?> extras = handle.getList("_extras");
            if (extras != null) {
                File indexFolder = new File(folder, ".index");
                for (Object extra : extras) {
                    new File(indexFolder, extra.toString().toLowerCase(Locale.ENGLISH) + ".idx").delete();
                }
            }
            file.delete();
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Set<T>> loadAllAsync(Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> loadAllSync(clazz), executor);
    }

    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensureConnected();
        Set<T> results = new HashSet<>();
        File folder = new File(rootFolder, clazz.getSimpleName());
        if (!folder.exists()) return results;

        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return results;

        for (File f : files) {
            try {
                ConfigurationHandle handle = getConfigurationProvider().load(f);
                Document doc = readDocumentFromHandle(handle);
                T obj = mapper().fromDocument(clazz, doc);
                if (obj != null) results.add(obj);
            } catch (Exception e) {
                logError("Skipping corrupt file " + f.getName(), e);
            }
        }
        return results;
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String ex) {
        return CompletableFuture.supplyAsync(() -> loadByExtraIdentifierSync(clazz, ex), executor);
    }

    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();
        File indexFolder = new File(new File(rootFolder, clazz.getSimpleName()), ".index");
        File indexFile = new File(indexFolder, extraIdentifier.toLowerCase(Locale.ENGLISH) + ".idx");

        if (indexFile.exists()) {
            try {
                String realId = Files.readString(indexFile.toPath()).trim();
                return Optional.of(new ReferencedObject(extraIdentifier, realId));
            } catch (IOException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String extractIdentifier(StorageObject obj) {
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(StorageIdentifier.class)) {
                    f.setAccessible(true);
                    try { Object v = f.get(obj); return v != null ? v.toString() : null; } catch (Exception ignored) {}
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
                    try { Object v = f.get(obj); if (v != null) extras.add(v.toString()); } catch (Exception ignored) {}
                }
            }
        }
        return extras;
    }
}
