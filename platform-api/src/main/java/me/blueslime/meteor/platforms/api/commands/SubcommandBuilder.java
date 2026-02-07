package me.blueslime.meteor.platforms.api.commands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SubcommandBuilder {
    private final String id;
    private final String[] aliases;
    private final List<Argument<?>> arguments = new ArrayList<>();
    private final List<Subcommand> children = new ArrayList<>(); // Recursividad
    private CommandExecutor executor;

    private SubcommandBuilder(String id, String... aliases) {
        this.id = id;
        this.aliases = aliases;
    }

    public static SubcommandBuilder of(String id, String... aliases) {
        return new SubcommandBuilder(id, aliases);
    }

    public static SubcommandBuilder empty() {
        return new SubcommandBuilder("");
    }

    public SubcommandBuilder with(Argument<?>... args) {
        this.arguments.addAll(Arrays.asList(args));
        return this;
    }

    public SubcommandBuilder addSubcommand(Subcommand... subcommands) {
        this.children.addAll(Arrays.asList(subcommands));
        return this;
    }

    // Soporte para añadir tus clases abstractas modulares como hijos
    public SubcommandBuilder addSubcommand(Class<? extends Subcommand>... subcommands) {
        for (Class<? extends Subcommand> clazz : subcommands) {
            try {
                Subcommand instance = clazz.getDeclaredConstructor().newInstance();
                this.children.add(instance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return this;
    }

    public SubcommandBuilder build(CommandExecutor executor) {
        this.executor = executor;
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> SubcommandBuilder build(BasicCommandExecutor<T> executor) {
        this.executor = (ctx) -> executor.execute(
                ctx.getSender(),
                (T) ctx.getArgument(0, Object.class) // O búscalo por ID si prefieres
        );
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T, U> SubcommandBuilder build(BasicCommandExecutorTwo<T, U> executor) {
        this.executor = (ctx) -> executor.execute(
                ctx.getSender(),
                (T) ctx.getArgument(0, Object.class),
                (U) ctx.getArgument(1, Object.class)
        );
        return this;
    }

    public String getId() { return id; }
    public List<Subcommand> getChildren() { return children; }
}