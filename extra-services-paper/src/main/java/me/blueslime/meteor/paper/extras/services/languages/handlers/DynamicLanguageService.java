package me.blueslime.meteor.paper.extras.services.languages.handlers;

import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.InvalidLocaleException;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.configuration.handle.DefaultConfigurationHandle;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DynamicLanguageService implements LanguageService {

    /* Here we cache all other languages*/
    private final Map<Locale, ConfigurationHandle> localeMap = new ConcurrentHashMap<>();

    /* When no language was found, it uses this language */
    private ConfigurationHandle fallbackLocaleConfiguration;

    /* getLocale method data */
    private final Class<?> playerSpigotClass = PluginConsumer.ofUnchecked(() -> Class.forName("org.bukkit.entity.Player$Spigot"), e -> {}, () -> null);
    private final Method playerSpigotMethod = PluginConsumer.ofUnchecked(() -> Player.class.getMethod("spigot"), e -> {}, () -> null);
    private final Method spigotLocale = PluginConsumer.ofUnchecked(() -> playerSpigotClass.getMethod("getLocale"), e -> {}, () -> null);
    private final Method playerLocale = PluginConsumer.ofUnchecked(() -> Player.class.getMethod("getLocale"), e -> {}, () -> null);

    /* Can't found method data of nothing xD */
    private boolean languageMethod = true;

    private final String fallbackLocale;
    private final File localesDir;

    public DynamicLanguageService(File directory, String... supportedLocales) {
        this.localesDir = new File(directory, "i18n");
        this.fallbackLocale = supportedLocales[0];

        if (!localesDir.exists() && !localesDir.mkdirs()) {
            getLogger().error("Can't create i18n folder");
            return;
        }

        saveDefaults(supportedLocales);

        reloadLocales();
    }

    private void saveDefaults(String... locales) {
        for (String locale : locales) {
            final File localeFile = new File(localesDir, locale + ".yml");

            if (!localeFile.exists()) {

                final InputStream in0 = DynamicLanguageService.class.getResourceAsStream(
                    "/i18n/" + locale + ".yml"
                );

                if (in0 == null) {
                    return;
                }

                try (
                    InputStream in = in0;
                    FileOutputStream fos = new FileOutputStream(localeFile)
                ) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = in.read(buffer)) != -1) {
                        fos.write(buffer, 0, length);
                    }
                    fos.flush();
                } catch (IOException e) {
                    getLogger().error(e, "Failed to save default locale " + locale + "!");
                }
            }
        }
    }

    @Override
    public void reload() {
        reloadLocales();
    }

    /**
     * Reload the language files from the "lang" folder.
     */
    public void reloadLocales() {
        localeMap.clear();

        File[] files = localesDir.listFiles((d, fn) -> fn.endsWith(".yml"));

        if (files == null) {
            return;
        }

        for (final File file : files) {
            final String localeName = file.getName().substring(0, file.getName().length() - 4);

            final Locale locale;
            try {
                locale = Locale.fromString(localeName);
            } catch (InvalidLocaleException e) {
                getLogger().debug("Invalid locale in file name: " + file);
                continue;
            }

            final ConfigurationHandle data = new DefaultConfigurationHandle(file, "/i18n/" + localeName + ".yml");

            localeMap.put(locale, data);
        }

        /* Default language location */
        InputStream resource = DynamicLanguageService.class.getResourceAsStream(
            "/i18n/" + fallbackLocale + ".yml"
        );

        if (resource == null) {
            return;
        }

        fallbackLocaleConfiguration = new DefaultConfigurationHandle(new File(localesDir, fallbackLocale), resource);
    }

    @Override
    public String getLocaleId(Player player) {
        return getPlayerLocale(player);
    }

    @Override
    public ConfigurationHandle fromPlayerLocale(Player player) {
        if (player == null) {
            return fallbackLocaleConfiguration;
        }
        return fromLocaleCode(Locale.fromString(getPlayerLocale(player)));
    }

    @Override
    public Locale fromPlayer(Player player) {
        return Locale.fromString(getPlayerLocale(player));
    }

    @Override
    public ConfigurationHandle fromLocaleCode(Locale locale) {
        if (localeMap.containsKey(locale)) {
            return localeMap.getOrDefault(locale, fallbackLocaleConfiguration);
        }

        return localeMap.getOrDefault(
            Locale.fromString(locale.getLanguage()),
            fallbackLocaleConfiguration
        );
    }

    public String getPlayerLocale(Player player) {
        if (!languageMethod) {
            return PluginConsumer.ofUnchecked(
                () -> (String) playerLocale.invoke(player),
                e -> {},
                () -> PluginConsumer.ofUnchecked(
                    () -> {
                        final Object spigot = playerSpigotMethod.invoke(player);
                        return (String) spigotLocale.invoke(spigot);
                    },
                    e -> languageMethod = true,
                    () -> fallbackLocale
                )
            );
        }
        return fallbackLocale;
    }
}