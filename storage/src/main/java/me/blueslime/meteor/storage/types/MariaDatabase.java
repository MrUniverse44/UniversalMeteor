package me.blueslime.meteor.storage.types;

import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;

@SuppressWarnings("unused")
public class MariaDatabase extends SQLDatabase {

    private HikariDataSource dataSource = null;

    /**
     * Creates a new SQL Database.
     *
     * @param host         The host of the MySQL server.
     * @param databaseName The name of the MySQL database.
     * @param user         The username for the MySQL database.
     * @param password     The password for the MySQL database.
     * @param port         The port of the MySQL server.
     */
    public MariaDatabase(@NotNull String host, @NotNull String databaseName, @NotNull String user, @Nullable String password, int port) {
        super(host, databaseName, user, password, port);
    }

    @Override
    public void connect() {
        dataSource = new HikariDataSource();
        dataSource.setDataSourceClassName("com.mysql.jdbc.jdbc2.optional.MysqlDataSource");
        dataSource.addDataSourceProperty("serverName", host);
        dataSource.addDataSourceProperty("port", port);
        dataSource.addDataSourceProperty("databaseName", databaseName);
        dataSource.addDataSourceProperty("user", user);
        dataSource.addDataSourceProperty("password", password);
    }

    @Override
    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
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
}
