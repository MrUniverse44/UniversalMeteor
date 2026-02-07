package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.commands.provider.PlatformCommandProvider;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.util.*;

public class PlatformCommands implements PlatformService {

    private final Map<String, SuggestionProvider> suggestionRegistry = new HashMap<>();
    private final Map<Class<?>, ArgumentTypeHandler<?>> typeRegistry = new HashMap<>();
    private final PlatformCommandProvider provider;

    public PlatformCommands(PlatformCommandProvider provider) {
        this.provider = provider;
        registerDefaultHandlers();
    }

    public void registerSuggestion(String id, SuggestionProvider provider) {
        this.suggestionRegistry.put(id, provider);
    }

    public <T> void registerType(Class<T> clazz, ArgumentTypeHandler<T> handler) {
        this.typeRegistry.put(clazz, handler);
    }

    public SuggestionProvider getSuggestion(String id) {
        return suggestionRegistry.get(id);
    }

    public void registerCommand(Command... commands) {
        if (commands == null || commands.length == 0) {
            return;
        }
        registerCommand(Arrays.asList(commands));
    }

    public void registerCommand(Collection<Command> commands) {
        if (commands == null || commands.isEmpty()) {
            return;
        }
        for (Command command : commands) {
            provider.register(command, this);
        }
    }

    @SafeVarargs
    public final void registerCommand(Class<? extends Command>... commands) {
        if (commands == null || commands.length == 0) {
            return;
        }
        Set<Command> commandSet = new LinkedHashSet<>();
        for (Class<? extends Command> command : commands) {
            Command cmd = PluginConsumer.ofUnchecked(
                () -> createInstance(command),
                e -> getLogger().error(e, "Can't register command " + command.getSimpleName() + " because an exception occurred"),
                () -> null
            );
            if (cmd != null) {
                commandSet.add(cmd);
            }
        }
        registerCommand(commandSet);
    }

    @SuppressWarnings("unchecked")
    public <T> ArgumentTypeHandler<T> getTypeHandler(Class<T> clazz) {
        if (typeRegistry.containsKey(clazz)) {
            return (ArgumentTypeHandler<T>) typeRegistry.get(clazz);
        }
        if (clazz.isEnum()) {
            return new ArgumentTypeHandler<>() {
                @Override
                public T parse(String input) {
                    for (T constant : clazz.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equalsIgnoreCase(input)) {
                            return constant;
                        }
                    }
                    throw new IllegalArgumentException("Invalid value for " + clazz.getSimpleName() + ": " + input);
                }

                @Override
                public Object getBrigadierType() {
                    return null;
                }
            };
        }
        return null;
    }

    private void registerDefaultHandlers() {
        provider.registerTypes(this);
    }
}