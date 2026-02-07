package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;

import java.util.List;

@FunctionalInterface
public interface SuggestionProvider {
    List<String> getSuggestions(Sender sender);
}
