package me.blueslime.meteor.paper.extras.languages.handlers;

import me.blueslime.meteor.paper.extras.languages.LanguageMode;
import me.blueslime.meteor.paper.extras.languages.LanguageService;
import me.blueslime.meteor.paper.extras.languages.locale.InvalidLocaleException;
import me.blueslime.meteor.paper.extras.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class DynamicLanguageService implements LanguageService {

    /**
     * Locales that Meteor should try to extract from the plugin resources
     * during initialize().
     *
     * This list is immutable after construction.
     */
    private final List<Locale> supportedLocales;

    /**
     * Fallback configured at construction time.
     */
    private final Locale initialFallbackLocale;

    /**
     * Complete immutable state.
     *
     * Reload creates a new State and publishes it only when the entire
     * operation completed successfully.
     */
    private volatile State state;

    /**
     * Initialized during initialize(), never from constructor.
     */
    private File localesDirectory;

    /**
     * Reflection compatibility layer.
     *
     * All of these are optional because depending on the server version
     * some of them simply do not exist.
     */
    private final Method modernLocaleMethod;
    private final Method legacyLocaleMethod;

    private final Method playerSpigotMethod;
    private final Method spigotLocaleMethod;

    /**
     * Avoid repeatedly trying a reflection strategy after we know that
     * particular implementation is unusable on the running server.
     */
    private final AtomicBoolean modernLocaleAvailable =
            new AtomicBoolean(true);

    private final AtomicBoolean legacyLocaleAvailable =
            new AtomicBoolean(true);

    private final AtomicBoolean spigotLocaleAvailable =
            new AtomicBoolean(true);

    public DynamicLanguageService(
            String... supportedLocales
    ) {
        List<Locale> parsed =
                parseSupportedLocales(
                        supportedLocales
                );

        if (parsed.isEmpty()) {
            parsed =
                    List.of(
                            Locale.fromString(
                                    "en"
                            )
                    );
        }

        this.supportedLocales =
                List.copyOf(
                        parsed
                );

        this.initialFallbackLocale =
                this.supportedLocales
                        .getFirst();

        this.state =
                State.empty(
                        initialFallbackLocale
                );

        /*
         * Reflection discovery is safe in the constructor:
         *
         * - no getPlugin()
         * - no fetch()
         * - no files
         * - no scheduler
         *
         * We're only checking the classes/API available in the
         * current runtime.
         */
        this.modernLocaleMethod =
                findMethod(
                        Player.class,
                        "locale"
                );

        this.legacyLocaleMethod =
                findMethod(
                        Player.class,
                        "getLocale"
                );

        this.playerSpigotMethod =
                findMethod(
                        Player.class,
                        "spigot"
                );

        Class<?> playerSpigotClass =
                findClass(
                        "org.bukkit.entity.Player$Spigot"
                );

        this.spigotLocaleMethod =
                playerSpigotClass == null
                        ? null
                        : findMethod(
                        playerSpigotClass,
                        "getLocale"
                );

        modernLocaleAvailable.set(
                modernLocaleMethod != null
        );

        legacyLocaleAvailable.set(
                legacyLocaleMethod != null
        );

        spigotLocaleAvailable.set(
                playerSpigotMethod != null &&
                        spigotLocaleMethod != null
        );
    }

    @Override
    public void initialize() {
        this.localesDirectory =
                getFileOfDirectory(
                        "i18n"
                );

        ensureDirectory();

        /*
         * Save packaged defaults only during first initialization.
         */
        saveDefaults();

        reloadLocales();

        logLocaleResolver();
    }

    @Override
    public void reload() {
        reloadLocales();
    }

    /**
     * Reloads every locale atomically.
     *
     * The old State remains available during the entire load operation.
     * Only after everything is valid do we publish the new State.
     */
    public void reloadLocales() {
        File directory =
                requireLocalesDirectory();

        File[] files =
                directory.listFiles(
                        (dir, fileName) ->
                                fileName
                                        .toLowerCase(
                                                java.util.Locale.ROOT
                                        )
                                        .endsWith(
                                                ".yml"
                                        )
                );

        if (files == null) {
            getLogger().warn(
                    "Unable to list locale directory: "
                            + directory
            );

            return;
        }

        Arrays.sort(
                files,
                Comparator.comparing(
                        File::getName,
                        String.CASE_INSENSITIVE_ORDER
                )
        );

        LinkedHashMap<
                Locale,
                ConfigurationHandle
                > loaded =
                new LinkedHashMap<>();

        for (File file : files) {
            String localeName =
                    removeYamlExtension(
                            file.getName()
                    );

            Locale locale;

            try {
                locale =
                        Locale.fromString(
                                localeName
                        );

            } catch (
                    InvalidLocaleException exception
            ) {
                getLogger().warn(
                        "Ignoring invalid locale file '"
                                + file.getName()
                                + "': "
                                + exception.getMessage()
                );

                continue;
            }

            try {
                ConfigurationHandle configuration =
                        getPlugin()
                                .getConfigurationProvider()
                                .load(
                                        file,
                                        "/i18n/"
                                                + localeName
                                                + ".yml"
                                );

                loaded.put(
                        locale,
                        configuration
                );

            } catch (
                    Exception exception
            ) {
                getLogger().error(
                        exception,
                        "Failed to load locale '"
                                + localeName
                                + "' from "
                                + file.getName()
                );
            }
        }

        if (loaded.isEmpty()) {
            /*
             * Do NOT destroy the current state when a reload fails.
             */
            getLogger().warn(
                    "No valid locales were found inside "
                            + directory
                            + ". Keeping previous locale registry."
            );

            return;
        }

        State previous =
                this.state;

        Locale requestedFallback =
                previous != null
                        ? previous.fallbackLocale()
                        : initialFallbackLocale;

        ResolvedLocale fallback =
                resolve(
                        loaded,
                        requestedFallback
                )
                        .orElseGet(() ->
                                findFirstSupported(
                                        loaded
                                )
                        );

        if (fallback == null) {
            getLogger().error(
                    "Unable to resolve a fallback locale after reload. "
                            + "Keeping previous language state."
            );

            return;
        }

        State next =
                new State(
                        Map.copyOf(
                                loaded
                        ),
                        fallback.locale(),
                        fallback.configuration()
                );

        /*
         * Atomic publication.
         */
        this.state =
                next;

        getLogger().debug(
                "Loaded "
                        + loaded.size()
                        + " language configuration(s). "
                        + "Fallback locale: "
                        + localeId(
                        next.fallbackLocale()
                )
        );
    }

    /**
     * Changes the fallback without having to reload every locale.
     *
     * The requested locale must resolve against the currently loaded
     * locale registry.
     */
    @Override
    public void updateFallbackLocale(
            String locale
    ) {
        Locale requested =
                Locale.fromString(
                        locale
                );

        State current =
                requireState();

        ResolvedLocale resolved =
                resolve(
                        current.locales(),
                        requested
                )
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "Locale '"
                                                + localeId(
                                                requested
                                        )
                                                + "' is not currently loaded"
                                )
                        );

        this.state =
                new State(
                        current.locales(),
                        resolved.locale(),
                        resolved.configuration()
                );

        getLogger().debug(
                "Updated fallback locale to "
                        + localeId(
                        resolved.locale()
                )
        );
    }

    /**
     * Returns the actual locale that Meteor can use for this player.
     *
     * Example:
     *
     * client: es_EC
     * loaded: es
     *
     * result: es
     */
    @Override
    public Locale fromPlayer(
            Player player
    ) {
        State snapshot =
                requireState();

        if (player == null) {
            return snapshot.fallbackLocale();
        }

        String rawLocale =
                getPlayerLocale(
                        player
                );

        if (
                rawLocale == null ||
                        rawLocale.isBlank()
        ) {
            return snapshot.fallbackLocale();
        }

        Locale requested;

        try {
            requested =
                    Locale.fromString(
                            rawLocale
                    );

        } catch (
                InvalidLocaleException exception
        ) {
            return snapshot.fallbackLocale();
        }

        return resolve(
                snapshot.locales(),
                requested
        )
                .map(
                        ResolvedLocale::locale
                )
                .orElse(
                        snapshot.fallbackLocale()
                );
    }

    @Override
    public String getLocaleId(
            Player player
    ) {
        return localeId(
                fromPlayer(
                        player
                )
        );
    }

    @Override
    public ConfigurationHandle fromPlayerLocale(
            Player player
    ) {
        State snapshot =
                requireState();

        if (player == null) {
            return snapshot
                    .fallbackConfiguration();
        }

        Locale locale =
                fromPlayer(
                        player
                );

        return fromLocaleCode(
                locale
        );
    }

    @Override
    public ConfigurationHandle fromLocaleCode(
            Locale locale
    ) {
        State snapshot =
                requireState();

        if (locale == null) {
            return snapshot
                    .fallbackConfiguration();
        }

        return resolve(
                snapshot.locales(),
                locale
        )
                .map(
                        ResolvedLocale::configuration
                )
                .orElse(
                        snapshot
                                .fallbackConfiguration()
                );
    }

    /**
     * Raw client locale.
     *
     * This method preserves the old API, but internally tries every
     * available Bukkit/Paper implementation.
     *
     * Resolution order:
     *
     * 1. Player#locale()
     * 2. Player#getLocale()
     * 3. Player#spigot().getLocale()
     * 4. Meteor fallback
     */
    public String getPlayerLocale(
            Player player
    ) {
        if (player == null) {
            return fallbackLocaleId();
        }

        /*
         * ------------------------------------------------------------
         * Modern Paper
         *
         * Player#locale() -> java.util.Locale
         * ------------------------------------------------------------
         */

        String locale =
                invokeModernLocale(
                        player
                );

        if (
                locale != null &&
                        !locale.isBlank()
        ) {
            return normalizeRawLocale(
                    locale
            );
        }

        /*
         * ------------------------------------------------------------
         * Bukkit / Spigot intermediate API
         *
         * Player#getLocale() -> String
         * ------------------------------------------------------------
         */

        locale =
                invokeLegacyLocale(
                        player
                );

        if (
                locale != null &&
                        !locale.isBlank()
        ) {
            return normalizeRawLocale(
                    locale
            );
        }

        /*
         * ------------------------------------------------------------
         * Old Spigot compatibility
         *
         * player.spigot().getLocale() -> String
         * ------------------------------------------------------------
         */

        locale =
                invokeSpigotLocale(
                        player
                );

        if (
                locale != null &&
                        !locale.isBlank()
        ) {
            return normalizeRawLocale(
                    locale
            );
        }

        return fallbackLocaleId();
    }

    /**
     * Tries Player#locale() reflectively.
     *
     * We deliberately do NOT reference player.locale() directly so
     * this class remains binary-compatible with older APIs.
     */
    private String invokeModernLocale(
            Player player
    ) {
        if (
                modernLocaleMethod == null ||
                        !modernLocaleAvailable.get()
        ) {
            return null;
        }

        return PluginConsumer.ofUnchecked(
                () -> {
                    Object result =
                            modernLocaleMethod.invoke(
                                    player
                            );

                    if (result == null) {
                        return null;
                    }

                    /*
                     * Current Paper returns java.util.Locale.
                     */
                    if (
                            result instanceof java.util.Locale javaLocale
                    ) {
                        return javaLocaleToId(
                                javaLocale
                        );
                    }

                    /*
                     * Defensive compatibility in case another platform
                     * implementation exposes something String-like.
                     */
                    return result.toString();
                },

                exception ->
                        modernLocaleAvailable.set(
                                false
                        ),

                () -> null
        );
    }

    /**
     * Tries Player#getLocale() reflectively.
     */
    private String invokeLegacyLocale(
            Player player
    ) {
        if (
                legacyLocaleMethod == null ||
                        !legacyLocaleAvailable.get()
        ) {
            return null;
        }

        return PluginConsumer.ofUnchecked(
                () -> {
                    Object result =
                            legacyLocaleMethod.invoke(
                                    player
                            );

                    return result == null
                            ? null
                            : result.toString();
                },

                exception ->
                        legacyLocaleAvailable.set(
                                false
                        ),

                () -> null
        );
    }

    /**
     * Tries the very old Player.Spigot locale API.
     */
    private String invokeSpigotLocale(
            Player player
    ) {
        if (
                playerSpigotMethod == null ||
                        spigotLocaleMethod == null ||
                        !spigotLocaleAvailable.get()
        ) {
            return null;
        }

        return PluginConsumer.ofUnchecked(
                () -> {
                    Object spigot =
                            playerSpigotMethod.invoke(
                                    player
                            );

                    if (spigot == null) {
                        return null;
                    }

                    Object result =
                            spigotLocaleMethod.invoke(
                                    spigot
                            );

                    return result == null
                            ? null
                            : result.toString();
                },

                exception ->
                        spigotLocaleAvailable.set(
                                false
                        ),

                () -> null
        );
    }

    @Override
    public Locale getFallbackLocale() {
        return requireState()
                .fallbackLocale();
    }

    /**
     * New LanguageService API.
     *
     * If your LanguageService hasn't added LanguageMode yet,
     * this method can remain as a normal public method without
     * the @Override annotation.
     */
    @Override
    public LanguageMode mode() {
        return LanguageMode.DYNAMIC;
    }

    /**
     * New API useful for inventories, scoreboards and diagnostics.
     */
    @Override
    public Set<Locale> getAvailableLocales() {
        return Set.copyOf(
                requireState()
                        .locales()
                        .keySet()
        );
    }

    @Override
    public void shutdown() {
        /*
         * Do not keep ConfigurationHandles from an old plugin lifecycle.
         */
        this.state =
                State.empty(
                        initialFallbackLocale
                );

        this.localesDirectory =
                null;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    /**
     * Resolves:
     *
     * 1. exact locale
     * 2. language-only locale
     *
     * Fallback itself is handled by the caller.
     */
    private Optional<ResolvedLocale> resolve(
            Map<Locale, ConfigurationHandle> locales,
            Locale requested
    ) {
        if (
                requested == null ||
                        locales.isEmpty()
        ) {
            return Optional.empty();
        }

        /*
         * 1. Exact locale.
         *
         * es_EC -> es_EC.yml
         */
        ConfigurationHandle exact =
                locales.get(
                        requested
                );

        if (exact != null) {
            return Optional.of(
                    new ResolvedLocale(
                            requested,
                            exact
                    )
            );
        }

        /*
         * 2. Base language.
         *
         * es_EC -> es.yml
         */
        if (requested.hasCountry()) {
            Locale base =
                    requested.base();

            ConfigurationHandle fallback =
                    locales.get(
                            base
                    );

            if (fallback != null) {
                return Optional.of(
                        new ResolvedLocale(
                                base,
                                fallback
                        )
                );
            }
        }

        return Optional.empty();
    }

    /**
     * When the configured fallback isn't present after a reload,
     * prefer one of the explicitly supported locales.
     */
    private ResolvedLocale findFirstSupported(
            Map<
                    Locale,
                    ConfigurationHandle
                    > locales
    ) {
        for (
                Locale supported :
                supportedLocales
        ) {
            Optional<ResolvedLocale> resolved =
                    resolve(
                            locales,
                            supported
                    );

            if (resolved.isPresent()) {
                return resolved.get();
            }
        }

        /*
         * Final emergency fallback:
         * first successfully loaded locale.
         */
        for (
                Map.Entry<
                        Locale,
                        ConfigurationHandle
                        > entry :
                locales.entrySet()
        ) {
            return new ResolvedLocale(
                    entry.getKey(),
                    entry.getValue()
            );
        }

        return null;
    }

    private State requireState() {
        State current =
                this.state;

        if (
                current == null ||
                        current.fallbackConfiguration()
                                == null
        ) {
            throw new IllegalStateException(
                    "DynamicLanguageService has not been initialized "
                            + "or no fallback language could be loaded"
            );
        }

        return current;
    }

    private File requireLocalesDirectory() {
        File current =
                this.localesDirectory;

        if (current == null) {
            throw new IllegalStateException(
                    "DynamicLanguageService has not been initialized"
            );
        }

        return current;
    }

    private void ensureDirectory() {
        File directory =
                requireLocalesDirectory();

        if (directory.exists()) {
            if (!directory.isDirectory()) {
                throw new IllegalStateException(
                        "Language path '"
                                + directory
                                + "' exists but isn't a directory"
                );
            }

            return;
        }

        if (!directory.mkdirs()) {
            throw new IllegalStateException(
                    "Can't create i18n folder at "
                            + directory
            );
        }
    }

    /**
     * Extracts configured defaults from the plugin JAR.
     *
     * Missing one locale resource does NOT abort the rest.
     */
    private void saveDefaults() {
        File directory =
                requireLocalesDirectory();

        /*
         * Avoid copying the same base locale several times.
         *
         * Example:
         *
         * supported:
         *   en_US
         *   en_GB
         *
         * resources:
         *   en.yml
         *
         * We only create en.yml once.
         */
        Set<String> saved =
                new HashSet<>();

        for (
                Locale supported :
                supportedLocales
        ) {
            DefaultLocaleResource resource =
                    findDefaultResource(
                            supported
                    );

            if (resource == null) {
                getLogger().warn(
                        "No default locale resource was found for '"
                                + supported.getId()
                                + "'. Expected one of: "
                                + defaultResourceCandidates(
                                supported
                        )
                );

                continue;
            }

            String destinationId =
                    resource.locale().getId();

            /*
             * Several regional locales may resolve to the
             * same base file:
             *
             * en_US -> en.yml
             * en_GB -> en.yml
             */
            if (!saved.add(destinationId)) {
                try {
                    resource.stream().close();
                } catch (IOException ignored) {}

                continue;
            }

            File destination =
                    new File(
                            directory,
                            destinationId + ".yml"
                    );

            if (destination.exists()) {
                try {
                    resource.stream().close();
                } catch (IOException ignored) {}

                continue;
            }

            try (
                    InputStream input =
                            resource.stream();

                    FileOutputStream output =
                            new FileOutputStream(
                                    destination
                            )
            ) {
                input.transferTo(
                        output
                );

                getLogger().debug(
                        "Saved default locale '"
                                + destinationId
                                + ".yml'"
                );

            } catch (
                    IOException exception
            ) {
                getLogger().error(
                        exception,
                        "Failed to save default locale '"
                                + destinationId
                                + "'"
                );
            }
        }
    }

    private DefaultLocaleResource findDefaultResource(
            Locale locale
    ) {
        /*
         * ------------------------------------------------------------
         * 1. Exact regional resource.
         *
         * en_US -> /i18n/en_US.yml
         * ------------------------------------------------------------
         */

        String exactPath =
                "/i18n/"
                        + locale.getId()
                        + ".yml";

        InputStream exact =
                DynamicLanguageService.class
                        .getResourceAsStream(
                                exactPath
                        );

        if (exact != null) {
            return new DefaultLocaleResource(
                    locale,
                    exactPath,
                    exact
            );
        }

        /*
         * ------------------------------------------------------------
         * 2. Base language resource.
         *
         * en_US -> /i18n/en.yml
         * ------------------------------------------------------------
         */

        if (locale.hasCountry()) {
            Locale base =
                    locale.base();

            String basePath =
                    "/i18n/"
                            + base.getId()
                            + ".yml";

            InputStream baseStream =
                    DynamicLanguageService.class
                            .getResourceAsStream(
                                    basePath
                            );

            if (baseStream != null) {
                return new DefaultLocaleResource(
                        base,
                        basePath,
                        baseStream
                );
            }
        }

        return null;
    }

    private List<String> defaultResourceCandidates(
            Locale locale
    ) {
        List<String> result =
                new ArrayList<>();

        result.add(
                "/i18n/"
                        + locale.getId()
                        + ".yml"
        );

        if (locale.hasCountry()) {
            result.add(
                    "/i18n/"
                            + locale.base().getId()
                            + ".yml"
            );
        }

        return List.copyOf(
                result
        );
    }

    private record DefaultLocaleResource(
            Locale locale,
            String path,
            InputStream stream
    ) {

        private DefaultLocaleResource {
            Objects.requireNonNull(
                    locale,
                    "locale"
            );

            Objects.requireNonNull(
                    path,
                    "path"
            );

            Objects.requireNonNull(
                    stream,
                    "stream"
            );
        }
    }

    private List<Locale> parseSupportedLocales(
            String[] source
    ) {
        if (
                source == null ||
                        source.length == 0
        ) {
            return List.of();
        }

        LinkedHashSet<Locale> result =
                new LinkedHashSet<>();

        for (String value : source) {
            if (
                    value == null ||
                            value.isBlank()
            ) {
                continue;
            }

            try {
                result.add(
                        Locale.fromString(
                                value
                        )
                );

            } catch (
                    InvalidLocaleException exception
            ) {
                throw new IllegalArgumentException(
                        "Invalid supported locale '"
                                + value
                                + "'",
                        exception
                );
            }
        }

        return List.copyOf(
                result
        );
    }

    /**
     * Converts whatever format the client/server gives us to Meteor's
     * conventional locale id.
     *
     * Examples:
     *
     * en-us -> en_US
     * es_ec -> es_EC
     * es    -> es
     */
    private String normalizeRawLocale(
            String locale
    ) {
        if (
                locale == null ||
                        locale.isBlank()
        ) {
            return fallbackLocaleId();
        }

        try {
            return localeId(
                    Locale.fromString(
                            locale
                    )
            );

        } catch (
                InvalidLocaleException ignored
        ) {
            /*
             * Some ancient/modded implementations may provide
             * unexpected values.
             */
            return locale;
        }
    }

    private String fallbackLocaleId() {
        State snapshot =
                this.state;

        Locale fallback =
                snapshot != null
                        ? snapshot.fallbackLocale()
                        : initialFallbackLocale;

        return localeId(
                fallback
        );
    }

    /**
     * Does not depend on Locale#getId(), which allows this service
     * to remain compatible with both your old Locale implementation
     * and the new refactored one.
     */
    private String localeId(
            Locale locale
    ) {
        if (locale == null) {
            return "en_US";
        }

        String language =
                locale.getLanguage();

        String country =
                locale.getCountry();

        if (
                country == null ||
                        country.isBlank()
        ) {
            return language;
        }

        return language
                + "_"
                + country;
    }

    private String javaLocaleToId(
            java.util.Locale locale
    ) {
        if (locale == null) {
            return null;
        }

        String language =
                locale.getLanguage();

        String country =
                locale.getCountry();

        if (
                language == null ||
                        language.isBlank()
        ) {
            return null;
        }

        if (
                country == null ||
                        country.isBlank()
        ) {
            return language;
        }

        return language
                .toLowerCase(
                        java.util.Locale.ROOT
                )
                + "_"
                + country.toUpperCase(
                java.util.Locale.ROOT
        );
    }

    private String removeYamlExtension(
            String fileName
    ) {
        if (
                fileName == null ||
                        fileName.length() <= 4
        ) {
            return fileName;
        }

        String lower =
                fileName.toLowerCase(
                        java.util.Locale.ROOT
                );

        if (lower.endsWith(".yml")) {
            return fileName.substring(
                    0,
                    fileName.length() - 4
            );
        }

        return fileName;
    }

    /**
     * Pure reflection helpers.
     *
     * They intentionally don't log: a missing method is expected when
     * Meteor is running on another supported Minecraft API generation.
     */
    private static Method findMethod(
            Class<?> owner,
            String name,
            Class<?>... parameters
    ) {
        if (owner == null) {
            return null;
        }

        return PluginConsumer.ofUnchecked(
                () ->
                        owner.getMethod(
                                name,
                                parameters
                        ),
                ignored -> {},
                () -> null
        );
    }

    private static Class<?> findClass(
            String name
    ) {
        return PluginConsumer.ofUnchecked(
                () ->
                        Class.forName(
                                name
                        ),
                ignored -> {},
                () -> null
        );
    }

    private void logLocaleResolver() {
        StringBuilder strategy =
                new StringBuilder();

        if (modernLocaleMethod != null) {
            strategy.append(
                    "Player#locale()"
            );
        }

        if (legacyLocaleMethod != null) {
            if (!strategy.isEmpty()) {
                strategy.append(" -> ");
            }

            strategy.append(
                    "Player#getLocale()"
            );
        }

        if (
                playerSpigotMethod != null &&
                        spigotLocaleMethod != null
        ) {
            if (!strategy.isEmpty()) {
                strategy.append(" -> ");
            }

            strategy.append(
                    "Player#spigot().getLocale()"
            );
        }

        if (!strategy.isEmpty()) {
            strategy.append(" -> ");
        }

        strategy.append(
                "fallback"
        );

        getLogger().debug(
                "Language locale resolver: "
                        + strategy
        );
    }

    private record ResolvedLocale(
            Locale locale,
            ConfigurationHandle configuration
    ) {

        private ResolvedLocale {
            Objects.requireNonNull(
                    locale,
                    "locale"
            );

            Objects.requireNonNull(
                    configuration,
                    "configuration"
            );
        }
    }

    /**
     * Immutable published language registry.
     */
    private record State(
            Map<
                    Locale,
                    ConfigurationHandle
                    > locales,
            Locale fallbackLocale,
            ConfigurationHandle fallbackConfiguration
    ) {

        private State {
            locales =
                    Map.copyOf(
                            locales
                    );

            Objects.requireNonNull(
                    fallbackLocale,
                    "fallbackLocale"
            );
        }

        private static State empty(
                Locale fallbackLocale
        ) {
            return new State(
                    Map.of(),
                    fallbackLocale,
                    null
            );
        }
    }
}