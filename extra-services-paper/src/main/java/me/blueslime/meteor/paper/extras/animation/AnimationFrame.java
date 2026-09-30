package me.blueslime.meteor.paper.extras.animation;

import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;
import me.blueslime.meteor.paper.extras.item.definition.ItemDefinition;

import java.util.Objects;

public record AnimationFrame(
        ItemDefinition item,
        ItemPlacement placement,
        long durationTicks
) {

    public AnimationFrame {
        Objects.requireNonNull(
                item,
                "item"
        );

        if (durationTicks <= 0) {
            throw new IllegalArgumentException(
                    "Animation frame duration must be > 0 ticks"
            );
        }
    }

    public boolean movesItem() {
        return placement != null;
    }
}