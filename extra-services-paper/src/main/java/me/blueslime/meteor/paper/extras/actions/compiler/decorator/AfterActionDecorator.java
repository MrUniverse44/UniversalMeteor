package me.blueslime.meteor.paper.extras.actions.compiler.decorator;

import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionDelay;

import me.blueslime.meteor.paper.extras.runtime.RuntimeValue;
import me.blueslime.meteor.paper.extras.runtime.value.RuntimeValueCompiler;

import java.time.Duration;
import java.util.Objects;

public final class AfterActionDecorator
        implements ActionInstructionDecorator {

    private final RuntimeValueCompiler values;

    public AfterActionDecorator(
            RuntimeValueCompiler values
    ) {
        this.values =
                Objects.requireNonNull(
                        values,
                        "values"
                );
    }

    @Override
    public ActionInstruction decorate(
            ActionNode node,
            ActionInstruction instruction
    ) {
        String raw =
                node.attribute(
                        "after"
                );

        if (
                raw == null ||
                        raw.isBlank()
        ) {
            return instruction;
        }

        /*
         * Static:
         *
         * after="200ms"
         *
         * is parsed ONCE.
         *
         * Dynamic:
         *
         * after="${delay}"
         * after="%some_placeholder%s"
         *
         * only resolves the dynamic portion at runtime.
         */
        RuntimeValue<Duration> delay =
                values.compileDuration(
                        raw
                );

        return context -> {
            ActionResult result =
                    instruction.execute(
                            context
                    );

            /*
             * STOP means there is no following action,
             * therefore waiting has no useful effect.
             */
            if (
                    result ==
                            ActionResult.STOP
            ) {
                return result;
            }

            Duration resolved =
                    delay.resolve(
                            context
                    );

            ActionDelay.await(
                    context,
                    resolved
            );

            return result;
        };
    }
}