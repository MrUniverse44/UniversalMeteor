package me.blueslime.meteor.paper.extras.inventories.listener;

import me.blueslime.meteor.paper.extras.interaction.InteractionType;

import org.bukkit.event.block.Action;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;

import org.bukkit.event.player.PlayerInteractEvent;

public final class BukkitInteractionMapper {

    private BukkitInteractionMapper() {}

    public static InteractionType from(
            InventoryClickEvent event
    ) {
        ClickType click =
                event.getClick();

        return switch (click) {
            case LEFT ->
                    InteractionType.LEFT_CLICK;

            case RIGHT ->
                    InteractionType.RIGHT_CLICK;

            case SHIFT_LEFT ->
                    InteractionType.SHIFT_LEFT_CLICK;

            case SHIFT_RIGHT ->
                    InteractionType.SHIFT_RIGHT_CLICK;

            case MIDDLE ->
                    InteractionType.MIDDLE_CLICK;

            case DOUBLE_CLICK ->
                    InteractionType.DOUBLE_CLICK;

            case NUMBER_KEY ->
                    InteractionType.NUMBER_KEY;

            case DROP ->
                    InteractionType.DROP;

            case CONTROL_DROP ->
                    InteractionType.CONTROL_DROP;

            case SWAP_OFFHAND ->
                    InteractionType.SWAP_OFFHAND;

            case CREATIVE ->
                    InteractionType.CREATIVE;

            default ->
                    InteractionType.UNKNOWN;
        };
    }

    public static InteractionType from(
            PlayerInteractEvent event
    ) {
        Action action =
                event.getAction();

        return switch (action) {
            case LEFT_CLICK_AIR,
                 LEFT_CLICK_BLOCK ->
                    InteractionType.LEFT_INTERACT;

            case RIGHT_CLICK_AIR,
                 RIGHT_CLICK_BLOCK ->
                    InteractionType.RIGHT_INTERACT;

            default ->
                    InteractionType.UNKNOWN;
        };
    }
}