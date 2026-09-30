package me.blueslime.meteor.paper.extras.runtime.text;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Objects;

public final class ExecutionTextResolver {

    private final JavaPlugin plugin;

    public ExecutionTextResolver(
            JavaPlugin plugin
    ) {
        this.plugin =
                Objects.requireNonNull(
                        plugin,
                        "plugin"
                );
    }

    /**
     * Thread-safe Meteor-owned replacements.
     *
     * This method intentionally does NOT execute
     * PlaceholderAPI.
     */
    public String resolveContext(
            ExecutionContext context,
            String input
    ) {
        if (input == null) {
            return "";
        }

        String result =
                input;

        for (
                Map.Entry<String, Object> entry :
                context
                        .variables()
                        .snapshot()
                        .entrySet()
        ) {
            String key =
                    entry.getKey();

            String value =
                    String.valueOf(
                            entry.getValue()
                    );

            if (
                    key.startsWith("<") ||
                            key.startsWith("{") ||
                            key.startsWith("%")
            ) {
                result =
                        result.replace(
                                key,
                                value
                        );

                continue;
            }

            result =
                    result
                            .replace(
                                    "<" + key + ">",
                                    value
                            )
                            .replace(
                                    "{" + key + "}",
                                    value
                            );
        }

        String playerName =
                context.get(
                        ContextKeys.PLAYER_NAME
                );

        if (playerName != null) {
            result =
                    result
                            .replace(
                                    "<player>",
                                    playerName
                            )
                            .replace(
                                    "<player_name>",
                                    playerName
                            );
        }

        return normalizeNewLines(
                result
        );
    }

    /**
     * Must be called from a safe Bukkit/Paper
     * context if PlaceholderAPI may be involved.
     */
    public String resolvePlaceholders(
            Player player,
            String input
    ) {
        if (input == null) {
            return "";
        }

        String result =
                input;

        if (player == null) {
            return normalizeNewLines(
                    result
            );
        }

        result =
                result
                        .replace(
                                "<player>",
                                player.getName()
                        )
                        .replace(
                                "<player_name>",
                                player.getName()
                        );

        if (isPlaceholderApiEnabled()) {
            result =
                    PlaceholderAPI.setPlaceholders(
                            player,
                            result
                    );
        }

        return normalizeNewLines(
                result
        );
    }

    public boolean isPlaceholderApiEnabled() {
        return plugin
                .getServer()
                .getPluginManager()
                .isPluginEnabled(
                        "PlaceholderAPI"
                );
    }

    private String normalizeNewLines(
            String input
    ) {
        return input.replace(
                "\\n",
                "\n"
        );
    }
}