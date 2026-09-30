package me.blueslime.meteor.paper.extras.interaction;

public enum InteractionType {

    /**
     * Fallback for every interaction.
     */
    ANY,

    /*
     * Inventory clicks.
     */

    LEFT_CLICK,

    RIGHT_CLICK,

    SHIFT_LEFT_CLICK,

    SHIFT_RIGHT_CLICK,

    MIDDLE_CLICK,

    DOUBLE_CLICK,

    NUMBER_KEY,

    DROP,

    CONTROL_DROP,

    SWAP_OFFHAND,

    CREATIVE,

    /*
     * PlayerInteractEvent / held inventory item.
     */

    LEFT_INTERACT,

    RIGHT_INTERACT,

    /*
     * Future compatibility.
     */

    UNKNOWN
}
