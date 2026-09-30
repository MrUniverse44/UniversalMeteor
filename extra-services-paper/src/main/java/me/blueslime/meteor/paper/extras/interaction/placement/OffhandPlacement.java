package me.blueslime.meteor.paper.extras.interaction.placement;

public record OffhandPlacement() implements ItemPlacement {

    public static final OffhandPlacement INSTANCE = new OffhandPlacement();

    @Override
    public PlacementType type() {
        return PlacementType.OFFHAND;
    }
}
