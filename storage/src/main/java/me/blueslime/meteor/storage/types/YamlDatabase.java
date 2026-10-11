package me.blueslime.meteor.storage.types;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.storage.database.StorageDatabase;
import me.blueslime.meteor.storage.interfaces.StorageExtraIdentifier;
import me.blueslime.meteor.storage.interfaces.StorageIdentifier;
import me.blueslime.meteor.storage.interfaces.StorageObject;
import me.blueslime.meteor.storage.query.StorageQuery;
import me.blueslime.meteor.storage.references.ReferencedObject;
import org.bson.Document;
import org.bson.types.Binary;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * YAML-backed storage implementation.
 * <br>
 * Intended mainly for local/development use. Async operations are inherited
 * from StorageDatabase and therefore use virtual threads.
 */
@SuppressWarnings({"unused", "ResultOfMethodCallIgnored"})
public class YamlDatabase extends StorageDatabase {

    private static final String BINARY_PREFIX =
            "$binary:";

    private static final int LOCK_STRIPES =
            64;

    private final File rootFolder;
    private final Object[] fileLocks =
            new Object[LOCK_STRIPES];

    public YamlDatabase(
            File rootFolder
    ) {
        this.rootFolder =
                Objects.requireNonNull(
                        rootFolder,
                        "rootFolder"
                );

        for (
            int index = 0;
            index < fileLocks.length;
            index++
        ) {
            fileLocks[index] =
                    new Object();
        }
    }

    @Override
    public void connect() {
        initializeAsyncExecutor();
        ensureConnected();
    }

    @Override
    public void closeConnection() {
        shutdownAsyncExecutor();
    }

    protected void ensureConnected() {
        if (
                rootFolder.exists()
        ) {
            if (
                    !rootFolder.isDirectory()
            ) {
                throw new IllegalStateException(
                        "YAML storage root is not a directory: "
                                + rootFolder
                );
            }

            return;
        }

        if (
                !rootFolder.mkdirs()
                        && !rootFolder.isDirectory()
        ) {
            throw new IllegalStateException(
                    "Unable to create YAML storage root: "
                            + rootFolder
            );
        }

        getLogger().info(
                "Created root storage folder: "
                        + rootFolder.getName()
        );
    }

    /*
     * ============================================================
     * SAVE
     * ============================================================
     */

    @Override
    public void saveOrUpdateSync(
            StorageObject object
    ) {
        if (
                object == null
        ) {
            return;
        }

        ensureConnected();

        Class<?> clazz =
                object.getClass();

        String identifier =
                extractIdentifier(
                        object
                );

        if (
                identifier == null
                        || identifier.isBlank()
        ) {
            identifier =
                    UUID.randomUUID()
                            .toString();

            injectIdentifier(
                    object,
                    identifier
            );
        }

        String finalIdentifier =
                identifier;

        synchronized (
                lockFor(
                        clazz.getName()
                                + ':'
                                + finalIdentifier
                )
        ) {
            Document document =
                    mapper().toDocument(
                            object
                    );

            document.put(
                    "_id",
                    finalIdentifier
            );

            Set<String> extraIdentifiers =
                    extractExtraIdentifier(
                            object
                    );

            document.put(
                    "_extras",
                    new ArrayList<>(
                            extraIdentifiers
                    )
            );

            File folder =
                    classFolder(
                            clazz
                    );

            ensureDirectory(
                    folder
            );

            File file =
                    entityFile(
                            folder,
                            finalIdentifier
                    );

            ConfigurationHandle handle =
                    getConfigurationProvider()
                            .load(
                                    file
                            );

            for (
                String key :
                handle.getKeys(
                        false
                )
            ) {
                handle.set(
                        key,
                        null
                );
            }

            writeDocumentToHandle(
                    handle,
                    "",
                    document
            );

            handle.save();

            saveExtraIndices(
                    folder,
                    finalIdentifier,
                    extraIdentifiers
            );
        }
    }

