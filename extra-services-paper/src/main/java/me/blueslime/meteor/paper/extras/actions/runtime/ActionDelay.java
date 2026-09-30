package me.blueslime.meteor.paper.extras.actions.runtime;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import java.time.Duration;

public final class ActionDelay {

    private ActionDelay() {}

    public static void await(
            ExecutionContext context,
            Duration duration
    ) throws InterruptedException {
        if (
                duration == null ||
                        duration.isZero()
        ) {
            return;
        }

        if (duration.isNegative()) {
            throw new IllegalArgumentException(
                    "Action delay cannot be negative"
            );
        }

        context
                .cancellation()
                .throwIfCancelled();

        try {
            Thread.sleep(
                    duration
            );

        } catch (
                InterruptedException exception
        ) {
            Thread
                    .currentThread()
                    .interrupt();

            /*
             * If ActionDispatcher cancelled us,
             * this should turn into ActionCancelledException.
             */
            context
                    .cancellation()
                    .throwIfCancelled();

            throw exception;
        }

        context
                .cancellation()
                .throwIfCancelled();
    }
}