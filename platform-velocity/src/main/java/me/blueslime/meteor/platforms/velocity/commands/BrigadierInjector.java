package me.blueslime.meteor.platforms.velocity.commands;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandSource;
import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.velocity.sender.VelocitySender;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

public class BrigadierInjector {

    public BrigadierCommand build(Command command, PlatformCommands registry) {
        LiteralArgumentBuilder<CommandSource> root = LiteralArgumentBuilder.literal(command.getName());

        root.executes(context -> {
            command.executeBase(VelocitySender.build(context.getSource()));
            return 1;
        });

        buildSubcommandTree(root, command.getSubcommands(), registry);

        return new BrigadierCommand(root);
    }

    private void buildSubcommandTree(LiteralArgumentBuilder<CommandSource> parent, List<Subcommand> subcommands, PlatformCommands registry) {
        if (subcommands == null || subcommands.isEmpty()) return;

        for (Subcommand sub : subcommands) {
            LiteralArgumentBuilder<CommandSource> literal = LiteralArgumentBuilder.literal(sub.getId());

            if (!sub.getArguments().isEmpty()) {
                CommandNode<CommandSource> argChain = buildArgumentChain(sub, registry);
                if (argChain != null) {
                    literal.then(argChain);
                }
            } else {
                literal.executes(context -> executeBrigadier(context, sub, registry));
            }

            buildSubcommandTree(literal, sub.getSubcommands(), registry);

            parent.then(literal);
        }
    }

    private CommandNode<CommandSource> buildArgumentChain(Subcommand subcommand, PlatformCommands registry) {
        List<Argument<?>> args = subcommand.getArguments();
        CommandNode<CommandSource> lastNode = null;

        ListIterator<Argument<?>> iterator = args.listIterator(args.size());

        while (iterator.hasPrevious()) {
            Argument<?> arg = iterator.previous();
            ArgumentType<?> type = getBrigadierType(arg, registry);

            RequiredArgumentBuilder<CommandSource, ?> builder = RequiredArgumentBuilder.argument(arg.getId(), type);

            if (arg.getSuggestionKey() != null) {
                var provider = registry.getSuggestion(arg.getSuggestionKey());
                if (provider != null) {
                    builder.suggests((ctx, suggestionsBuilder) -> {
                        Sender sender = VelocitySender.build(ctx.getSource());
                        String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                        for (String s : provider.getSuggestions(sender)) {
                            if (s.toLowerCase().startsWith(remaining)) suggestionsBuilder.suggest(s);
                        }
                        return suggestionsBuilder.buildFuture();
                    });
                }
            }

            if (lastNode != null) {
                builder.then(lastNode);
            } else {
                builder.executes(context -> executeBrigadier(context, subcommand, registry));
            }

            lastNode = builder.build();
        }
        return lastNode;
    }

    /**
     * Bridge between brigadier and Meteor commands
     */
    private int executeBrigadier(CommandContext<CommandSource> context, Subcommand sub, PlatformCommands registry) {
        Sender sender = VelocitySender.build(context.getSource());
        List<Object> parsedArgs = new ArrayList<>();

        for (Argument<?> arg : sub.getArguments()) {
            try {
                Object val;
                try {
                    val = context.getArgument(arg.getId(), Object.class);
                } catch (IllegalArgumentException e) {
                    if (arg.getArgType() == me.blueslime.meteor.platforms.api.commands.ArgumentType.OPTIONAL) {
                        parsedArgs.add(null);
                        continue;
                    }
                    throw e;
                }

                if (val instanceof String && !arg.getType().equals(String.class)) {
                    ArgumentTypeHandler<?> handler = registry.getTypeHandler(arg.getType());
                    if (handler != null) {
                        val = handler.parse((String) val);
                    }
                }

                parsedArgs.add(val);

            } catch (Exception e) {
                return 0;
            }
        }

        sub.executeInternal(sender, parsedArgs.toArray());
        return 1;
    }

    private ArgumentType<?> getBrigadierType(Argument<?> arg, PlatformCommands registry) {
        ArgumentTypeHandler<?> handler = registry.getTypeHandler(arg.getType());
        if (handler != null && handler.getBrigadierType() instanceof ArgumentType) {
            return (ArgumentType<?>) handler.getBrigadierType();
        }
        return StringArgumentType.word();
    }
}
