package me.blueslime.meteor.paper.extras.services.item;

import me.blueslime.meteor.color.renders.ComponentRenderer;
import me.blueslime.meteor.paper.extras.services.item.armor.ItemArmorSlot;
import me.blueslime.meteor.paper.extras.services.item.skin.ItemSkinService;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.colors.JavaColorUtils;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.blueslime.meteor.utilities.tools.Tools;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.*;
import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.*;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;

import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings({"UnusedReturnValue", "unused"})
public class ItemWrapper implements Cloneable, PlatformService {

    private PluginConsumer.ReturnablePluginConsumer<Boolean, ItemCondition> giveConditions = wrapper -> true;
    private static final MiniMessage message = MiniMessage.miniMessage();
    private ItemStack item;
    private String id;
    private final List<Integer> slot = new ArrayList<>();
    private String rawName = null;
    private List<String> rawLore = new ArrayList<>();
    private boolean autoEquip = false;

    public static record ItemCondition(ItemWrapper wrapper, Player player) {}

    private ItemWrapper(String material, int amount, String name, Collection<String> lore, List<String> enchantments) {
        this.item = parseItemStack(material);
        this.amount(amount);
        if (name != null) {
            this.name(name);
        }
        this.lore(lore);
        this.enchantments(enchantments);
    }

    private ItemWrapper(ItemStack item) {
        this.item = item != null ? item.clone() : new ItemStack(Material.POTION);
    }

    public boolean isArmorPeace() {
        return getArmorSlot().isPresent();
    }

    public Optional<ItemArmorSlot> getArmorSlot() {
        checkItem();
        return ItemArmorSlot.anyMatch(item.getType());
    }