    private void writeDocumentToHandle(
            ConfigurationHandle handle,
            String parentPath,
            Object value
    ) {
        if (
                value instanceof Map<?, ?> map
        ) {
            for (
                Map.Entry<?, ?> entry :
                map.entrySet()
            ) {
                String key =
                        String.valueOf(
                                entry.getKey()
                        );

                String path =
                        parentPath.isEmpty()
                                ? key
                                : parentPath
                                        + "."
                                        + key;

                writeDocumentToHandle(
                        handle,
                        path,
                        entry.getValue()
                );
            }

            return;
        }

        if (
                value instanceof Binary binary
        ) {
            handle.set(
                    parentPath,
                    BINARY_PREFIX
                            + Base64
                                    .getEncoder()
                                    .encodeToString(
                                            binary.getData()
                                    )
            );

            return;
        }

        if (
                value instanceof Collection<?> collection
        ) {
            handle.set(
                    parentPath,
                    normalizeYamlCollection(
                            collection
                    )
            );

            return;
        }

        handle.set(
                parentPath,
                normalizeYamlScalar(
                        value
                )
        );
    }

    private List<Object> normalizeYamlCollection(
            Collection<?> collection
    ) {
        List<Object> result =
                new ArrayList<>(
                        collection.size()
                );

        for (
            Object value :
            collection
        ) {
            result.add(
                    normalizeYamlValue(
                            value
                    )
            );
        }

        return result;
    }

    private Object normalizeYamlValue(
            Object value
    ) {
        if (
                value instanceof Binary binary
        ) {
            return BINARY_PREFIX
                    + Base64
                            .getEncoder()
                            .encodeToString(
                                    binary.getData()
                            );
        }

        if (
                value instanceof Map<?, ?> map
        ) {
            Map<String, Object> normalized =
                    new LinkedHashMap<>();

            for (
                Map.Entry<?, ?> entry :
                map.entrySet()
            ) {
                normalized.put(
                        String.valueOf(
                                entry.getKey()
                        ),
                        normalizeYamlValue(
                                entry.getValue()
                        )
                );
            }

            return normalized;
        }

        if (
                value instanceof Collection<?> collection
        ) {
            return normalizeYamlCollection(
                    collection
            );
        }

        return normalizeYamlScalar(
                value
        );
    }

    private Object normalizeYamlScalar(
            Object value
    ) {
        if (
                value instanceof Enum<?> enumValue
        ) {
            return enumValue.toString();
        }

        return value;
    }

    /*
     * ============================================================
     * LOAD
     * ============================================================
     */

    @Override
    public <T extends StorageObject>
    Optional<T> loadByIdSync(
            Class<T> clazz,
            String identifier
    ) {
        ensureConnected();

        File folder =
                classFolder(
                        clazz
                );

        File file =
                entityFile(
                        folder,
                        identifier
                );

        if (
                !file.isFile()
        ) {
            return Optional.empty();
        }

        synchronized (
                lockFor(
                        clazz.getName()
                                + ':'
                                + identifier
                )
        ) {
            ConfigurationHandle handle =
                    getConfigurationProvider()
                            .load(
                                    file
                            );

            Document document =
                    readDocumentFromHandle(
                            handle
                    );

            return Optional.ofNullable(
                    mapper().fromDocument(
                            clazz,
                            document
                    )
            );
        }
    }

    @Override
    public <T extends StorageObject>
    Set<T> loadAllSync(
            Class<T> clazz
    ) {
        ensureConnected();

        Set<T> results =
                new LinkedHashSet<>();

        File folder =
                classFolder(
                        clazz
                );

        if (
                !folder.isDirectory()
        ) {
            return results;
        }

        File[] files =
                folder.listFiles(
                        (directory, name) ->
                                name.endsWith(
                                        ".yml"
                                )
                );

        if (
                files == null
        ) {
            return results;
        }

        for (
            File file :
            files
        ) {
            String name =
                    file.getName();

            String identifier =
                    name.substring(
                            0,
                            name.length() - 4
                    );

            try {
                loadByIdSync(
                        clazz,
                        identifier
                ).ifPresent(
                        results::add
                );

            } catch (Exception exception) {
                logError(
                        "Skipping corrupt YAML storage file "
                                + file.getName(),
                        exception
                );
            }
        }

        return results;
    }

