package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class Subcommand {

    private final List<Subcommand> subcommands = new ArrayList<>();
    private final List<Argument<?>> arguments = new ArrayList<>();
    private Method executorMethod;

    public Subcommand() {
        scanExecutor();
    }

    private void scanExecutor() {
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Executor.class)) {
                this.executorMethod = method;
                method.setAccessible(true);

                for (Parameter param : method.getParameters()) {
                    if (param.isAnnotationPresent(CommandSender.class)) continue; // Ignoramos el Sender

                    String name = param.isAnnotationPresent(Name.class) ?
                            param.getAnnotation(Name.class).value() : param.getName();

                    Argument<?> argument = defineArgument(name, param.getType());

                    if (param.isAnnotationPresent(Optional.class)) argument.type(ArgumentType.OPTIONAL);
                    if (param.isAnnotationPresent(Suggestion.class)) {
                        argument.withSuggestionKey(param.getAnnotation(Suggestion.class).value());
                    }

                    arguments.add(argument);
                }
                break;
            }
        }
    }

    protected <T> Argument<T> defineArgument(String name, Class<T> type) {
        return Argument.of(
            name, type
        );
    }

    protected void registerSubcommands(Subcommand... cmd) {
        if (cmd == null || cmd.length == 0) return;
        subcommands.addAll(Arrays.asList(cmd));
    }

    protected void registerArguments(Argument<?>... overrides) {
        if (overrides == null) return;

        for (Argument<?> override : overrides) {
            // Buscamos si ya existe un argumento con ese ID generado por scanExecutor
            arguments.replaceAll(current ->
                    current.getId().equals(override.getId()) ? override : current
            );

            // Si no existía (es un argumento extra manual), lo agregamos
            boolean exists = arguments.stream().anyMatch(a -> a.getId().equals(override.getId()));
            if (!exists) {
                arguments.add(override);
            }
        }
    }

    public abstract String getId();

    public void executeInternal(Sender sender, Object[] args) {
        try {
            List<Object> invokeArgs = new ArrayList<>();
            int argIndex = 0;

            for (Parameter param : executorMethod.getParameters()) {
                if (param.isAnnotationPresent(CommandSender.class)) {
                    invokeArgs.add(sender);
                } else {
                    invokeArgs.add(args[argIndex++]);
                }
            }
            executorMethod.invoke(this, invokeArgs.toArray());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<Subcommand> getSubcommands() {
        return subcommands;
    }

    public List<Argument<?>> getArguments() {
        return arguments;
    }

    public String getUsage() {
        return "";
    }
}
