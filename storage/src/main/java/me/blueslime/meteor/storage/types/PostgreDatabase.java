package me.blueslime.meteor.storage.types;

import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

public class PostgreDatabase extends SQLDatabase {

    private HikariDataSource dataSource;

    /**
     * Creates a new SQL Database.
     *
     * @param host         The host of the MySQL server.
     * @param databaseName The name of the MySQL database.
     * @param user         The username for the MySQL database.
     * @param password     The password for the MySQL database.
     * @param port         The port of the MySQL server.
     */
    public PostgreDatabase(@NotNull String host, @NotNull String databaseName, @NotNull String user, @Nullable String password, int port) {
        super(host, databaseName, user, password, port);
    }

    @Override
    public void connect() {
        dataSource = new HikariDataSource();
        dataSource.setDriverClassName("org.postgresql.Driver");

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, databaseName);
        dataSource.setJdbcUrl(jdbcUrl);

        dataSource.setUsername(user);
        dataSource.setPassword(password);

        
        dataSource.addDataSourceProperty("cachePrepStmts", "true");
        dataSource.addDataSourceProperty("prepStmtCacheSize", "250");
        dataSource.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        dataSource.addDataSourceProperty("useServerPrepStmts", "true");
        dataSource.addDataSourceProperty("reWriteBatchedInserts", "true");

        
        dataSource.setMaximumPoolSize(10);
        dataSource.setMinimumIdle(2);
        dataSource.setConnectionTimeout(30000);
        dataSource.setIdleTimeout(600000);
        dataSource.setMaxLifetime(1800000);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }


    /**
     * Checks whether the underlying handle is an instance of the given type.
     *
     * @param type The class to check compatibility with.
     * @return true if the handle is non-null and can be cast to {@code type}, false otherwise.
     */
    @Override
    public boolean is(Class<?> type) {
        Object handle = dataSource;
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
        return is(type) ? (T) dataSource : null;
    }

    @Override
    protected String buildJsonCondition(String key) {
        // En PostgreSQL usamos la sintaxis ->> para extraer texto de un JSON
        // Nota: asumiendo que tu columna json_data es tipo TEXT o VARCHAR,
        // la casteamos a json:: para asegurarnos que funcione.
        return "CAST(`json_data` AS json)->>'" + key + "'";
    }

    @Override
    protected void ensureConnected() {
        try {
            if (dataSource == null || dataSource.getConnection() == null || dataSource.getConnection().isClosed() || !dataSource.getConnection().isValid(2)) {
                throw new IllegalStateException("MariaDB connection is not valid; call connect() first.");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Error checking DB connection", e);
        }
    }

    @Override
    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }
}
