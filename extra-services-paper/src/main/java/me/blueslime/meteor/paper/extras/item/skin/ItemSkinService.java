package me.blueslime.meteor.paper.extras.item.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.Bukkit;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.nio.charset.StandardCharsets;

import java.util.Locale;
import java.util.UUID;

public final class ItemSkinService implements PlatformService {

    public void applyTexture(ItemStack head, String value) {
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

        applyBase64(
                head,
                cleanValue
        );
    }

    private void applyUsername(
            ItemStack item,
            String username
    ) {
        item.editMeta(
                SkullMeta.class,
                meta -> {
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
                    /*
                     * Deterministic UUID:
                     *
                     * identical texture
                     *      ↓
                     * identical profile UUID
                     */
                    UUID profileId =
                            UUID.nameUUIDFromBytes(
                                    (
                                            "meteor-texture:"
                                                    + base64
                                    ).getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            );

                    PlayerProfile profile =
                            Bukkit.createProfile(
                                    profileId
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

    private boolean isMinecraftUsername(
            String value
    ) {
        return value.matches(
                "^[a-zA-Z0-9_]{3,16}$"
        );
    }
}