package me.blueslime.meteor.paper.extras.interaction;

import me.blueslime.meteor.paper.extras.services.animation.AnimationDefinition;

import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.interaction.placement.FirstEmptyPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;

import me.blueslime.meteor.paper.extras.item.definition.ItemDefinition;

import java.util.*;

public final class InteractiveItemDefinition {

    private final String id;

    private final ItemDefinition item;

    private final ConditionPlan visibility;

    private final ItemPlacement placement;

    private final Map<
            InteractionType,
            InteractionDefinition
            > interactions;

    private final InteractionPolicy policy;

    private final AnimationDefinition animation;

    public InteractiveItemDefinition(
            String id,
            ItemDefinition item,
            ConditionPlan visibility,
            ItemPlacement placement,
            Map<
                    InteractionType,
                    InteractionDefinition
                    > interactions,
            InteractionPolicy policy,
            AnimationDefinition animation
    ) {
        if (
                id == null ||
                        id.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Interactive item id cannot be empty"
            );
        }

        this.id =
                id.strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        this.item =
                Objects.requireNonNull(
                        item
                );

        this.visibility =
                Objects.requireNonNullElse(
                        visibility,
                        ConditionPlan.EMPTY
                );

        this.placement =
                Objects.requireNonNullElse(
                        placement,
                        FirstEmptyPlacement.INSTANCE
                );

        this.policy =
                Objects.requireNonNullElseGet(
                        policy,
                        InteractionPolicy::protectedItem
                );

        this.animation =
                Objects.requireNonNullElse(
                        animation,
                        AnimationDefinition.NONE
                );

        EnumMap<
                InteractionType,
                InteractionDefinition
                > copy =
                new EnumMap<>(
                        InteractionType.class
                );

        if (interactions != null) {
            copy.putAll(
                    interactions
            );
        }

        this.interactions =
                Map.copyOf(copy);
    }

    public String id() {
        return id;
    }

    public ItemDefinition item() {
        return item;
    }

    public ConditionPlan visibility() {
        return visibility;
    }

    public ItemPlacement placement() {
        return placement;
    }

    public Map<
            InteractionType,
            InteractionDefinition
            > interactions() {
        return interactions;
    }

    public InteractionPolicy policy() {
        return policy;
    }

    public AnimationDefinition animation() {
        return animation;
    }

    public boolean animated() {
        return animation.enabled();
    }

    public Optional<InteractionDefinition> interaction(
            InteractionType type
    ) {
        InteractionDefinition exact =
                interactions.get(type);

        if (exact != null) {
            return Optional.of(exact);
        }

        return Optional.ofNullable(
                interactions.get(
                        InteractionType.ANY
                )
        );
    }

    public boolean hasInteraction(
            InteractionType type
    ) {
        return interaction(type)
                .isPresent();
    }

    public boolean interactive() {
        return !interactions.isEmpty();
    }
}