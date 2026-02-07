package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;

@FunctionalInterface
public interface BasicCommandExecutor<T> {
    void execute(Sender sender, T arg1);
}