    private Document readDocumentFromHandle(
            ConfigurationHandle handle
    ) {
        Document document =
                new Document();

        Set<String> keys =
                handle.getKeys(
                        true
                );

        for (
            String key :
            keys
        ) {
            Object value =
                    handle.get(
                            key
                    );

            if (
                    isConfigurationSection(
                            value
                    )
            ) {
                continue;
            }

            placeValueInDocument(
                    document,
                    key,
                    decodeYamlValue(
                            value
                    )
            );
        }

        return document;
    }

    private Object decodeYamlValue(
            Object value
    ) {
        if (
                value instanceof String string
                        && string.startsWith(
                                BINARY_PREFIX
                        )
        ) {
            return new Binary(
                    Base64
                            .getDecoder()
                            .decode(
                                    string.substring(
                                            BINARY_PREFIX.length()
                                    )
                            )
            );
        }

        if (
                value instanceof List<?> list
        ) {
            List<Object> decoded =
                    new ArrayList<>(
                            list.size()
                    );

            for (
                Object element :
                list
            ) {
                decoded.add(
                        decodeYamlValue(
                                element
                        )
                );
            }

            return decoded;
        }

        if (
                value instanceof Map<?, ?> map
        ) {
            Document decoded =
                    new Document();

            for (
                Map.Entry<?, ?> entry :
                map.entrySet()
            ) {
                decoded.put(
                        String.valueOf(
                                entry.getKey()
                        ),
                        decodeYamlValue(
                                entry.getValue()
                        )
                );
            }

            return decoded;
        }

        return value;
    }

    private boolean isConfigurationSection(
            Object value
    ) {
        return value != null
                && !(value instanceof String)
                && !(value instanceof Number)
                && !(value instanceof Boolean)
                && !(value instanceof List<?>)
                && !(value instanceof Character)
                && !(value instanceof Binary);
    }

    private void placeValueInDocument(
            Document root,
            String path,
            Object value
    ) {
        String[] parts =
                path.split(
                        "\\."
                );

        Document current =
                root;

        for (
            int index = 0;
            index < parts.length - 1;
            index++
        ) {
            String part =
                    parts[index];

            Object existing =
                    current.get(
                            part
                    );

            if (
                    existing instanceof Document document
            ) {
                current =
                        document;

                continue;
            }

            Document document =
                    new Document();

            current.put(
                    part,
                    document
            );

            current =
                    document;
        }

        current.put(
                parts[
                    parts.length - 1
                ],
                value
        );
    }

    /*
     * ============================================================
     * QUERY
     * ============================================================
     */

    @Override
    public <T extends StorageObject>
    Set<T> matchSync(
            Class<T> clazz,
            StorageQuery query
    ) {
        return applyQueryInMemory(
                loadAllSync(
                        clazz
                ),
                query
        );
    }

    @Override
    public <T extends StorageObject>
    long countSync(
            Class<T> clazz,
            StorageQuery query
    ) {
        return matchSync(
                clazz,
                query
        ).size();
    }

    @Override
    public <T extends StorageObject>
    long countSync(
            Class<T> clazz
    ) {
        return loadAllSync(
                clazz
        ).size();
    }