    public ItemStack getUserItem(Player player) {
        if (giveConditions.accept(new ItemCondition(this, player))) {
            ItemStack clonedItem = item.clone();

            if (rawName != null || !rawLore.isEmpty()) {
                clonedItem.editMeta(meta -> {

                    if (rawName != null) {
                        String papiName = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, rawName);
                        meta.displayName(ComponentRenderer.translate(papiName));
                    }

                    if (!rawLore.isEmpty()) {
                        List<Component> parsedLore = new ArrayList<>();
                        for (String line : rawLore) {
                            String papiLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, line);
                            parsedLore.add(ComponentRenderer.translate(papiLine));
                        }
                        meta.lore(parsedLore);
                    }
                });
            }
            return clonedItem;
        }

        return null;
    }

    public boolean isAutoEquip() {
        return autoEquip;
    }

    public ItemWrapper giveConditions(PluginConsumer.ReturnablePluginConsumer<Boolean, ItemCondition> giveConditions) {
        this.giveConditions = giveConditions;
        return this;
    }

    public ItemWrapper customId(String id) {
        checkItem();
        String pluginName = getPluginData().getPluginName().toLowerCase(Locale.ROOT);
        NamespacedKey key = new NamespacedKey(getPlugin().to(JavaPlugin.class), pluginName + "_menu_item");
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id));
        this.id = id;
        return this;
    }

    public ItemWrapper customIdAsInventory(String id) {
        checkItem();
        String pluginName = getPluginData().getPluginName().toLowerCase(Locale.ROOT);
        NamespacedKey key = new NamespacedKey(getPlugin().to(JavaPlugin.class), pluginName + "_lobby_item");
        item.editMeta(meta -> meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id));
        //NamespacedKey secondKey = new NamespacedKey(getPlugin().to(JavaPlugin.class), "latamhub_lobby_item");
        //item.editMeta(meta -> meta.getPersistentDataContainer().set(secondKey, PersistentDataType.STRING, id));
        this.id = id;
        return this;
    }

    public ItemWrapper addBooleanData(String key, boolean value) {
        checkItem();
        NamespacedKey namespacedKey = new NamespacedKey(getPlugin().to(JavaPlugin.class), key);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.BOOLEAN, value));
        return this;
    }

    public ItemWrapper addStringData(String key, String value) {
        checkItem();
        NamespacedKey namespacedKey = new NamespacedKey(getPlugin().to(JavaPlugin.class), key);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(namespacedKey, PersistentDataType.STRING, value));
        return this;
    }

    public ItemWrapper slot(int slot) {
        this.slot.clear();
        this.slot.add(slot);
        return this;
    }

    public ItemWrapper slot(List<Integer> slots) {
        this.slot.clear();
        this.slot.addAll(slots);
        return this;
    }

    public List<Integer> getSlots() {
        return this.slot;
    }

    public String getId() {
        return this.id;
    }

    /**
     * Sets the display name of the item.
     * @param name The name to set, supports color codes via {@link ComponentRenderer}.
     * @return Current ItemWrapper instance.
     */
    public ItemWrapper name(String name) {
        if (name == null) return this;
        this.rawName = name; // <-- Guardamos el texto original con los %placeholders%
        checkItem();
        item.editMeta(meta -> meta.displayName(ComponentRenderer.translate(name)));
        return this;
    }

    /**
     * Auto equip this item in the armor slots if the item is a armor part
     * @param autoEquip armor part detection
     * @return Current ItemWrapper instance modified
     */
    public ItemWrapper autoEquip(boolean autoEquip) {
        this.autoEquip = autoEquip;
        return this;
    }

    public ItemWrapper potionMeta(String typeKey, String colorString) {
        checkItem();
        item.editMeta(PotionMeta.class, meta -> {
            if (typeKey != null) {
                PotionType type = Registry.POTION.get(NamespacedKey.minecraft(typeKey.toLowerCase(Locale.ENGLISH)));
                if (type != null) {
                    meta.setBasePotionType(type);
                }
            }
            if (colorString != null) {
                String[] split = colorString.replace(" ", "").split(",");
                int r = split.length > 0 ? Tools.toInteger(split[0], 0) : 0;
                int g = split.length > 1 ? Tools.toInteger(split[1], 0) : 0;
                int b = split.length > 2 ? Tools.toInteger(split[2], 0) : 0;
                meta.setColor(Color.fromRGB(r, g, b));
            }
        });
        return this;
    }

    public ItemWrapper storedEnchantments(List<String> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return this;
        checkItem();

        item.editMeta(EnchantmentStorageMeta.class, meta -> {
            for (String line : enchantments) {
                String[] split = line.replace(" ", "").split(",", 2);
                String name = split[0].toLowerCase(Locale.ENGLISH);
                int level = split.length >= 2 ? Tools.toInteger(split[1], 1) : 1;

                @SuppressWarnings("deprecation") Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));
                if (enchantment != null) {
                    meta.addStoredEnchant(enchantment, level, true);
                }
            }
        });
        return this;
    }

    @SuppressWarnings({"UnstableApiUsage", "removal"})
    public ItemWrapper bannerPatterns(List<String> patternsConfig) {
        if (patternsConfig == null || patternsConfig.isEmpty()) return this;
        checkItem();

        List<Pattern> patterns = new ArrayList<>();
        for (String line : patternsConfig) {
            String[] split = line.replace(" ", "").split(",", 2);
            try {
                DyeColor color = DyeColor.valueOf(split[0].toUpperCase(Locale.ENGLISH));
                PatternType type = PatternType.valueOf(split[1].toUpperCase(Locale.ENGLISH));
                patterns.add(new Pattern(color, type));
            } catch (Exception ignored) {}
        }

        item.editMeta(BannerMeta.class, meta -> meta.setPatterns(patterns));

        item.editMeta(BlockStateMeta.class, meta -> {
            if (meta.getBlockState() instanceof Banner banner) {
                banner.setPatterns(patterns);
                banner.update();
                meta.setBlockState(banner);
            }
        });
        return this;
    }

    public ItemWrapper bookMeta(String title, String author, List<String> pages) {
        checkItem();
        item.editMeta(BookMeta.class, meta -> {
            if (title != null) meta.title(ComponentRenderer.translate(title));
            if (author != null) meta.author(ComponentRenderer.translate(author));
            if (pages != null && !pages.isEmpty()) {
                //noinspection ResultOfMethodCallIgnored
                meta.pages(pages.stream().map(ComponentRenderer::translate).collect(Collectors.toList()));
            }
        });
        return this;
    }

    public ItemWrapper spawnEggMeta(String entityTypeString) {
        if (entityTypeString == null || entityTypeString.isBlank()) return this;
        checkItem();
        item.editMeta(SpawnEggMeta.class, meta -> {
            try {
                EntityType type = EntityType.valueOf(entityTypeString.toUpperCase(Locale.ENGLISH));
                meta.setCustomSpawnedType(type);
            } catch (IllegalArgumentException ignored) {
                getLogger().error("Invalid EntityType for SpawnEgg: " + entityTypeString);
            }
        });
        return this;
    }

    public ItemWrapper musicInstrument(String instrumentKey) {
        if (instrumentKey == null || instrumentKey.isBlank()) return this;
        checkItem();
        item.editMeta(MusicInstrumentMeta.class, meta -> {
            @SuppressWarnings("deprecation") MusicInstrument instrument = Registry.INSTRUMENT.get(NamespacedKey.minecraft(instrumentKey.toLowerCase(Locale.ENGLISH)));
            if (instrument != null) meta.setInstrument(instrument);
        });
        return this;
    }

    public ItemWrapper ominousLevel(int level) {
        checkItem();
        item.editMeta(OminousBottleMeta.class, meta -> {
            int finalLevel = Math.max(1, Math.min(5, level));
            meta.setAmplifier(finalLevel - 1);
        });
        return this;
    }



    /**
     * Sets the lore of the item.
     * @param lore Collection of strings representing the lore.
     * @return Current ItemWrapper instance.
     */
    public ItemWrapper lore(Collection<String> lore) {
        if (lore == null || lore.isEmpty()) return this;
        this.rawLore = new ArrayList<>(lore);
        checkItem();
        item.editMeta(meta -> meta.lore(lore.stream().map(ComponentRenderer::translate).collect(Collectors.toList())));
        return this;
    }

    /**
     * Applies a list of enchantments to the item.
     * Format: "ENCHANTMENT_NAME, LEVEL"
     * @param enchantments List of formatted enchantment strings.
     */
    public ItemWrapper enchantments(List<String> enchantments) {
        if (enchantments == null || enchantments.isEmpty()) return this;
        checkItem();

        Map<Enchantment, Integer> enchantmentMap = new HashMap<>();

        for (String line : enchantments) {
            String[] split = line.replace(" ", "").split(",", 2);
            String name = split[0].toLowerCase(Locale.ENGLISH); // 1.21 uses lowercase keys
            int level = split.length >= 2 ? Tools.toInteger(split[1], 1) : 1;

            // Modern 1.21 Registry approach
            @SuppressWarnings("deprecation") Enchantment enchantment = Registry.ENCHANTMENT.get(NamespacedKey.minecraft(name));

            if (enchantment != null) {
                enchantmentMap.put(enchantment, level);
            } else {
                getLogger().error("Enchantment '" + name + "' was not found.");
            }
        }

        if (!enchantmentMap.isEmpty()) {
            item.editMeta(meta -> {
                for (Map.Entry<Enchantment, Integer> entry : enchantmentMap.entrySet()) {
                    meta.addEnchant(entry.getKey(), entry.getValue(), true);
                }
            });
        }
        return this;
    }

    /**
     * Changes the amount of the item.
     * @param amount the new amount.
     */
    public ItemWrapper amount(int amount) {
        checkItem();
        item.setAmount(amount);
        return this;
    }

    /**
     * Parses an ItemStack from a string, handling textures and legacy damage values.
     * @param value The material or texture string.
     * @return A valid ItemStack.
     */
    private ItemStack parseItemStack(String value) {
        if (value == null || value.isBlank()) return new ItemStack(Material.POTION);

        String lowerValue = value.toLowerCase(Locale.ENGLISH);
        if (lowerValue.contains("texture") || lowerValue.contains("skin")) {
            this.item = new ItemStack(Material.PLAYER_HEAD);
            applyTexture(value);
            return item;
        }

        if (value.contains(":")) {
            String[] split = value.split(":", 2);
            Material material = parseMaterial(split[0]);
            ItemStack newItem = new ItemStack(material);

            if (Tools.isInteger(split[1])) {
                int damage = Tools.toInteger(split[1], 0);
                // In 1.21, data values are handled via Damageable Meta
                newItem.editMeta(Damageable.class, meta -> meta.setDamage(damage));
            }
            return newItem;
        }

        return new ItemStack(parseMaterial(value));
    }

    /**
     * Applies a Base64 skin texture to a player head using modern Paper API.
     * Avoids heavy reflection.
     */
    private void applyTexture(String value) {
        fetch(ItemSkinService.class).applyTexture(item, value);
    }

    private Material parseMaterial(String material) {
        try {
            Material mat = Material.matchMaterial(material); // Better than valueOf as it handles namespaces
            return mat != null ? mat : Material.valueOf(material.toUpperCase(Locale.ENGLISH));
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return Material.POTION;
        }
    }

    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public ItemWrapper clone() {
        return new ItemWrapper(this.item);
    }

    /**
     * Gets the display name of the item.
     * @return Display name Component, or empty Component if null.
     */
    public Component getName() {
        return (item != null && item.hasItemMeta() && item.getItemMeta().hasDisplayName())
                ? item.getItemMeta().displayName()
                : Component.empty();
    }

    /**
     * Gets the lore of the item.
     * @return List of lore Components, or an empty list if none exists.
     */
    public List<Component> getLore() {
        return (item != null && item.hasItemMeta() && item.getItemMeta().hasLore())
                ? item.getItemMeta().lore()
                : Collections.emptyList();
    }

    /**
     * Gets the current amount of the item.
     * @return Integer representing the stack size.
     */
    public int getAmount() {
        return item != null ? item.getAmount() : 1;
    }

    private void checkItem() {
        if (this.item == null || this.item.getType().isAir()) {
            this.item = new ItemStack(Material.POTION);
        }
    }

    /**
     * Retrieves the built ItemStack.
     * @return The underlying ItemStack.
     */
    public ItemStack getItem() {
        checkItem();
        return item;
    }

    // --- Static Builders ---

    public static ItemWrapper fromData(String material, int amount, String name, List<String> lore, List<String> enchantments) {
        return new ItemWrapper(material, amount, name, lore, enchantments);
    }

    public static ItemWrapper fromData(String material, int amount, String name, List<String> lore) {
        return fromData(material, amount, name, lore, Collections.emptyList());
    }

    public static ItemWrapper fromData(String material, String name, List<String> lore) {
        return fromData(material, 1, name, lore, Collections.emptyList());
    }

    public static ItemWrapper fromData(String material, int amount, String name) {
        return fromData(material, amount, name, Collections.emptyList(), Collections.emptyList());
    }

    public static ItemWrapper fromData(String material, String name) {
        return fromData(material, 1, name, Collections.emptyList(), Collections.emptyList());
    }

    public static ItemWrapper fromData(String material) {
        return fromData(material, 1, null, Collections.emptyList(), Collections.emptyList());
    }

    public static ItemWrapper fromData(String material, int amount) {
        return fromData(material, amount, material, Collections.emptyList(), Collections.emptyList());
    }

    public static ItemWrapper fromData(ConfigurationHandle configuration, String path, TextReplacer replacer) {
        return fromData(configuration.getSection(path), replacer);
    }

    public static ItemWrapper fromData(ConfigurationHandle configuration, String path) {
        return fromData(configuration.getSection(path), TextReplacer.EMPTY);
    }

    public static ItemWrapper fromData(ConfigurationHandle configuration) {
        return fromData(configuration, TextReplacer.EMPTY);
    }

    public static ItemWrapper fromData(ConfigurationHandle configuration, TextReplacer replacer) {
        if (configuration == null) {
            return fromData("POTION");
        }

        if (replacer == null) {
            replacer = TextReplacer.EMPTY;
        }

        ItemWrapper wrapper = fromData(
            replacer.apply(configuration.getString("material", "POTION")),
            configuration.getInt("amount", 1),
            replacer.apply(configuration.getString("name", null)),
            replacer.applyAll(configuration.getStringList("lore")),
            configuration.getStringList("enchantments")
        ).autoEquip(configuration.getBoolean("auto-equip", false));

        if (configuration.contains("charge-color")) {
            wrapper.chargeMeta(configuration.getString("charge-color", "red"));
        }
        if (configuration.contains("armor-color")) {
            wrapper.armorMeta(configuration.getString("armor-color", "0, 0, 0"));
        }
        if (configuration.contains("armor-trim.material")) {
            wrapper.armorTrim(
                    configuration.getString("armor-trim.material", "DIAMOND"),
                    configuration.getString("armor-trim.pattern", "COAST")
            );
        }
        if (configuration.contains("unbreakable")) {
            wrapper.unbreakable(configuration.getBoolean("unbreakable", false));
        }
        if (configuration.contains("custom-model-data")) {
            wrapper.customModelData(configuration.getInt("custom-model-data", 0));
        }
        if (configuration.contains("item-flags")) {
            wrapper.itemFlags(configuration.getStringList("item-flags"));
        }

        if (configuration.contains("potion-type") || configuration.contains("potion-color")) {
            wrapper.potionMeta(
                    configuration.getString("potion-type"),
                    configuration.getString("potion-color")
            );
        }

        if (configuration.contains("stored-enchantments")) {
            wrapper.storedEnchantments(configuration.getStringList("stored-enchantments"));
        }

        if (configuration.contains("slot")) {
            wrapper.slot(configuration.getInt("slot", 0));
        }

        if (configuration.contains("slots")) {
            wrapper.slot(configuration.getIntList("slots"));
        }

        if (configuration.contains("ominous-level")) {
            wrapper.ominousLevel(configuration.getInt("ominous-level", 1));
        }

        if (configuration.contains("banner-patterns")) {
            wrapper.bannerPatterns(configuration.getStringList("banner-patterns"));
        }

        if (configuration.contains("book")) {
            wrapper.bookMeta(
                    replacer.apply(configuration.getString("book.title")),
                    replacer.apply(configuration.getString("book.author")),
                    replacer.applyAll(configuration.getStringList("book.pages"))
            );
        }

        if (configuration.contains("spawn-egg-type")) {
            wrapper.spawnEggMeta(configuration.getString("spawn-egg-type"));
        }
        if (configuration.contains("music-instrument")) {
            wrapper.musicInstrument(configuration.getString("music-instrument"));
        }

        return wrapper;
    }

    // --- Meta Modifiers ---

    /**
     * Sets the armor trim on the item (1.20+ feature).
     * @param materialKey The Trim Material (e.g., "diamond").
     * @param patternKey  The Trim Pattern (e.g., "coast").
     */
    @SuppressWarnings("deprecation")
    public ItemWrapper armorTrim(String materialKey, String patternKey) {
        checkItem();
        item.editMeta(ArmorMeta.class, meta -> {
            NamespacedKey matKey = NamespacedKey.minecraft(materialKey.toLowerCase(Locale.ENGLISH));
            NamespacedKey patKey = NamespacedKey.minecraft(patternKey.toLowerCase(Locale.ENGLISH));

            TrimMaterial trimMaterial = Registry.TRIM_MATERIAL.get(matKey);
            TrimPattern trimPattern = Registry.TRIM_PATTERN.get(patKey);

            if (trimMaterial != null && trimPattern != null) {
                meta.setTrim(new ArmorTrim(trimMaterial, trimPattern));
            }
        });
        return this;
    }

    /**
     * Sets whether the item is unbreakable.
     * @param unbreakable True to make unbreakable.
     */
    public ItemWrapper unbreakable(boolean unbreakable) {
        checkItem();
        item.editMeta(meta -> meta.setUnbreakable(unbreakable));
        return this;
    }

    /**
     * Sets the custom model data for the item.
     * @param modelDataInt Integer value for the custom model data.
     */
    @SuppressWarnings("deprecation")
    public ItemWrapper customModelData(int modelDataInt) {
        checkItem();
        item.editMeta(meta -> meta.setCustomModelData(modelDataInt));
        return this;
    }

    /**
     * Hides specific properties of the item via ItemFlags.
     * @param stringList List of flag names (e.g., "HIDE_ENCHANTS").
     */
    public ItemWrapper itemFlags(List<String> stringList) {
        checkItem();
        if (stringList == null || stringList.isEmpty()) return this;

        item.editMeta(meta -> {
            for (String flagName : stringList) {
                try {
                    meta.addItemFlags(ItemFlag.valueOf(flagName.toUpperCase(Locale.ENGLISH)));
                } catch (IllegalArgumentException e) {
                    getLogger().error("Invalid ItemFlag: " + flagName);
                }
            }
        });
        return this;
    }

    /**
     * Colors leather armor.
     * Format: "R, G, B" or "R, G" or "R"
     * @param string Comma-separated RGB values.
     */
    public ItemWrapper armorMeta(String string) {
        checkItem();
        item.editMeta(LeatherArmorMeta.class, meta -> {
            String[] split = string.replace(" ", "").split(",");
            int r = split.length > 0 ? Tools.toInteger(split[0], 0) : 0;
            int g = split.length > 1 ? Tools.toInteger(split[1], 0) : 0;
            int b = split.length > 2 ? Tools.toInteger(split[2], 0) : 0;
            meta.setColor(Color.fromRGB(r, g, b));
        });
        return this;
    }

    /**
     * Configures a Firework Star (Charge) color.
     * Format: "COLOR:FLICKER:TRAIL:FADE_COLOR"
     * @param value Configuration string.
     */
    public ItemWrapper chargeMeta(String value) {
        checkItem();
        item.editMeta(FireworkEffectMeta.class, meta -> {
            String[] split = value.replace(" ", "").split(":", 4);
            FireworkEffect.Builder builder = FireworkEffect.builder();

            builder.withColor(Color.fromRGB(JavaColorUtils.getColor(split[0]).getRGB()));

            if (split.length >= 2) builder.flicker(Boolean.parseBoolean(split[1]));
            if (split.length >= 3) builder.trail(Boolean.parseBoolean(split[2]));
            if (split.length == 4) builder.withFade(Color.fromRGB(JavaColorUtils.getColor(split[3]).getRGB()));

            meta.setEffect(builder.build());
        });
        return this;
    }

    /**
     * Saves the current item's properties to a ConfigurationSection.
     * @param section The YAML section to save into.
     */
    @SuppressWarnings({"deprecation", "UnstableApiUsage", "removal"})
    public ItemWrapper saveAt(ConfigurationHandle section) {
        checkItem();
        ItemMeta meta = item.getItemMeta();

        section.set("material", item.getType().name());
        section.set("amount", item.getAmount());

        if (meta != null) {
            Component displayName = meta.displayName();
            if (meta.hasDisplayName() && displayName != null) {
                section.set("name", message.serialize(displayName));
            }

            List<Component> lore = meta.lore();
            if (meta.hasLore() && lore != null) {
                section.set("lore", lore.stream()
                        .map(message::serialize)
                        .collect(Collectors.toList()));
            }

            if (meta.isUnbreakable()) section.set("unbreakable", true);
            if (meta.hasCustomModelData()) section.set("custom-model-data", meta.getCustomModelData());

            if (!meta.getEnchants().isEmpty()) {
                section.set("enchantments", meta.getEnchants().entrySet().stream()
                        .map(e -> e.getKey().getKey().getKey() + "," + e.getValue())
                        .collect(Collectors.toList()));
            }

            if (!meta.getItemFlags().isEmpty()) {
                section.set("item-flags", meta.getItemFlags().stream()
                        .map(Enum::name)
                        .collect(Collectors.toList()));
            }

            if (meta instanceof Damageable damageable && damageable.hasDamage()) {
                section.set("damage", damageable.getDamage());
            }

            if (meta instanceof ArmorMeta armorMeta && armorMeta.hasTrim()) {
                ArmorTrim trim = armorMeta.getTrim();
                if (trim != null) {
                    //noinspection removal
                    section.set("armor-trim.material", trim.getMaterial().getKey().getKey());
                    //noinspection removal
                    section.set("armor-trim.pattern", trim.getPattern().getKey().getKey());
                }
            }

            if (meta instanceof LeatherArmorMeta leather) {
                Color c = leather.getColor();
                section.set("armor-color", c.getRed() + ", " + c.getGreen() + ", " + c.getBlue());
            }

            if (meta instanceof PotionMeta potion) {
                if (potion.getBasePotionType() != null) {
                    section.set("potion-type", potion.getBasePotionType().getKey().getKey());
                }
                Color c = potion.getColor();
                if (potion.hasColor() && c != null) {
                    section.set("potion-color", c.getRed() + ", " + c.getGreen() + ", " + c.getBlue());
                }
            }

            if (meta instanceof EnchantmentStorageMeta enchantmentStorage) {
                if (enchantmentStorage.hasStoredEnchants()) {
                    section.set("stored-enchantments", enchantmentStorage.getStoredEnchants().entrySet().stream()
                            .map(e -> e.getKey().getKey().getKey() + "," + e.getValue())
                            .collect(Collectors.toList()));
                }
            }

            if (meta instanceof OminousBottleMeta ominous) {
                if (ominous.hasAmplifier()) {
                    section.set("ominous-level", ominous.getAmplifier() + 1);
                }
            }

            if (meta instanceof BookMeta book) {
                Component title = book.title();
                Component author = book.author();
                if (book.hasTitle() && title != null) section.set("book.title", message.serialize(title));
                if (book.hasAuthor() && author != null) section.set("book.author",  message.serialize(author));
                if (book.hasPages()) {
                    section.set("book.pages", book.pages().stream()
                            .map(message::serialize)
                            .collect(Collectors.toList()));
                }
            }

            if (meta instanceof SpawnEggMeta eggMeta) {
                if (eggMeta.getCustomSpawnedType() != null) {
                    section.set("spawn-egg-type", eggMeta.getCustomSpawnedType().name());
                }
            }

            if (meta instanceof MusicInstrumentMeta instrumentMeta) {
                if (instrumentMeta.getInstrument() != null) {
                    section.set("music-instrument", instrumentMeta.getInstrument().getKey().getKey());
                }
            }

            // Para Banners y Escudos
            if (meta instanceof BannerMeta bannerMeta) {
                if (!bannerMeta.getPatterns().isEmpty()) {
                    section.set("banner-patterns", bannerMeta.getPatterns().stream()
                            .map(p -> p.getColor().name() + ", " + p.getPattern().name())
                            .collect(Collectors.toList()));
                }
            } else if (meta instanceof BlockStateMeta blockStateMeta && blockStateMeta.getBlockState() instanceof Banner banner) {
                if (!banner.getPatterns().isEmpty()) {
                    section.set("banner-patterns", banner.getPatterns().stream()
                            .map(p -> p.getColor().name() + ", " + p.getPattern().name())
                            .collect(Collectors.toList()));
                }
            }
        }
        return this;
    }

    private ItemWrapper verify() {
        checkItem();
        return this;
    }

    public static ItemWrapper fromItem(ItemStack itemStack) {
        return new ItemWrapper(itemStack).verify();
    }

    public ItemWrapper copy() {
        ItemWrapper newWrapper = new ItemWrapper(this.item.clone());
        newWrapper.rawName = this.rawName;
        newWrapper.rawLore = new ArrayList<>(this.rawLore);
        newWrapper.giveConditions = this.giveConditions;
        newWrapper.id = this.id;
        newWrapper.slot.addAll(this.slot);
        return newWrapper;
    }
}
