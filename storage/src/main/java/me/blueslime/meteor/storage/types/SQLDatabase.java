package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.StorageExtraIdentifier;
import me.blueslime.meteor.storage.interfaces.StorageIdentifier;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.query.StorageQuery;
import me.blueslime.meteor.storage.references.ReferencedObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("unused")
public abstract class SQLDatabase extends StorageDatabase {

    protected final Set<String> createdTables = ConcurrentHashMap.newKeySet();

    protected final String host;
    protected final String databaseName;
    protected final String user;
    protected final String password;
    protected final int port;

    protected SQLDatabase(
        @NotNull String host,
        @NotNull String databaseName,
        @NotNull String user,
        @Nullable String password,
        int port
    ) {
        this.host = host;
        this.databaseName = databaseName;
        this.user = user;
        this.password = password;
        this.port = port;
    }

    @Override
    public abstract void connect();

    /**
     * Closes the JDBC backend itself.
     * <br>
     * The public lifecycle entry point is {@link #closeConnection()}.
     */
    protected abstract void close();

    @Override
    public final void closeConnection() {
        shutdownAsyncExecutor();

        try {
            close();
        } finally {
            createdTables.clear();
        }
    }

    protected abstract void ensureConnected();

    public abstract Connection getConnection() throws SQLException;

    /**
     * Runs a callback on ONE JDBC connection and commits all its writes
     * together. PostgreSQL/MariaDB/SQLite can provide ACID transactions
     * without a MongoDB replica set. The callback MUST use the supplied
     * Connection for every query; ordinary saveOrUpdateSync() opens a
     * different connection and is therefore NOT part of this transaction.
     */
    @FunctionalInterface
    public interface SqlTransaction<T> {
        T apply(Connection connection) throws SQLException;
    }

