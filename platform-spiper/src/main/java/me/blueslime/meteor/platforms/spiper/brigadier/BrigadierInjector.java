package me.blueslime.meteor.platforms.spiper.brigadier;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.ListIterator;

public class BrigadierInjector implements PlatformService {

    private final Commodore commodore;

    public BrigadierInjector() {
        JavaPlugin plugin = fetch(JavaPlugin.class);
        if (CommodoreProvider.isSupported()) {
            this.commodore = CommodoreProvider.getCommodore(plugin);
        } else {
            this.commodore = null;
        }
    }

    public void register(Command command, PlatformCommands registry, org.bukkit.command.Command bukkitCommand) {
        if (commodore == null) return;

        LiteralArgumentBuilder<Object> rootBuilder = LiteralArgumentBuilder.literal(command.getName());

        buildSubcommandTree(rootBuilder, command.getSubcommands(), registry);

        commodore.register(bukkitCommand, rootBuilder);
    }

    /**
     * Find tus Subcommands and convert it to LiteralArgumentBuilder
     */
    private void buildSubcommandTree(LiteralArgumentBuilder<Object> parent, List<Subcommand> subcommands, PlatformCommands registry) {
        if (subcommands == null || subcommands.isEmpty()) return;

        for (Subcommand sub : subcommands) {
            LiteralArgumentBuilder<Object> literal = LiteralArgumentBuilder.literal(sub.getId());

            if (!sub.getArguments().isEmpty()) {
                CommandNode<Object> argumentChain = buildArgumentChain(sub, registry);
                if (argumentChain != null) {
                    literal.then(argumentChain);
                }
            }

            buildSubcommandTree(literal, sub.getSubcommands(), registry);

            parent.then(literal);
        }
    }

    /**
     * Convert List<Argument> for RequiredArgumentBuilder.
     */
    private CommandNode<Object> buildArgumentChain(Subcommand subcommand, PlatformCommands registry) {
        List<Argument<?>> args = subcommand.getArguments();

        CommandNode<Object> lastNode = null;
        ListIterator<Argument<?>> iterator = args.listIterator(args.size());

        while (iterator.hasPrevious()) {
            Argument<?> arg = iterator.previous();
            ArgumentType<?> brigadierType = getBrigadierType(arg, registry);
            RequiredArgumentBuilder<Object, ?> builder = RequiredArgumentBuilder.argument(arg.getId(), brigadierType);

            if (arg.hasSuggestions()) {
                if (arg.isSuggestionKeyPresent()) {
                    me.blueslime.meteor.platforms.api.commands.SuggestionProvider provider = registry.getSuggestion(arg.getSuggestionKey());

                    if (provider != null) {
                        builder.suggests(createSuggestionProvider(provider));
                    }
                } else {
                    builder.suggests((context, suggestionsBuilder) -> {
                        String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                        for (String s : arg.getSuggestions()) {
                            if (s.toLowerCase().startsWith(remaining)) {
                                suggestionsBuilder.suggest(s);
                            }
                        }
                        return suggestionsBuilder.buildFuture();
                    });
                }
            } else {
                Class<?> clazz = arg.getType();
                if (clazz.isEnum()) {
                    builder.suggests((context, suggestionsBuilder) -> {
                        String remaining = suggestionsBuilder.getRemaining().toLowerCase();
                        for (Object constant : clazz.getEnumConstants()) {
                            String name = ((Enum<?>) constant).name();
                            if (name.toLowerCase().startsWith(remaining)) {
                                suggestionsBuilder.suggest(name);
                            }
                        }
                        return suggestionsBuilder.buildFuture();
                    });
                }
            }

            if (lastNode != null) {
                builder.then(lastNode);
            }

            lastNode = builder.build();
        }

        return lastNode;
    }

    private ArgumentType<?> getBrigadierType(Argument<?> arg, PlatformCommands registry) {
        ArgumentTypeHandler<?> handler = registry.getTypeHandler(arg.getType());
        if (handler != null) {
            Object type = handler.getBrigadierType();
            if (type == null) {
                type = StringArgumentType.string();
            }
            if (type instanceof ArgumentType) {
                return (ArgumentType<?>) type;
            }
        }
        return com.mojang.brigadier.arguments.StringArgumentType.word();
    }

    private SuggestionProvider<Object> createSuggestionProvider(
        me.blueslime.meteor.platforms.api.commands.SuggestionProvider meteorProvider
    ) {
        return (context, builder) -> {
            Object source = context.getSource();
            CommandSender sender = null;

            if (source instanceof CommandSender) {
                sender = (CommandSender) source;
            } else {
                try {
                    sender = (CommandSender) source.getClass().getMethod("getCaller").invoke(source);
                } catch (Exception ignored) {}
            }

            List<String> suggestions = meteorProvider.getSuggestions(
                    sender != null ? new SpigotSender(sender) : null
            );

            String remaining = builder.getRemaining().toLowerCase();
            for (String s : suggestions) {
                if (s.toLowerCase().startsWith(remaining)) {
                    builder.suggest(s);
                }
            }
            return builder.buildFuture();
        };
    }
}
