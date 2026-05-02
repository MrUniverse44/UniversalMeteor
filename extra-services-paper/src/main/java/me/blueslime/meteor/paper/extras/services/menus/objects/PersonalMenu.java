package me.blueslime.meteor.paper.extras.services.menus.objects;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.paper.extras.services.actions.ActionService;
import me.blueslime.meteor.paper.extras.services.conditions.CompiledCondition;
import me.blueslime.meteor.paper.extras.services.conditions.objects.ConditionCompiler;
import me.blueslime.meteor.paper.extras.services.item.ItemWrapper;
import me.blueslime.meteor.paper.extras.services.menus.LatamInv;
import me.blueslime.meteor.paper.extras.services.slots.SlotHandler;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.utilities.text.TextReplacer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class PersonalMenu extends LatamInv {

    private final Map<String, List<CompiledCondition>> itemConditions = new ConcurrentHashMap<>();

    public PersonalMenu(Player player, ConfigurationHandle configuration) {
        this(player, configuration, TextReplacer.builder());
    }

    public PersonalMenu(Player player, ConfigurationHandle configuration, TextReplacer replacer) {
        super(
            SlotHandler.fromSize(configuration.getInt("menu-settings.rows", 54)),
            configuration.getString("menu-settings.name", "menu-settings.name not found")
                .replace("%page%", "1")
                .replace("<page>", "1")
        );

        boolean overwriteListeners = configuration.getBoolean("menu-settings.overwrite-listeners", false);

        replacer = replacer
                .replace("<player>", player.getName())
                .replace("<player_name>", player.getName())
                .replace("<heart>", "❤");

        ConfigurationHandle extra = configuration.getSection("items");

        ActionService actions = Implements.fetch(ActionService.class);

        if (!overwriteListeners) {
            addClickHandler(event -> event.setCancelled(true));
        }

        for (String key : extra.getKeys(false)) {
            String path = "items." + key;

            if (extra.contains(path + ".conditions")) {
                List<CompiledCondition> compiled = new ArrayList<>();
                for (String raw : configuration.getStringList(path + ".conditions")) {
                    compiled.add(ConditionCompiler.compile(replacer.apply(raw)));
                }
                itemConditions.put(key, compiled);
            }

            ItemWrapper wrapper = ItemWrapper
                .fromData(configuration, path, replacer)
                .customId(key)
                .addBooleanData("latam_item_movement", !configuration.getBoolean(path + ".allow-item-move", false))
                .addBooleanData("latam_item_drop", !configuration.getBoolean(path + ".allow-item-drop", false))
                .addBooleanData("latam_item_drag", !configuration.getBoolean(path + ".allow-item-drag", false))
                .giveConditions(condition -> canUseItem(key, condition.player()));

            final TextReplacer finalReplacer = replacer;

            Consumer<InventoryClickEvent> clickEvent = event -> {
                boolean cancellable = !configuration.getBoolean(path + ".allow-item-move", false);
                event.setCancelled(cancellable);
                List<String> list = configuration.getStringList(path + ".actions");

                if (!list.isEmpty()) {
                    actions.execute(list, player, finalReplacer);
                }
            };

            ItemStack itemStack = wrapper.getUserItem(player);

            if (itemStack == null) {
                continue;
            }

            if (wrapper.getSlots().isEmpty()) {
                addItem(itemStack, clickEvent);
                continue;
            }

            for (int slot : wrapper.getSlots()) {
                InventoryType type = this.getInventory().getType();
                boolean isValidSlot = type == InventoryType.PLAYER ? slot >= 0 && slot < 36 : slot >= 0 && slot < this.getInventory().getStorageContents().length;

                if (isValidSlot) {
                    this.setItem(slot, itemStack, clickEvent);
                }
            }
        }
    }

    @Override
    public void open(Player player) {
        super.open(player);
    }

    public boolean canUseItem(String itemId, Player player) {
        List<CompiledCondition> conditions = itemConditions.get(itemId);
        if (conditions == null) return true;

        for (CompiledCondition condition : conditions) {
            if (!condition.test(player)) {
                return false;
            }
        }
        return true;
    }
}

