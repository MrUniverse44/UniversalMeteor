package me.blueslime.meteor.platforms.api.commands;

public interface ArgumentTypeHandler<T> {
    Object getBrigadierType();
    T parse(String input) throws Exception;
}
