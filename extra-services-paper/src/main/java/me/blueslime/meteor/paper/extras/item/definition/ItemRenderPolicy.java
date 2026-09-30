package me.blueslime.meteor.paper.extras.item.definition;

public enum ItemRenderPolicy {

    /**
     * Meteor decides.
     * <br>
     * If the item contains dynamic templates,
     * it will be dynamically rendered.
     * <br>
     * Otherwise the base ItemStack can be reused
     * as a cheap clone.
     */
    AUTO,

    /**
     * Never resolve runtime context or PlaceholderAPI.
     * <br>
     * Useful for decorative items.
     */
    STATIC,

    /**
     * Always execute the dynamic render path.
     */
    DYNAMIC
}
