package me.blueslime.meteor.paper.extras.actions.list;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;
import me.blueslime.meteor.platforms.paper.sender.PaperSender;

import java.util.Set;

public final class MessageAction implements Action {

    private final ExecutionTextResolver textResolver;

    public MessageAction(ExecutionTextResolver textResolver) {
        this.textResolver = textResolver;
    }

    @Override
    public String id() {
        return "message";
    }

    @Override
    public Set<String> aliases() {
        return Set.of("msg");
    }

    @Override
    public Set<ContextKey<?>> requirements(ActionNode node) {
        return Set.of(ContextKeys.PLAYER_ID);
    }

    @Override
    public ActionInstruction compile(
            ActionNode node,
            ActionCompileContext compileContext
    ) {
        final String template =
                node.payload();

        return context -> {
            /*
             * Everything that belongs exclusively to
             * Meteor is calculated outside Bukkit thread.
             */
            String processed =
                    textResolver.resolveContext(
                            context,
                            template
                    );

            /*
             * Only Player/PAPI/send goes through
             * the sync bridge.
             */
            context.syncPlayer(player -> {
                String message =
                        textResolver.resolvePlaceholders(
                                player,
                                processed
                        );

                PaperSender
                        .build(player)
                        .send(message);
            });

            return ActionResult.CONTINUE;
        };
    }
}