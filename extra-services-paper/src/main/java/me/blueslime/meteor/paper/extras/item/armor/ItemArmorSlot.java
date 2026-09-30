package me.blueslime.meteor.paper.extras.item.armor;

import org.bukkit.Material;

import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.Locale;
import java.util.Optional;

public enum ItemArmorSlot {

    HELMET,
    CHESTPLATE,
    LEGGINGS,
    BOOTS;

    public void equip(
            Player player,
            ItemStack itemStack
    ) {
        PlayerInventory inventory =
                player.getInventory();

        switch (this) {
            case HELMET ->
                    inventory.setHelmet(
                            itemStack
                    );

            case CHESTPLATE ->
                    inventory.setChestplate(
                            itemStack
                    );

            case LEGGINGS ->
                    inventory.setLeggings(
                            itemStack
                    );

            case BOOTS ->
                    inventory.setBoots(
                            itemStack
                    );
        }
    }

    public ItemStack get(
            Player player
    ) {
        PlayerInventory inventory =
                player.getInventory();

        return switch (this) {
            case HELMET ->
                    inventory.getHelmet();

            case CHESTPLATE ->
                    inventory.getChestplate();

            case LEGGINGS ->
                    inventory.getLeggings();

            case BOOTS ->
                    inventory.getBoots();
        };
    }

    public String toLowerString() {
        return name()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    public static Optional<ItemArmorSlot> anyMatch(
            Material material
    ) {
        if (material == null) {
            return Optional.empty();
        }

        String name =
                material.name();

        if (
                name.endsWith(
                        "_HELMET"
                ) ||
                        material ==
                                Material.CARVED_PUMPKIN ||
                        material ==
                                Material.PLAYER_HEAD ||
                        material ==
                                Material.PLAYER_WALL_HEAD
        ) {
            return Optional.of(
                    HELMET
            );
        }

        if (
                name.endsWith(
                        "_CHESTPLATE"
                ) ||
                        material ==
                                Material.ELYTRA
        ) {
            return Optional.of(
                    CHESTPLATE
            );
        }

        if (
                name.endsWith(
                        "_LEGGINGS"
                )
        ) {
            return Optional.of(
                    LEGGINGS
            );
        }

        if (
                name.endsWith(
                        "_BOOTS"
                )
        ) {
            return Optional.of(
                    BOOTS
            );
        }

        return Optional.empty();
    }

    public static Optional<ItemArmorSlot> anyMatch(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return Optional.empty();
        }

        String normalized =
                value
                        .strip()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                normalized.endsWith(
                        "_HELMET"
                ) ||
                        normalized.equals(
                                "HELMET"
                        )
        ) {
            return Optional.of(
                    HELMET
            );
        }

        if (
                normalized.endsWith(
                        "_CHESTPLATE"
                ) ||
                        normalized.equals(
                                "CHESTPLATE"
                        ) ||
                        normalized.endsWith(
                                "ELYTRA"
                        )
        ) {
            return Optional.of(
                    CHESTPLATE
            );
        }

        if (
                normalized.endsWith(
                        "_LEGGINGS"
                ) ||
                        normalized.equals(
                                "LEGGINGS"
                        )
        ) {
            return Optional.of(
                    LEGGINGS
            );
        }

        if (
                normalized.endsWith(
                        "_BOOTS"
                ) ||
                        normalized.equals(
                                "BOOTS"
                        )
        ) {
            return Optional.of(
                    BOOTS
            );
        }

        return Optional.empty();
    }
}