package me.blueslime.meteor.paper.extras.interaction.placement;

public record FirstEmptyPlacement() implements ItemPlacement {

    public static final FirstEmptyPlacement INSTANCE = new FirstEmptyPlacement();

    @Override
    public PlacementType type() {
        return PlacementType.FIRST_EMPTY;
    }
}
