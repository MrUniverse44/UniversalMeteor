package me.blueslime.meteor.paper.extras.services;

import me.blueslime.meteor.implementation.service.Service;
import me.blueslime.meteor.paper.extras.services.actions.ActionService;
import me.blueslime.meteor.paper.extras.services.item.inventory.InventoryService;
import me.blueslime.meteor.paper.extras.services.item.inventory.InventoryServiceSettings;
import me.blueslime.meteor.paper.extras.services.item.skin.ItemSkinService;
import me.blueslime.meteor.paper.extras.services.languages.handlers.DynamicLanguageService;
import me.blueslime.meteor.paper.extras.services.languages.handlers.StaticLanguageService;
import me.blueslime.meteor.paper.extras.services.menus.MenuService;
import me.blueslime.meteor.paper.extras.services.scoreboard.ScoreboardService;

public class PaperExtraServices {

    public static Service findLocales(boolean dynamic, String... supportedLanguages) {
        return dynamic ? new DynamicLanguageService(supportedLanguages) : new StaticLanguageService(supportedLanguages.length >= 1 ? supportedLanguages[0] : "en_US");
    }

    public static Service[] findAll(InventoryServiceSettings inventorySettings, String scoreboardFileName, String scoreboardResourcePath) {
        return new Service[] {
            new ActionService(),
            new ItemSkinService(),
            new InventoryService(inventorySettings.shouldLoadConfigurations(), inventorySettings.getFileName(), inventorySettings.getResourcePath()),
            new MenuService(),
            new ScoreboardService(scoreboardFileName, scoreboardResourcePath),
        };
    }
    public static Service[] findAllWithoutScoreboards(InventoryServiceSettings inventorySettings) {
        return new Service[] {
            new ActionService(),
            new ItemSkinService(),
            new InventoryService(inventorySettings.shouldLoadConfigurations(), inventorySettings.getFileName(), inventorySettings.getResourcePath()),
            new MenuService()
        };
    }

}
