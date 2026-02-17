package me.blueslime.meteor.platforms.velocity.sender;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.Player;
import me.blueslime.meteor.color.renders.VelocitySpongeRenderer;
import me.blueslime.meteor.platforms.api.entity.PlatformSender;
import net.kyori.adventure.text.Component;

import java.util.UUID;

public class VelocitySender extends PlatformSender<CommandSource, Component> {

    /**
     * Constructs a new PlatformSender.
     *
     * @param handle The platform-specific sender instance.
     */
    protected VelocitySender(CommandSource handle) {
        super(handle);
    }

    public static VelocitySender build(CommandSource handle) {
        return new VelocitySender(handle);
    }

    /**
     * Performs the actual sending of the message to the platform-specific handle.
     * <p>
     * This method receives the message <b>already colorized</b> and with placeholders replaced.
     *
     * @param message The final message string to send.
     */
    @Override
    protected void performSend(Component message) {
        handle.sendMessage(message);
    }

    /**
     * Colorizes the given text using the ColorHandler.
     *
     * @param text The text to colorize.
     * @return The colorized text.
     */
    @Override
    protected Component colorize(String text) {
        return VelocitySpongeRenderer.create(text);
    }

    /**
     * Gets the name of the sender.
     *
     * @return The name of the sender (e.g., player name or "Console").
     */
    @Override
    public String getName() {
        return handle instanceof Player player ? player.getUsername() : "[Console]";
    }

    @Override
    public UUID getUniqueID() {
        return handle instanceof Player player ? player.getUniqueId() : UUID.fromString("0-0-0-0");
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
        return handle instanceof ConsoleCommandSource;
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

