package me.blueslime.meteor.storage.types;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

@SuppressWarnings("unused")
public class LocalDatabase extends SQLDatabase {

    private final String name;
    private final File folder;

    private volatile HikariDataSource dataSource;

    public LocalDatabase(
            @NotNull String databaseName,
            @NotNull File folder
    ) {
        super(
                "localhost",
                databaseName,
                "root",
                "",
                0
        );

        this.name =
                databaseName.endsWith(
                        ".db"
                )
                        ? databaseName
                        : databaseName
                                + ".db";

        this.folder =
                folder;
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

        if (
                !folder.exists()
                        && !folder.mkdirs()
                        && !folder.exists()
        ) {
            throw new IllegalStateException(
                    "Unable to create SQLite directory: "
                            + folder
            );
        }

        File databaseFile =
                new File(
                        folder,
                        name
                );

        boolean firstCreation =
                !databaseFile.exists();

        HikariConfig config =
                new HikariConfig();

        config.setPoolName(
                "Meteor-SQLite"
        );

        config.setDriverClassName(
                "org.sqlite.JDBC"
        );

        config.setJdbcUrl(
                "jdbc:sqlite:"
                        + databaseFile.getAbsolutePath()
        );

        /*
         * SQLite allows many readers but has a single writer. A pool size of
         * one gives this generic storage backend predictable behavior and
         * avoids SQLITE_BUSY storms under high virtual-thread concurrency.
         */
        config.setMaximumPoolSize(
                1
        );

        config.setMinimumIdle(
                1
        );

        config.setConnectionTimeout(
                30_000L
        );

        config.setIdleTimeout(
                0L
        );

        config.setMaxLifetime(
                0L
        );

        config.setConnectionInitSql(
                "PRAGMA foreign_keys = ON"
        );

        HikariDataSource source =
                new HikariDataSource(
                        config
                );

        try (
            Connection connection =
                    source.getConnection();

            Statement statement =
                    connection.createStatement()
        ) {
            statement.execute(
                    "PRAGMA journal_mode = WAL"
            );

            statement.execute(
                    "PRAGMA synchronous = NORMAL"
            );

            statement.execute(
                    "PRAGMA busy_timeout = 5000"
            );

            statement.execute(
                    "PRAGMA foreign_keys = ON"
            );

        } catch (SQLException exception) {
            source.close();

            throw new IllegalStateException(
                    "Failed to initialize SQLite pragmas",
                    exception
            );
        }

        this.dataSource =
                source;

        if (
                firstCreation
        ) {
            getLogger().info(
                    "SQLite database file created at: "
                            + databaseFile.getAbsolutePath()
            );
        }
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
                    "SQLite database is not connected; call connect() first."
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
                + " TEXT NOT NULL PRIMARY KEY, "
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
                + " = excluded."
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
                + " TEXT NOT NULL PRIMARY KEY, "
                + quoteIdentifier("referenced")
                + " TEXT NOT NULL"
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
                + " = excluded."
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

        return "json_extract("
                + quoteIdentifier("json_data")
                + ", '$.\""
                + safeKey
                + "\"')";
    }

    @Override
    protected String buildNumericJsonCondition(
            String key
    ) {
        return "CAST("
                + buildJsonCondition(
                        key
                )
                + " AS REAL)";
    }

    @Override
    protected String buildJsonExistsCondition(
            String key
    ) {
        String safeKey =
                sanitizeJsonKey(
                        key
                );

        return "json_type("
                + quoteIdentifier("json_data")
                + ", '$.\""
                + safeKey
                + "\"') IS NOT NULL";
    }

    @Override
    protected boolean isMissingTable(
            SQLException exception
    ) {
        String message =
                exception.getMessage();

        return exception.getErrorCode() == 1
                && message != null
                && message.toLowerCase()
                        .contains(
                                "no such table"
                        );
    }
}
