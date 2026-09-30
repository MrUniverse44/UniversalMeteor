package me.blueslime.meteor.paper.extras.interaction.placement;

public sealed interface ItemPlacement permits
        SlotPlacement,
        ArmorPlacement,
        OffhandPlacement,
        FirstEmptyPlacement {

    PlacementType type();

    enum PlacementType {
        SLOT,
        ARMOR,
        OFFHAND,
        FIRST_EMPTY
    }
}
