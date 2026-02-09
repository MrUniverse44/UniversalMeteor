package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;
import me.blueslime.meteor.storage.references.ReferencedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;

@SuppressWarnings("unused")
public abstract class SQLDatabase extends StorageDatabase {

    protected final Set<String> createdTables = ConcurrentHashMap.newKeySet();

    protected final ExecutorService dbExecutor;
    protected final String host, databaseName, user, password;
    protected final int port;

    public SQLDatabase(@NotNull String host, @NotNull String databaseName, @NotNull String user, @Nullable String password, int port) {
        this.host = host;
        this.databaseName = databaseName;
        this.user = user;
        this.password = password;
        this.port = port;
        this.dbExecutor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    }

    @Override
    public abstract void connect();

    public abstract void close();

    @Override
    public void closeConnection() {
        close();
        dbExecutor.shutdown();
        try {
            if (!dbExecutor.awaitTermination(2, TimeUnit.SECONDS))
                dbExecutor.shutdownNow();
        } catch (InterruptedException ignored) {
            dbExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    protected abstract void ensureConnected();

    @Override
    public CompletableFuture<Void> saveOrUpdateAsync(StorageObject obj) {
        return CompletableFuture.runAsync(() -> saveOrUpdateSync(obj), dbExecutor);
    }

    @Override
    public void saveOrUpdateSync(StorageObject obj) {
        ensureConnected();

        String table = obj.getClass().getSimpleName();
        String safeTable = sanitizeIdentifier(table);
        String id = extractIdentifier(obj);
        if (id == null) id = obj.getClass().getSimpleName(); // Fallback ID

        String jsonData = mapper().toJson(obj);

        String sql = "INSERT INTO " + safeTable + " (`_id`, `json_data`) VALUES (?, ?) " +
                     "ON DUPLICATE KEY UPDATE `json_data` = VALUES(`json_data`)";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.setString(2, jsonData);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146 || "42S02".equals(e.getSQLState())) {
                createMainTable(safeTable);
                saveOrUpdateSync(obj);
                return;
            }
            logError("Error saving object to SQL Database in table " + safeTable, e);
        }

        Set<String> extraIds = extractExtraIdentifier(obj);
        if (!extraIds.isEmpty()) {
            saveExtraIdentifiers(table, id, extraIds);
        }
    }

    private void createMainTable(String tableName) {
        String sql = "CREATE TABLE IF NOT EXISTS " + tableName + " (" +
                     "`_id` VARCHAR(255) NOT NULL PRIMARY KEY, " +
                     "`json_data` LONGTEXT" +
                     ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
        try (Statement stmt = getConnection().createStatement()) {
            stmt.execute(sql);
            createdTables.add(tableName);
        } catch (SQLException e) {
            logError("Failed to create main table " + tableName, e);
        }
    }

    private void saveExtraIdentifiers(String rawTableName, String realId, Set<String> extraIds) {
        String namingTable = sanitizeIdentifier(rawTableName + "_StringNaming");

        if (!createdTables.contains(namingTable)) {
            String createSql = "CREATE TABLE IF NOT EXISTS " + namingTable +
                               "(`_id` VARCHAR(255) NOT NULL PRIMARY KEY," +
                               " `referenced` VARCHAR(255) NOT NULL" +
                               ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            try (Statement stmt = getConnection().createStatement()) {
                stmt.execute(createSql);
                createdTables.add(namingTable);
            } catch (SQLException e) {
                logError("Error creating naming table " + namingTable, e);
                return;
            }
        }

        String insertSql = "INSERT INTO " + namingTable +
                           " (`_id`, `referenced`) VALUES (?, ?) " +
                           "ON DUPLICATE KEY UPDATE `referenced` = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(insertSql)) {
            for (String extra : extraIds) {
                String keyLower = extra.toLowerCase(Locale.ENGLISH);
                stmt.setString(1, keyLower);
                stmt.setString(2, realId);
                stmt.setString(3, realId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException e) {
            logError("Error saving extra identifiers for " + rawTableName, e);
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String id) {
        return CompletableFuture.supplyAsync(() -> loadByIdSync(clazz, id), dbExecutor);
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String id) {
        ensureConnected();
        String table = sanitizeIdentifier(clazz.getSimpleName());
        String sql = "SELECT `json_data` FROM " + table + " WHERE `_id` = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    String json = rs.getString("json_data");
                    return Optional.ofNullable(mapper().fromJson(json, clazz));
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146 || "42S02".equals(e.getSQLState())) {
                return Optional.empty();
            }
            logError("Error loading object " + id + " from " + table, e);
        }
        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Set<T>> loadAllAsync(Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> loadAllSync(clazz), dbExecutor);
    }

    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensureConnected();
        Set<T> results = new HashSet<>();
        String table = sanitizeIdentifier(clazz.getSimpleName());
        String sql = "SELECT `json_data` FROM " + table;

        try (PreparedStatement stmt = getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String json = rs.getString("json_data");
                T obj = mapper().fromJson(json, clazz);
                if (obj != null) {
                    results.add(obj);
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 1146 && !"42S02".equals(e.getSQLState())) {
                logError("Error loading all objects from " + table, e);
            }
        }
        return results;
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraId) {
        return CompletableFuture.supplyAsync(() -> loadByExtraIdentifierSync(clazz, extraId), dbExecutor);
    }

    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraId) {
        ensureConnected();
        String table = sanitizeIdentifier(clazz.getSimpleName() + "_StringNaming");
        String key = extraId.toLowerCase(Locale.ENGLISH);
        String sql = "SELECT `referenced` FROM " + table + " WHERE `_id` = ?";

        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ReferencedObject(extraId, rs.getString("referenced")));
                }
            }
        } catch (SQLException e) {
            if (e.getErrorCode() != 1146 && !"42S02".equals(e.getSQLState())) {
                logError("Error loading extra identifier " + extraId, e);
            }
        }
        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String id) {
        return CompletableFuture.runAsync(() -> deleteByIdSync(clazz, id), dbExecutor);
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String id) {
        ensureConnected();
        String table = sanitizeIdentifier(clazz.getSimpleName());
        String sql = "DELETE FROM " + table + " WHERE `_id` = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            if (e.getErrorCode() != 1146 && !"42S02".equals(e.getSQLState())) {
                logError("Error deleting object " + id, e);
            }
        }
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
        Set<String> extras = new HashSet<>();
        for (Class<?> c = obj.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (var field : c.getDeclaredFields()) {
                if (field.isAnnotationPresent(StorageExtraIdentifier.class)) {
                    field.setAccessible(true);
                    try {
                        Object val = field.get(obj);
                        if (val != null) extras.add(val.toString());
                    } catch (Exception e) {
                        logError("Failed to extract extra identifier", e);
                    }
                }
            }
        }
        return extras;
    }

    private static String sanitizeIdentifier(String s) {
        if (s == null) throw new IllegalArgumentException("Identifier null");
        String cleaned = s.replaceAll("[^A-Za-z0-9_]", "_");
        return "`" + cleaned + "`";
    }

    public abstract Connection getConnection() throws SQLException;
}