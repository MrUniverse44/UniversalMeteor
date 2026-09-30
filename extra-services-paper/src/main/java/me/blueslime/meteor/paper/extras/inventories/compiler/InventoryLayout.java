package me.blueslime.meteor.paper.extras.inventories.compiler;

public enum InventoryLayout {

    /**
     * InventoryService determines it from LanguageService.
     */
    AUTO,

    /**
     * items:
     *   lobby:
     *     selector:
     */
    STATIC,

    /**
     * items:
     *   en:
     *     lobby:
     *       selector:
     */
    LOCALIZED
}