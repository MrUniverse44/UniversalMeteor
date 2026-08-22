package me.blueslime.meteor.platforms.standalone.commands;


import me.blueslime.meteor.platforms.standalone.Bootstrap;
import me.blueslime.meteor.platforms.standalone.commands.context.CommandContext;

import java.util.*;

public class CommandManager {
    private final Map<String, Command> commands = new HashMap<>();

    public void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }

    public void unregisterCommand(String name) {
        Command cmd = commands.get(name.toLowerCase());
        if (cmd != null) {
            commands.remove(cmd.getName().toLowerCase());
            for (String alias : cmd.getAliases()) {
                commands.remove(alias.toLowerCase());
            }
        }
    }

    public void executeInput(String input, Bootstrap server) {
        if (input == null || input.trim().isEmpty()) return;

        String[] parts = input.trim().split("\\s+");
        String commandName = parts[0].toLowerCase();
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);

        Command command = commands.get(commandName);

        if (command != null) {
            // Se obtiene la acción
            CommandContext context = command.execute(args);
            if (context != null) {
                context.accept(server);
            }
        } else {
        }
    }

    public Collection<Command> getRegisteredCommands() {
        return new HashSet<>(commands.values());
    }
}


