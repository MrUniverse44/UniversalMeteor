package me.blueslime.meteor.platforms.api.entity;

import java.util.UUID;

import static me.blueslime.meteor.platforms.api.plugin.PlatformPlugin.primitiveToWrapper;

public interface Sender extends MessageNotifier {

    /**
     * Gets the name of the sender.
     *
     * @return The name of the sender (e.g., player name or "Console").
     */
    String getName();

    /**
     * Gets the unique id of the sender
     *
     * @return The UUID of the sender (e.g., player uuid or "0-0-0-0" for console)
     */
    UUID getUniqueID();

    /**
     * Checks if the sender is a player.
     *
     * @return true if the sender is a player, false otherwise.
     */
    boolean isPlayer();

    /**
     * Checks if the sender is the console.
     *
     * @return true if the sender is the console, false otherwise.
     */
    boolean isConsole();

    /**
     * Checks if the sender has a specific permission.
     *
     * @param permission The permission node to check.
     * @return true if the sender has the permission, false otherwise.
     */
    boolean hasPermission(String permission);

    /**
     * Gets the underlying platform-specific handle object.
     *
     * @return The raw object (e.g., org.bukkit.command.CommandSender, net.md_5.bungee.api.CommandSender, etc.).
     */
    Object getHandle();

    /**
     * Checks whether the underlying handle is an instance of the given type.
     *
     * @param type The class to check compatibility with.
     * @return true if the handle is non-null and can be cast to {@code type}, false otherwise.
     */
    default boolean is(Class<?> type) {
        Object handle = getHandle();
        if (handle == null || type == null) return false;

        Class<?> check = type.isPrimitive() ? primitiveToWrapper(type) : type;
        return check.isInstance(handle);
    }

    /**
     * Casts the underlying handle to a specific class type if compatible.
     * <p>
     * If the handle is not compatible with {@code type} this method returns {@code null}
     * (no ClassCastException will be thrown).
     *
     * @param type The class to cast the handle to.
     * @param <T>  The type of the class.
     * @return The cast handle, or {@code null} if not compatible.
     */
    @SuppressWarnings("unchecked")
    default <T> T to(Class<T> type) {
        return is(type) ? (T) getHandle() : null;
    }

}