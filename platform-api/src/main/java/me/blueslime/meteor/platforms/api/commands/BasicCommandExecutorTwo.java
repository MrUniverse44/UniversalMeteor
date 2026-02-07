package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;

@FunctionalInterface
public interface BasicCommandExecutorTwo<T, U> {
    void execute(Sender sender, T arg1, U arg2);
}