package me.blueslime.meteor.paper.extras.interaction.placement;

import me.blueslime.meteor.paper.extras.item.armor.ItemArmorSlot;

import java.util.Objects;

public record ArmorPlacement(ItemArmorSlot slot) implements ItemPlacement {

    public ArmorPlacement {
        Objects.requireNonNull(slot, "slot");
    }

    @Override
    public PlacementType type() {
        return PlacementType.ARMOR;
    }
}