    public final <T> T inTransaction(SqlTransaction<T> work) throws SQLException {
        java.util.Objects.requireNonNull(work, "work");
        try (Connection connection = getConnection()) {
            if (!connection.getAutoCommit()) {
                throw new IllegalStateException("Expected a fresh JDBC connection with autoCommit=true");
            }
            connection.setAutoCommit(false);
            boolean committed = false;
            try {
                T result = work.apply(connection);
                connection.commit();
                committed = true;
                return result;
            } catch (SQLException | RuntimeException | Error failure) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            } finally {
                if (committed) {
                    try {
                        connection.setAutoCommit(true);
                    } catch (SQLException ignored) {
                        // Connection.close() follows. Do not mask original error.
                    }
                }
                // On failed rollback, do NOT reset autoCommit=true because
                // JDBC drivers may implicitly commit outstanding writes.
            }
        }
    }

    protected abstract String quoteIdentifier(String identifier);

    protected abstract String buildCreateMainTableStatement(String quotedTable);

    protected abstract String buildUpsertStatement(String quotedTable);

    protected abstract String buildCreateLegacyIndexTableStatement(String quotedTable);

    protected abstract String buildLegacyIndexUpsertStatement(String quotedTable);

    protected abstract String buildJsonCondition(String key);

    protected abstract String buildNumericJsonCondition(String key);

    protected abstract String buildJsonExistsCondition(String key);

    protected abstract boolean isMissingTable(SQLException exception);

    @Override
    public void saveOrUpdateSync(StorageObject object) {
        if (object == null) {
            return;
        }

        ensureConnected();

        String rawTable =
                sanitizeIdentifier(
                        object
                                .getClass()
                                .getSimpleName()
                );

        String table =
                quoteIdentifier(
                        rawTable
                );

        ensureMainTable(
                rawTable,
                table
        );

        String identifier =
                extractIdentifier(
                        object
                );

        if (identifier == null) {
            identifier =
                    object
                            .getClass()
                            .getSimpleName();
        }

        String jsonData =
                mapper().toJson(
                        object
                );

        String sql =
                buildUpsertStatement(
                        table
                );

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    identifier
            );

            statement.setString(
                    2,
                    jsonData
            );

            statement.executeUpdate();

        } catch (SQLException exception) {
            /*
             * The table cache can become stale if an administrator drops a
             * table while the process is alive. Retry once after recreation.
             */
            if (isMissingTable(exception)) {
                createdTables.remove(rawTable);

                ensureMainTable(
                        rawTable,
                        table
                );

                retrySave(
                        table,
                        identifier,
                        jsonData
                );

                return;
            }

            logError(
                    "Error saving object to SQL database table "
                            + rawTable,
                    exception
            );

            return;
        }

        Set<String> extraIdentifiers =
                extractExtraIdentifier(
                        object
                );

        if (!extraIdentifiers.isEmpty()) {
            saveExtraIdentifiers(
                    rawTable,
                    identifier,
                    extraIdentifiers
            );
        }
    }

    private void retrySave(String table, String identifier, String jsonData) {
        String sql =
                buildUpsertStatement(
                        table
                );

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    identifier
            );

            statement.setString(
                    2,
                    jsonData
            );

            statement.executeUpdate();

        } catch (SQLException exception) {
            logError(
                    "Failed to retry SQL save for identifier "
                            + identifier,
                    exception
            );
        }
    }

    private void ensureMainTable(String rawTable, String quotedTable) {
        if (
                createdTables.contains(
                        rawTable
                )
        ) {
            return;
        }

        String sql =
                buildCreateMainTableStatement(
                        quotedTable
                );

        try (
            Connection connection =
                    getConnection();

            Statement statement =
                    connection.createStatement()
        ) {
            statement.execute(
                    sql
            );

            createdTables.add(
                    rawTable
            );

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to create SQL table "
                            + rawTable,
                    exception
            );
        }
    }

    private void saveExtraIdentifiers(String rawMainTable, String realIdentifier, Set<String> extraIdentifiers) {
        String rawNamingTable =
                sanitizeIdentifier(
                        rawMainTable
                                + "_StringNaming"
                );

        String namingTable =
                quoteIdentifier(
                        rawNamingTable
                );

        ensureLegacyIndexTable(
                rawNamingTable,
                namingTable
        );

        String sql =
                buildLegacyIndexUpsertStatement(
                        namingTable
                );

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            for (
                String extraIdentifier :
                extraIdentifiers
            ) {
                statement.setString(
                        1,
                        extraIdentifier.toLowerCase(
                                Locale.ENGLISH
                        )
                );

                statement.setString(
                        2,
                        realIdentifier
                );

                statement.addBatch();
            }

            statement.executeBatch();

        } catch (SQLException exception) {
            logError(
                    "Error saving legacy extra identifiers for "
                            + rawMainTable,
                    exception
            );
        }
    }

    private void ensureLegacyIndexTable(String rawTable, String quotedTable) {
        if (
                createdTables.contains(
                        rawTable
                )
        ) {
            return;
        }

        String sql =
                buildCreateLegacyIndexTableStatement(
                        quotedTable
                );

        try (
            Connection connection =
                    getConnection();

            Statement statement =
                    connection.createStatement()
        ) {
            //noinspection SqlSourceToSinkFlow
            statement.execute(
                    sql
            );

            createdTables.add(
                    rawTable
            );

        } catch (SQLException exception) {
            throw new IllegalStateException(
                    "Failed to create SQL legacy index table "
                            + rawTable,
                    exception
            );
        }
    }

    @Override
    public <T extends StorageObject> Optional<ReferencedObject> loadByExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        String rawTable =
                sanitizeIdentifier(
                        clazz.getSimpleName()
                                + "_StringNaming"
                );

        String table =
                quoteIdentifier(
                        rawTable
                );

        String sql =
                "SELECT "
                        + quoteIdentifier("referenced")
                        + " FROM "
                        + table
                        + " WHERE "
                        + quoteIdentifier("_id")
                        + " = ?";

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    extraIdentifier.toLowerCase(
                            Locale.ENGLISH
                    )
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (
                        resultSet.next()
                ) {
                    return Optional.of(
                            new ReferencedObject(
                                    extraIdentifier,
                                    resultSet.getString(
                                            "referenced"
                                    )
                            )
                    );
                }
            }

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error loading legacy extra identifier "
                                + extraIdentifier,
                        exception
                );
            }
        }

        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> boolean deleteExtraIdentifierSync(Class<T> clazz, String extraIdentifier) {
        ensureConnected();

        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                                        + "_StringNaming"
                        )
                );

        String sql =
                "DELETE FROM "
                        + table
                        + " WHERE "
                        + quoteIdentifier("_id")
                        + " = ?";

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    extraIdentifier.toLowerCase(
                            Locale.ENGLISH
                    )
            );

            return statement.executeUpdate()
                    > 0;

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error deleting legacy extra identifier "
                                + extraIdentifier,
                        exception
                );
            }

            return false;
        }
    }

    @Override
    public <T extends StorageObject> Set<T> matchSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();

        Set<T> results =
                new LinkedHashSet<>();

        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                        )
                );

        StringBuilder sql =
                new StringBuilder(
                        "SELECT "
                                + quoteIdentifier("json_data")
                                + " FROM "
                                + table
                );

        List<Object> parameters =
                new ArrayList<>();

        appendQueryFilters(
                sql,
                parameters,
                query
        );

        if (
                query != null
                        && query.getSortBy() != null
        ) {
            sql.append(
                    " ORDER BY "
            );

            sql.append(
                    buildJsonCondition(
                            sanitizeJsonKey(
                                    query.getSortBy()
                            )
                    )
            );

            sql.append(
                    query.isSortDescending()
                            ? " DESC"
                            : " ASC"
            );
        }

        if (
                query != null
                        && query.getLimit() != null
        ) {
            sql.append(
                    " LIMIT "
            );

            sql.append(
                    query.getLimit()
            );
        }

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql.toString()
                    )
        ) {
            bindParameters(
                    statement,
                    parameters
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                while (
                        resultSet.next()
                ) {
                    String json =
                            resultSet.getString(
                                    "json_data"
                            );

                    T object =
                            mapper().fromJson(
                                    json,
                                    clazz
                            );

                    if (
                            object != null
                    ) {
                        results.add(
                                object
                        );
                    }
                }
            }

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error in matchSync for table "
                                + clazz.getSimpleName(),
                        exception
                );
            }
        }

        return results;
    }

    @Override
    public <T extends StorageObject> long countSync(Class<T> clazz, StorageQuery query) {
        ensureConnected();

        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                        )
                );

        StringBuilder sql =
                new StringBuilder(
                        "SELECT COUNT(*) FROM "
                                + table
                );

        List<Object> parameters =
                new ArrayList<>();

        appendQueryFilters(
                sql,
                parameters,
                query
        );

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql.toString()
                    )
        ) {
            bindParameters(
                    statement,
                    parameters
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (
                        resultSet.next()
                ) {
                    return resultSet.getLong(
                            1
                    );
                }
            }

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error in countSync for table "
                                + clazz.getSimpleName(),
                        exception
                );
            }
        }

        return 0L;
    }

    @Override
    public <T extends StorageObject> long countSync(Class<T> clazz) {
        return countSync(
                clazz,
                null
        );
    }

    private void appendQueryFilters(
            StringBuilder sql,
            List<Object> parameters,
            StorageQuery query
    ) {
        if (
                query == null
                        || query
                                .getConditions()
                                .isEmpty()
        ) {
            return;
        }

        sql.append(
                " WHERE "
        );

        boolean first =
                true;

        for (
            StorageQuery.Condition condition :
            query.getConditions()
        ) {
            if (!first) {
                sql.append(
                        " AND "
                );
            }

            first = false;

            appendCondition(
                    sql,
                    parameters,
                    condition
            );
        }
    }

    private void appendCondition(StringBuilder sql, List<Object> parameters, StorageQuery.Condition condition) {
        String key =
                sanitizeJsonKey(
                        condition.key()
                );

        Object value =
                condition.value();

        String textExpression =
                buildJsonCondition(
                        key
                );

        switch (
                condition.operator()
        ) {
            case EQUALS -> {
                if (
                        value == null
                ) {
                    sql.append(
                            textExpression
                    );

                    sql.append(
                            " IS NULL"
                    );

                    return;
                }

                if (
                        value instanceof Number
                ) {
                    sql.append(
                            buildNumericJsonCondition(
                                    key
                            )
                    );

                    sql.append(
                            " = ?"
                    );

                    parameters.add(
                            value
                    );

                    return;
                }

                sql.append(
                        textExpression
                );

                sql.append(
                        " = ?"
                );

                parameters.add(
                        normalizeScalarParameter(
                                value
                        )
                );
            }

            case GREATER_THAN -> {
                sql.append(
                        value instanceof Number
                                ? buildNumericJsonCondition(
                                        key
                                )
                                : textExpression
                );

                sql.append(
                        " > ?"
                );

                parameters.add(
                        value instanceof Number
                                ? value
                                : normalizeScalarParameter(
                                        value
                                )
                );
            }

            case LESS_THAN -> {
                sql.append(
                        value instanceof Number
                                ? buildNumericJsonCondition(
                                        key
                                )
                                : textExpression
                );

                sql.append(
                        " < ?"
                );

                parameters.add(
                        value instanceof Number
                                ? value
                                : normalizeScalarParameter(
                                        value
                                )
                );
            }

            case CONTAINS -> {
                sql.append(
                        "LOWER(COALESCE("
                );

                sql.append(
                        textExpression
                );

                sql.append(
                        ", '')) LIKE ? ESCAPE '!'"
                );

                parameters.add(
                        "%"
                                + escapeLike(
                                    String.valueOf(
                                            value
                                    ).toLowerCase(
                                            Locale.ROOT
                                    )
                                )
                                + "%"
                );
            }

            case STARTS_WITH -> {
                sql.append(
                        "LOWER(COALESCE("
                );

                sql.append(
                        textExpression
                );

                sql.append(
                        ", '')) LIKE ? ESCAPE '!'"
                );

                parameters.add(
                        escapeLike(
                                String.valueOf(
                                        value
                                ).toLowerCase(
                                        Locale.ROOT
                                )
                        ) + "%"
                );
            }

            case ENDS_WITH -> {
                sql.append(
                        "LOWER(COALESCE("
                );

                sql.append(
                        textExpression
                );

                sql.append(
                        ", '')) LIKE ? ESCAPE '!'"
                );

                parameters.add(
                        "%"
                                + escapeLike(
                                    String.valueOf(
                                            value
                                    ).toLowerCase(
                                            Locale.ROOT
                                    )
                                )
                );
            }

            case PRESENT -> {
                boolean present =
                        Boolean.TRUE.equals(
                                value
                        );

                if (!present) {
                    sql.append(
                            "NOT ("
                    );
                }

                sql.append(
                        buildJsonExistsCondition(
                                key
                        )
                );

                if (!present) {
                    sql.append(
                            ')'
                    );
                }
            }
        }
    }

    private static Object normalizeScalarParameter(Object value) {
        if (
                value instanceof Enum<?> enumValue
        ) {
            return enumValue.toString();
        }

        if (
                value instanceof Boolean booleanValue
        ) {
            return Boolean.toString(
                    booleanValue
            );
        }

        return value;
    }

    private static String escapeLike(String value) {
        return value
                .replace(
                        "!",
                        "!!"
                )
                .replace(
                        "%",
                        "!%"
                )
                .replace(
                        "_",
                        "!_"
                );
    }

    private static void bindParameters(PreparedStatement statement, List<Object> parameters) throws SQLException {
        for (
            int index = 0;
            index < parameters.size();
            index++
        ) {
            Object value =
                    parameters.get(
                            index
                    );

            if (
                    value instanceof BigDecimal decimal
            ) {
                statement.setBigDecimal(
                        index + 1,
                        decimal
                );
            } else {
                statement.setObject(
                        index + 1,
                        value
                );
            }
        }
    }

    @Override
    public <T extends StorageObject> Optional<T> loadByIdSync(Class<T> clazz, String identifier) {
        ensureConnected();

        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                        )
                );

        String sql =
                "SELECT "
                        + quoteIdentifier("json_data")
                        + " FROM "
                        + table
                        + " WHERE "
                        + quoteIdentifier("_id")
                        + " = ?";

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    identifier
            );

            try (
                ResultSet resultSet =
                        statement.executeQuery()
            ) {
                if (
                        resultSet.next()
                ) {
                    String json =
                            resultSet.getString(
                                    "json_data"
                            );

                    return Optional.ofNullable(
                            mapper().fromJson(
                                    json,
                                    clazz
                            )
                    );
                }
            }

        } catch (SQLException exception) {
            if (
                    isMissingTable(
                            exception
                    )
            ) {
                return Optional.empty();
            }

            logError(
                    "Error loading object "
                            + identifier
                            + " from "
                            + clazz.getSimpleName(),
                    exception
            );
        }

        return Optional.empty();
    }

    @Override
    public <T extends StorageObject> Set<T> loadAllSync(Class<T> clazz) {
        ensureConnected();

        Set<T> results =
                new HashSet<>();

        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                        )
                );

        String sql =
                "SELECT "
                        + quoteIdentifier("json_data")
                        + " FROM "
                        + table;

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    );

            ResultSet resultSet =
                    statement.executeQuery()
        ) {
            while (
                    resultSet.next()
            ) {
                String json =
                        resultSet.getString(
                                "json_data"
                        );

                T object =
                        mapper().fromJson(
                                json,
                                clazz
                        );

                if (
                        object != null
                ) {
                    results.add(
                            object
                    );
                }
            }

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error loading all objects from "
                                + clazz.getSimpleName(),
                        exception
                );
            }
        }

        return results;
    }

    @Override
    public <T extends StorageObject> void deleteByIdSync(Class<T> clazz, String identifier) {
        ensureConnected();

        deleteLegacyReferencesById(
                clazz,
                identifier
        );

        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                        )
                );

        String sql =
                "DELETE FROM "
                        + table
                        + " WHERE "
                        + quoteIdentifier("_id")
                        + " = ?";

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    identifier
            );

            statement.executeUpdate();

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error deleting object "
                                + identifier
                                + " from "
                                + clazz.getSimpleName(),
                        exception
                );
            }
        }
    }

    private <T extends StorageObject> void deleteLegacyReferencesById(Class<T> clazz, String identifier) {
        String table =
                quoteIdentifier(
                        sanitizeIdentifier(
                                clazz.getSimpleName()
                                        + "_StringNaming"
                        )
                );

        String sql =
                "DELETE FROM "
                        + table
                        + " WHERE "
                        + quoteIdentifier("referenced")
                        + " = ?";

        try (
            Connection connection =
                    getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            sql
                    )
        ) {
            statement.setString(
                    1,
                    identifier
            );

            statement.executeUpdate();

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error cleaning legacy references for "
                                + identifier,
                        exception
                );
            }
        }
    }

    @Override
    public <T extends StorageObject>
    void deleteAllSync(
            Class<T> clazz
    ) {
        ensureConnected();

        deleteAllFromTable(
                sanitizeIdentifier(
                        clazz.getSimpleName()
                )
        );

        deleteAllFromTable(
                sanitizeIdentifier(
                        clazz.getSimpleName()
                                + "_StringNaming"
                )
        );
    }

    private void deleteAllFromTable(
            String rawTable
    ) {
        String sql =
                "DELETE FROM "
                        + quoteIdentifier(
                            rawTable
                        );

        try (
            Connection connection =
                    getConnection();

            Statement statement =
                    connection.createStatement()
        ) {
            statement.executeUpdate(
                    sql
            );

        } catch (SQLException exception) {
            if (
                    !isMissingTable(
                            exception
                    )
            ) {
                logError(
                        "Error deleting all records from "
                                + rawTable,
                        exception
                );
            }
        }
    }

    @Override
    public <T extends StorageObject> void deleteAllSync(Class<T> clazz, StorageQuery query) {
        if (
                query == null
                        || query.isEmpty()
        ) {
            deleteAllSync(
                    clazz
            );

            return;
        }

        Set<T> matching =
                matchSync(
                        clazz,
                        query
                );

        for (
            T object :
            matching
        ) {
            String identifier =
                    extractIdentifier(
                            object
                    );

            if (
                    identifier != null
            ) {
                deleteByIdSync(
                        clazz,
                        identifier
                );
            }
        }
    }

    protected String extractIdentifier(StorageObject object) {
        for (
            Class<?> current =
                    object.getClass();

            current != null
                    && current != Object.class;

            current =
                    current.getSuperclass()
        ) {
            for (
                Field field :
                current.getDeclaredFields()
            ) {
                if (
                        !field.isAnnotationPresent(
                                StorageIdentifier.class
                        )
                ) {
                    continue;
                }

                field.setAccessible(
                        true
                );

                try {
                    Object value =
                            field.get(
                                    object
                            );

                    return value == null
                            ? null
                            : value.toString();

                } catch (ReflectiveOperationException exception) {
                    logError(
                            "Failed to extract storage identifier",
                            exception
                    );

                    return null;
                }
            }
        }

        return null;
    }

    protected Set<String> extractExtraIdentifier(StorageObject object) {
        Set<String> extraIdentifiers =
                new HashSet<>();

        for (
            Class<?> current =
                    object.getClass();

            current != null
                    && current != Object.class;

            current =
                    current.getSuperclass()
        ) {
            for (
                Field field :
                current.getDeclaredFields()
            ) {
                if (
                        !field.isAnnotationPresent(
                                StorageExtraIdentifier.class
                        )
                ) {
                    continue;
                }

                field.setAccessible(
                        true
                );

                try {
                    Object value =
                            field.get(
                                    object
                            );

                    if (
                            value != null
                    ) {
                        extraIdentifiers.add(
                                value.toString()
                        );
                    }

                } catch (ReflectiveOperationException exception) {
                    logError(
                            "Failed to extract extra identifier",
                            exception
                    );
                }
            }
        }

        return extraIdentifiers;
    }

    protected static String sanitizeIdentifier(
            String identifier
    ) {
        if (
                identifier == null
        ) {
            throw new IllegalArgumentException(
                    "SQL identifier cannot be null"
            );
        }

        String cleaned =
                identifier.replaceAll(
                        "[^A-Za-z0-9_]",
                        "_"
                );

        if (
                cleaned.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "SQL identifier cannot be blank"
            );
        }

        return cleaned;
    }

    protected static String sanitizeJsonKey(
            String key
    ) {
        if (
                key == null
                        || key.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "JSON key cannot be null or blank"
            );
        }

        if (
                !key.matches(
                        "[A-Za-z0-9_-]+"
                )
        ) {
            throw new IllegalArgumentException(
                    "Unsafe JSON key: "
                            + key
            );
        }

        return key;
    }
}
