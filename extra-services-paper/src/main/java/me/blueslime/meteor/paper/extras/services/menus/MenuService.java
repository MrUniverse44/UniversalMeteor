package me.blueslime.meteor.paper.extras.services.menus;

import me.blueslime.meteor.paper.extras.services.actions.ActionService;
import me.blueslime.meteor.paper.extras.services.item.inventory.InventoryService;
import me.blueslime.meteor.platforms.api.configuration.PlatformConfigurations;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import me.blueslime.meteor.utilities.text.TextReplacer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class MenuService implements PlatformService {

    private static final Map<String, ConfigurationHandle> menus = new ConcurrentHashMap<>();
    private static final AtomicBoolean REGISTERED = new AtomicBoolean(false);
    private final String pluginName;

    public MenuService() {
        pluginName = getPluginData().getPluginName().toLowerCase(Locale.ROOT);
    }

    @Override
    public void initialize() {
        if (!REGISTERED.getAndSet(true)) {
            getEvents().registerListener(
                new InventoryListener(pluginName)
            );
        }

        File folder = getFileOfDirectory("menus");

        if (!folder.exists() && !folder.mkdirs()) {
            return;
        }

        PlatformConfigurations configurations = getPlugin().getConfigurationProvider();

        File[] files =  folder.listFiles(((dir, name) ->  name.endsWith(".yml")));

        if (files == null) {
            return;
        }

        for (File file : files) {
            String id = file.getName().toLowerCase(Locale.ENGLISH).replace(".yml", "");
            menus.put(id, configurations.load(file));
            getLogger().info("New menu registered: " + id);
        }
    }

    @Override
    public void shutdown() {
        menus.clear();
    }

    @Override
    public void reload() {
        shutdown();
        initialize();
    }

    public ConfigurationHandle find(String id) {
        if (id == null) {
            return null;
        }
        return menus.get(id.toLowerCase(Locale.ENGLISH).replace(".yml", ""));
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    public static final class InventoryListener implements Listener, PlatformService {

        private final String pluginName;

        public InventoryListener(String pluginName) {
            this.pluginName = pluginName;
        }

        private <T> boolean has(ItemStack item, String prefix, PersistentDataType<?, T> type, Class<T> typeClass, Consumer<T> id) {
            if (item == null) return false;
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) return false;
            NamespacedKey namespacedKey = new NamespacedKey(getPlugin().to(JavaPlugin.class), prefix);
            if (meta.getPersistentDataContainer().has(namespacedKey)) {
                T result = meta.getPersistentDataContainer().get(namespacedKey, type);
                if (result != null) {
                    id.accept(result);
                    return true;
                }
            }
            return false;
        }

        private void hasString(ItemStack item, String prefix, PersistentDataType<?, String> type, Consumer<String> id) {
            if (item == null) return;
            final ItemMeta meta = item.getItemMeta();
            if (meta == null) return;
            NamespacedKey namespacedKey = new NamespacedKey(getPlugin().to(JavaPlugin.class), prefix);
            if (meta.getPersistentDataContainer().has(namespacedKey)) {
                String result = meta.getPersistentDataContainer().get(namespacedKey, type);
                if (result != null) {
                    id.accept(result);
                }
            }
        }

        private <T> boolean has(ItemStack item, PersistentDataType<?, T> type, Class<T> typeClass, String prefix) {
            return has(item, prefix, type, typeClass, unknown -> {});
        }

        private <T> boolean has(ItemStack item, String prefix) {
            return has(item, prefix, PersistentDataType.STRING, String.class, string -> {});
        }

        @EventHandler
        public void onInventoryClick(InventoryClickEvent e) {
            if (e.getInventory().getHolder() instanceof LatamInv inv && e.getClickedInventory() != null) {

                boolean wasCancelled = e.isCancelled();
                e.setCancelled(true);

                inv.handleClick(e);

                // This prevents uncanceling the event if another plugin canceled it before
                if (!wasCancelled && !e.isCancelled()) {
                    e.setCancelled(false);
                }
            }

            if (e.getCurrentItem() != null) {
                ItemStack current = e.getCurrentItem();
                has(current, "latam_item_movement", PersistentDataType.BOOLEAN, Boolean.class, e::setCancelled);

                hasString(
                    current,
                    "latam_item_locale",
                    PersistentDataType.STRING,
                    localeId -> hasString(
                        current,
                        "latam_item_inventory",
                        PersistentDataType.STRING,
                        inventoryId -> hasString(
                            current,
                            "latam_item_id",
                            PersistentDataType.STRING,
                            itemId -> {
                                List<String> actions = fetch(InventoryService.class).getActionsOf(localeId, inventoryId, itemId);

                                ActionService actionService = fetch(ActionService.class);
                                if (actions.isEmpty()) {
                                    return;
                                }

                                actionService.execute(
                                    actions,
                                    (Player) e.getWhoClicked(),
                                    TextReplacer.builder().replace("<player>", e.getWhoClicked().getName())
                                );
                            }
                        )
                    )
                );
            }
        }

        @EventHandler
        public void onInventoryDrag(InventoryDragEvent e) {
            if (e.getInventory().getHolder() instanceof LatamInv inv) {

                boolean wasCancelled = e.isCancelled();
                e.setCancelled(true);

                inv.handleDrag(e);

                // This prevents uncanceling the event if another plugin canceled it before
                if (!wasCancelled && !e.isCancelled()) {
                    e.setCancelled(false);
                }
            }
            if (e.getCursor() != null) {
                ItemStack current = e.getCursor();
                has(current, "latam_item_drag", PersistentDataType.BOOLEAN, Boolean.class, e::setCancelled);
            }
        }

        @EventHandler
        public void onInventoryOpen(InventoryOpenEvent e) {
            if (e.getInventory().getHolder() instanceof LatamInv inv) {

                inv.handleOpen(e);
            }
        }

        @EventHandler
        public void onDrop(PlayerDropItemEvent e) {
            ItemStack current = e.getItemDrop().getItemStack();
            has(current, "latam_item_drop", PersistentDataType.BOOLEAN, Boolean.class, e::setCancelled);
        }

        @SuppressWarnings("deprecation")
        @EventHandler
        public void onClick(PlayerInteractEvent event) {
            if (event.getAction().equals(Action.PHYSICAL)) {
                return;
            }

            ItemStack item = event.getPlayer().getItemInHand();

            has(item, "latam_item_movement", PersistentDataType.BOOLEAN, Boolean.class, event::setCancelled);

            if (event.getAction().equals(Action.PHYSICAL)) {
                return;
            }

            hasString(item, pluginName + "_item", PersistentDataType.STRING, string -> {
                List<String> actions = fetch(InventoryService.class).getExternalItems().get(string);

                ActionService actionService = fetch(ActionService.class);
                if (actions.isEmpty()) {
                    return;
                }

                actionService.execute(
                        actions,
                        event.getPlayer(),
                        TextReplacer.builder().replace("<player>", event.getPlayer().getName())
                );
            });
            hasString(
            item,
            "latam_item_locale",
            PersistentDataType.STRING,
            localeId -> hasString(
                item,
                "latam_item_inventory",
                PersistentDataType.STRING,
                inventoryId -> hasString(
                    item,
                    "latam_item_id",
                    PersistentDataType.STRING,
                    itemId -> {
                        List<String> actions = fetch(InventoryService.class).getActionsOf(localeId, inventoryId, itemId);

                        ActionService actionService = fetch(ActionService.class);
                        if (actions.isEmpty()) {
                            return;
                        }

                        actionService.execute(
                            actions,
                            event.getPlayer(),
                            TextReplacer.builder().replace("<player>", event.getPlayer().getName())
                        );
                    }
                    )
                )
            );
        }

        @EventHandler
        public void onInventoryClose(InventoryCloseEvent e) {
            if (e.getInventory().getHolder() instanceof LatamInv inv) {

                if (inv.handleClose(e)) {
                    getTaskScheduler().runSync(() -> inv.open((Player) e.getPlayer()));
                }
            }
        }
    }
}



