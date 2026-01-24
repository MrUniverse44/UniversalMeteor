package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.*;

import me.blueslime.meteor.storage.references.ReferencedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public abstract class SQLDatabase extends StorageDatabase {
    
    protected final Set<String> createdTables = ConcurrentHashMap.newKeySet();
    
    protected final ExecutorService dbExecutor;

    protected final String host, databaseName, user, password;
    protected final int port;

    /**
     * Creates a new SQL Database.
     *
     * @param logger       logger instance
     * @param host         The host of the MySQL server.
     * @param databaseName The name of the MySQL database.
     * @param user         The username for the MySQL database.
     * @param password     The password for the MySQL database.
     * @param port         The port of the MySQL server.
     */
    public SQLDatabase(@NotNull Logger logger, @NotNull String host, @NotNull String databaseName, @NotNull String user, @Nullable String password, int port) {
        super(logger);
        this.host = host;
        this.databaseName = databaseName;
        this.user = user;
        this.password = password;
        this.port = port;
        this.dbExecutor =
                Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
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
        
        String id = extractIdentifier(obj);
        if (id == null) id = obj.getClass().getSimpleName();
        Set<String> extraIds = extractExtraIdentifier(obj);
        
        Map<String, Object> data = mapper().toMap(obj);
        data.put("_id", id);
        
        List<String> cols = new ArrayList<>(data.keySet());
        
        cols.remove("_id");
        cols.add(0, "_id");
        
        String safeTable = sanitizeIdentifier(table);
        
        if (!createdTables.contains(safeTable)) {
            StringBuilder createSql = new StringBuilder()
                    .append("CREATE TABLE IF NOT EXISTS ")
                    .append(safeTable)
                    .append(" (\n `_id` VARCHAR(255) PRIMARY KEY");
            for (String c : cols) {
                if ("_id".equals(c)) continue;
                createSql.append(",\n `").append(c).append("` TEXT");
            }
            createSql.append("\n) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;");
            try (Statement stmtCreate = getConnection().createStatement()) {
                stmtCreate.execute(createSql.toString());
                createdTables.add(safeTable);
            } catch (SQLException e) {
                
                logError("Error creating main table " + safeTable, e);
            }
        }
        
        String colNames = String.join(", ", cols.stream().map(c -> "`" + c +
                "`").toList());
        String placeholders = String.join(", ",
                Collections.nCopies(cols.size(), "?"));
        StringBuilder sql = new StringBuilder()
                .append("INSERT INTO ").append(safeTable).append(" (")
                .append(colNames)
                .append(") VALUES (")
                .append(placeholders)
                .append(")");
        sql.append(" ON DUPLICATE KEY UPDATE ");
        List<String> updates = new ArrayList<>();
        for (String c : cols) {
            if ("_id".equals(c)) continue;
            updates.add("`" + c + "` = VALUES(`" + c + "`)");
        }
        sql.append(String.join(", ", updates));
        
        try (PreparedStatement stmt = getConnection().prepareStatement(sql.toString())) {
            int idx = 1;
            for (String c : cols) {
                Object v = data.get(c);
                
                if (v instanceof StorageObject) {
                    
                    Map<String, Object> nested =
                            mapper().toMap((StorageObject) v);
                    stmt.setString(idx++, mapper().toJsonCompatible(nested));
                    continue;
                }
                if (v instanceof Map || v instanceof Collection || v != null && mapper().isComplexObject(v.getClass())) {
                    stmt.setString(idx++, mapper().toJsonCompatible(v));
                } else {
                    stmt.setObject(idx++, v);
                }
            }
            stmt.executeUpdate();
        } catch (SQLException e) {
            logError("Error saving/updating object in a SQL Database", e);
        }

        if (!extraIds.isEmpty()) {
            String namingTable = sanitizeIdentifier(table + "_StringNaming");
            if (!createdTables.contains(namingTable)) {
                String createNamingSql =
                        "CREATE TABLE IF NOT EXISTS " + namingTable +
                                "(`_id` VARCHAR(255) NOT NULL PRIMARY KEY," +
                                " `referenced` VARCHAR(255) NOT NULL" +
                                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
                try (Statement stmt = getConnection().createStatement()) {
                    stmt.execute(createNamingSql);
                    createdTables.add(namingTable);
                } catch (SQLException e) {
                    logError("Error creating naming table " + namingTable,
                            e);
                }
            }
            String namingSql =
                    "INSERT INTO " + namingTable +
                            " (_id, referenced) VALUES (?, ?) ON DUPLICATE KEY UPDATE referenced = ?";
            try (PreparedStatement stmt = getConnection().prepareStatement(namingSql)) {
                for (String extra : extraIds) {
                    String keyLower = extra.toLowerCase(Locale.ENGLISH);
                    stmt.setString(1, keyLower);
                    stmt.setString(2, id);
                    stmt.setString(3, id);
                    stmt.addBatch();
                }
                stmt.executeBatch();
            } catch (SQLException e) {
                logError("Error saving extra identifiers in MariaDB", e);
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
    @Override
    public <T extends StorageObject> CompletableFuture<Optional<ReferencedObject>> loadByExtraIdentifierAsync(Class<T> clazz, String extraId) {
        return CompletableFuture.supplyAsync(() ->
                loadByExtraIdentifierSync(clazz, extraId), dbExecutor);
    }
    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraId) {
        ensureConnected();
        String table = sanitizeIdentifier(clazz.getSimpleName() +
                "_StringNaming");
        String key = extraId.toLowerCase(Locale.ENGLISH);
        String sql = "SELECT referenced FROM " + table + " WHERE _id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, key);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new ReferencedObject(extraId,
                            rs.getString("referenced")));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146 || "42P01".equals(e.getSQLState()))
            {
                return Optional.empty();
            }
            logError("Error loading by extra identifier", e);
            return Optional.empty();
        }
    }

    @Override
    public <T extends StorageObject> CompletableFuture<Optional<T>> loadByIdAsync(Class<T> clazz, String id) {
        return CompletableFuture.supplyAsync(() -> loadByIdSync(clazz, id),
                dbExecutor);
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T>
                                                                      clazz, String id) {
        ensureConnected();
        String table = sanitizeIdentifier(clazz.getSimpleName());
        String sql = "SELECT * FROM " + table + " WHERE _id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> map = new HashMap<>();
                    ResultSetMetaData meta = rs.getMetaData();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        String column = meta.getColumnLabel(i);
                        Object raw = rs.getObject(i);
                        
                        if (raw instanceof String s) {
                            Object parsed = null;
                            try {
                                parsed = mapper().fromJsonCompatible(s);
                            } catch (Exception ignore) {}
                            if (parsed != null) {
                                map.put(column, parsed);
                                continue;
                            }
                        }
                        map.put(column, raw);
                    }
                    T obj = mapper().fromMap(clazz, map, id);
                    return Optional.ofNullable(obj);
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            if (e.getErrorCode() == 1146 || "42P01".equals(e.getSQLState()))
            {
                return Optional.empty();
            }
            logError("Error loading object by id in a SQLDatabase", e);
            return Optional.empty();
        }
    }
    @Override
    public <T extends StorageObject> CompletableFuture<Void> deleteByIdAsync(Class<T> clazz, String id) {
        return CompletableFuture.runAsync(() -> deleteByIdSync(clazz, id), dbExecutor);
    }
    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz,
                                                         String id) {
        ensureConnected();
        String table = sanitizeIdentifier(clazz.getSimpleName());
        String sql = "DELETE FROM " + table + " WHERE _id = ?";
        try (PreparedStatement stmt = getConnection().prepareStatement(sql)) {
            stmt.setString(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            logError("Error deleting object by id in MariaDB", e);
        }
    }
    @Override
    public <T extends StorageObject> CompletableFuture<Set<T>>
    loadAllAsync(Class<T> clazz) {
        return CompletableFuture.supplyAsync(() -> loadAllSync(clazz),
                dbExecutor);
    }
    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensureConnected();
        Set<T> results = new HashSet<>();
        String table = sanitizeIdentifier(clazz.getSimpleName());
        String sql = "SELECT * FROM " + table;
        try (PreparedStatement stmt = getConnection().prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            ResultSetMetaData meta = rs.getMetaData();
            while (rs.next()) {
                Map<String, Object> map = new HashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    String column = meta.getColumnLabel(i);
                    Object raw = rs.getObject(i);
                    if (raw instanceof String s) {
                        Object parsed = null;
                        try {
                            parsed = mapper().fromJsonCompatible(s);
                        } catch (Exception ignore) {}
                        if (parsed != null) {
                            map.put(column, parsed);
                            continue;
                        }
                    }
                    map.put(column, raw);
                }
                String id = (String) map.get("_id");
                T obj = mapper().fromMap(clazz, map, id);
                if (obj != null) results.add(obj);
            }
        } catch (SQLException e) {
            logError("Error loading all objects in MariaDB", e);
        }
        return results;
    }
    
    private static String sanitizeIdentifier(String s) {
        if (s == null) throw new IllegalArgumentException("Identifier null");
        String cleaned = s.replaceAll("[^A-Za-z0-9_]", "_");
        return "`" + cleaned + "`";
    }

    public abstract Connection getConnection() throws SQLException;
}
