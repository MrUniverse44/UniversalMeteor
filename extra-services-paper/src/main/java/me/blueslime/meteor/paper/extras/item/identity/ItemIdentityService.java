package me.blueslime.meteor.paper.extras.item.identity;

import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.bukkit.NamespacedKey;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class ItemIdentityService implements PlatformService {

    private final NamespacedKey typeKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey containerKey;
    private final NamespacedKey itemKey;

    public ItemIdentityService(
            JavaPlugin plugin
    ) {

        String name = fetch(Project.class).name();

        this.typeKey =
                new NamespacedKey(
                        plugin,
                        name + "_item_type"
                );

        this.ownerKey =
                new NamespacedKey(
                        plugin,
                        name + "_item_owner"
                );

        this.sessionKey =
                new NamespacedKey(
                        plugin,
                        name + "_item_session"
                );

        this.containerKey =
                new NamespacedKey(
                        plugin,
                        name + "_item_container"
                );

        this.itemKey =
                new NamespacedKey(
                        plugin,
                        name + "_item_id"
                );
    }

    public ItemStack tag(
            ItemStack item,
            ItemIdentity identity
    ) {
        if (item == null) {
            return null;
        }

        item.editMeta(meta -> {
            PersistentDataContainer data =
                    meta.getPersistentDataContainer();

            data.set(
                    typeKey,
                    PersistentDataType.STRING,
                    identity
                            .type()
                            .name()
            );

            data.set(
                    ownerKey,
                    PersistentDataType.STRING,
                    identity
                            .ownerId()
                            .toString()
            );

            data.set(
                    sessionKey,
                    PersistentDataType.STRING,
                    identity
                            .sessionId()
                            .toString()
            );

            data.set(
                    containerKey,
                    PersistentDataType.STRING,
                    identity.containerId()
            );

            data.set(
                    itemKey,
                    PersistentDataType.STRING,
                    identity.itemId()
            );
        });

        return item;
    }

    public Optional<ItemIdentity> read(
            ItemStack item
    ) {
        if (
                item == null ||
                        item.getType().isAir()
        ) {
            return Optional.empty();
        }

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return Optional.empty();
        }

        PersistentDataContainer data =
                meta.getPersistentDataContainer();

        String typeRaw =
                data.get(
                        typeKey,
                        PersistentDataType.STRING
                );

        String ownerRaw =
                data.get(
                        ownerKey,
                        PersistentDataType.STRING
                );

        String sessionRaw =
                data.get(
                        sessionKey,
                        PersistentDataType.STRING
                );

        String container =
                data.get(
                        containerKey,
                        PersistentDataType.STRING
                );

        String itemId =
                data.get(
                        itemKey,
                        PersistentDataType.STRING
                );

        if (
                typeRaw == null ||
                        ownerRaw == null ||
                        sessionRaw == null ||
                        container == null ||
                        itemId == null
        ) {
            return Optional.empty();
        }

        try {
            return Optional.of(
                    new ItemIdentity(
                            ItemIdentityType.valueOf(
                                    typeRaw
                                            .toUpperCase(
                                                    Locale.ROOT
                                            )
                            ),
                            UUID.fromString(
                                    ownerRaw
                            ),
                            UUID.fromString(
                                    sessionRaw
                            ),
                            container,
                            itemId
                    )
            );

        } catch (
                IllegalArgumentException ignored
        ) {
            return Optional.empty();
        }
    }

    public boolean belongsTo(
            ItemStack item,
            UUID owner,
            UUID session
    ) {
        return read(item)
                .map(identity ->
                        identity
                                .ownerId()
                                .equals(owner) &&
                                identity
                                        .sessionId()
                                        .equals(session)
                )
                .orElse(false);
    }
}