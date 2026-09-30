package me.blueslime.meteor.platforms.spiper.brigadier;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;

import com.mojang.brigadier.tree.CommandNode;

import me.blueslime.meteor.platforms.api.commands.Argument;
import me.blueslime.meteor.platforms.api.commands.ArgumentTypeHandler;
import me.blueslime.meteor.platforms.api.commands.Command;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.commands.Subcommand;

import me.lucko.commodore.Commodore;
import me.lucko.commodore.CommodoreProvider;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.ListIterator;
import java.util.Objects;

public final class BrigadierInjector {

    private final Commodore commodore;

    public BrigadierInjector(
            JavaPlugin plugin
    ) {
        Objects.requireNonNull(
                plugin,
                "plugin"
        );

        this.commodore =
                CommodoreProvider.isSupported()
                        ? CommodoreProvider.getCommodore(
                        plugin
                )
                        : null;
    }

    public boolean isSupported() {
        return commodore != null;
    }

    public void register(
            Command command,
            PlatformCommands registry,
            org.bukkit.command.Command bukkitCommand
    ) {
        if (commodore == null) {
            return;
        }

        Objects.requireNonNull(
                command,
                "command"
        );

        Objects.requireNonNull(
                registry,
                "registry"
        );

        Objects.requireNonNull(
                bukkitCommand,
                "bukkitCommand"
        );

        LiteralArgumentBuilder<Object> root =
                LiteralArgumentBuilder.literal(
                        command.getName()
                );

        buildSubcommandTree(
                root,
                command.getSubcommands(),
                registry
        );

        /*
         * Root command can itself contain arguments.
         */
        if (!command.getArguments().isEmpty()) {
            CommandNode<Object> argumentChain =
                    buildArgumentChain(
                            command,
                            registry
                    );

            if (argumentChain != null) {
                root.then(
                        argumentChain
                );
            }
        }

        commodore.register(
                bukkitCommand,
                root
        );
    }

    private void buildSubcommandTree(
            LiteralArgumentBuilder<Object> parent,
            List<Subcommand> subcommands,
            PlatformCommands registry
    ) {
        if (
                subcommands == null ||
                        subcommands.isEmpty()
        ) {
            return;
        }

        for (Subcommand subcommand : subcommands) {
            LiteralArgumentBuilder<Object> literal =
                    LiteralArgumentBuilder.literal(
                            subcommand.getId()
                    );

            if (
                    !subcommand
                            .getArguments()
                            .isEmpty()
            ) {
                CommandNode<Object> argumentChain =
                        buildArgumentChain(
                                subcommand,
                                registry
                        );

                if (argumentChain != null) {
                    literal.then(
                            argumentChain
                    );
                }
            }

            buildSubcommandTree(
                    literal,
                    subcommand.getSubcommands(),
                    registry
            );

            parent.then(
                    literal
            );
        }
    }

    private CommandNode<Object> buildArgumentChain(
            Subcommand subcommand,
            PlatformCommands registry
    ) {
        List<Argument<?>> arguments =
                subcommand.getArguments();

        if (arguments.isEmpty()) {
            return null;
        }

        CommandNode<Object> child =
                null;

        ListIterator<Argument<?>> iterator =
                arguments.listIterator(
                        arguments.size()
                );

        while (iterator.hasPrevious()) {
            int index =
                    iterator.previousIndex();

            Argument<?> argument =
                    iterator.previous();

            ArgumentType<?> type =
                    getBrigadierType(
                            argument,
                            registry,
                            index,
                            arguments.size()
                    );

            RequiredArgumentBuilder<Object, ?> builder =
                    RequiredArgumentBuilder.argument(
                            argument.getId(),
                            type
                    );

            /*
             * Do NOT install Meteor suggestion providers here.
             *
             * With Commodore's Bukkit Command registration,
             * Bukkit's tab completion is the reliable source
             * of dynamic suggestions.
             */

            if (child != null) {
                builder.then(
                        child
                );
            }

            child =
                    builder.build();
        }

        return child;
    }

    private ArgumentType<?> getBrigadierType(
            Argument<?> argument,
            PlatformCommands registry,
            int argumentIndex,
            int argumentCount
    ) {
        /*
         * Meteor treats the final String parameter as:
         *
         *   hello this is a message
         *
         * not:
         *
         *   "hello this is a message"
         *
         * Therefore the Brigadier tree must describe the same
         * syntax with greedyString().
         */
        if (
                argument.getType() == String.class &&
                        argumentIndex == argumentCount - 1
        ) {
            return StringArgumentType.greedyString();
        }

        ArgumentTypeHandler<?> handler =
                registry.getTypeHandler(
                        argument.getType()
                );

        if (handler == null) {
            return StringArgumentType.word();
        }

        Object type =
                handler.getBrigadierType();

        if (type instanceof ArgumentType<?> brigadierType) {
            return brigadierType;
        }

        return StringArgumentType.word();
    }
}