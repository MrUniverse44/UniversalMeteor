package me.blueslime.meteor.platforms.spigot.commands;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.api.logger.IPlatformLogger;
import me.blueslime.meteor.platforms.api.utils.CommandUtils;
import me.blueslime.meteor.platforms.spiper.brigadier.SpigotSender;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class SpigotCommandExecute extends org.bukkit.command.Command {
    private final Command rootCommand;
    private final PlatformCommands registry;

    protected SpigotCommandExecute(Command rootCommand, PlatformCommands registry) {
        super(rootCommand.getName());
        this.rootCommand = rootCommand;
        this.registry = registry;
        this.setAliases(new ArrayList<>(rootCommand.getAliases()));
        this.setDescription(rootCommand.getDescription());
    }

    @SuppressWarnings("NullableProblems")
    @Override
    public boolean execute(CommandSender bukkitSender, String label, String[] args) {
        IPlatformLogger logger = Implements.fetch(IPlatformLogger.class);
        Sender sender = SpigotSender.build(bukkitSender);

        try {
            String[] processedArgs = CommandUtils.groupQuotedArguments(args);

            processExecution(sender, rootCommand, processedArgs, bukkitSender);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                return true;
            }
            logger.error(e, "Can't perform command for " + sender.getName());
        }
        return true;
    }

    private void processExecution(Sender sender, Command root, String[] args, CommandSender bukkitSender) {
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

            if (i == expectedArgs.size() - 1 && definition.getType().equals(String.class)) {
                StringBuilder builder = new StringBuilder(input);
                for (int j = i + 1; j < rawArgs.length; j++) {
                    builder.append(" ").append(rawArgs[j]);
                }
                input = builder.toString();
            }

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


    @Override
    public @NonNull  List<String> tabComplete(
            @NonNull CommandSender bukkitSender,
            @NonNull String alias,
            String @NonNull [] args
    ) {
        Sender sender =
            SpigotSender.build(
                    bukkitSender
            );

        try {
            return findSuggestions(
                    sender,
                    rootCommand,
                    args
            );

        } catch (
                RuntimeException exception
        ) {
            return List.of();
        }
    }

    private List<String> findSuggestions(
            Sender sender,
            Command root,
            String[] args
    ) {
        if (args == null) {
            args =
                    new String[0];
        }

        Subcommand current =
                root;

        List<Subcommand> children =
                root.getSubcommands();

        int consumed =
                0;

        /*
         * The final token is the one currently being completed.
         *
         * Therefore only consume COMPLETE previous tokens as
         * subcommands.
         */
        int completedArguments =
                Math.max(
                        0,
                        args.length - 1
                );

        while (
                consumed < completedArguments &&
                        !children.isEmpty()
        ) {
            String token =
                    args[consumed];

            Subcommand match =
                    null;

            for (Subcommand child : children) {
                if (
                        child
                                .getId()
                                .equalsIgnoreCase(
                                        token
                                )
                ) {
                    match =
                            child;

                    break;
                }
            }

            if (match == null) {
                break;
            }

            current =
                    match;

            children =
                    match.getSubcommands();

            consumed++;
        }

        String remaining =
                args.length == 0
                        ? ""
                        : args[args.length - 1];

        List<String> suggestions =
                new ArrayList<>();

        /*
         * If we haven't started entering the command's arguments yet,
         * children are possible candidates too.
         */
        int argumentIndex =
                Math.max(
                        0,
                        args.length - 1 - consumed
                );

        if (argumentIndex == 0) {
            for (Subcommand child : children) {
                suggestions.add(
                        child.getId()
                );
            }
        }

        List<Argument<?>> definitions =
                current.getArguments();

        if (argumentIndex < definitions.size()) {
            Argument<?> argument =
                    definitions.get(
                            argumentIndex
                    );

            suggestions.addAll(
                    suggestionsFor(
                            sender,
                            argument
                    )
            );
        }

        String lower =
                remaining.toLowerCase(
                        java.util.Locale.ROOT
                );

        return suggestions
                .stream()
                .filter(Objects::nonNull)
                .filter(value ->
                        value
                                .toLowerCase(
                                        java.util.Locale.ROOT
                                )
                                .startsWith(
                                        lower
                                )
                )
                .distinct()
                .sorted(
                        String.CASE_INSENSITIVE_ORDER
                )
                .toList();
    }

    private List<String> suggestionsFor(
            Sender sender,
            Argument<?> argument
    ) {
        if (argument.isSuggestionKeyPresent()) {
            me.blueslime.meteor.platforms.api.commands.SuggestionProvider provider =
                    registry.getSuggestion(
                            argument.getSuggestionKey()
                    );

            if (provider != null) {
                List<String> values =
                        provider.getSuggestions(
                                sender
                        );

                return values == null
                        ? List.of()
                        : values;
            }
        }

        if (argument.isSuggestionListPresent()) {
            return List.copyOf(
                    argument.getSuggestions()
            );
        }

        Class<?> type =
                argument.getType();

        if (
                type == Player.class ||
                        type == OfflinePlayer.class ||
                        type == Sender.class
        ) {
            return Bukkit
                    .getOnlinePlayers()
                    .stream()
                    .map(Player::getName)
                    .toList();
        }

        if (type == Boolean.class || type == boolean.class) {
            return List.of(
                    "true",
                    "false"
            );
        }

        if (type.isEnum()) {
            Object[] constants =
                    type.getEnumConstants();

            if (constants == null) {
                return List.of();
            }

            return Arrays
                    .stream(
                            constants
                    )
                    .map(value ->
                            ((Enum<?>) value).name()
                    )
                    .toList();
        }

        return List.of();
    }


}
