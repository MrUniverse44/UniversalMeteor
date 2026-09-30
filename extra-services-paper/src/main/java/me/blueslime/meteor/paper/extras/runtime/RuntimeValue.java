package me.blueslime.meteor.paper.extras.runtime;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

@FunctionalInterface
public interface RuntimeValue<T> {

    T resolve(ExecutionContext context) throws Exception;
}
