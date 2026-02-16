package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unused")
public class CommandBuilder {

    private final String name;
    private List<String> aliases = Collections.emptyList();
    private String description = "";
    private final List<Argument<?>> arguments = new ArrayList<>();
    private final List<Subcommand> subcommands = new ArrayList<>();
    private CommandExecutor executor;

    private CommandBuilder(String name) {
        this.name = name;
    }

    public static CommandBuilder of(String name) {
        return new CommandBuilder(name);
    }

    public CommandBuilder aliases(String... aliases) {
        this.aliases = List.of(aliases);
        return this;
    }

    public CommandBuilder description(String description) {
        this.description = description;
        return this;
    }

    public CommandBuilder argument(Argument<?> argument) {
        this.arguments.add(argument);
        return this;
    }

    public CommandBuilder subcommands(Subcommand... subcommands) {
        if (subcommands != null) {
            this.subcommands.addAll(List.of(subcommands));
        }
        return this;
    }

    public CommandBuilder subcommands(SubcommandBuilder... builders) {
        if (builders != null) {
            for (SubcommandBuilder builder : builders) {
                this.subcommands.add(builder.build());
            }
        }
        return this;
    }

    public CommandBuilder execute(CommandExecutor executor) {
        this.executor = executor;
        return this;
    }

    public Command build() {
        return new Command() {
            @Override
            public @NotNull String getName() {
                return name;
            }

            @Override
            public Collection<String> getAliases() {
                return aliases;
            }

            @Override
            public @NotNull String getDescription() {
                return description;
            }

            @Override
            protected void registerCommandData() {
                if (!arguments.isEmpty()) {
                    registerArguments(arguments.toArray(new Argument[0]));
                }
                if (!subcommands.isEmpty()) {
                    registerSubcommands(subcommands.toArray(new Subcommand[0]));
                }
            }

            @Override
            public void executeInternal(Sender sender, Object[] args) {
                if (executor != null) {
                    CommandContext context = new SimpleCommandContext(sender, args, getArguments());
                    executor.execute(context);
                }
            }
        };
    }
}