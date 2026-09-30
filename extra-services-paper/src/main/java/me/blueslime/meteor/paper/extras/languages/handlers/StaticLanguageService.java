package me.blueslime.meteor.paper.extras.languages.handlers;

import me.blueslime.meteor.paper.extras.languages.LanguageMode;
import me.blueslime.meteor.paper.extras.languages.LanguageService;

import me.blueslime.meteor.paper.extras.languages.locale.Locale;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;

import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.Set;

public final class StaticLanguageService
        implements LanguageService {

    private final String fileName;

    private final String resourcePath;

    private volatile Locale fallbackLocale;

    private volatile ConfigurationHandle configuration;

    public StaticLanguageService(
            String fallbackLocale
    ) {
        this(
                fallbackLocale,
                "messages.yml",
                "/messages.yml"
        );
    }

    public StaticLanguageService(
            String fallbackLocale,
            String fileName,
            String resourcePath
    ) {
        this.fallbackLocale =
                Locale.fromString(
                        fallbackLocale
                );

        this.fileName =
                Objects.requireNonNull(
                        fileName,
                        "fileName"
                );

        this.resourcePath =
                Objects.requireNonNull(
                        resourcePath,
                        "resourcePath"
                );

        /*
         * NO initialize()
         * NO getPlugin()
         * NO File I/O
         * NO fetch()
         *
         * inside constructors.
         */
    }

    @Override
    public void initialize() {
        loadConfiguration();
    }

    @Override
    public void reload() {
        loadConfiguration();
    }

    private void loadConfiguration() {
        ConfigurationHandle loaded =
                getPlugin()
                        .getConfigurationProvider()
                        .load(
                                getFileOfDirectory(
                                        fileName
                                ),
                                resourcePath
                        );

        /*
         * Publish only after load completed.
         */
        this.configuration =
                loaded;

        registerImpl(
            ConfigurationHandle.class,
            "messages.yml",
            configuration,
            true
        );
    }

    @Override
    public ConfigurationHandle fromPlayerLocale(
            Player player
    ) {
        return requireConfiguration();
    }

    @Override
    public ConfigurationHandle fromLocaleCode(
            Locale locale
    ) {
        return requireConfiguration();
    }

    @Override
    public Locale fromPlayer(
            Player player
    ) {
        return fallbackLocale;
    }

    @Override
    public String getLocaleId(
            Player player
    ) {
        return fallbackLocale.getId();
    }

    @Override
    public void updateFallbackLocale(
            String locale
    ) {
        this.fallbackLocale =
                Locale.fromString(
                        locale
                );
    }

    @Override
    public Locale getFallbackLocale() {
        return fallbackLocale;
    }

    @Override
    public LanguageMode mode() {
        return LanguageMode.STATIC;
    }

    @Override
    public Set<Locale> getAvailableLocales() {
        return Set.of(
                fallbackLocale
        );
    }

    @Override
    public void shutdown() {
        configuration =
                null;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private ConfigurationHandle requireConfiguration() {
        ConfigurationHandle current =
                configuration;

        if (current == null) {
            throw new IllegalStateException(
                    "StaticLanguageService has not been initialized"
            );
        }

        return current;
    }
}