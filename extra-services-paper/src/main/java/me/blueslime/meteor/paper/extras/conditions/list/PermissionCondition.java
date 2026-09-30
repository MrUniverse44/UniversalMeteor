package me.blueslime.meteor.paper.extras.conditions.list;

import me.blueslime.meteor.paper.extras.conditions.api.Condition;
import me.blueslime.meteor.paper.extras.conditions.api.ConditionInstruction;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionCompileContext;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionNode;
import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import java.util.Set;

public final class PermissionCondition
        implements Condition {

    private final ExecutionTextResolver textResolver;

    public PermissionCondition(
            ExecutionTextResolver textResolver
    ) {
        this.textResolver =
                textResolver;
    }

    @Override
    public String id() {
        return "permission";
    }

    @Override
    public Set<String> aliases() {
        return Set.of(
                "perm"
        );
    }

    @Override
    public Set<ContextKey<?>> requirements(
            ConditionNode node
    ) {
        return Set.of(
                ContextKeys.PLAYER_ID
        );
    }

    @Override
    public ConditionInstruction compile(
            ConditionNode node,
            ConditionCompileContext context
    ) {
        String permission =
                firstNonBlank(
                        node.attribute(
                                "permission"
                        ),
                        node.attribute(
                                "value"
                        )
                );

        if (permission == null) {
            permission =
                    node.payload()
                            .strip();
        }

        if (permission.isBlank()) {
            throw new ConditionCompileException(
                    "Permission condition requires a permission"
            );
        }

        final String template =
                permission;

        return execution -> {
            String processed =
                    textResolver
                            .resolveContext(
                                    execution,
                                    template
                            );

            Boolean result =
                    execution.waitSyncPlayer(
                            player ->
                                    player.hasPermission(
                                            processed
                                    )
                    );

            return Boolean.TRUE.equals(
                    result
            );
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