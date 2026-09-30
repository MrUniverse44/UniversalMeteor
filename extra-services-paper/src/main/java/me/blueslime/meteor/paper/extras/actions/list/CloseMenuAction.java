package me.blueslime.meteor.paper.extras.actions.list;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;

import org.bukkit.entity.HumanEntity;

import java.util.Set;

public final class CloseMenuAction implements Action {

    @Override
    public String id() {
        return "close-menu";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("close", "closemenu");
    }

    @Override
    public Set<ContextKey<?>> requirements(ActionNode node) {
        return Set.of(ContextKeys.PLAYER_ID);
    }

    @Override
    public ActionInstruction compile(ActionNode node, ActionCompileContext compileContext) {
        return context -> {
            context.syncPlayer(HumanEntity::closeInventory);

            return ActionResult.CONTINUE;
        };
    }
}