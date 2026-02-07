package me.blueslime.meteor.platforms.api.commands;

@FunctionalInterface
public interface CommandExecutor {
    void execute(CommandContext context);
}
