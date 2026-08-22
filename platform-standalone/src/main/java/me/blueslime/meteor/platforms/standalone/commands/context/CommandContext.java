package me.blueslime.meteor.platforms.standalone.commands.context;

import me.blueslime.meteor.platforms.standalone.Bootstrap;

@FunctionalInterface
public interface CommandContext {
    void accept(Bootstrap server);
}