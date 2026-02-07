package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;

public interface CommandContext {
    Sender getSender();
    <T> T getArgument(String id, Class<T> clazz);
    <T> T getArgument(int index, Class<T> clazz);
}
