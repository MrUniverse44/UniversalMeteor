package me.blueslime.meteor.platforms.standalone.commands;

import me.blueslime.meteor.platforms.standalone.commands.context.CommandContext;

public abstract class Command {
    private final String name;
    private final String[] aliases;

    public Command(String name, String... aliases) {
        this.name = name;
        this.aliases = aliases;
    }

    public String getName() {
        return name;
    }

    public String[] getAliases() {
        return aliases;
    }

    public abstract CommandContext execute(String[] args);
}
