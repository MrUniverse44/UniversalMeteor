package me.blueslime.meteor.paper.extras.services.conditions.handler.list;

import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;
import me.blueslime.meteor.paper.extras.services.conditions.handler.InventoryHandler;
import me.blueslime.meteor.paper.extras.services.conditions.objects.ConditionCompiler;
import me.blueslime.meteor.paper.extras.services.conditions.objects.LocalizedInventory;
import me.blueslime.meteor.paper.extras.services.item.ItemWrapper;
import me.blueslime.meteor.paper.extras.services.item.armor.ItemArmorSlot;
import me.blueslime.meteor.paper.extras.services.languages.LanguageService;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class DynamicInventoryHandler implements InventoryHandler {

    public static boolean SHOULD_DEBUG = false;
    private final Map<String, LocalizedInventory> localeItemMap = new ConcurrentHashMap<>();

    private final boolean loadConfiguration;
    private final String itemFileName;
    private final String itemResourcePath;

    public DynamicInventoryHandler(boolean loadConfiguration, String itemFileName, String itemResourcePath) {
        this.loadConfiguration = loadConfiguration;
        this.itemFileName = itemFileName;
        this.itemResourcePath = itemResourcePath;
    }

    @Override
    public void initialize() {
        if (loadConfiguration) {
            getLogger().debug("Loading items configuration from file '" + itemFileName + "' with resource path: '" + itemResourcePath + "'");
            ConfigurationHandle items = getPlugin().getConfigurationProvider().load(getFileOfDirectory(itemFileName), itemResourcePath);

            for (String language : items.getKeys("items", false)) {
                Locale locale = Locale.fromString(language);
                LocalizedInventory inventories = localeItemMap.computeIfAbsent(locale.getLanguage(), k -> LocalizedInventory.fromLocale(locale.getLanguage()));
                getLogger().debug("Loaded language inventory with id: " + inventories.locale().toString());

                for (String inventoryId : items.getKeys("items." + language, false)) {
                    final String invPath = "items." + language + "." + inventoryId;
                    getLogger().debug("Registered inventory with id '" + inventoryId + "' for language id '" + inventories.locale().toString() + "'");

                    List<ItemWrapper> wrappers = inventories.itemMap().computeIfAbsent(inventoryId, k -> new CopyOnWriteArrayList<>());

                    for (String itemId : items.getKeys(invPath, false)) {

                        final String itemPath = invPath + "." + itemId;

                        ItemWrapper wrapper = ItemWrapper.fromData(items, itemPath)
                            .customIdAsInventory(inventoryId)
                            .addStringData("latam_item_locale", inventories.locale().getLanguage())
                            .addStringData("latam_item_inventory", inventoryId)
                            .addStringData("latam_item_id", itemId)
                            .addBooleanData("latam_item_movement", !items.getBoolean(itemPath + ".allow-item-move", false))
                            .addBooleanData("latam_item_drop", !items.getBoolean(itemPath + ".allow-item-drop", false))
                            .addBooleanData("latam_item_drag", !items.getBoolean(itemPath + ".allow-item-drag", false));

                        if (items.contains(itemPath + ".conditions")) {
                            register(inventories, inventoryId, itemId, items.getStringList(itemPath + ".conditions"));
                            wrapper.giveConditions(condition -> canUseItem(inventories, inventoryId, itemId, condition.player()));
                        }
                        inventories.itemActions().put(inventoryId + "_" + itemId, items.getStringList(itemPath + ".actions"));
                        getLogger().debug("Registered item with id '" + itemId + "' to inventory with id '" + inventoryId + "' at language id '" + inventories.locale().toString() + "'");
                        wrappers.add(wrapper);
                    }
                    getLogger().debug("Inventory with id '" + inventoryId + "' finished loading at locale id '" + inventories.locale().toString() + "' with " + wrappers.size() + " item(s).");
                }
            }
        }
    }

    @Override
    public void shutdown() {
        localeItemMap.clear();
    }

    @Override
    public void give(String id, Player player) {
        give(id, player, player.getInventory());
    }

    @Override
    public void give(String id, Player player, Inventory inventory) {
        inventory.clear();

        LanguageService languages = getLanguages();
        Locale locale = languages.fromPlayer(player);

        LocalizedInventory localeInventories = localeItemMap.get(locale.getLanguage());

        if (SHOULD_DEBUG) {
            getLogger().debug("Searching inventory with id '" + id + "' for '" + player.getName() + "' at locale '" + locale.getLanguage() + "'");
        }

        boolean isNotFallbackLocale = !locale.getLanguage().equalsIgnoreCase(languages.getFallbackLocale().getLanguage());
        if (isNotFallbackLocale && (localeInventories == null || localeInventories.isEmpty() || !localeInventories.containsInventory(id))) {
            localeInventories = localeItemMap.computeIfAbsent(languages.getFallbackLocale().getLanguage(), k -> LocalizedInventory.fromLocale(languages.getFallbackLocale()));
            if (SHOULD_DEBUG) {
                getLogger().debug("That locale was not found, now we changed to fallback locale.");
            }
        }

        if (localeInventories == null || localeInventories.isEmpty() || !localeInventories.containsInventory(id)) {
            return;
        }

        List<ItemWrapper> wrappers = localeInventories.itemMap().computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());

        wrappers.forEach(item -> {
            ItemStack itemStack = item.getUserItem(player);

            if (itemStack == null) {
                return;
            }

            Optional<ItemArmorSlot> optionalSlot = item.getArmorSlot();
            if (item.isAutoEquip() && optionalSlot.isPresent()) {
                optionalSlot.ifPresent(slot -> slot.equip(player, itemStack));
                return;
            }

            if (item.getSlots().isEmpty()) {
                inventory.addItem(itemStack);
                return;
            }

            for (int slot : item.getSlots()) {
                InventoryType type = inventory.getType();

                boolean isValidSlot = type == InventoryType.PLAYER ? slot >= 0 && slot < 36 : slot >= 0 && slot < inventory.getStorageContents().length;

                if (isValidSlot) {
                    inventory.setItem(slot, itemStack);
                }
            }
        });
    }

    @Override
    public List<String> getActionsFor(Locale locale, String inventoryId, String itemId) {
        LocalizedInventory inventory = localeItemMap.get(locale.getLanguage());
        if (inventory == null) {
            if (SHOULD_DEBUG) {
                getLogger().error("No items has been found with locale '" + locale.getLanguage() + "'");
            }
            return Collections.emptyList();
        }
        return inventory.itemActions().getOrDefault(inventoryId + "_" + itemId, Collections.emptyList());
    }

    @Override
    public void reload() {
        shutdown();
        initialize();
    }

    public void register(LocalizedInventory localizedInventories, String inventoryId, String itemId, List<String> rawConditions) {
        getLogger().debug("Registering item conditions with id '" + itemId + "' to inventory with id '" + inventoryId + "' in language '" + localizedInventories.locale().toString() + "'");
        Map<String, List<CompiledCondition>> actionListMap = localizedInventories.itemConditions().computeIfAbsent(inventoryId, k -> new ConcurrentHashMap<>());

        List<CompiledCondition> compiled = new ArrayList<>();

        for (String raw : rawConditions) {
            compiled.add(ConditionCompiler.compile(raw));
        }

        actionListMap.put(itemId, compiled);
    }

    public boolean canUseItem(LocalizedInventory localizedInventories, String inventoryId, String itemId, Player player) {
        Map<String, List<CompiledCondition>> actionListMap = localizedInventories.itemConditions().computeIfAbsent(inventoryId, k -> new ConcurrentHashMap<>());

        List<CompiledCondition> conditions = actionListMap.get(itemId);
        if (conditions == null) return true;

        for (CompiledCondition condition : conditions) {
            if (!condition.test(player)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}




