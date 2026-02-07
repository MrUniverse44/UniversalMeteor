package me.blueslime.meteor.platforms.api.commands.provider;

import me.blueslime.meteor.platforms.api.commands.Command;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.service.PlatformService;

public interface PlatformCommandProvider extends PlatformService {
    /**
     * Register a command in this current platform
     * @param command to register.
     * @param registry types and global suggests.
     */
    void register(Command command, PlatformCommands registry);

    /**
     * Removes the command from the platform
     */
    void unregister(Command command);

    /**
     * Register platform types to the command system
     * @param platformCommands instance
     */
    default void registerTypes(PlatformCommands platformCommands) {

    }
}
