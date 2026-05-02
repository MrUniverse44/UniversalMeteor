package me.blueslime.meteor.paper.extras.services.item.skin;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class ItemSkinService implements PlatformService {

    public void applyTexture(final ItemStack head, String value) {
        String cleanValue = value.replaceAll("(?i)^(textures?[:;]\\s*|skin[:;]\\s*|player[:;]\\s*)", "");

        if (cleanValue.isBlank()) {
            return;
        }

        if (cleanValue.matches("^[a-zA-Z0-9_]{3,16}$")) {
            head.editMeta(SkullMeta.class, meta -> {
                PlayerProfile profile = Bukkit.createProfile(cleanValue);
                meta.setPlayerProfile(profile);
            });
            return;
        }

        applyBase64ToItem(head, cleanValue);
    }

    private void applyBase64ToItem(final ItemStack item, String base64) {
        item.editMeta(SkullMeta.class, meta -> {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", base64));
            meta.setPlayerProfile(profile);
        });
    }
}

