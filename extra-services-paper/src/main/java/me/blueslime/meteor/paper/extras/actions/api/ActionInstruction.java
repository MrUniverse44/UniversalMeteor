package me.blueslime.meteor.paper.extras.actions.api;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

@FunctionalInterface
public interface ActionInstruction {

    ActionResult execute(ExecutionContext context) throws Exception;
}