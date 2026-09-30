package me.blueslime.meteor.paper.extras.runtime.value;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

@FunctionalInterface
public interface RuntimeTextResolver {

    String resolve(String input, ExecutionContext context) throws Exception;

}