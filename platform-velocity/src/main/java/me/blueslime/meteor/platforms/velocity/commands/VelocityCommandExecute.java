package me.blueslime.meteor.platforms.velocity.commands;

import com.velocitypowered.api.command.SimpleCommand;
import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.platforms.velocity.sender.VelocitySender;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class VelocityCommandExecute implements SimpleCommand, PlatformService {

    private final Command rootCommand;
    private final PlatformCommands registry;

    public VelocityCommandExecute(Command rootCommand, PlatformCommands registry) {
        this.rootCommand = rootCommand;
        this.registry = registry;
    }

    @Override
    public void execute(Invocation invocation) {
        Sender sender = VelocitySender.build(invocation.source());
        String[] args = invocation.arguments();

        try {
            processExecution(sender, rootCommand, args);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                return;
            }
            getLogger().error(e, "Internal error during command execution.");
            sender.send("&cInternal error during command execution. Please contact an developer.");
        }
    }

    private void processExecution(Sender sender, Command root, String[] args) {
        Subcommand current = null;
        List<Subcommand> currentChildren = root.getSubcommands();
        int argIndex = 0;

        while (argIndex < args.length) {
            String potentialSubcommand = args[argIndex];
            boolean foundChild = false;

            if (currentChildren == null || currentChildren.isEmpty()) break;

            for (Subcommand child : currentChildren) {
                if (child.getId().equalsIgnoreCase(potentialSubcommand)) {
                    current = child;
                    currentChildren = child.getSubcommands();
                    argIndex++;
                    foundChild = true;
                    break;
                }
            }
            if (!foundChild) break;
        }

        String[] parameters = Arrays.copyOfRange(args, argIndex, args.length);

        if (current == null) {
            current = root;
        }
        Object[] parsedArgs = parseArguments(sender, current, parameters);
        current.executeInternal(sender, parsedArgs);
    }

    private Object[] parseArguments(Sender sender, Subcommand cmd, String[] rawArgs) {
        List<Argument<?>> expectedArgs = cmd.getArguments();
        List<Object> parsed = new ArrayList<>();

        long requiredCount = expectedArgs.stream()
                .filter(a -> a.getArgType() == ArgumentType.NEEDED).count();

        if (rawArgs.length < requiredCount) {
            if (cmd.getUsage() != null) sender.send(cmd.getUsage());
            throw new IllegalArgumentException("Not enough arguments");
        }

        for (int i = 0; i < expectedArgs.size(); i++) {
            Argument<?> definition = expectedArgs.get(i);

            if (i >= rawArgs.length) {
                if (definition.getArgType() == ArgumentType.OPTIONAL) {
                    parsed.add(null);
                    continue;
                } else break;
            }

            String input = rawArgs[i];
            ArgumentTypeHandler<?> handler = registry.getTypeHandler(definition.getType());

            if (handler == null) {
                if (definition.getType().equals(String.class)) parsed.add(input);
                else throw new IllegalStateException("No handler for: " + definition.getType().getSimpleName());
            } else {
                try {
                    parsed.add(handler.parse(input));
                } catch (Exception e) {
                    if (cmd.getWrongUsage() != null) sender.send(cmd.getWrongUsage());
                    throw new IllegalArgumentException(e.getMessage());
                }
            }
        }
        return parsed.toArray();
    }

    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        return CompletableFuture.completedFuture(List.of());
    }

    @Override
    public List<String> suggest(Invocation invocation) {
        return List.of();
    }
}