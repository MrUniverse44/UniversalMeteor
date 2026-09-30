package me.blueslime.meteor.paper.extras.conditions.api;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

@FunctionalInterface
public interface ConditionInstruction {

    boolean test(ExecutionContext context) throws Exception;
}