    protected <T extends StorageObject>
    Set<T> applyQueryInMemory(
            Set<T> elements,
            StorageQuery query
    ) {
        if (
                query == null
        ) {
            return elements;
        }

        List<Map.Entry<T, Document>> wrapped =
                elements.stream()
                        .map(
                                element ->
                                        new AbstractMap.SimpleEntry<>(
                                                element,
                                                mapper().toDocument(
                                                        element
                                                )
                                        )
                        )
                        .collect(
                                Collectors.toCollection(
                                        ArrayList::new
                                )
                        );

        if (
                !query
                        .getConditions()
                        .isEmpty()
        ) {
            wrapped.removeIf(
                    entry ->
                            !matchesAllConditions(
                                    entry.getValue(),
                                    query.getConditions()
                            )
            );
        }

        if (
                query.getSortBy() != null
        ) {
            Comparator<Map.Entry<T, Document>> comparator =
                    Comparator.comparing(
                            entry ->
                                    entry
                                            .getValue()
                                            .get(
                                                    query.getSortBy()
                                            ),
                            this::compareValues
                    );

            if (
                    query.isSortDescending()
            ) {
                comparator =
                        comparator.reversed();
            }

            wrapped.sort(
                    comparator
            );
        }

        if (
                query.getLimit() != null
                        && query.getLimit()
                                < wrapped.size()
        ) {
            wrapped =
                    new ArrayList<>(
                            wrapped.subList(
                                    0,
                                    query.getLimit()
                            )
                    );
        }

        return wrapped.stream()
                .map(
                        Map.Entry::getKey
                )
                .collect(
                        Collectors.toCollection(
                                LinkedHashSet::new
                        )
                );
    }

