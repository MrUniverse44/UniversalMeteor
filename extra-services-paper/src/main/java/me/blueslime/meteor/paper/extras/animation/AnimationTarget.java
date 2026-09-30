package me.blueslime.meteor.paper.extras.animation;

public interface AnimationTarget {

    /**
     * Called on Bukkit main thread.
     */
    boolean active();

    /**
     * Called on Bukkit main thread.
     *
     * @return false to permanently stop this animation.
     */
    boolean apply(
            AnimationFrame frame,
            int frameIndex
    );
}