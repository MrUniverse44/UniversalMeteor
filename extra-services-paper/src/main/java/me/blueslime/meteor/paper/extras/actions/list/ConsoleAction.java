package me.blueslime.meteor.paper.extras.actions.list;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public final class ConsoleAction implements Action {

    private final ExecutionTextResolver textResolver;

    public ConsoleAction(ExecutionTextResolver textResolver) {
        this.textResolver = textResolver;
    }

    @Override
    public String id() {
        return "console";
    }

    @Override
    public ActionInstruction compile(ActionNode node, ActionCompileContext compileContext) {
        final String template =
                node.payload();

        return context -> {
            String processed = textResolver.resolveContext(
                context,
                template
            );

            UUID playerId = context.get(
                ContextKeys.PLAYER_ID
            );

            context.sync(() -> {
                Player player = playerId == null
                    ? null
                    : Bukkit.getPlayer(
                    playerId
                );

                String command = textResolver.resolvePlaceholders(
                    player,
                    processed
                );

                command = stripLeadingSlash(command);

                if (command.isBlank()) {
                    return;
                }

                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            });

            return ActionResult.CONTINUE;
        };
    }

    private String stripLeadingSlash(String command) {
        String result = command.strip();

        if (result.startsWith("/")) {
            return result.substring(1);
        }

        return result;
    }
}