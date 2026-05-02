package me.blueslime.meteor.paper.extras.services.item.armor;

import me.blueslime.meteor.paper.extras.services.item.ItemWrapper;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

public enum ItemArmorSlot {
    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    public void equip(Player player, ItemWrapper wrapper) {
        switch (this) {
            case BOOTS -> player.getInventory().setBoots(wrapper.getUserItem(player));
            case CHESTPLATE -> player.getInventory().setChestplate(wrapper.getUserItem(player));
            case LEGGINGS -> player.getInventory().setLeggings(wrapper.getUserItem(player));
            case HELMET -> player.getInventory().setHelmet(wrapper.getUserItem(player));
        }
    }

    public void equip(Player player, ItemStack itemStack) {
        switch (this) {
            case BOOTS -> player.getInventory().setBoots(itemStack);
            case CHESTPLATE -> player.getInventory().setChestplate(itemStack);
            case LEGGINGS -> player.getInventory().setLeggings(itemStack);
            case HELMET -> player.getInventory().setHelmet(itemStack);
        }
    }

    public String toLowerString() {
        return toString().toLowerCase(Locale.ENGLISH);
    }

    public static Optional<ItemArmorSlot> anyMatch(Material material) {
        return anyMatch(material.toString().toLowerCase(Locale.ENGLISH));
    }

    public static Optional<ItemArmorSlot> anyMatch(String value) {
        return Arrays.stream(values()).filter(slot -> value.contains("_" + slot.toLowerString())).findFirst();
    }
}
