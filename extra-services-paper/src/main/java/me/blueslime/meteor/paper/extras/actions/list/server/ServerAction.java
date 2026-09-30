package me.blueslime.meteor.paper.extras.actions.list.server;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;

import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;

import me.blueslime.meteor.paper.extras.actions.exception.ActionCompileException;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerAction
        implements Action {

    private static final Pattern DURATION =
            Pattern.compile(
                    "^([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h|d)?$",
                    Pattern.CASE_INSENSITIVE
            );

    private final ServerTransferService transfers;
    private final ExecutionTextResolver textResolver;

    public ServerAction(
            ServerTransferService transfers,
            ExecutionTextResolver textResolver
    ) {
        this.transfers =
                transfers;

        this.textResolver =
                textResolver;
    }

    @Override
    public String id() {
        return "server";
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
            ActionCompileContext compileContext
    ) {
        String destinationTemplate =
                firstNonBlank(
                        node.attribute("to"),
                        node.attribute("server")
                );

        if (destinationTemplate == null) {
            destinationTemplate =
                    node.payload().strip();
        }

        if (destinationTemplate.isBlank()) {
            throw new ActionCompileException(
                    "Server action requires a destination"
            );
        }

        final String destination =
                destinationTemplate;

        int max =
                node.intAttribute(
                        "max",
                        Integer.MAX_VALUE
                );

        if (max <= 0) {
            throw new ActionCompileException(
                    "Server action max must be > 0"
            );
        }

        Duration resetAt =
                parseDuration(
                        node.attribute(
                                "resetAt",
                                "1s"
                        )
                );

        Duration retryAt =
                parseDuration(
                        node.attribute(
                                "retryAt",
                                node.attribute(
                                        "resetAt",
                                        "1s"
                                )
                        )
                );

        Duration pendingAt =
                parseDuration(
                        node.attribute(
                                "pendingAt",
                                "1s"
                        )
                );

        ServerTransferPolicy policy =
                new ServerTransferPolicy(
                        max,
                        resetAt,
                        retryAt,
                        pendingAt
                );

        ActionPlan onJoin =
                compileNested(
                        compileContext,
                        node.attribute(
                                "onJoin"
                        )
                );

        ActionPlan onPending =
                compileNested(
                        compileContext,
                        node.attribute(
                                "onPending"
                        )
                );

        ActionPlan onQuit =
                compileNested(
                        compileContext,
                        node.attribute(
                                "onQuit"
                        )
                );

        ActionPlan onTransfer =
                compileNested(
                        compileContext,
                        node.attribute(
                                "onTransfer"
                        )
                );

        ServerTransferLifecycle lifecycle =
                new ServerTransferLifecycle(
                        onJoin,
                        onPending,
                        onQuit,
                        onTransfer
                );

        return context -> {
            String processedDestination =
                    textResolver.resolveContext(
                            context,
                            destination
                    );

            /*
             * We resolve PAPI on Bukkit thread because
             * third-party expansions cannot universally
             * be assumed thread-safe.
             *
             * At the same time this verifies that the
             * player is currently online.
             */
            String resolvedDestination =
                    context.waitSyncPlayer(
                            player ->
                                    textResolver.resolvePlaceholders(
                                            player,
                                            processedDestination
                                    )
                    );

            if (
                    resolvedDestination == null ||
                            resolvedDestination.isBlank()
            ) {
                return ActionResult.CONTINUE;
            }

            resolvedDestination =
                    resolvedDestination.strip();

            UUID playerId =
                    context.require(
                            ContextKeys.PLAYER_ID
                    );

            String playerName =
                    context.get(
                            ContextKeys.PLAYER_NAME
                    );

            ServerTransferRequest request =
                    new ServerTransferRequest(
                            playerId,
                            playerName,
                            resolvedDestination,
                            policy,
                            lifecycle,
                            context.snapshot()
                    );

            transfers.enqueueOrToggle(
                    request
            );

            return ActionResult.CONTINUE;
        };
    }

    private ActionPlan compileNested(
            ActionCompileContext context,
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return ActionPlan.EMPTY;
        }

        return context.compileInline(
                input
        );
    }

    private Duration parseDuration(
            String input
    ) {
        String value =
                input
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        /*
         * Allow java.time format:
         *
         * PT1S
         * PT500MS
         * PT2M
         */
        if (
                value.startsWith("p")
        ) {
            try {
                return Duration.parse(
                        input.toUpperCase(
                                Locale.ROOT
                        )
                );

            } catch (
                    Exception exception
            ) {
                throw new ActionCompileException(
                        "Invalid duration '"
                                + input
                                + "'",
                        exception
                );
            }
        }

        Matcher matcher =
                DURATION.matcher(
                        value
                );

        if (!matcher.matches()) {
            throw new ActionCompileException(
                    "Invalid duration '"
                            + input
                            + "'"
            );
        }

        double amount =
                Double.parseDouble(
                        matcher.group(1)
                );

        String unit =
                matcher.group(2);

        if (unit == null) {
            unit = "s";
        }

        double nanos =
                switch (unit) {
                    case "ms" ->
                            amount
                                    * 1_000_000D;

                    case "s" ->
                            amount
                                    * 1_000_000_000D;

                    case "m" ->
                            amount
                                    * 60D
                                    * 1_000_000_000D;

                    case "h" ->
                            amount
                                    * 3_600D
                                    * 1_000_000_000D;

                    case "d" ->
                            amount
                                    * 86_400D
                                    * 1_000_000_000D;

                    default ->
                            throw new ActionCompileException(
                                    "Unsupported duration unit '"
                                            + unit
                                            + "'"
                            );
                };

        if (nanos <= 0D) {
            throw new ActionCompileException(
                    "Duration must be > 0: "
                            + input
            );
        }

        return Duration.ofNanos(
                (long) nanos
        );
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
