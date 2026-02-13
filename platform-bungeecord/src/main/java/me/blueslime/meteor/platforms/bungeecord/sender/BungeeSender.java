package me.blueslime.meteor.platforms.bungeecord.sender;

import me.blueslime.meteor.color.renders.BungeeRenderer;
import me.blueslime.meteor.platforms.api.entity.PlatformSender;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;

public class BungeeSender extends PlatformSender<CommandSender, BaseComponent[]> {


    /**
     * Constructs a new PlatformSender.
     *
     * @param handle The platform-specific sender instance.
     */
    protected BungeeSender(CommandSender handle) {
        super(handle);
    }

    public static BungeeSender build(CommandSender handle) {
        return new BungeeSender(handle);
    }

    /**
     * Performs the actual sending of the message to the platform-specific handle.
     * <p>
     * This method receives the message <b>already colorized</b> and with placeholders replaced.
     *
     * @param message The final message string to send.
     */
    @Override
    protected void performSend(BaseComponent[] message) {
        handle.sendMessage(message);
    }

    /**
     * Colorizes the given text using the ColorHandler.
     *
     * @param text The text to colorize.
     * @return The colorized text.
     */
    @Override
    protected BaseComponent[] colorize(String text) {
        return BungeeRenderer.create(text);
    }

    /**
     * Gets the name of the sender.
     *
     * @return The name of the sender (e.g., player name or "Console").
     */
    @Override
    public String getName() {
        return handle instanceof ProxiedPlayer player ? player.getName() : "[Console]";
    }

    /**
     * Checks if the sender is a player.
     *
     * @return true if the sender is a player, false otherwise.
     */
    @Override
    public boolean isPlayer() {
        return handle instanceof ProxiedPlayer;
    }

    /**
     * Checks if the sender is the console.
     *
     * @return true if the sender is the console, false otherwise.
     */
    @Override
    public boolean isConsole() {
        return !isPlayer();
    }

    /**
     * Checks if the sender has a specific permission.
     *
     * @param permission The permission node to check.
     * @return true if the sender has the permission, false otherwise.
     */
    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }
}

