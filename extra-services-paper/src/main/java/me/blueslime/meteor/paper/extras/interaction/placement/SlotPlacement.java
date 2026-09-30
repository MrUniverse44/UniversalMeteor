package me.blueslime.meteor.paper.extras.interaction.placement;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

public record SlotPlacement(List<Integer> slots) implements ItemPlacement {

    public SlotPlacement {
        if (
                slots == null ||
                        slots.isEmpty()
        ) {
            throw new IllegalArgumentException(
                    "SlotPlacement requires at least one slot"
            );
        }

        LinkedHashSet<Integer> unique =
                new LinkedHashSet<>();

        for (Integer slot : slots) {
            if (slot == null) {
                continue;
            }

            if (slot < 0) {
                throw new IllegalArgumentException(
                        "Inventory slot cannot be negative: "
                                + slot
                );
            }

            unique.add(
                    slot
            );
        }

        if (unique.isEmpty()) {
            throw new IllegalArgumentException(
                    "SlotPlacement requires at least one valid slot"
            );
        }

        slots =
                List.copyOf(
                        unique
                );
    }

    public static SlotPlacement of(
            int slot
    ) {
        return new SlotPlacement(
                List.of(slot)
        );
    }

    public static SlotPlacement of(
            int... slots
    ) {
        List<Integer> values =
                new ArrayList<>(
                        slots.length
                );

        for (int slot : slots) {
            values.add(slot);
        }

        return new SlotPlacement(
                values
        );
    }

    public static SlotPlacement of(
            Collection<Integer> slots
    ) {
        return new SlotPlacement(
                List.copyOf(slots)
        );
    }

    @Override
    public PlacementType type() {
        return PlacementType.SLOT;
    }

    public int primarySlot() {
        return slots.getFirst();
    }

    public boolean contains(
            int slot
    ) {
        return slots.contains(
                slot
        );
    }
}
