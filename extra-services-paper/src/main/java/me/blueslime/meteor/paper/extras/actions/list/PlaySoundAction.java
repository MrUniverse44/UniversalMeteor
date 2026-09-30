package me.blueslime.meteor.paper.extras.actions.list;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.api.ActionInstruction;
import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompileContext;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionNode;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKey;
import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.actions.exception.ActionCompileException;
import me.blueslime.meteor.paper.extras.actions.exception.ActionExecutionException;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class PlaySoundAction
        implements Action {

    private final Map<String, Sound> soundCache =
            new ConcurrentHashMap<>();

    private final Set<String> missingSounds =
            ConcurrentHashMap.newKeySet();

    private final ExecutionTextResolver textResolver;

    public PlaySoundAction(
            ExecutionTextResolver textResolver
    ) {
        this.textResolver = textResolver;
    }

    @Override
    public String id() {
        return "sound";
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
        SoundSpec spec =
                parseSpec(node);

        boolean dynamic =
                isDynamic(spec.name()) ||
                        isDynamic(spec.volume()) ||
                        isDynamic(spec.pitch());

        /*
         * Static configuration:
         *
         * resolve absolutely everything ONCE.
         */
        if (!dynamic) {
            Sound sound =
                    requireSound(
                            spec.name()
                    );

            float volume =
                    parseFloat(
                            spec.volume(),
                            "volume"
                    );

            float pitch =
                    parseFloat(
                            spec.pitch(),
                            "pitch"
                    );

            return context -> {
                context.syncPlayer(player ->
                        player.playSound(
                                player.getLocation(),
                                sound,
                                volume,
                                pitch
                        )
                );

                return ActionResult.CONTINUE;
            };
        }

        /*
         * Dynamic configuration:
         *
         * Variables can change per execution.
         */
        return context -> {
            String name =
                    textResolver.resolveContext(
                            context,
                            spec.name()
                    );

            String volumeRaw =
                    textResolver.resolveContext(
                            context,
                            spec.volume()
                    );

            String pitchRaw =
                    textResolver.resolveContext(
                            context,
                            spec.pitch()
                    );

            context.syncPlayer(player -> {
                String resolvedName =
                        textResolver.resolvePlaceholders(
                                player,
                                name
                        );

                String resolvedVolume =
                        textResolver.resolvePlaceholders(
                                player,
                                volumeRaw
                        );

                String resolvedPitch =
                        textResolver.resolvePlaceholders(
                                player,
                                pitchRaw
                        );

                Sound sound =
                        resolveSound(
                                resolvedName
                        );

                if (sound == null) {
                    throw new ActionExecutionException(
                            "Sound '"
                                    + resolvedName
                                    + "' does not exist"
                    );
                }

                float volume =
                        parseRuntimeFloat(
                                resolvedVolume,
                                "volume"
                        );

                float pitch =
                        parseRuntimeFloat(
                                resolvedPitch,
                                "pitch"
                        );

                player.playSound(
                        player.getLocation(),
                        sound,
                        volume,
                        pitch
                );
            });

            return ActionResult.CONTINUE;
        };
    }

    private SoundSpec parseSpec(
            ActionNode node
    ) {
        String name =
                firstNonBlank(
                        node.attribute("name"),
                        node.attribute("sound")
                );

        String volume =
                node.attribute(
                        "volume",
                        "1"
                );

        String pitch =
                node.attribute(
                        "pitch",
                        "1"
                );

        /*
         * Modern attributes take priority.
         */
        if (name != null) {
            return new SoundSpec(
                    name,
                    volume,
                    pitch
            );
        }

        /*
         * Legacy:
         *
         * <sound>ENTITY_PLAYER_LEVELUP,1,1
         */
        String payload =
                node.payload().trim();

        if (payload.isEmpty()) {
            throw new ActionCompileException(
                    "Sound action requires a sound name"
            );
        }

        String[] split =
                payload.split(",", 3);

        name =
                split[0].trim();

        if (split.length >= 2) {
            volume =
                    split[1].trim();
        }

        if (split.length >= 3) {
            pitch =
                    split[2].trim();
        }

        return new SoundSpec(
                name,
                volume,
                pitch
        );
    }

    private Sound requireSound(
            String input
    ) {
        Sound sound =
                resolveSound(input);

        if (sound == null) {
            throw new ActionCompileException(
                    "Sound '"
                            + input
                            + "' does not exist"
            );
        }

        return sound;
    }

    private Sound resolveSound(
            String input
    ) {
        if (input == null || input.isBlank()) {
            return null;
        }

        String normalized =
                input.trim()
                        .toLowerCase(
                                Locale.ROOT
                        );

        Sound cached =
                soundCache.get(normalized);

        if (cached != null) {
            return cached;
        }

        /*
         * Modern namespaced key:
         *
         * minecraft:entity.player.levelup
         *
         * or:
         *
         * entity.player.levelup
         */
        String directInput =
                normalized.contains(":")
                        ? normalized
                        : "minecraft:"
                          + normalized;

        NamespacedKey directKey =
                NamespacedKey.fromString(
                        directInput
                );

        if (directKey != null) {
            Sound direct =
                    Registry.SOUND_EVENT.get(
                            directKey
                    );

            if (direct != null) {
                soundCache.put(
                        normalized,
                        direct
                );

                return direct;
            }
        }

        /*
         * Backwards compatibility:
         *
         * ENTITY_PLAYER_LEVELUP
         *
         * maps to:
         *
         * minecraft:entity.player.levelup
         */
        String legacy = input.trim().toUpperCase(Locale.ROOT);

        for (Sound sound :
                Registry.SOUND_EVENT) {

            NamespacedKey key =
                    Registry.SOUND_EVENT
                            .getKey(sound);

            if (key == null) {
                continue;
            }

            String legacyKey =
                    key.getKey()
                            .replace(
                                    '.',
                                    '_'
                            )
                            .toUpperCase(
                                    Locale.ROOT
                            );

            if (
                    legacyKey.equals(legacy) ||
                            (
                                    key.getNamespace()
                                            .equalsIgnoreCase(
                                                    "minecraft"
                                            ) &&
                                            (
                                                    "MINECRAFT:"
                                                            + legacyKey
                                            ).equals(legacy)
                            )
            ) {
                soundCache.put(
                        normalized,
                        sound
                );

                return sound;
            }
        }

        missingSounds.add(normalized);

        return null;
    }

    private float parseFloat(
            String input,
            String field
    ) {
        try {
            return Float.parseFloat(
                    input
            );

        } catch (
                NumberFormatException exception
        ) {
            throw new ActionCompileException(
                    "Invalid sound "
                            + field
                            + " '"
                            + input
                            + "'",
                    exception
            );
        }
    }

    private float parseRuntimeFloat(
            String input,
            String field
    ) {
        try {
            return Float.parseFloat(
                    input
            );

        } catch (
                NumberFormatException exception
        ) {
            throw new ActionExecutionException(
                    "Invalid dynamic sound "
                            + field
                            + " '"
                            + input
                            + "'",
                    exception
            );
        }
    }

    private boolean isDynamic(
            String input
    ) {
        if (input == null) {
            return false;
        }

        return input.contains("<") ||
                input.contains("%") ||
                input.contains("{");
    }

    private String firstNonBlank(String first, String second) {
        if (
                first != null &&
                        !first.isBlank()
        ) {
            return first;
        }

        if (second != null && !second.isBlank()) {
            return second;
        }

        return null;
    }

    private record SoundSpec(
            String name,
            String volume,
            String pitch
    ) {}
}