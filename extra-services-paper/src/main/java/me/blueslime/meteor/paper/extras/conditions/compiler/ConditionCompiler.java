package me.blueslime.meteor.paper.extras.conditions.compiler;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.conditions.api.Condition;
import me.blueslime.meteor.paper.extras.conditions.api.ConditionInstruction;
import me.blueslime.meteor.paper.extras.conditions.exception.ConditionCompileException;
import me.blueslime.meteor.paper.extras.conditions.registry.ConditionRegistry;
import me.blueslime.meteor.paper.extras.conditions.runtime.CompiledCondition;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class ConditionCompiler {

    private final ConditionRegistry registry;

    private final ConditionParser parser;

    public ConditionCompiler(
            ConditionRegistry registry,
            ConditionParser parser
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
    }

    public ConditionPlan compile(
            Collection<String> source
    ) {
        return compile(
                source,
                ConditionMode.ALL
        );
    }

    public ConditionPlan compile(
            Collection<String> source,
            ConditionMode mode
    ) {
        if (
                source == null ||
                        source.isEmpty()
        ) {
            return ConditionPlan.EMPTY;
        }

        List<CompiledCondition> compiled =
                new ArrayList<>();

        for (String raw : source) {
            if (
                    raw == null ||
                            raw.isBlank()
            ) {
                continue;
            }

            compiled.add(
                    compileNode(
                            parser.parse(raw)
                    )
            );
        }

        if (compiled.isEmpty()) {
            return ConditionPlan.EMPTY;
        }

        return new ConditionPlan(
                mode,
                compiled
        );
    }

    public ConditionPlan compile(
            String source
    ) {
        if (
                source == null ||
                        source.isBlank()
        ) {
            return ConditionPlan.EMPTY;
        }

        return new ConditionPlan(
                ConditionMode.ALL,
                List.of(
                        compileNode(
                                parser.parse(
                                        source
                                )
                        )
                )
        );
    }

    private CompiledCondition compileNode(
            ConditionNode node
    ) {
        Condition condition =
                registry
                        .find(
                                node.type()
                        )
                        .orElseThrow(
                                () ->
                                        new ConditionCompileException(
                                                "Unknown condition '"
                                                        + node.type()
                                                        + "' in: "
                                                        + node.raw()
                                        )
                        );

        try {
            ConditionInstruction instruction =
                    condition.compile(
                            node,
                            new ConditionCompileContext(
                                    this
                            )
                    );

            Objects.requireNonNull(
                    instruction,
                    "Compiled ConditionInstruction"
            );

            if (node.negated()) {
                ConditionInstruction original =
                        instruction;

                instruction =
                        context ->
                                !original.test(
                                        context
                                );
            }

            Set<
                    ContextKey<?>
                    > requirements =
                    condition.requirements(
                            node
                    );

            return new CompiledCondition(
                    condition.id(),
                    node.raw(),
                    requirements,
                    instruction
            );

        } catch (
                ConditionCompileException exception
        ) {
            throw exception;

        } catch (Throwable throwable) {
            throw new ConditionCompileException(
                    "Unable to compile condition '"
                            + node.raw()
                            + "'",
                    throwable
            );
        }
    }
}