package me.blueslime.meteor.paper.extras.item.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;

import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

public final class ItemSkinService
        implements PlatformService {

    public void applyTexture(
            ItemStack head,
            String value
    ) {
        if (
                head == null ||
                        value == null ||
                        value.isBlank()
        ) {
            return;
        }

        String cleanValue =
                clean(
                        value
                );

        if (cleanValue.isBlank()) {
            return;
        }

        /*
         * Runtime templates must have been resolved
         * by ItemRenderer BEFORE reaching this service.
         *
         * Never interpret unresolved placeholders
         * as Base64 textures.
         */
        if (
                containsUnresolvedRuntimeValue(
                        cleanValue
                )
        ) {
            getLogger().warn(
                    "Unable to apply player head skin because "
                            + "the value still contains unresolved runtime data: '"
                            + cleanValue
                            + "'"
            );

            return;
        }

        if (
                isMinecraftUsername(
                        cleanValue
                )
        ) {
            applyUsername(
                    head,
                    cleanValue
            );

            return;
        }

        if (
                isValidTextureBase64(
                        cleanValue
                )
        ) {
            applyBase64(
                    head,
                    cleanValue
            );

            return;
        }

        getLogger().warn(
                "Unable to apply player head skin: value is neither "
                        + "a valid Minecraft username nor a valid texture Base64."
        );
    }

    private void applyUsername(
            ItemStack item,
            String username
    ) {
        item.editMeta(
                SkullMeta.class,
                meta -> {
                    Player online =
                            Bukkit.getPlayerExact(
                                    username
                            );

                    if (online != null) {
                        PlayerProfile profile =
                                online.getPlayerProfile();

                        if (profile.hasTextures()) {
                            meta.setPlayerProfile(
                                    profile
                            );

                            return;
                        }

                        getLogger().warn(
                                "Unable to apply player head skin for '"
                                        + username
                                        + "': online profile contains no textures."
                        );

                        return;
                    }

                    /*
                     * Offline usernames may require an external
                     * Mojang lookup. This path should therefore
                     * not normally be used for %player_name%.
                     */
                    PlayerProfile profile =
                            Bukkit.createProfile(
                                    username
                            );

                    meta.setPlayerProfile(
                            profile
                    );
                }
        );
    }

    private void applyBase64(
            ItemStack item,
            String base64
    ) {
        item.editMeta(
                SkullMeta.class,
                meta -> {
                    UUID profileId =
                            UUID.nameUUIDFromBytes(
                                    (
                                            "meteor-texture:"
                                                    + base64
                                    ).getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

                    /*
                     * Paper considers a profile complete when
                     * UUID + name + textures are available.
                     *
                     * Give custom texture profiles a deterministic
                     * synthetic name so Paper has no reason to
                     * complete the profile through Mojang.
                     *
                     * Minecraft usernames have a maximum length
                     * of 16 characters:
                     *
                     * meteor_ + 8 chars = 15
                     */
                    String profileName =
                            "meteor_"
                                    + profileId
                                    .toString()
                                    .replace(
                                            "-",
                                            ""
                                    )
                                    .substring(
                                            0,
                                            8
                                    );

                    PlayerProfile profile =
                            Bukkit.createProfileExact(
                                    profileId,
                                    profileName
                            );

                    profile.setProperty(
                            new ProfileProperty(
                                    "textures",
                                    base64
                            )
                    );

                    meta.setPlayerProfile(
                            profile
                    );
                }
        );
    }

    private String clean(
            String value
    ) {
        String result =
                value.strip();

        String lower =
                result.toLowerCase(
                        Locale.ROOT
                );

        String[] prefixes = {
                "textures:",
                "textures;",
                "texture:",
                "texture;",
                "skin:",
                "skin;",
                "player:",
                "player;"
        };

        for (String prefix : prefixes) {
            if (
                    lower.startsWith(
                            prefix
                    )
            ) {
                return result
                        .substring(
                                prefix.length()
                        )
                        .strip();
            }
        }

        return result;
    }

    private boolean containsUnresolvedRuntimeValue(
            String value
    ) {
        /*
         * PlaceholderAPI:
         *
         * %player_name%
         */
        if (
                value.indexOf('%') >= 0
        ) {
            return true;
        }

        /*
         * ExecutionScope:
         *
         * ${texture}
         */
        if (
                value.contains("${")
        ) {
            return true;
        }

        /*
         * Meteor context values:
         *
         * <player>
         */
        return value.indexOf('<') >= 0
                &&
                value.indexOf('>') >= 0;
    }

    private boolean isMinecraftUsername(
            String value
    ) {
        return value.matches(
                "^[a-zA-Z0-9_]{3,16}$"
        );
    }

    private boolean isValidTextureBase64(
            String value
    ) {
        /*
         * Texture payloads are substantially longer
         * than Minecraft usernames.
         *
         * This also avoids interpreting arbitrary short
         * strings as textures.
         */
        if (value.length() < 32) {
            return false;
        }

        try {
            byte[] decoded =
                    Base64
                            .getDecoder()
                            .decode(
                                    value
                            );

            String json =
                    new String(
                            decoded,
                            StandardCharsets.UTF_8
                    );

            return json.contains("\"textures\"")
                    &&
                    json.contains("\"SKIN\"")
                    &&
                    json.contains("textures.minecraft.net");

        } catch (
                IllegalArgumentException exception
        ) {
            return false;
        }
    }
}