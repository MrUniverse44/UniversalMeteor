package me.blueslime.meteor.platforms.api.entity;

public interface Sender extends MessageNotifier {

    /**
     * Gets the name of the sender.
     *
     * @return The name of the sender (e.g., player name or "Console").
     */
    String getName();

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
     * Casts the underlying handle to a specific class type.
     * <p>
     * Useful when you need to access platform-specific methods not covered by this interface.
     *
     * @param type The class to cast the handle to.
     * @param <T>  The type of the class.
     * @return The cast handle.
     * @throws ClassCastException If the handle is not an instance of the specified type.
     */
    @SuppressWarnings("unchecked")
    default <T> T to(Class<T> type) {
        return (T) getHandle();
    }
}