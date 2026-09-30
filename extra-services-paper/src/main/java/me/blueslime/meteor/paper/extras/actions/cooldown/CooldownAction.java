package me.blueslime.meteor.paper.extras.actions.cooldown;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.actions.exception.ActionCompileException;
import me.blueslime.meteor.paper.extras.actions.exception.ActionExecutionException;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.context.LocationSnapshot;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CooldownAction
        implements Action {

    private static final Pattern DURATION_PATTERN =
            Pattern.compile(
                    "^([0-9]+(?:\\.[0-9]+)?)(ms|s|m|h|d)?$",
                    Pattern.CASE_INSENSITIVE
            );

    private final CooldownService cooldowns;

    public CooldownAction(
            CooldownService cooldowns
    ) {
        this.cooldowns = cooldowns;
    }

    @Override
    public String id() {
        return "cooldown";
    }

    @Override
    public Set<ContextKey<?>> requirements(
            ActionNode node
    ) {
        String scope =
                node.attribute(
                        "scope",
                        "player"
                ).toLowerCase(Locale.ROOT);

        return switch (scope) {
            case "player" ->
                    Set.of(
                            ContextKeys.PLAYER_ID
                    );

            case "location" ->
                    Set.of(
                            ContextKeys.LOCATION
                    );

            default ->
                    Set.of();
        };
    }

    @Override
    public ActionInstruction compile(
            ActionNode node,
            ActionCompileContext context
    ) {
        String rawDuration =
                node.attribute("duration");

        if (
                rawDuration == null ||
                        rawDuration.isBlank()
        ) {
            rawDuration =
                    node.payload().trim();
        }

        if (rawDuration.isBlank()) {
            rawDuration = "3s";
        }

        Duration duration =
                parseDuration(rawDuration);

        String key =
                node.attribute(
                        "key",
                        "default"
                );

        String scope =
                node.attribute(
                        "scope",
                        "player"
                );

        String onCooldownRaw =
                node.attribute(
                        "on-cooldown",
                        ""
                );

        ActionPlan onCooldown =
                onCooldownRaw.isBlank()
                        ? ActionPlan.EMPTY
                        : context.compileInline(
                        onCooldownRaw
                );

        return executionContext -> {
            String scopeValue =
                    resolveScope(
                            executionContext,
                            scope
                    );

            CooldownService.CooldownCheck check =
                    cooldowns.acquire(
                            scopeValue,
                            key,
                            duration
                    );

            if (check.acquired()) {
                return ActionResult.CONTINUE;
            }

            long milliseconds =
                    check.remaining()
                            .toMillis();

            long seconds =
                    Math.max(
                            1L,
                            (milliseconds + 999L)
                                    / 1000L
                    );

            executionContext.variables()
                    .set(
                            "seconds",
                            seconds
                    );

            executionContext.variables()
                    .set(
                            "cooldown_seconds",
                            seconds
                    );

            executionContext.variables()
                    .set(
                            "cooldown_millis",
                            milliseconds
                    );

            if (!onCooldown.isEmpty()) {
                onCooldown.execute(
                        executionContext
                );
            }

            return ActionResult.STOP;
        };
    }

    private String resolveScope(
            ExecutionContext context,
            String scopeRaw
    ) {
        String scope =
                scopeRaw.trim()
                        .toLowerCase(Locale.ROOT);

        if (scope.equals("global")) {
            return "global";
        }

        if (scope.equals("player")) {
            UUID playerId =
                    context.require(
                            ContextKeys.PLAYER_ID
                    );

            return "player:"
                    + playerId;
        }

        if (scope.equals("location")) {
            LocationSnapshot location =
                    context.require(
                            ContextKeys.LOCATION
                    );

            return "location:"
                    + location.worldId()
                    + ":"
                    + location.blockX()
                    + ":"
                    + location.blockY()
                    + ":"
                    + location.blockZ();
        }

        if (
                scope.startsWith("context:")
        ) {
            String key =
                    scopeRaw.substring(
                            "context:".length()
                    );

            Object value =
                    context.values()
                            .findById(key)
                            .orElseThrow(
                                    () ->
                                            new ActionExecutionException(
                                                    "Cooldown scope context key '"
                                                            + key
                                                            + "' was not found"
                                            )
                            );

            return "context:"
                    + key
                    + ":"
                    + value;
        }

        return "custom:" + scope;
    }

    private Duration parseDuration(
            String input
    ) {
        String normalized =
                input.trim()
                        .toLowerCase(Locale.ROOT);

        if (
                normalized.startsWith("p")
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
                DURATION_PATTERN.matcher(
                        normalized
                );

        if (!matcher.matches()) {
            throw new ActionCompileException(
                    "Invalid duration '"
                            + input
                            + "'"
            );
        }

        double value =
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
                            value * 1_000_000D;

                    case "s" ->
                            value * 1_000_000_000D;

                    case "m" ->
                            value * 60D
                                    * 1_000_000_000D;

                    case "h" ->
                            value * 3600D
                                    * 1_000_000_000D;

                    case "d" ->
                            value * 86400D
                                    * 1_000_000_000D;

                    default ->
                            throw new ActionCompileException(
                                    "Invalid duration unit '"
                                            + unit
                                            + "'"
                            );
                };

        return Duration.ofNanos(
                (long) nanos
        );
    }
}
