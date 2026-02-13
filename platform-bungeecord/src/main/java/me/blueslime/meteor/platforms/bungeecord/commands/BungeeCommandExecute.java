package me.blueslime.meteor.platforms.bungeecord.commands;

import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.platforms.bungeecord.sender.BungeeSender;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.plugin.Command;
import net.md_5.bungee.api.plugin.TabExecutor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BungeeCommandExecute extends Command implements TabExecutor, PlatformService {

    private final me.blueslime.meteor.platforms.api.commands.Command rootCommand;
    private final PlatformCommands registry;

    public BungeeCommandExecute(me.blueslime.meteor.platforms.api.commands.Command rootCommand, PlatformCommands registry) {
        super(
            rootCommand.getName(),
            null,
            rootCommand.getAliases().toArray(new String[0])
        );
        this.rootCommand = rootCommand;
        this.registry = registry;

        this.rootCommand.register();
    }

    @Override
    public void execute(CommandSender bungeeSender, String[] args) {
        Sender sender = BungeeSender.build(bungeeSender);

        try {
            processExecution(sender, rootCommand, args);
        } catch (IllegalArgumentException ignored) {
            // Already sent a message
        } catch (Exception e) {
            getLogger().error(e, "Cannot execute command due to an internal error.");
        }
    }

    private void processExecution(Sender sender, me.blueslime.meteor.platforms.api.commands.Command root, String[] args) throws Exception {
        Subcommand current = null;
        List<Subcommand> currentChildren = root.getSubcommands();
        int argIndex = 0;

        while (argIndex < args.length) {
            String potential = args[argIndex];
            boolean found = false;

            if (currentChildren == null || currentChildren.isEmpty()) break;

            for (Subcommand child : currentChildren) {
                if (child.getId().equalsIgnoreCase(potential)) {
                    current = child;
                    currentChildren = child.getSubcommands();
                    argIndex++;
                    found = true;
                    break;
                }
            }
            if (!found) break;
        }

        String[] params = Arrays.copyOfRange(args, argIndex, args.length);

        if (current == null) {
            root.executeBase(sender);
        } else {
            Object[] parsedArgs = parseArguments(sender, current, params);
            current.executeInternal(sender, parsedArgs);
        }
    }

    private Object[] parseArguments(Sender sender, Subcommand cmd, String[] rawArgs) {
        List<Argument<?>> expected = cmd.getArguments();
        List<Object> parsed = new ArrayList<>();

        long required = expected.stream().filter(a -> a.getArgType() == ArgumentType.NEEDED).count();

        if (rawArgs.length < required) {
            if (cmd.getUsage() != null) sender.send(cmd.getUsage());
            throw new IllegalArgumentException("Not enough arguments");
        }

        for (int i = 0; i < expected.size(); i++) {
            Argument<?> def = expected.get(i);

            if (i >= rawArgs.length) {
                if (def.getArgType() == ArgumentType.OPTIONAL) {
                    parsed.add(null);
                    continue;
                } else break;
            }

            String input = rawArgs[i];
            ArgumentTypeHandler<?> handler = registry.getTypeHandler(def.getType());

            if (handler == null) {
                if (def.getType().equals(String.class)) parsed.add(input);
                else throw new IllegalStateException("No handler for: " + def.getType().getSimpleName());
            } else {
                try {
                    parsed.add(handler.parse(input));
                } catch (Exception e) {
                    if (cmd.getWrongUsage() != null) sender.send(cmd.getWrongUsage());
                    throw new IllegalArgumentException(e);
                }
            }
        }
        return parsed.toArray();
    }

    @Override
    public Iterable<String> onTabComplete(CommandSender sender, String[] args) {
        if (args.length == 0) return Collections.emptyList();

        Subcommand current = null;
        List<Subcommand> children = rootCommand.getSubcommands();
        int argIndex = 0;

        while (argIndex < args.length - 1) {
            String potential = args[argIndex];
            boolean found = false;
            if (children != null) {
                for (Subcommand child : children) {
                    if (child.getId().equalsIgnoreCase(potential)) {
                        current = child;
                        children = child.getSubcommands();
                        argIndex++;
                        found = true;
                        break;
                    }
                }
            }
            if (!found) break;
        }

        String token = args[args.length - 1].toLowerCase();
        List<String> suggestions = new ArrayList<>();

        List<Subcommand> targets = (current == null) ? rootCommand.getSubcommands() : current.getSubcommands();

        if (targets != null && !targets.isEmpty()) {
            for (Subcommand s : targets) {
                if (s.getId().toLowerCase().startsWith(token)) {
                    suggestions.add(s.getId());
                }
            }
        }

        if (current != null && !current.getArguments().isEmpty()) {
            int paramIndex = (args.length - 1) - argIndex;

            if (paramIndex >= 0 && paramIndex < current.getArguments().size()) {
                Argument<?> arg = current.getArguments().get(paramIndex);

                if (arg.getSuggestionKey() != null) {
                    var provider = registry.getSuggestion(arg.getSuggestionKey());
                    if (provider != null) {
                        List<String> result = provider.getSuggestions(BungeeSender.build(sender));
                        suggestions.addAll(filter(result, token));
                    }
                }
                else if (arg.getSuggestions() != null) {
                    suggestions.addAll(filter(arg.getSuggestions(), token));
                }
                else if (arg.getType().isEnum()) {
                    List<String> enums = Arrays.stream(arg.getType().getEnumConstants())
                            .map(o -> ((Enum<?>)o).name())
                            .collect(Collectors.toList());
                    suggestions.addAll(filter(enums, token));
                }
                else if (
                    arg.getId().equalsIgnoreCase("player") || arg.getType().getSimpleName().equals("ProxiedPlayer") ||
                    arg.getId().equalsIgnoreCase("Sender")
                ) {
                    suggestions.addAll(filter(
                            net.md_5.bungee.api.ProxyServer.getInstance().getPlayers().stream()
                                    .map(net.md_5.bungee.api.connection.ProxiedPlayer::getName)
                                    .collect(Collectors.toList()),
                            token
                    ));
                }
            }
        }

        return suggestions;
    }

    private List<String> filter(List<String> list, String token) {
        if (list == null) return Collections.emptyList();
        return list.stream()
                .filter(s -> s.toLowerCase().startsWith(token))
                .collect(Collectors.toList());
    }
}
