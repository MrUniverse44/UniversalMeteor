package me.blueslime.meteor.storage.types;

import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.*;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class MariaDatabase extends SQLDatabase {

    private HikariDataSource dataSource = null;

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
    public MariaDatabase(@NotNull Logger logger, @NotNull String host, @NotNull String databaseName, @NotNull String user, @Nullable String password, int port) {
        super(logger, host, databaseName, user, password, port);
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
}
