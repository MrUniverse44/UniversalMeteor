package me.blueslime.meteor.paper.extras.inventories.listener;

import me.blueslime.meteor.paper.extras.interaction.InteractionPolicy;
import me.blueslime.meteor.paper.extras.interaction.InteractionType;

import me.blueslime.meteor.paper.extras.inventories.InventoryService;

import me.blueslime.meteor.paper.extras.inventories.session.ResolvedInventoryItem;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import org.bukkit.event.block.Action;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public final class PlayerInventoryListener implements Listener {

    private final InventoryService inventories;

    public PlayerInventoryListener(
            InventoryService inventories
    ) {
        this.inventories =
                inventories;
    }

    @EventHandler
    public void onInventoryClick(
            InventoryClickEvent event
    ) {
        if (
                !(event.getWhoClicked()
                        instanceof Player player)
        ) {
            return;
        }

        Optional<ResolvedInventoryItem> current =
                inventories.resolve(
                        player,
                        event.getCurrentItem()
                );

        Optional<ResolvedInventoryItem> cursor =
                inventories.resolve(
                        player,
                        event.getCursor()
                );

        /*
         * Protect both the clicked item and an item
         * currently being carried by the cursor.
         */
        current.ifPresent(
                resolved ->
                        applyClickPolicy(
                                event,
                                resolved
                        )
        );

        cursor.ifPresent(
                resolved -> {
                    if (
                            !resolved
                                    .definition()
                                    .policy()
                                    .allowMove()
                    ) {
                        event.setCancelled(
                                true
                        );
                    }
                }
        );

        /*
         * NUMBER_KEY can swap a different hotbar
         * item into the clicked slot.
         */
        if (
                event.getClick()
                        == ClickType.NUMBER_KEY
        ) {
            int hotbar =
                    event.getHotbarButton();

            if (
                    hotbar >= 0 &&
                            hotbar <= 8
            ) {
                ItemStack hotbarItem =
                        player
                                .getInventory()
                                .getItem(
                                        hotbar
                                );

                inventories
                        .resolve(
                                player,
                                hotbarItem
                        )
                        .ifPresent(
                                resolved -> {
                                    if (
                                            !resolved
                                                    .definition()
                                                    .policy()
                                                    .allowMove()
                                    ) {
                                        event.setCancelled(
                                                true
                                        );
                                    }
                                }
                        );
            }
        }

        /*
         * The interaction belongs to the item being
         * clicked, not merely the cursor item.
         */
        if (current.isEmpty()) {
            return;
        }

        InteractionType type =
                BukkitInteractionMapper.from(
                        event
                );

        inventories.interact(
                current.get(),
                type
        );
    }

    private void applyClickPolicy(
            InventoryClickEvent event,
            ResolvedInventoryItem resolved
    ) {
        InteractionPolicy policy =
                resolved
                        .definition()
                        .policy();

        ClickType click =
                event.getClick();

        boolean drop =
                click == ClickType.DROP ||
                        click == ClickType.CONTROL_DROP;

        if (drop) {
            if (!policy.allowDrop()) {
                event.setCancelled(
                        true
                );
            }

            return;
        }

        if (!policy.allowMove()) {
            event.setCancelled(
                    true
            );
        }
    }

    @EventHandler
    public void onInventoryDrag(
            InventoryDragEvent event
    ) {
        if (
                !(event.getWhoClicked()
                        instanceof Player player)
        ) {
            return;
        }

        inventories
                .resolve(
                        player,
                        event.getOldCursor()
                )
                .ifPresent(
                        resolved -> {
                            if (
                                    !resolved
                                            .definition()
                                            .policy()
                                            .allowDrag()
                            ) {
                                event.setCancelled(
                                        true
                                );
                            }
                        }
                );
    }

    @EventHandler
    public void onDrop(
            PlayerDropItemEvent event
    ) {
        inventories
                .resolve(
                        event.getPlayer(),
                        event
                                .getItemDrop()
                                .getItemStack()
                )
                .ifPresent(
                        resolved -> {
                            if (
                                    !resolved
                                            .definition()
                                            .policy()
                                            .allowDrop()
                            ) {
                                event.setCancelled(
                                        true
                                );
                            }

                            /*
                             * DROP can itself be an interaction.
                             */
                            inventories.interact(
                                    resolved,
                                    InteractionType.DROP
                            );
                        }
                );
    }

    @EventHandler
    public void onInteract(
            PlayerInteractEvent event
    ) {
        if (
                event.getAction()
                        == Action.PHYSICAL
        ) {
            return;
        }

        ItemStack item =
                event.getItem();

        if (item == null) {
            return;
        }

        Optional<ResolvedInventoryItem> resolved =
                inventories.resolve(
                        event.getPlayer(),
                        item
                );

        if (resolved.isEmpty()) {
            return;
        }

        ResolvedInventoryItem itemDefinition =
                resolved.get();

        if (
                !itemDefinition
                        .definition()
                        .policy()
                        .allowWorldInteraction()
        ) {
            /*
             * Cancel vanilla interaction while still
             * allowing our custom ActionPlan.
             */
            event.setCancelled(
                    true
            );
        }

        inventories.interact(
                itemDefinition,
                BukkitInteractionMapper.from(
                        event
                )
        );
    }

    @EventHandler
    public void onSwapHands(
            PlayerSwapHandItemsEvent event
    ) {
        Player player =
                event.getPlayer();

        Optional<ResolvedInventoryItem> main =
                inventories.resolve(
                        player,
                        event.getMainHandItem()
                );

        Optional<ResolvedInventoryItem> off =
                inventories.resolve(
                        player,
                        event.getOffHandItem()
                );

        boolean cancelled =
                main
                        .map(resolved ->
                                !resolved
                                        .definition()
                                        .policy()
                                        .allowMove()
                        )
                        .orElse(false)
                        ||
                        off
                                .map(resolved ->
                                        !resolved
                                                .definition()
                                                .policy()
                                                .allowMove()
                                )
                                .orElse(false);

        if (cancelled) {
            event.setCancelled(
                    true
            );
        }

        /*
         * Avoid executing twice when only one managed
         * item participates in the swap.
         */
        main.ifPresent(
                resolved ->
                        inventories.interact(
                                resolved,
                                InteractionType.SWAP_OFFHAND
                        )
        );

        if (
                main.isEmpty()
        ) {
            off.ifPresent(
                    resolved ->
                            inventories.interact(
                                    resolved,
                                    InteractionType.SWAP_OFFHAND
                            )
            );
        }
    }

    @EventHandler
    public void onQuit(
            PlayerQuitEvent event
    ) {
        inventories.handleQuit(
                event.getPlayer()
        );
    }
}
