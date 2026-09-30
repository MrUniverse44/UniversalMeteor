package me.blueslime.meteor.paper.extras.interaction;

public record InteractionPolicy(
        boolean allowMove,
        boolean allowDrop,
        boolean allowDrag,
        boolean allowWorldInteraction,
        boolean cancelUnhandledInteractions
) {

    /**
     * Default for menu/lobby action items.
     */
    public static InteractionPolicy protectedItem() {
        return new InteractionPolicy(
                false,
                false,
                false,
                false,
                true
        );
    }

    /**
     * Item behaves like a normal Minecraft item
     * except for its configured custom interactions.
     */
    public static InteractionPolicy permissive() {
        return new InteractionPolicy(
                true,
                true,
                true,
                true,
                false
        );
    }

    /**
     * Typical GUI menu item.
     */
    public static InteractionPolicy menuItem() {
        return protectedItem();
    }

    public InteractionPolicy withMove(
            boolean value
    ) {
        return new InteractionPolicy(
                value,
                allowDrop,
                allowDrag,
                allowWorldInteraction,
                cancelUnhandledInteractions
        );
    }

    public InteractionPolicy withDrop(
            boolean value
    ) {
        return new InteractionPolicy(
                allowMove,
                value,
                allowDrag,
                allowWorldInteraction,
                cancelUnhandledInteractions
        );
    }

    public InteractionPolicy withDrag(
            boolean value
    ) {
        return new InteractionPolicy(
                allowMove,
                allowDrop,
                value,
                allowWorldInteraction,
                cancelUnhandledInteractions
        );
    }

    public InteractionPolicy withWorldInteraction(
            boolean value
    ) {
        return new InteractionPolicy(
                allowMove,
                allowDrop,
                allowDrag,
                value,
                cancelUnhandledInteractions
        );
    }
}
