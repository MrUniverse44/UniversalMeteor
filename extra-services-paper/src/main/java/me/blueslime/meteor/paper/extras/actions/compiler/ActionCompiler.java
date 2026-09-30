package me.blueslime.meteor.paper.extras.actions.compiler;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;

import me.blueslime.meteor.paper.extras.actions.compiler.decorator.ActionInstructionDecorators;
import me.blueslime.meteor.paper.extras.actions.compiler.decorator.AfterActionDecorator;

import me.blueslime.meteor.paper.extras.actions.exception.ActionCompileException;

import me.blueslime.meteor.paper.extras.actions.registry.ActionRegistry;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.actions.runtime.CompiledAction;

import me.blueslime.meteor.paper.extras.runtime.value.RuntimeValueCompiler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class ActionCompiler {

    private final ActionRegistry registry;

    private final ActionParser parser;

    private final RuntimeValueCompiler values;

    private final ActionCompileContext compileContext;

    private final ActionInstructionDecorators decorators;

    public ActionCompiler(
            ActionRegistry registry,
            ActionParser parser,
            RuntimeValueCompiler values
    ) {
        this.registry =
                Objects.requireNonNull(
                        registry,
                        "registry"
                );

        this.parser =
                Objects.requireNonNull(
                        parser,
                        "parser"
                );

        this.values =
                Objects.requireNonNull(
                        values,
                        "values"
                );

        this.compileContext =
                new ActionCompileContext(
                        this,
                        values
                );

        this.decorators =
                new ActionInstructionDecorators(
                        List.of(
                                new AfterActionDecorator(
                                        values
                                )
                        )
                );
    }

    public ActionPlan compile(
            Collection<String> source
    ) {
        if (
                source == null ||
                        source.isEmpty()
        ) {
            return ActionPlan.EMPTY;
        }

        List<CompiledAction> compiled =
                new ArrayList<>(
                        source.size()
                );

        for (String raw : source) {
            if (
                    raw == null ||
                            raw.isBlank()
            ) {
                continue;
            }

            compiled.add(
                    compileNode(
                            parser.parse(
                                    raw
                            )
                    )
            );
        }

        if (compiled.isEmpty()) {
            return ActionPlan.EMPTY;
        }

        return new ActionPlan(
                compiled
        );
    }

    public ActionPlan compile(
            String raw
    ) {
        if (
                raw == null ||
                        raw.isBlank()
        ) {
            return ActionPlan.EMPTY;
        }

        return new ActionPlan(
                List.of(
                        compileNode(
                                parser.parse(
                                        raw
                                )
                        )
                )
        );
    }

    public ActionPlan compileInline(
            String input
    ) {
        List<String> source =
                parser.splitInline(
                        input
                );

        return compile(
                source
        );
    }

    private CompiledAction compileNode(
            ActionNode node
    ) {
        Action action =
                registry
                        .find(
                                node.type()
                        )
                        .orElseThrow(
                                () ->
                                        new ActionCompileException(
                                                "Unknown action '"
                                                        + node.type()
                                                        + "' in: "
                                                        + node.raw()
                                        )
                        );

        try {
            ActionInstruction instruction =
                    Objects.requireNonNull(
                            action.compile(
                                    node,
                                    compileContext
                            ),
                            "Compiled ActionInstruction"
                    );

            /*
             * Global modifiers such as:
             *
             * after
             * timeout
             * retry
             * ...
             *
             * are added AFTER the action has compiled its
             * own behavior.
             */
            instruction =
                    decorators.decorate(
                            node,
                            instruction
                    );

            return new CompiledAction(
                    action.id(),
                    action.requirements(
                            node
                    ),
                    instruction
            );

        } catch (
                ActionCompileException exception
        ) {
            throw exception;

        } catch (
                RuntimeException exception
        ) {
            throw new ActionCompileException(
                    "Unable to compile action '"
                            + node.raw()
                            + "'",
                    exception
            );
        }
    }

    public RuntimeValueCompiler values() {
        return values;
    }
}