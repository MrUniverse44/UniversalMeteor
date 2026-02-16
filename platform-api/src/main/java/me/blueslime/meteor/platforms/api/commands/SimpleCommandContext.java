package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;

import java.util.List;

public class SimpleCommandContext implements CommandContext {
    private final Sender sender;
    private final Object[] args;
    private final List<Argument<?>> definitions;

    public SimpleCommandContext(Sender sender, Object[] args, List<Argument<?>> definitions) {
        this.sender = sender;
        this.args = args;
        this.definitions = definitions;
    }

    @Override
    public Sender getSender() {
        return sender;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getArgument(String id, Class<T> clazz) {
        for (int i = 0; i < definitions.size(); i++) {
            Argument<?> arg = definitions.get(i);
            if (arg.getId().equalsIgnoreCase(id)) {
                return (T) args[i];
            }
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getArgument(int index, Class<T> clazz) {
        if (index >= 0 && index < args.length) {
            return (T) args[index];
        }
        return null;
    }
}
