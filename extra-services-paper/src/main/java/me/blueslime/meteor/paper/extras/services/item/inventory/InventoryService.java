package me.blueslime.meteor.paper.extras.services.item.inventory;

import me.blueslime.meteor.paper.extras.services.conditions.handler.InventoryHandler;

import me.blueslime.meteor.paper.extras.services.conditions.handler.list.DynamicInventoryHandler;
import me.blueslime.meteor.paper.extras.services.conditions.handler.list.StaticInventoryHandler;
import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.inventory.Inventory;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;

public class InventoryService implements PlatformService {

    private final ExternalItems externalItems = new ExternalItems();
    private InventoryHandler inventoryHandler;

    public InventoryService(boolean loadConfiguration, String itemFileName, String itemResourcePath) {
        if (isImplemented(LanguageService.class)) {
            LanguageService language = fetch(LanguageService.class);
            if (language.isStatic()) {
                inventoryHandler = new StaticInventoryHandler(loadConfiguration, itemFileName, itemResourcePath);
            } else {
                inventoryHandler = new DynamicInventoryHandler(loadConfiguration, itemFileName, itemResourcePath);
            }
        } else {
            inventoryHandler = new StaticInventoryHandler(loadConfiguration, itemFileName, itemResourcePath);
        }
        inventoryHandler.initialize();
    }

    public void updateInventoryHandler(InventoryHandler inventoryHandler) {
        this.inventoryHandler = inventoryHandler;
    }

    public void give(String id, Player player) {
        inventoryHandler.give(id, player);
    }

    public void give(String id, Player player, Inventory inventory) {
        inventoryHandler.give(id, player, inventory);
    }

    public List<String> getActionsOf(Locale locale, String inventoryId, String itemId) {
        return inventoryHandler.getActionsFor(locale, inventoryId, itemId);
    }

    public List<String> getActionsOf(String locale, String inventoryId, String itemId) {
        return inventoryHandler.getActionsFor(Locale.fromString(locale), inventoryId, itemId);
    }

    @Override
    public void initialize() {
        inventoryHandler.initialize();
    }

    @Override
    public void shutdown() {
        inventoryHandler.shutdown();
    }

    @Override
    public void reload() {
        shutdown();
        initialize();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    public ExternalItems getExternalItems() {
        return externalItems;
    }
}


