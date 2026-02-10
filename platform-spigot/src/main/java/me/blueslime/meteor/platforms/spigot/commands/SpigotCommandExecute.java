package me.blueslime.meteor.platforms.spigot.commands;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.spiper.brigadier.SpigotSender;
import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpigotCommandExecute extends org.bukkit.command.Command {
    private final Command rootCommand;
    private final PlatformCommands registry;

    protected SpigotCommandExecute(Command rootCommand, PlatformCommands registry) {
        super(rootCommand.getName());
        this.rootCommand = rootCommand;
        this.registry = registry;
        this.rootCommand.register();
        this.setAliases(new ArrayList<>(rootCommand.getAliases()));
        this.setDescription(rootCommand.getDescription());
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean execute(CommandSender bukkitSender, String label, String[] args) {
        PlatformLogger logger = Implements.fetch(PlatformLogger.class);
        Sender sender = SpigotSender.build(bukkitSender);

        try {
            processExecution(sender, rootCommand, args, bukkitSender);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                return true;
            }
            logger.error(e, "Can't perform command for " + sender.getName());
        }
        return true;
    }

    private void processExecution(Sender sender, Command root, String[] args, CommandSender bukkitSender) throws Exception {
        Subcommand current = null;

        List<Subcommand> currentChildren = root.getSubcommands();

        int argIndex = 0;

        while (argIndex < args.length) {
            String potentialSubcommand = args[argIndex];
            boolean foundChild = false;

            for (Subcommand child : currentChildren) {
                boolean matches = child.getId().equalsIgnoreCase(potentialSubcommand);

                if (matches) {
                    current = child;
                    currentChildren = child.getSubcommands();
                    argIndex++;
                    foundChild = true;
                    break;
                }
            }

            if (!foundChild) {
                break;
            }
        }

        String[] parameters = Arrays.copyOfRange(args, argIndex, args.length);

        if (current == null) {
            root.executeBase(sender);
        } else {
            Object[] parsedArgs = parseArguments(sender, current, parameters);
            current.executeInternal(sender, parsedArgs);
        }
    }

    private Object[] parseArguments(Sender sender, Subcommand cmd, String[] rawArgs) {
        List<Argument<?>> expectedArgs = cmd.getArguments();
        List<Object> parsed = new ArrayList<>();

        long requiredCount = expectedArgs.stream()
                .filter(a -> a.getArgType() == ArgumentType.NEEDED).count();

        if (rawArgs.length < requiredCount) {
            if (cmd.getUsage() != null && !cmd.getUsage().isEmpty()) {
                sender.send(cmd.getUsage());
            }
            throw new IllegalArgumentException("Not enough arguments: " + cmd.getUsage());
        }

        for (int i = 0; i < expectedArgs.size(); i++) {
            Argument<?> definition = expectedArgs.get(i);

            if (i >= rawArgs.length) {
                if (definition.getArgType() == ArgumentType.OPTIONAL) {
                    parsed.add(null);
                    continue;
                } else {
                    break;
                }
            }

            String input = rawArgs[i];
            ArgumentTypeHandler<?> handler = registry.getTypeHandler(definition.getType());

            if (handler == null) {
                if (definition.getType().equals(String.class)) {
                    parsed.add(input);
                } else {
                    throw new IllegalStateException("No handler registered for: " + definition.getType().getSimpleName());
                }
            } else {
                try {
                    parsed.add(handler.parse(input));
                } catch (Exception e) {
                    throw new IllegalArgumentException("Invalid argument '" + input + "': " + e.getMessage());
                }
            }
        }
        return parsed.toArray();
    }
}
