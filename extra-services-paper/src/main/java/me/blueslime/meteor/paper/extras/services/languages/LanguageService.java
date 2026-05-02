package me.blueslime.meteor.paper.extras.services.languages;

import me.blueslime.meteor.paper.extras.services.languages.handlers.StaticLanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.bukkit.entity.Player;


public interface LanguageService extends PlatformService {

    ConfigurationHandle fromPlayerLocale(Player player);

    ConfigurationHandle fromLocaleCode(Locale locale);

    String getLocaleId(Player player);

    Locale fromPlayer(Player player);

    void updateFallbackLocale(String locale);

    default boolean isStatic() {
        return this instanceof StaticLanguageService;
    }

    Locale getFallbackLocale();

}
