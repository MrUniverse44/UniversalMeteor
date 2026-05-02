package me.blueslime.meteor.paper.extras.services.languages.handlers;

import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.configuration.handle.DefaultConfigurationHandle;
import org.bukkit.entity.Player;

import java.io.File;

public class StaticLanguageService implements LanguageService {

    private Locale fallbackLocaleObject;
    private String fallbackLocaleText;
    private final File directory;

    public StaticLanguageService(String fallbackLocaleText) {
        initialize();
        this.fallbackLocaleObject = Locale.fromString(fallbackLocaleText);
        this.fallbackLocaleText = fallbackLocaleText;
        this.directory = getDirectory();
    }

    @Override
    public void initialize() {
        reload();
    }

    @Override
    public void reload() {
        registerImpl(
            ConfigurationHandle.class,
            "messages.yml",
            getPlugin().getConfigurationProvider().load(new File(directory, "messages.yml"), "/messages.yml"),
            true
        );
    }

    @Override
    public ConfigurationHandle fromPlayerLocale(Player player) {
        return fetch(ConfigurationHandle.class, "messages.yml");
    }

    @Override
    public ConfigurationHandle fromLocaleCode(Locale locale) {
        return fetch(ConfigurationHandle.class, "messages.yml");
    }

    @Override
    public String getLocaleId(Player player) {
        return fallbackLocaleText;
    }

    public void updateFallbackLocale(String locale) {
        this.fallbackLocaleObject = Locale.fromString(locale);
        this.fallbackLocaleText = fallbackLocaleObject.getLanguage() + "_" + fallbackLocaleObject.getCountry();
    }

    @Override
    public Locale fromPlayer(Player player) {
        return fallbackLocaleObject;
    }

    @Override
    public Locale getFallbackLocale() {
        return fallbackLocaleObject;
    }

}