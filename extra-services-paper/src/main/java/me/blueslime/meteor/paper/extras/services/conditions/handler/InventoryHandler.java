package me.blueslime.meteor.paper.extras.services.conditions.handler;

import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.bukkit.entity.Player;

import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;

public interface InventoryHandler extends PlatformService {

    default LanguageService getLanguages() {
        return fetch(LanguageService.class);
    }

    default void give(String id, Player player) {

    }

    void give(String id, Player player, Inventory inventory);

    List<String> getActionsFor(Locale locale, String inventoryId, String itemId);
}
