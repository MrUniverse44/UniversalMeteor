package me.blueslime.meteor.paper.extras.services.conditions.handler.list;

import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;
import me.blueslime.meteor.paper.extras.services.conditions.handler.InventoryHandler;
import me.blueslime.meteor.paper.extras.services.conditions.objects.ConditionCompiler;
import me.blueslime.meteor.paper.extras.services.item.ItemWrapper;
import me.blueslime.meteor.paper.extras.services.item.armor.ItemArmorSlot;
import me.blueslime.meteor.paper.extras.services.languages.locale.Locale;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class StaticInventoryHandler implements InventoryHandler {

    private final Map<String, Map<String, List<CompiledCondition>>> itemConditions = new ConcurrentHashMap<>();
    private final Map<String, List<ItemWrapper>> itemMap = new ConcurrentHashMap<>();
    private final Map<String, List<String>> itemActions = new ConcurrentHashMap<>();

    private final boolean loadConfiguration;
    private final String itemFileName;
    private final String itemResourcePath;

    public StaticInventoryHandler(boolean loadConfiguration, String itemFileName, String itemResourcePath) {
        this.loadConfiguration = loadConfiguration;
        this.itemFileName = itemFileName;
        this.itemResourcePath = itemResourcePath;
    }

    @Override
    public void initialize() {
        if (loadConfiguration) {
            ConfigurationHandle items = getPlugin().getConfigurationProvider().load(getFileOfDirectory(itemFileName), itemResourcePath);

            for (String key : items.getKeys("items", false)) {
                for (String subKey : items.getKeys("items." + key, false)) {
                    String path = "items." + subKey + "." + key;

                    List<ItemWrapper> wrappers = itemMap.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>());

                    ItemWrapper wrapper = ItemWrapper.fromData(items, path)
                        .customIdAsInventory(key)
                        .addStringData("latam_item_locale", "en")
                        .addStringData("latam_item_inventory", key)
                        .addStringData("latam_item_id", subKey)
                        .addBooleanData("latam_item_movement", items.getBoolean(path + ".allow-item-move", false))
                        .addBooleanData("latam_item_drop", items.getBoolean(path + ".allow-item-drop", false));

                    if (items.contains(path + ".conditions")) {
                        register(subKey, key, items.getStringList(path + ".conditions"));
                        wrapper.giveConditions(condition -> canUseItem(subKey, key, condition.player()));
                    }
                    itemActions.put(key + "_" + subKey, items.getStringList(path + ".actions"));
                    wrappers.add(wrapper);
                }
            }
        }
    }

    @Override
    public void shutdown() {
        itemConditions.clear();
        itemMap.clear();
    }

    @Override
    public void give(String id, Player player) {
        give(id, player, player.getInventory());
    }

    @Override
    public void give(String id, Player player, Inventory inventory) {
        inventory.clear();

        List<ItemWrapper> wrappers = itemMap.computeIfAbsent(id, k -> new CopyOnWriteArrayList<>());

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
        return itemActions.getOrDefault(inventoryId + "_" + itemId, Collections.emptyList());
    }

    @Override
    public void reload() {
        shutdown();
        initialize();
    }

    public void register(String mainKey, String id, List<String> rawConditions) {
        Map<String, List<CompiledCondition>> actionListMap = itemConditions.computeIfAbsent(mainKey, k -> new ConcurrentHashMap<>());

        List<CompiledCondition> compiled = new ArrayList<>();

        for (String raw : rawConditions) {
            compiled.add(ConditionCompiler.compile(raw));
        }

        actionListMap.put(id, compiled);
    }

    public boolean canUseItem(String mainKey, String itemId, Player player) {
        Map<String, List<CompiledCondition>> actionListMap = itemConditions.computeIfAbsent(mainKey, k -> new ConcurrentHashMap<>());

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

    public List<String> actions(String id) {
        return itemActions.getOrDefault(id, Collections.emptyList());
    }
}



