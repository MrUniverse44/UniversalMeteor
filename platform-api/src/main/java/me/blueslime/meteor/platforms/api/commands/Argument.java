package me.blueslime.meteor.platforms.api.commands;

import java.util.List;
import java.util.function.Function;

public class Argument<T> {
    private final Class<T> type;
    private final String id; // Útil para Brigadier
    private ArgumentType argType = ArgumentType.NEEDED;
    private List<String> suggestions = null;
    private String suggestionKey = null;
    private String tooltip;

    private Function<String, T> parser;

    private Argument(Class<T> type, String id) {
        this.type = type;
        this.id = id;
    }

    public static <T> Argument<T> of(String id, Class<T> type) {
        return new Argument<>(type, id);
    }

    public Argument<T> type(ArgumentType type) { this.argType = type; return this; }
    public Argument<T> withSuggestions(List<String> suggestions) { this.suggestions = suggestions; return this; }
    public Argument<T> tooltip(String tooltip) { this.tooltip = tooltip; return this; }
    public Argument<T> parser(Function<String, T> parser) { this.parser = parser; return this; }

    public Class<T> getType() { return type; }
    public String getId() { return id; }
    public ArgumentType getArgType() { return argType; }

    public List<String> getSuggestions() {
        return suggestions;
    }

    public String getTooltip() {
        return tooltip;
    }

    public Argument<T> withSuggestionKey(String value) {
        this.suggestionKey = value;
        return this;
    }

    public String getSuggestionKey() {
        return suggestionKey;
    }

    public boolean isSuggestionListPresent() {
        return  suggestions != null && !suggestions.isEmpty();
    }

    public boolean isSuggestionKeyPresent() {
        return suggestionKey != null && !suggestionKey.isEmpty();
    }

    public boolean hasSuggestions() {
        return isSuggestionKeyPresent() || isSuggestionListPresent();
    }
}