    private boolean matchesAllConditions(
            Document document,
            List<StorageQuery.Condition> conditions
    ) {
        for (
            StorageQuery.Condition condition :
            conditions
        ) {
            if (
                    !matchesCondition(
                            document,
                            condition
                    )
            ) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesCondition(
            Document document,
            StorageQuery.Condition condition
    ) {
        boolean exists =
                document.containsKey(
                        condition.key()
                );

        Object actual =
                document.get(
                        condition.key()
                );

        Object expected =
                condition.value();

        return switch (
                condition.operator()
        ) {
            case EQUALS ->
                    valuesEqual(
                            actual,
                            expected
                    );

            case GREATER_THAN ->
                    compareValues(
                            actual,
                            expected
                    ) > 0;

            case LESS_THAN ->
                    compareValues(
                            actual,
                            expected
                    ) < 0;

            case CONTAINS ->
                    actual != null
                            && String.valueOf(
                                    actual
                            ).toLowerCase(
                                    Locale.ROOT
                            ).contains(
                                    String.valueOf(
                                            expected
                                    ).toLowerCase(
                                            Locale.ROOT
                                    )
                            );

            case STARTS_WITH ->
                    actual != null
                            && String.valueOf(
                                    actual
                            ).toLowerCase(
                                    Locale.ROOT
                            ).startsWith(
                                    String.valueOf(
                                            expected
                                    ).toLowerCase(
                                            Locale.ROOT
                                    )
                            );

            case ENDS_WITH ->
                    actual != null
                            && String.valueOf(
                                    actual
                            ).toLowerCase(
                                    Locale.ROOT
                            ).endsWith(
                                    String.valueOf(
                                            expected
                                    ).toLowerCase(
                                            Locale.ROOT
                                    )
                            );

            case PRESENT ->
                    exists
                            == Boolean.TRUE.equals(
                                    expected
                            );
        };
    }

    private boolean valuesEqual(
            Object first,
            Object second
    ) {
        if (
                first == null
                        || second == null
        ) {
            return first == second;
        }

        if (
                first instanceof Number
                        && second instanceof Number
        ) {
            return numericValue(
                    first
            ).compareTo(
                    numericValue(
                            second
                    )
            ) == 0;
        }

        if (
                first instanceof Enum<?> enumValue
        ) {
            first =
                    enumValue.toString();
        }

        if (
                second instanceof Enum<?> enumValue
        ) {
            second =
                    enumValue.toString();
        }

        return Objects.equals(
                String.valueOf(
                        first
                ),
                String.valueOf(
                        second
                )
        );
    }

    private int compareValues(
            Object first,
            Object second
    ) {
        if (
                first == second
        ) {
            return 0;
        }

        if (
                first == null
        ) {
            return -1;
        }

        if (
                second == null
        ) {
            return 1;
        }

        if (
                first instanceof Number
                        && second instanceof Number
        ) {
            return numericValue(
                    first
            ).compareTo(
                    numericValue(
                            second
                    )
            );
        }

        return String.valueOf(
                first
        ).compareToIgnoreCase(
                String.valueOf(
                        second
                )
        );
    }

    private BigDecimal numericValue(
            Object value
    ) {
        return new BigDecimal(
                String.valueOf(
                        value
                )
        );
    }

    /*
     * ============================================================
     * DELETE
     * ============================================================
     */

    @Override
    public <T extends StorageObject>
    void deleteByIdSync(
            Class<T> clazz,
            String identifier
    ) {
        ensureConnected();

        File folder =
                classFolder(
                        clazz
                );

        synchronized (
                lockFor(
                        clazz.getName()
                                + ':'
                                + identifier
                )
        ) {
            File file =
                    entityFile(
                            folder,
                            identifier
                    );

            if (
                    !file.isFile()
            ) {
                return;
            }

            ConfigurationHandle handle =
                    getConfigurationProvider()
                            .load(
                                    file
                            );

            List<?> extras =
                    handle.getList(
                            "_extras"
                    );

            if (
                    extras != null
            ) {
                File indexFolder =
                        new File(
                                folder,
                                ".index"
                        );

                for (
                    Object extra :
                    extras
                ) {
                    if (
                            extra == null
                    ) {
                        continue;
                    }

                    new File(
                            indexFolder,
                            safeIndexFileName(
                                    extra.toString()
                            )
                                    + ".idx"
                    ).delete();
                }
            }

            file.delete();
        }
    }

    @Override
    public <T extends StorageObject>
    void deleteAllSync(
            Class<T> clazz
    ) {
        ensureConnected();

        File folder =
                classFolder(
                        clazz
                );

        if (
                folder.exists()
        ) {
            deleteDirectoryRecursively(
                    folder
            );
        }
    }

    @Override
    public <T extends StorageObject>
    void deleteAllSync(
            Class<T> clazz,
            StorageQuery query
    ) {
        if (
                query == null
                        || query.isEmpty()
        ) {
            deleteAllSync(
                    clazz
            );

            return;
        }

        Set<T> toDelete =
                matchSync(
                        clazz,
                        query
                );

        for (
            T object :
            toDelete
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

    private void deleteDirectoryRecursively(
            File file
    ) {
        File[] contents =
                file.listFiles();

        if (
                contents != null
        ) {
            for (
                File child :
                contents
            ) {
                deleteDirectoryRecursively(
                        child
                );
            }
        }

        file.delete();
    }

    /*
     * ============================================================
     * LEGACY EXTRA IDENTIFIERS
     * ============================================================
     */

    private void saveExtraIndices(
            File folder,
            String realIdentifier,
            Set<String> extraIdentifiers
    ) {
        if (
                extraIdentifiers.isEmpty()
        ) {
            return;
        }

        File indexFolder =
                new File(
                        folder,
                        ".index"
                );

        ensureDirectory(
                indexFolder
        );

        for (
            String extra :
            extraIdentifiers
        ) {
            File indexFile =
                    new File(
                            indexFolder,
                            safeIndexFileName(
                                    extra
                            )
                                    + ".idx"
                    );

            try (
                FileWriter writer =
                        new FileWriter(
                                indexFile,
                                StandardCharsets.UTF_8
                        )
            ) {
                writer.write(
                        realIdentifier
                );

            } catch (IOException exception) {
                logError(
                        "Failed to write YAML legacy index "
                                + indexFile.getName(),
                        exception
                );
            }
        }
    }

    @Override
    public <T extends StorageObject>
    Optional<ReferencedObject> loadByExtraIdentifierSync(
            Class<T> clazz,
            String extraIdentifier
    ) {
        ensureConnected();

        File indexFolder =
                new File(
                        classFolder(
                                clazz
                        ),
                        ".index"
                );

        File indexFile =
                new File(
                        indexFolder,
                        safeIndexFileName(
                                extraIdentifier
                        )
                                + ".idx"
                );

        if (
                !indexFile.isFile()
        ) {
            return Optional.empty();
        }

        try {
            String realIdentifier =
                    Files.readString(
                            indexFile.toPath(),
                            StandardCharsets.UTF_8
                    ).trim();

            if (
                    realIdentifier.isEmpty()
            ) {
                return Optional.empty();
            }

            return Optional.of(
                    new ReferencedObject(
                            extraIdentifier,
                            realIdentifier
                    )
            );

        } catch (IOException exception) {
            logError(
                    "Failed to read YAML legacy index "
                            + indexFile.getName(),
                    exception
            );

            return Optional.empty();
        }
    }

    @Override
    public <T extends StorageObject>
    boolean deleteExtraIdentifierSync(
            Class<T> clazz,
            String extraIdentifier
    ) {
        ensureConnected();

        File indexFile =
                new File(
                        new File(
                                classFolder(
                                        clazz
                                ),
                                ".index"
                        ),
                        safeIndexFileName(
                                extraIdentifier
                        )
                                + ".idx"
                );

        try {
            return Files.deleteIfExists(
                    indexFile.toPath()
            );

        } catch (IOException exception) {
            logError(
                    "Failed to delete YAML legacy index "
                            + indexFile.getName(),
                    exception
            );

            return false;
        }
    }

    /*
     * ============================================================
     * REFLECTION
     * ============================================================
     */

    private String extractIdentifier(
            StorageObject object
    ) {
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
                            "Failed to extract YAML storage identifier",
                            exception
                    );

                    return null;
                }
            }
        }

        return null;
    }

