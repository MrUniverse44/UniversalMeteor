package me.blueslime.meteor.paper.extras.languages;

import me.blueslime.meteor.paper.extras.languages.locale.Locale;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.entity.Player;

import java.util.Set;

public interface LanguageService
        extends PlatformService {

    ConfigurationHandle fromPlayerLocale(
            Player player
    );

    ConfigurationHandle fromLocaleCode(
            Locale locale
    );

    default ConfigurationHandle fromLocaleCode(
            String locale
    ) {
        return fromLocaleCode(
                Locale.fromString(
                        locale
                )
        );
    }

    Locale fromPlayer(
            Player player
    );

    void updateFallbackLocale(
            String locale
    );

    Locale getFallbackLocale();

    LanguageMode mode();

    default String getLocaleId(
            Player player
    ) {
        return fromPlayer(
                player
        ).getId();
    }

    default boolean isStatic() {
        return mode() ==
                LanguageMode.STATIC;
    }

    default boolean isDynamic() {
        return mode() ==
                LanguageMode.DYNAMIC;
    }

    /**
     * Mainly useful for diagnostics or configuration UIs.
     */
    default Set<Locale> getAvailableLocales() {
        return Set.of(
                getFallbackLocale()
        );
    }
}