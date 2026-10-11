package me.blueslime.meteor.storage.types;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;

@SuppressWarnings("unused")
public class PostgreDatabase extends SQLDatabase {

    private volatile HikariDataSource dataSource;

    public PostgreDatabase(
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
                "Meteor-PostgreSQL"
        );

        config.setDriverClassName(
                "org.postgresql.Driver"
        );

        config.setJdbcUrl(
                "jdbc:postgresql://"
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

        config.addDataSourceProperty(
                "reWriteBatchedInserts",
                "true"
        );

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
                    "PostgreSQL database is not connected; call connect() first."
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
        return "\""
                + sanitizeIdentifier(
                        identifier
                )
                + "\"";
    }

    @Override
    protected String buildCreateMainTableStatement(
            String quotedTable
    ) {
        return "CREATE TABLE IF NOT EXISTS "
                + quotedTable
                + " ("
                + quoteIdentifier("_id")
                + " VARCHAR(255) PRIMARY KEY, "
                + quoteIdentifier("json_data")
                + " TEXT NOT NULL"
                + ")";
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
                + "ON CONFLICT ("
                + quoteIdentifier("_id")
                + ") DO UPDATE SET "
                + quoteIdentifier("json_data")
                + " = EXCLUDED."
                + quoteIdentifier("json_data");
    }

    @Override
    protected String buildCreateLegacyIndexTableStatement(
            String quotedTable
    ) {
        return "CREATE TABLE IF NOT EXISTS "
                + quotedTable
                + " ("
                + quoteIdentifier("_id")
                + " VARCHAR(255) PRIMARY KEY, "
                + quoteIdentifier("referenced")
                + " VARCHAR(255) NOT NULL"
                + ")";
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
                + "ON CONFLICT ("
                + quoteIdentifier("_id")
                + ") DO UPDATE SET "
                + quoteIdentifier("referenced")
                + " = EXCLUDED."
                + quoteIdentifier("referenced");
    }

    @Override
    protected String buildJsonCondition(
            String key
    ) {
        String safeKey =
                sanitizeJsonKey(
                        key
                );

        return "(CAST("
                + quoteIdentifier("json_data")
                + " AS jsonb) ->> '"
                + safeKey
                + "')";
    }

    @Override
    protected String buildNumericJsonCondition(
            String key
    ) {
        return "CAST("
                + buildJsonCondition(
                        key
                )
                + " AS NUMERIC)";
    }

    @Override
    protected String buildJsonExistsCondition(
            String key
    ) {
        String safeKey =
                sanitizeJsonKey(
                        key
                );

        return "(CAST("
                + quoteIdentifier("json_data")
                + " AS jsonb) ? '"
                + safeKey
                + "')";
    }

    @Override
    protected boolean isMissingTable(
            SQLException exception
    ) {
        return "42P01".equals(
                exception.getSQLState()
        );
    }
}
