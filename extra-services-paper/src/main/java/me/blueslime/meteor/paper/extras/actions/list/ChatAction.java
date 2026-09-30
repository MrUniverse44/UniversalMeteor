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

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ChatAction implements Action {

    private static final Pattern LEGACY_TARGET_PATTERN =
            Pattern.compile(
                    "%for:([^%]+)%",
                    Pattern.CASE_INSENSITIVE
            );

    private final ExecutionTextResolver textResolver;

    public ChatAction(
            ExecutionTextResolver textResolver
    ) {
        this.textResolver = textResolver;
    }

    @Override
    public String id() {
        return "chat";
    }

    @Override
    public ActionInstruction compile(ActionNode node, ActionCompileContext compileContext) {
        final String messageTemplate =
                node.payload();

        final String targetTemplate =
                node.attribute("to");

        return context -> {
            /*
             * Meteor replacements happen async.
             */
            String processedMessage =
                    textResolver.resolveContext(
                            context,
                            messageTemplate
                    );

            String processedTarget =
                    targetTemplate == null
                            ? null
                            : textResolver.resolveContext(
                            context,
                            targetTemplate
                    );

            UUID sourceId =
                    context.get(
                            ContextKeys.PLAYER_ID
                    );

            context.sync(() -> {
                Player source =
                        sourceId == null
                                ? null
                                : Bukkit.getPlayer(
                                sourceId
                        );

                Set<String> targetNames =
                        new LinkedHashSet<>();

                /*
                 * New syntax:
                 *
                 * <chat to="Notch,Steve">Hello
                 */
                if (
                        processedTarget != null &&
                                !processedTarget.isBlank()
                ) {
                    String resolvedTargets =
                            textResolver.resolvePlaceholders(
                                    source,
                                    processedTarget
                            );

                    addTargets(
                            targetNames,
                            resolvedTargets
                    );
                }

                /*
                 * Legacy syntax:
                 *
                 * %for:Notch%
                 */
                Matcher matcher =
                        LEGACY_TARGET_PATTERN.matcher(
                                processedMessage
                        );

                while (matcher.find()) {
                    String target =
                            matcher.group(1);

                    if (
                            target != null &&
                                    !target.isBlank()
                    ) {
                        targetNames.add(
                                target.trim()
                        );
                    }
                }

                String cleanMessage =
                        matcher
                                .replaceAll("")
                                .strip();

                /*
                 * No explicit targets:
                 * the context Player says the message.
                 */
                if (targetNames.isEmpty()) {
                    if (source == null) {
                        return;
                    }

                    String finalMessage =
                            textResolver.resolvePlaceholders(
                                    source,
                                    cleanMessage
                            );

                    if (!finalMessage.isBlank()) {
                        source.chat(
                                finalMessage
                        );
                    }

                    return;
                }

                /*
                 * Explicit targets keep the old behavior:
                 * each target becomes the player executing
                 * the chat.
                 *
                 * PAPI is evaluated against that target.
                 */
                for (String name : targetNames) {
                    Player target =
                            Bukkit.getPlayerExact(
                                    name
                            );

                    if (target == null) {
                        continue;
                    }

                    String finalMessage =
                            textResolver.resolvePlaceholders(
                                    target,
                                    cleanMessage
                            );

                    if (!finalMessage.isBlank()) {
                        target.chat(
                                finalMessage
                        );
                    }
                }
            });

            return ActionResult.CONTINUE;
        };
    }

    private void addTargets(Set<String> targets, String input) {
        String[] split = input.split(",");

        for (String value : split) {
            String target = value.trim();

            if (!target.isEmpty()) {
                targets.add(target);
            }
        }
    }
}