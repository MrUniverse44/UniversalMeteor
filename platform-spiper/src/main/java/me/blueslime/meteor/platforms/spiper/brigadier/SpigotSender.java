package me.blueslime.meteor.platforms.spiper.brigadier;

import me.blueslime.meteor.color.renders.StringRenderer;
import me.blueslime.meteor.platforms.api.entity.PlatformSender;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;

public class SpigotSender extends PlatformSender<CommandSender, String> {

    /**
     * Constructs a new PlatformSender.
     *
     * @param handle The platform-specific sender instance.
     */
    protected SpigotSender(CommandSender handle) {
        super(handle);
    }

    public static SpigotSender build(CommandSender handle) {
        return new SpigotSender(handle);
    }

    /**
     * Performs the actual sending of the message to the platform-specific handle.
     * <p>
     * This method receives the message <b>already colorized</b> and with placeholders replaced.
     *
     * @param message The final message string to send.
     */
    @Override
    protected void performSend(String message) {
        handle.sendMessage(colorize(message));
    }

    /**
     * Colorizes the given text using the ColorHandler.
     *
     * @param text The text to colorize.
     * @return The colorized text.
     */
    @Override
    protected String colorize(String text) {
        return StringRenderer.create(text);
    }

    /**
     * Gets the name of the sender.
     *
     * @return The name of the sender (e.g., player name or "Console").
     */
    @Override
    public String getName() {
        return handle instanceof LivingEntity entity ? entity.getName() : "[Console]";
    }

    /**
     * Checks if the sender is a player.
     *
     * @return true if the sender is a player, false otherwise.
     */
    @Override
    public boolean isPlayer() {
        return handle instanceof Player;
    }

    /**
     * Checks if the sender is the console.
     *
     * @return true if the sender is the console, false otherwise.
     */
    @Override
    public boolean isConsole() {
        return handle instanceof ConsoleCommandSender;
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
