package me.blueslime.meteor.platforms.standalone.commands;

import me.blueslime.meteor.platforms.api.commands.Command;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.commands.provider.PlatformCommandProvider;
import me.blueslime.meteor.platforms.standalone.Bootstrap;

public class AppPlatformCommandProvider implements PlatformCommandProvider {

    public AppPlatformCommandProvider(Bootstrap main) {
    }

    /**
     * Register a command in this current platform
     *
     * @param command  to register.
     * @param registry types and global suggests.
     */
    @Override
    public void register(Command command, PlatformCommands registry) {

    }

    /**
     * Removes the command from the platform
     *
     * @param command to unregister
     */
    @Override
    public void unregister(Command command) {

    }
}

