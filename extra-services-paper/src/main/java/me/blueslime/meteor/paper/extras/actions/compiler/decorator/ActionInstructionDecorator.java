package me.blueslime.meteor.paper.extras.actions.compiler.decorator;

import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;

@FunctionalInterface
public interface ActionInstructionDecorator {

    ActionInstruction decorate(
            ActionNode node,
            ActionInstruction instruction
    );
}