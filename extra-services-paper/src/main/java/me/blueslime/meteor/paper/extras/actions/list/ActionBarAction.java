package me.blueslime.meteor.paper.extras.actions.list;

import me.blueslime.meteor.color.renders.ComponentRenderer;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import java.util.Set;

public final class ActionBarAction implements Action {

    private final ExecutionTextResolver textResolver;

    public ActionBarAction(ExecutionTextResolver textResolver) {
        this.textResolver = textResolver;
    }

    @Override
    public String id() {
        return "actionbar";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("action-bar");
    }

    @Override
    public Set<ContextKey<?>> requirements(ActionNode node) {
        return Set.of(ContextKeys.PLAYER_ID);
    }

    @Override
    public ActionInstruction compile(ActionNode node, ActionCompileContext compileContext) {
        final String template =
                node.payload();

        return context -> {
            String processed =
                    textResolver.resolveContext(
                            context,
                            template
                    );

            context.syncPlayer(player -> {
                String message =
                        textResolver.resolvePlaceholders(
                                player,
                                processed
                        );

                player.sendActionBar(
                        ComponentRenderer.translate(
                                message
                        )
                );
            });

            return ActionResult.CONTINUE;
        };
    }
}