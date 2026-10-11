package me.blueslime.meteor.storage.types;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

@SuppressWarnings("unused")
public class MariaDatabase extends SQLDatabase {

    private volatile HikariDataSource dataSource;

    public MariaDatabase(
            @NotNull String host,
            @NotNull String databaseName,
            @NotNull String user,
            @Nullable String password,
            int port
    ) {
        super(
                host,
                databaseName,
                user,
                password,
                port
        );
    }

    @Override
    public synchronized void connect() {
        HikariDataSource current =
                dataSource;

        if (
                current != null
                        && !current.isClosed()
        ) {
            return;
        }

        initializeAsyncExecutor();

        HikariConfig config =
                new HikariConfig();

        config.setPoolName(
                "Meteor-MariaDB"
        );

        config.setDriverClassName(
                "org.mariadb.jdbc.Driver"
        );

        config.setJdbcUrl(
                "jdbc:mariadb://"
                        + host
                        + ":"
                        + port
                        + "/"
                        + databaseName
        );

        config.setUsername(
                user
        );

        if (
                password != null
        ) {
            config.setPassword(
                    password
            );
        }

        /*
         * Virtual threads make waiting cheap, but the database still has a
         * finite amount of useful parallelism. Keep a bounded JDBC pool.
         */
        config.setMaximumPoolSize(
                10
        );

        config.setMinimumIdle(
                2
        );

        config.setConnectionTimeout(
                30_000L
        );

        config.setIdleTimeout(
                600_000L
        );

        config.setMaxLifetime(
                1_800_000L
        );

        this.dataSource =
                new HikariDataSource(
                        config
                );
    }

    @Override
    protected synchronized void close() {
        HikariDataSource current =
                dataSource;

        dataSource =
                null;

        if (
                current != null
        ) {
            current.close();
        }
    }

    @Override
    protected void ensureConnected() {
        HikariDataSource current =
                dataSource;

        if (
                current == null
                        || current.isClosed()
        ) {
            throw new IllegalStateException(
                    "MariaDB database is not connected; call connect() first."
            );
        }
    }

    @Override
    public Connection getConnection()
            throws SQLException {
        ensureConnected();

        return dataSource.getConnection();
    }

    @Override
    public boolean is(
            Class<?> type
    ) {
        HikariDataSource handle =
                dataSource;

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
                ? (T) dataSource
                : null;
    }

    @Override
    protected String quoteIdentifier(
            String identifier
    ) {
        return "`"
                + sanitizeIdentifier(
                        identifier
                )
                + "`";
    }

    @Override
    protected String buildCreateMainTableStatement(
            String quotedTable
    ) {
        return "CREATE TABLE IF NOT EXISTS "
                + quotedTable
                + " ("
                + quoteIdentifier("_id")
                + " VARCHAR(255) NOT NULL PRIMARY KEY, "
                + quoteIdentifier("json_data")
                + " LONGTEXT NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String buildUpsertStatement(
            String quotedTable
    ) {
        return "INSERT INTO "
                + quotedTable
                + " ("
                + quoteIdentifier("_id")
                + ", "
                + quoteIdentifier("json_data")
                + ") VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + quoteIdentifier("json_data")
                + " = VALUES("
                + quoteIdentifier("json_data")
                + ")";
    }

    @Override
    protected String buildCreateLegacyIndexTableStatement(
            String quotedTable
    ) {
        return "CREATE TABLE IF NOT EXISTS "
                + quotedTable
                + " ("
                + quoteIdentifier("_id")
                + " VARCHAR(255) NOT NULL PRIMARY KEY, "
                + quoteIdentifier("referenced")
                + " VARCHAR(255) NOT NULL"
                + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
    }

    @Override
    protected String buildLegacyIndexUpsertStatement(
            String quotedTable
    ) {
        return "INSERT INTO "
                + quotedTable
                + " ("
                + quoteIdentifier("_id")
                + ", "
                + quoteIdentifier("referenced")
                + ") VALUES (?, ?) "
                + "ON DUPLICATE KEY UPDATE "
                + quoteIdentifier("referenced")
                + " = VALUES("
                + quoteIdentifier("referenced")
                + ")";
    }

    @Override
    protected String buildJsonCondition(
            String key
    ) {
        String safeKey =
                sanitizeJsonKey(
                        key
                );

        return "JSON_UNQUOTE(JSON_EXTRACT("
                + quoteIdentifier("json_data")
                + ", '$.\""
                + safeKey
                + "\"'))";
    }

    @Override
    protected String buildNumericJsonCondition(
            String key
    ) {
        return "CAST("
                + buildJsonCondition(
                        key
                )
                + " AS DECIMAL(65, 10))";
    }

    @Override
    protected String buildJsonExistsCondition(
            String key
    ) {
        String safeKey =
                sanitizeJsonKey(
                        key
                );

        return "JSON_CONTAINS_PATH("
                + quoteIdentifier("json_data")
                + ", 'one', '$.\""
                + safeKey
                + "\"')";
    }

    @Override
    protected boolean isMissingTable(
            SQLException exception
    ) {
        return exception.getErrorCode() == 1146
                || "42S02".equals(
                        exception.getSQLState()
                );
    }
}
