package me.blueslime.meteor.paper.extras.actions.list;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;

import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;

import me.blueslime.meteor.paper.extras.menus.MenuService;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;

import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Set;
import java.util.UUID;

public final class MenuAction
        implements Action {

    private final ExecutionTextResolver textResolver;

    public MenuAction(
            ExecutionTextResolver textResolver
    ) {

        this.textResolver =
                textResolver;
    }

    @Override
    public String id() {
        return "menu";
    }

    @Override
    public Set<String> aliases() {
        return Set.of(
                "open-menu"
        );
    }

    @Override
    public Set<ContextKey<?>> requirements(
            ActionNode node
    ) {
        return Set.of(
                ContextKeys.PLAYER_ID
        );
    }

    @Override
    public ActionInstruction compile(
            ActionNode node,
            ActionCompileContext context
    ) {
        String menuTemplate =
                firstNonBlank(
                        node.attribute(
                                "id"
                        ),
                        node.attribute(
                                "menu"
                        )
                );

        String targetTemplate =
                firstNonBlank(
                        node.attribute(
                                "to"
                        ),
                        node.attribute(
                                "player"
                        )
                );

        /*
         * Legacy:
         *
         * <menu>selector
         * <menu>selector,Notch
         */
        if (menuTemplate == null) {
            String payload =
                    node.payload()
                            .strip();

            int comma =
                    payload.indexOf(',');

            if (comma >= 0) {
                menuTemplate =
                        payload
                                .substring(
                                        0,
                                        comma
                                )
                                .strip();

                if (
                        targetTemplate == null
                ) {
                    targetTemplate =
                            payload
                                    .substring(
                                            comma + 1
                                    )
                                    .strip();
                }

            } else {
                menuTemplate =
                        payload;
            }
        }

        if (
                menuTemplate == null ||
                        menuTemplate.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Menu action requires a menu id"
            );
        }

        final String finalMenuTemplate =
                menuTemplate;

        final String finalTargetTemplate =
                targetTemplate;

        return execution -> {
            String preparedMenu =
                    textResolver.resolveContext(
                            execution,
                            finalMenuTemplate
                    );

            String menuId =
                    execution.waitSyncPlayer(
                            player ->
                                    textResolver.resolvePlaceholders(
                                            player,
                                            preparedMenu
                                    )
                    );

            if (
                    menuId == null ||
                            menuId.isBlank()
            ) {
                return ActionResult.CONTINUE;
            }

            UUID targetId =
                    execution.require(
                            ContextKeys.PLAYER_ID
                    );

            if (
                    finalTargetTemplate != null &&
                            !finalTargetTemplate.isBlank()
            ) {
                String preparedTarget =
                        textResolver.resolveContext(
                                execution,
                                finalTargetTemplate
                        );

                String targetName =
                        execution.waitSyncPlayer(
                                player ->
                                        textResolver.resolvePlaceholders(
                                                player,
                                                preparedTarget
                                        )
                        );

                if (
                        targetName == null ||
                                targetName.isBlank()
                ) {
                    return ActionResult.CONTINUE;
                }

                targetId =
                        execution.waitSync(() -> {
                            Player target =
                                    Bukkit.getPlayerExact(
                                            targetName
                                    );

                            return target == null
                                    ? null
                                    : target.getUniqueId();
                        });

                if (targetId == null) {
                    return ActionResult.CONTINUE;
                }
            }

            /*
             * We're already on an Action virtual thread,
             * so waiting is cheap and preserves ActionPlan
             * ordering:
             *
             * <menu>selector
             * &&
             * <message>opened
             *
             * message executes after menu processing.
             */

            MenuService menuService = Implements.fetch(MenuService.class);

            menuService.open(
                    menuId.strip(),
                    targetId
            ).join();

            return ActionResult.CONTINUE;
        };
    }

    private String firstNonBlank(
            String first,
            String second
    ) {
        if (
                first != null &&
                        !first.isBlank()
        ) {
            return first;
        }

        if (
                second != null &&
                        !second.isBlank()
        ) {
            return second;
        }

        return null;
    }
}