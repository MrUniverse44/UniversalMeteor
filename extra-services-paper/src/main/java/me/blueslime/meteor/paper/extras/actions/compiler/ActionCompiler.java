package me.blueslime.meteor.paper.extras.actions.compiler;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.exception.ActionCompileException;
import me.blueslime.meteor.paper.extras.actions.registry.ActionRegistry;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.actions.runtime.CompiledAction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public final class ActionCompiler {

    private final ActionRegistry registry;
    private final ActionParser parser;

    public ActionCompiler(
            ActionRegistry registry,
            ActionParser parser
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
                new ArrayList<>();

        for (String raw : source) {
            if (raw == null || raw.isBlank()) {
                continue;
            }

            compiled.add(
                    compile(
                            parser.parse(raw)
                    )
            );
        }

        if (compiled.isEmpty()) {
            return ActionPlan.EMPTY;
        }

        return new ActionPlan(compiled);
    }

    public ActionPlan compile(String raw) {
        if (raw == null || raw.isBlank()) {
            return ActionPlan.EMPTY;
        }

        return new ActionPlan(
                List.of(
                        compile(
                                parser.parse(raw)
                        )
                )
        );
    }

    public ActionPlan compileInline(
            String input
    ) {
        List<String> source =
                parser.splitInline(input);

        return compile(source);
    }

    private CompiledAction compile(
            ActionNode node
    ) {
        Action action =
                registry.find(node.type())
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
                    action.compile(
                            node,
                            new ActionCompileContext(this)
                    );

            Objects.requireNonNull(
                    instruction,
                    "Compiled ActionInstruction"
            );

            return new CompiledAction(
                    action.id(),
                    action.requirements(node),
                    instruction
            );

        } catch (ActionCompileException exception) {
            throw exception;

        } catch (Throwable throwable) {
            throw new ActionCompileException(
                    "Unable to compile action '"
                            + node.raw()
                            + "'",
                    throwable
            );
        }
    }
}
