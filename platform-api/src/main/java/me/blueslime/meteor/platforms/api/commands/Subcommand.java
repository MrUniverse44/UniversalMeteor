package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unused")
public abstract class Subcommand implements PlatformService {

    private final List<Subcommand> subcommands = new ArrayList<>();
    private final List<Argument<?>> arguments = new ArrayList<>();
    private Method executorMethod;

    public Subcommand() {
        registerCommandData();
        scanExecutor();
    }

    protected void registerCommandData() {

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
                    if (param.isAnnotationPresent(Suggestions.class)) {
                        Suggestions suggestions = param.getAnnotation(Suggestions.class);
                        if (suggestions.suggests().length > 0) {
                            argument.withSuggestions(
                                Arrays.asList(param.getAnnotation(Suggestions.class).suggests())
                            );
                        } else {
                            argument.withSuggestions(new ArrayList<>());
                        }
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

    @SafeVarargs
    protected final void registerSubcommands(Class<? extends Subcommand>... cmd) {
        if (cmd == null || cmd.length == 0) return;
        List<Subcommand> subcommands = new ArrayList<>();
        for (Class<? extends Subcommand> subcommand : cmd) {
            Subcommand instance = PluginConsumer.ofUnchecked(
                () -> createInstance(subcommand),
                e -> getLogger().error(e, "Can't create subcommand instance for: " + subcommand.getSimpleName()),
                () -> null
            );
            if (instance != null) {
                subcommands.add(instance);
            }
        }
        registerSubcommands(subcommands.toArray(new Subcommand[0]));
    }

    public void registerSubcommands(Subcommand... cmd) {
        if (cmd == null || cmd.length == 0) return;
        subcommands.addAll(Arrays.asList(cmd));
    }

    public void registerArguments(Argument<?>... overrides) {
        if (overrides == null) return;

        for (Argument<?> override : overrides) {
            arguments.replaceAll(current ->
                current.getId().equals(override.getId()) ? override : current
            );

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
                    if (args != null && args.length > argIndex) {
                        invokeArgs.add(args[argIndex++]);
                    } else {
                        invokeArgs.add(null);
                    }
                }
            }
            executorMethod.invoke(this, invokeArgs.toArray());
        } catch (Exception e) {
            getLogger().error(e, "Can't execute subcommand internal handle");
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

    public String getWrongUsage() {
        return "";
    }
}
