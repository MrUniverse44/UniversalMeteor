package me.blueslime.meteor.paper.extras.menus.listener;

import me.blueslime.meteor.paper.extras.interaction.InteractionPolicy;
import me.blueslime.meteor.paper.extras.interaction.InteractionType;
import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.inventories.listener.BukkitInteractionMapper;

import me.blueslime.meteor.paper.extras.menus.MenuService;

import me.blueslime.meteor.paper.extras.menus.definition.MenuPolicy;

import me.blueslime.meteor.paper.extras.menus.session.MenuSession;

import org.bukkit.entity.Player;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Optional;

public final class MenuListener
        implements Listener {

    private final MenuService menus;

    public MenuListener(
            MenuService menus
    ) {
        this.menus =
                menus;
    }

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onClick(
            InventoryClickEvent event
    ) {
        MenuSession session =
                sessionOf(
                        event
                                .getView()
                                .getTopInventory()
                                .getHolder()
                );

        if (
                session == null ||
                        !session.isActive()
        ) {
            return;
        }

        if (
                !(event.getWhoClicked()
                        instanceof Player player) ||
                        !player
                                .getUniqueId()
                                .equals(
                                        session.playerId()
                                )
        ) {
            return;
        }

        int rawSlot =
                event.getRawSlot();

        int topSize =
                session
                        .getInventory()
                        .getSize();

        /*
         * Outside click.
         */
        if (rawSlot < 0) {
            return;
        }

        MenuPolicy menuPolicy =
                session
                        .definition()
                        .policy();

        /*
         * Player's own inventory.
         */
        if (rawSlot >= topSize) {
            if (
                    menuPolicy
                            .cancelPlayerInventoryClicks()
            ) {
                event.setCancelled(
                        true
                );
            }

            /*
             * PlayerInventoryListener is responsible
             * for managed persistent items down here.
             */
            return;
        }

        /*
         * Top menu inventory.
         */
        if (
                menuPolicy
                        .cancelTopInventoryClicks()
        ) {
            event.setCancelled(
                    true
            );
        }

        Optional<InteractiveItemDefinition> optional =
                session.itemAt(
                        rawSlot
                );

        if (optional.isEmpty()) {
            return;
        }

        InteractiveItemDefinition definition =
                optional.get();

        applyItemPolicy(
                event,
                definition.policy()
        );

        InteractionType interaction =
                BukkitInteractionMapper.from(
                        event
                );

        menus.interact(
                session,
                rawSlot,
                interaction
        );
    }

    private void applyItemPolicy(
            InventoryClickEvent event,
            InteractionPolicy policy
    ) {
        ClickType click =
                event.getClick();

        boolean dropping =
                click == ClickType.DROP ||
                        click == ClickType.CONTROL_DROP;

        if (dropping) {
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

    @EventHandler(
            priority = EventPriority.HIGH
    )
    public void onDrag(
            InventoryDragEvent event
    ) {
        MenuSession session =
                sessionOf(
                        event
                                .getView()
                                .getTopInventory()
                                .getHolder()
                );

        if (
                session == null ||
                        !session.isActive()
        ) {
            return;
        }

        int topSize =
                session
                        .getInventory()
                        .getSize();

        boolean touchesMenu =
                event
                        .getRawSlots()
                        .stream()
                        .anyMatch(
                                slot ->
                                        slot >= 0 &&
                                                slot < topSize
                        );

        if (!touchesMenu) {
            return;
        }

        if (
                session
                        .definition()
                        .policy()
                        .cancelDrag()
        ) {
            event.setCancelled(
                    true
            );

            return;
        }

        /*
         * Even in a permissive menu, an individual
         * protected item can deny drag operations.
         */
        for (
                int slot :
                event.getRawSlots()
        ) {
            if (
                    slot < 0 ||
                            slot >= topSize
            ) {
                continue;
            }

            InteractiveItemDefinition item =
                    session
                            .itemAt(slot)
                            .orElse(null);

            if (
                    item != null &&
                            !item
                                    .policy()
                                    .allowDrag()
            ) {
                event.setCancelled(
                        true
                );

                return;
            }
        }
    }

    @EventHandler(
            priority = EventPriority.MONITOR,
            ignoreCancelled = true
    )
    public void onOpen(
            InventoryOpenEvent event
    ) {
        MenuSession session =
                sessionOf(
                        event
                                .getInventory()
                                .getHolder()
                );

        if (session == null) {
            return;
        }

        menus.handleOpen(
                session
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onClose(
            InventoryCloseEvent event
    ) {
        MenuSession session =
                sessionOf(
                        event
                                .getInventory()
                                .getHolder()
                );

        if (session == null) {
            return;
        }

        menus.handleClose(
                session,
                event.getReason()
        );
    }

    @EventHandler(
            priority = EventPriority.MONITOR
    )
    public void onQuit(
            PlayerQuitEvent event
    ) {
        menus.handleQuit(
                event.getPlayer()
        );
    }

    private MenuSession sessionOf(
            Object holder
    ) {
        return holder
                instanceof MenuSession session
                ? session
                : null;
    }
}