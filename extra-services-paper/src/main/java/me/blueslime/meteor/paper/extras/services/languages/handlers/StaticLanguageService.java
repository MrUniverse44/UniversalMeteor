package me.blueslime.meteor.paper.extras.services.languages.handlers;

import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.configuration.handle.DefaultConfigurationHandle;
import me.blueslime.meteor.platforms.api.utils.files.YamlConfiguration;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import org.bukkit.entity.Player;

import java.io.File;

public class StaticLanguageService implements LanguageService {

    private final Locale fallbackLocaleObject;
    private final String fallbackLocaleText;
    private final File directory;

    public StaticLanguageService(File directory, String fallbackLocaleText) {
        initialize();
        this.fallbackLocaleObject = Locale.fromString(fallbackLocaleText);
        this.fallbackLocaleText = fallbackLocaleText;
        this.directory = directory;
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
            new DefaultConfigurationHandle(new File(directory, "messages.yml"), "/messages.yml"),
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

    @Override
    public Locale fromPlayer(Player player) {
        return fallbackLocaleObject;
    }

}