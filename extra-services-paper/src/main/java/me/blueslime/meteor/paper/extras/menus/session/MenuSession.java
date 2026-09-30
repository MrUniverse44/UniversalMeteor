package me.blueslime.meteor.paper.extras.menus.session;

import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.menus.definition.MenuDefinition;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContextSnapshot;

import net.kyori.adventure.text.Component;

import org.bukkit.Bukkit;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MenuSession
        implements InventoryHolder {

    private final UUID sessionId =
            UUID.randomUUID();

    private final UUID playerId;

    private final MenuDefinition definition;

    private final ExecutionContextSnapshot contextSnapshot;

    private final Inventory inventory;

    /**
     * Runtime resolution:
     *
     * rawSlot -> compiled definition
     */
    private final Map<
            Integer,
            InteractiveItemDefinition
            > slots =
            new HashMap<>();

    private final Map<
            String,
            Set<Integer>
            > itemSlots =
            new HashMap<>();

    private final AtomicBoolean active =
            new AtomicBoolean(true);

    private final AtomicBoolean opened =
            new AtomicBoolean(false);

    private final AtomicBoolean closed =
            new AtomicBoolean(false);

    private final long createdAtNanos =
            System.nanoTime();

    public MenuSession(
            UUID playerId,
            MenuDefinition definition,
            ExecutionContextSnapshot contextSnapshot,
            Component title
    ) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException(
                    "MenuSession must be created on the Bukkit primary thread"
            );
        }

        this.playerId =
                Objects.requireNonNull(
                        playerId
                );

        this.definition =
                Objects.requireNonNull(
                        definition
                );

        this.contextSnapshot =
                Objects.requireNonNull(
                        contextSnapshot
                );

        this.inventory =
                Bukkit.createInventory(
                        this,
                        definition.size(),
                        Objects.requireNonNull(
                                title
                        )
                );
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public MenuDefinition definition() {
        return definition;
    }

    public ExecutionContextSnapshot contextSnapshot() {
        return contextSnapshot;
    }

    public long createdAtNanos() {
        return createdAtNanos;
    }

    public boolean isActive() {
        return active.get();
    }

    public boolean invalidate() {
        return active.compareAndSet(
                true,
                false
        );
    }

    public boolean markOpened() {
        return opened.compareAndSet(
                false,
                true
        );
    }

    public boolean markClosed() {
        return closed.compareAndSet(
                false,
                true
        );
    }

    public boolean wasOpened() {
        return opened.get();
    }

    public boolean wasClosed() {
        return closed.get();
    }

    public void bind(
            int rawSlot,
            InteractiveItemDefinition definition
    ) {
        InteractiveItemDefinition previous =
                slots.put(
                        rawSlot,
                        definition
                );

        if (previous != null) {
            Set<Integer> previousSlots =
                    itemSlots.get(
                            previous.id()
                    );

            if (previousSlots != null) {
                previousSlots.remove(
                        rawSlot
                );

                if (previousSlots.isEmpty()) {
                    itemSlots.remove(
                            previous.id()
                    );
                }
            }
        }

        itemSlots
                .computeIfAbsent(
                        definition.id(),
                        ignored ->
                                new LinkedHashSet<>()
                )
                .add(rawSlot);
    }

    public boolean canBind(
            int rawSlot,
            String itemId
    ) {
        InteractiveItemDefinition current =
                slots.get(
                        rawSlot
                );

        return current == null ||
                current.id()
                        .equals(itemId);
    }

    public Set<Integer> slotsOf(
            String itemId
    ) {
        Set<Integer> result =
                itemSlots.get(
                        itemId
                );

        return result == null
                ? Set.of()
                : Set.copyOf(result);
    }

    public Set<Integer> unbindItem(
            String itemId
    ) {
        Set<Integer> current =
                itemSlots.remove(
                        itemId
                );

        if (
                current == null ||
                        current.isEmpty()
        ) {
            return Set.of();
        }

        Set<Integer> copy =
                Set.copyOf(current);

        for (int slot : copy) {
            slots.remove(slot);
        }

        return copy;
    }

    public void clearBindings() {
        slots.clear();
        itemSlots.clear();
    }

    public Collection<InteractiveItemDefinition> boundItems() {
        return itemSlots
                .keySet()
                .stream()
                .map(definition::item)
                .flatMap(Optional::stream)
                .toList();
    }

    public int firstAvailableSlot(
            String itemId
    ) {
        for (
                int slot = 0;
                slot < inventory.getSize();
                slot++
        ) {
            InteractiveItemDefinition bound =
                    slots.get(slot);

            if (
                    bound == null ||
                            bound.id().equals(itemId)
            ) {
                return slot;
            }
        }

        return -1;
    }

    public Optional<InteractiveItemDefinition> itemAt(
            int rawSlot
    ) {
        return Optional.ofNullable(
                slots.get(
                        rawSlot
                )
        );
    }

    public Map<
            Integer,
            InteractiveItemDefinition
            > slotBindings() {
        return Map.copyOf(
                slots
        );
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}