    private void injectIdentifier(
            StorageObject object,
            String identifier
    ) {
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
                    field.set(
                            object,
                            identifier
                    );

                } catch (ReflectiveOperationException exception) {
                    throw new IllegalStateException(
                            "Unable to inject generated YAML storage identifier",
                            exception
                    );
                }

                return;
            }
        }

        throw new IllegalStateException(
                "StorageObject "
                        + object.getClass().getName()
                        + " does not declare @StorageIdentifier"
        );
    }

    private Set<String> extractExtraIdentifier(
            StorageObject object
    ) {
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
                            "Failed to extract YAML extra identifier",
                            exception
                    );
                }
            }
        }

        return extraIdentifiers;
    }

    /*
     * ============================================================
     * FILE HELPERS
     * ============================================================
     */

    private File classFolder(
            Class<?> clazz
    ) {
        return new File(
                rootFolder,
                safePathName(
                        clazz.getSimpleName()
                )
        );
    }

    private File entityFile(
            File folder,
            String identifier
    ) {
        String safeIdentifier =
                safePathName(
                        identifier
                );

        return new File(
                folder,
                safeIdentifier.endsWith(
                        ".yml"
                )
                        ? safeIdentifier
                        : safeIdentifier
                                + ".yml"
        );
    }

    private void ensureDirectory(
            File directory
    ) {
        if (
                directory.exists()
        ) {
            if (
                    !directory.isDirectory()
            ) {
                throw new IllegalStateException(
                        "Expected directory but found file: "
                                + directory
                );
            }

            return;
        }

        if (
                !directory.mkdirs()
                        && !directory.isDirectory()
        ) {
            throw new IllegalStateException(
                    "Unable to create directory: "
                            + directory
            );
        }
    }

    private Object lockFor(
            String key
    ) {
        int hash =
                key.hashCode();

        hash ^=
                hash >>> 16;

        return fileLocks[
                hash & (LOCK_STRIPES - 1)
        ];
    }

    private static String safeIndexFileName(
            String value
    ) {
        return safePathName(
                value.toLowerCase(
                        Locale.ENGLISH
                )
        );
    }

    private static String safePathName(
            String value
    ) {
        if (
                value == null
                        || value.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Storage path name cannot be null or blank"
            );
        }

        return value.replaceAll(
                "[^A-Za-z0-9._-]",
                "_"
        );
    }
}
