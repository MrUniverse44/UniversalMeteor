package me.blueslime.meteor.paper.extras.item;

import me.blueslime.meteor.color.renders.ComponentRenderer;
import me.blueslime.meteor.paper.extras.item.armor.ItemArmorSlot;
import me.blueslime.meteor.paper.extras.item.skin.ItemSkinService;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import me.blueslime.meteor.utilities.colors.JavaColorUtils;
import me.blueslime.meteor.utilities.text.TextReplacer;
import me.blueslime.meteor.utilities.tools.Tools;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.MusicInstrument;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import org.bukkit.block.Banner;
import org.bukkit.block.banner.Pattern;
import org.bukkit.block.banner.PatternType;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;

import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SpawnEggMeta;

import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;

import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import java.util.stream.Collectors;

@SuppressWarnings({
    "UnusedReturnValue",
    "unused",
    "deprecation",
    "removal",
    "UnstableApiUsage"
})
public class ItemWrapper
        implements Cloneable, PlatformService {

    private static final MiniMessage MINI_MESSAGE =
            MiniMessage.miniMessage();

    /**
     * Keys owned by ItemWrapper serialization.
     * <br>
     * IMPORTANT:
     * placement, conditions, actions and interaction
     * settings are intentionally NOT here.
     */
    private static final List<String> SERIALIZED_KEYS =
            List.of(
                "material",
                "skin",
                "amount",
                "name",
                "lore",
                "damage",
                "enchantments",
                "stored-enchantments",
                "unbreakable",
                "custom-model-data",
                "item-flags",
                "armor-color",
                "armor-trim",
                "potion-type",
                "potion-color",
                "ominous-level",
                "banner-patterns",
                "book",
                "spawn-egg-type",
                "music-instrument",
                "charge-color"
            );

    /**
     * Base ItemStack.
     * <br>
     * It represents the static portion of this item
     * template.
     */
    private ItemStack item;

    /**
     * Raw templates.
     * <br>
     * They are kept separately from ItemMeta so that
     * placeholders/context variables can be resolved
     * every time ItemRenderer renders this wrapper.
     */
    private String rawName;

    private List<String> rawLore = new ArrayList<>();

    private String rawBookTitle;

    private String rawBookAuthor;

    private List<String> rawBookPages = new ArrayList<>();

    /**
     * Original skin expression.
     * <br>
     * Examples:
     * <br>
     * player:Notch
     * texture:<base64>
     * skin:<base64>
     * <br>
     * This gives us an actual configuration round-trip.
     */
    private String skinSource;

    private ItemWrapper(
            ItemStack item,
            boolean captureTemplates
    ) {
        this.item =
                item == null
                        ? new ItemStack(
                        Material.POTION
                )
                        : item.clone();

        checkItem();

        if (captureTemplates) {
            captureTemplates();
        }
    }

    private ItemWrapper(
            ItemStack item
    ) {
        this(
                item,
                true
        );
    }

    private ItemWrapper(
            String material,
            int amount,
            String name,
            Collection<String> lore,
            List<String> enchantments
    ) {
        this.item =
                parseItemStack(
                        material
                );

        amount(amount);

        if (name != null) {
            name(name);
        }

        if (lore != null) {
            lore(lore);
        }

        if (
                enchantments != null &&
                        !enchantments.isEmpty()
        ) {
            enchantments(
                    enchantments
            );
        }
    }

    public ItemStack build() {
        checkItem();

        return item.clone();
    }

    /**
     * Compatibility-style accessor.
     * <br>
     * It returns a COPY intentionally.
     * Mutating it does not modify this ItemWrapper.
     */
    public ItemStack getItem() {
        return build();
    }

    public Material getMaterial() {
        checkItem();

        return item.getType();
    }

    public int getAmount() {
        checkItem();

        return item.getAmount();
    }

    public Component getName() {
        checkItem();

        ItemMeta meta =
                item.getItemMeta();

        if (
            meta == null ||
            !meta.hasDisplayName() ||
            meta.displayName() == null
        ) {
            return Component.empty();
        }

        return meta.displayName();
    }

    public List<Component> getLore() {
        checkItem();

        ItemMeta meta =
                item.getItemMeta();

        if (
            meta == null ||
            !meta.hasLore() ||
            meta.lore() == null
        ) {
            return Collections.emptyList();
        }

        return List.copyOf(
                Objects.requireNonNull(
                        meta.lore()
                )
        );
    }

    public String getRawName() {
        return rawName;
    }

    public List<String> getRawLore() {
        return List.copyOf(
                rawLore
        );
    }

    public String getRawBookTitle() {
        return rawBookTitle;
    }

    public String getRawBookAuthor() {
        return rawBookAuthor;
    }

    public List<String> getRawBookPages() {
        return List.copyOf(
                rawBookPages
        );
    }

    public String getSkinSource() {
        return skinSource;
    }

    public boolean hasTextTemplates() {
        return rawName != null ||
                !rawLore.isEmpty() ||
                rawBookTitle != null ||
                rawBookAuthor != null ||
                !rawBookPages.isEmpty();
    }

    public ItemWrapper material(
            String material
    ) {
        int previousAmount =
                item == null
                        ? 1
                        : Math.max(
                        1,
                        item.getAmount()
                );

        this.item =
                parseItemStack(
                        material
                );

        amount(
                previousAmount
        );

        return this;
    }

    public ItemWrapper material(
            Material material
    ) {
        Objects.requireNonNull(
                material,
                "material"
        );

        int previousAmount =
                item == null
                        ? 1
                        : Math.max(
                        1,
                        item.getAmount()
                );

        this.item =
                new ItemStack(
                        material,
                        previousAmount
                );

        this.skinSource =
                null;

        return this;
    }

    public ItemWrapper amount(
            int amount
    ) {
        checkItem();

        int maximum =
                item.getMaxStackSize();

        //noinspection MathClampMigration
        item.setAmount(
                Math.max(
                        1,
                        Math.min(
                                amount,
                                maximum
                        )
                )
        );

        return this;
    }

    public ItemWrapper damage(
            int damage
    ) {
        checkItem();

        item.editMeta(
                Damageable.class,
                meta ->
                        meta.setDamage(
                                Math.max(
                                        0,
                                        damage
                                )
                        )
        );

        return this;
    }

    public ItemWrapper name(String name) {
        checkItem();

        this.rawName =
                name;

        item.editMeta(meta -> {
            if (name == null) {
                meta.displayName(null);
                return;
            }

            meta.displayName(
                    ComponentRenderer.translate(
                            name
                    )
            );
        });

        return this;
    }

    public ItemWrapper clearName() {
        return name(
                null
        );
    }

    public ItemWrapper lore(Collection<String> lore) {
        checkItem();

        if (lore == null || lore.isEmpty()) {
            this.rawLore =
                    new ArrayList<>();

            item.editMeta(meta -> meta.lore(null));

            return this;
        }

        this.rawLore =
                new ArrayList<>(
                        lore
                );

        List<Component> components =
                lore.stream()
                        .map(
                                ComponentRenderer::translate
                        )
                        .collect(
                                Collectors.toList()
                        );

        item.editMeta(
                meta ->
                        meta.lore(
                                components
                        )
        );

        return this;
    }

    public ItemWrapper clearLore() {
        return lore(
                Collections.emptyList()
        );
    }

    public ItemWrapper skin(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            this.skinSource =
                    null;

            return this;
        }

        int amount =
                item == null
                        ? 1
                        : item.getAmount();

        if (
                item == null ||
                        item.getType()
                                != Material.PLAYER_HEAD
        ) {
            item =
                    new ItemStack(
                            Material.PLAYER_HEAD,
                            amount
                    );
        }

        this.skinSource =
                value;

        fetch(
                ItemSkinService.class
        ).applyTexture(
                item,
                value
        );

        return this;
    }

    public ItemWrapper enchantments(
            List<String> enchantments
    ) {
        if (
                enchantments == null ||
                        enchantments.isEmpty()
        ) {
            return this;
        }

        checkItem();

        for (String line : enchantments) {
            if (
                    line == null ||
                            line.isBlank()
            ) {
                continue;
            }

            String[] split =
                    line
                            .replace(
                                    " ",
                                    ""
                            )
                            .split(
                                    ",",
                                    2
                            );

            NamespacedKey key =
                    parseKey(
                            split[0]
                    );

            if (key == null) {
                getLogger().error(
                        "Invalid enchantment key '"
                                + split[0]
                                + "'"
                );

                continue;
            }

            Enchantment enchantment =
                    Registry.ENCHANTMENT.get(
                            key
                    );

            if (enchantment == null) {
                getLogger().error(
                        "Enchantment '"
                                + split[0]
                                + "' was not found."
                );

                continue;
            }

            int level =
                    split.length >= 2
                            ? Tools.toInteger(
                            split[1],
                            1
                    )
                            : 1;

            item.editMeta(
                    meta ->
                            meta.addEnchant(
                                    enchantment,
                                    level,
                                    true
                            )
            );
        }

        return this;
    }

    public ItemWrapper storedEnchantments(
            List<String> enchantments
    ) {
        if (
                enchantments == null ||
                        enchantments.isEmpty()
        ) {
            return this;
        }

        checkItem();

        item.editMeta(
                EnchantmentStorageMeta.class,
                meta -> {
                    for (
                            String line :
                            enchantments
                    ) {
                        if (
                                line == null ||
                                        line.isBlank()
                        ) {
                            continue;
                        }

                        String[] split =
                                line
                                        .replace(
                                                " ",
                                                ""
                                        )
                                        .split(
                                                ",",
                                                2
                                        );

                        NamespacedKey key =
                                parseKey(
                                        split[0]
                                );

                        if (key == null) {
                            continue;
                        }

                        Enchantment enchantment =
                                Registry.ENCHANTMENT
                                        .get(key);

                        if (enchantment == null) {
                            continue;
                        }

                        int level =
                                split.length >= 2
                                        ? Tools.toInteger(
                                        split[1],
                                        1
                                )
                                        : 1;

                        meta.addStoredEnchant(
                                enchantment,
                                level,
                                true
                        );
                    }
                }
        );

        return this;
    }

    public ItemWrapper potionMeta(
            String typeKey,
            String colorString
    ) {
        checkItem();

        item.editMeta(
                PotionMeta.class,
                meta -> {
                    if (
                            typeKey != null &&
                                    !typeKey.isBlank()
                    ) {
                        NamespacedKey key =
                                parseKey(
                                        typeKey
                                );

                        if (key != null) {
                            PotionType type =
                                    Registry.POTION
                                            .get(key);

                            if (type != null) {
                                meta.setBasePotionType(
                                        type
                                );
                            }
                        }
                    }

                    if (
                            colorString != null &&
                                    !colorString.isBlank()
                    ) {
                        Color color =
                                parseColor(
                                        colorString
                                );

                        if (color != null) {
                            meta.setColor(
                                    color
                            );
                        }
                    }
                }
        );

        return this;
    }

    public ItemWrapper bannerPatterns(
            List<String> patterns
    ) {
        if (
                patterns == null ||
                        patterns.isEmpty()
        ) {
            return this;
        }

        checkItem();

        List<Pattern> parsed =
                new ArrayList<>();

        for (
                String line :
                patterns
        ) {
            if (
                    line == null ||
                            line.isBlank()
            ) {
                continue;
            }

            String[] split =
                    line
                            .replace(
                                    " ",
                                    ""
                            )
                            .split(
                                    ",",
                                    2
                            );

            if (split.length < 2) {
                continue;
            }

            try {
                DyeColor color =
                        DyeColor.valueOf(
                                split[0]
                                        .toUpperCase(
                                                Locale.ROOT
                                        )
                        );

                PatternType type =
                        parsePatternType(
                                split[1]
                        );

                if (type == null) {
                    getLogger().error(
                            "Invalid banner pattern: "
                                    + split[1]
                    );

                    continue;
                }

                parsed.add(
                        new Pattern(
                                color,
                                type
                        )
                );

            } catch (
                    IllegalArgumentException exception
            ) {
                getLogger().error(
                        "Invalid banner pattern line: "
                                + line
                );
            }
        }

        if (parsed.isEmpty()) {
            return this;
        }

        item.editMeta(
                BannerMeta.class,
                meta ->
                        meta.setPatterns(
                                parsed
                        )
        );

        item.editMeta(
                BlockStateMeta.class,
                meta -> {
                    if (
                            meta.getBlockState()
                                    instanceof Banner banner
                    ) {
                        banner.setPatterns(
                                parsed
                        );

                        meta.setBlockState(
                                banner
                        );
                    }
                }
        );

        return this;
    }

    public ItemWrapper bookMeta(
            String title,
            String author,
            List<String> pages
    ) {
        checkItem();

        this.rawBookTitle =
                title;

        this.rawBookAuthor =
                author;

        this.rawBookPages =
                pages == null
                        ? new ArrayList<>()
                        : new ArrayList<>(
                        pages
                );

        item.editMeta(
                BookMeta.class,
                meta -> {
                    if (title != null) {
                        meta.title(
                                ComponentRenderer.translate(
                                        title
                                )
                        );
                    }

                    if (author != null) {
                        meta.author(
                                ComponentRenderer.translate(
                                        author
                                )
                        );
                    }

                    if (
                            pages != null &&
                                    !pages.isEmpty()
                    ) {
                        meta.pages(
                                pages.stream()
                                        .map(
                                                ComponentRenderer::translate
                                        )
                                        .collect(
                                                Collectors.toList()
                                        )
                        );
                    }
                }
        );

        return this;
    }

    public ItemWrapper spawnEggMeta(
            String entityType
    ) {
        if (
                entityType == null ||
                        entityType.isBlank()
        ) {
            return this;
        }

        checkItem();

        item.editMeta(
                SpawnEggMeta.class,
                meta -> {
                    try {
                        EntityType type =
                                EntityType.valueOf(
                                        entityType
                                                .strip()
                                                .toUpperCase(
                                                        Locale.ROOT
                                                )
                                );

                        meta.setCustomSpawnedType(
                                type
                        );

                    } catch (
                            IllegalArgumentException exception
                    ) {
                        getLogger().error(
                                "Invalid EntityType for SpawnEgg: "
                                        + entityType
                        );
                    }
                }
        );

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Instrument
     * ------------------------------------------------------------------------
     */

    public ItemWrapper musicInstrument(
            String instrument
    ) {
        if (
                instrument == null ||
                        instrument.isBlank()
        ) {
            return this;
        }

        checkItem();

        NamespacedKey key =
                parseKey(
                        instrument
                );

        if (key == null) {
            return this;
        }

        MusicInstrument value =
                Registry.INSTRUMENT.get(
                        key
                );

        if (value == null) {
            getLogger().error(
                    "Invalid music instrument: "
                            + instrument
            );

            return this;
        }

        item.editMeta(
                MusicInstrumentMeta.class,
                meta ->
                        meta.setInstrument(
                                value
                        )
        );

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Ominous bottle
     * ------------------------------------------------------------------------
     */

    public ItemWrapper ominousLevel(
            int level
    ) {
        checkItem();

        item.editMeta(
                OminousBottleMeta.class,
                meta -> {
                    int finalLevel =
                            Math.max(
                                    1,
                                    Math.min(
                                            5,
                                            level
                                    )
                            );

                    meta.setAmplifier(
                            finalLevel - 1
                    );
                }
        );

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Armor
     * ------------------------------------------------------------------------
     */

    public ItemWrapper armorTrim(
            String material,
            String pattern
    ) {
        if (
                material == null ||
                        material.isBlank() ||
                        pattern == null ||
                        pattern.isBlank()
        ) {
            return this;
        }

        checkItem();

        NamespacedKey materialKey =
                parseKey(
                        material
                );

        NamespacedKey patternKey =
                parseKey(
                        pattern
                );

        if (
                materialKey == null ||
                        patternKey == null
        ) {
            return this;
        }

        TrimMaterial trimMaterial =
                Registry.TRIM_MATERIAL.get(
                        materialKey
                );

        TrimPattern trimPattern =
                Registry.TRIM_PATTERN.get(
                        patternKey
                );

        if (
                trimMaterial == null ||
                        trimPattern == null
        ) {
            getLogger().error(
                    "Invalid armor trim: material='"
                            + material
                            + "', pattern='"
                            + pattern
                            + "'"
            );

            return this;
        }

        item.editMeta(
                ArmorMeta.class,
                meta ->
                        meta.setTrim(
                                new ArmorTrim(
                                        trimMaterial,
                                        trimPattern
                                )
                        )
        );

        return this;
    }

    public ItemWrapper armorMeta(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return this;
        }

        checkItem();

        Color color =
                parseColor(
                        value
                );

        if (color == null) {
            return this;
        }

        item.editMeta(
                LeatherArmorMeta.class,
                meta ->
                        meta.setColor(
                                color
                        )
        );

        return this;
    }

    public boolean isArmorPiece() {
        return getArmorSlot()
                .isPresent();
    }

    /**
     * Temporary compatibility for old typo.
     */
    @Deprecated
    public boolean isArmorPeace() {
        return isArmorPiece();
    }

    public Optional<ItemArmorSlot> getArmorSlot() {
        checkItem();

        return ItemArmorSlot.anyMatch(
                item.getType()
        );
    }

    /*
     * ------------------------------------------------------------------------
     * General ItemMeta
     * ------------------------------------------------------------------------
     */

    public ItemWrapper unbreakable(
            boolean unbreakable
    ) {
        checkItem();

        item.editMeta(
                meta ->
                        meta.setUnbreakable(
                                unbreakable
                        )
        );

        return this;
    }

    public ItemWrapper customModelData(
            int modelData
    ) {
        checkItem();

        item.editMeta(
                meta ->
                        meta.setCustomModelData(
                                modelData
                        )
        );

        return this;
    }

    public ItemWrapper itemFlags(
            Collection<String> flags
    ) {
        if (
                flags == null ||
                        flags.isEmpty()
        ) {
            return this;
        }

        checkItem();

        item.editMeta(meta -> {
            for (String value : flags) {
                if (
                        value == null ||
                                value.isBlank()
                ) {
                    continue;
                }

                try {
                    meta.addItemFlags(
                            ItemFlag.valueOf(
                                    value
                                            .strip()
                                            .toUpperCase(
                                                    Locale.ROOT
                                            )
                            )
                    );

                } catch (
                        IllegalArgumentException exception
                ) {
                    getLogger().error(
                            "Invalid ItemFlag: "
                                    + value
                    );
                }
            }
        });

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Firework star
     * ------------------------------------------------------------------------
     */

    public ItemWrapper chargeMeta(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return this;
        }

        checkItem();

        String[] split =
                value
                        .strip()
                        .split(
                                ":",
                                4
                        );

        Color color =
                parseColor(
                        split[0]
                );

        if (color == null) {
            return this;
        }

        FireworkEffect.Builder builder =
                FireworkEffect.builder()
                        .withColor(
                                color
                        );

        if (split.length >= 2) {
            builder.flicker(
                    Boolean.parseBoolean(
                            split[1]
                    )
            );
        }

        if (split.length >= 3) {
            builder.trail(
                    Boolean.parseBoolean(
                            split[2]
                    )
            );
        }

        if (split.length >= 4) {
            Color fade =
                    parseColor(
                            split[3]
                    );

            if (fade != null) {
                builder.withFade(
                        fade
                );
            }
        }

        FireworkEffect effect =
                builder.build();

        item.editMeta(
                FireworkEffectMeta.class,
                meta ->
                        meta.setEffect(
                                effect
                        )
        );

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Generic PDC
     * ------------------------------------------------------------------------
     *
     * Generic PDC belongs to an ItemStack and is still
     * useful.
     *
     * Menu/inventory identity DOES NOT belong here.
     */

    public <P, C> ItemWrapper persistentData(
            NamespacedKey key,
            PersistentDataType<P, C> type,
            C value
    ) {
        Objects.requireNonNull(
                key,
                "key"
        );

        Objects.requireNonNull(
                type,
                "type"
        );

        checkItem();

        item.editMeta(
                meta -> {
                    if (value == null) {
                        meta
                                .getPersistentDataContainer()
                                .remove(key);

                        return;
                    }

                    meta
                            .getPersistentDataContainer()
                            .set(
                                    key,
                                    type,
                                    value
                            );
                }
        );

        return this;
    }

    public ItemWrapper removePersistentData(
            NamespacedKey key
    ) {
        Objects.requireNonNull(
                key,
                "key"
        );

        checkItem();

        item.editMeta(
                meta ->
                        meta
                                .getPersistentDataContainer()
                                .remove(key)
        );

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Configuration loading
     * ------------------------------------------------------------------------
     */

    public static ItemWrapper fromData(
            String material,
            int amount,
            String name,
            List<String> lore,
            List<String> enchantments
    ) {
        return new ItemWrapper(
                material,
                amount,
                name,
                lore,
                enchantments
        );
    }

    public static ItemWrapper fromData(
            String material,
            int amount,
            String name,
            List<String> lore
    ) {
        return fromData(
                material,
                amount,
                name,
                lore,
                Collections.emptyList()
        );
    }

    public static ItemWrapper fromData(
            String material,
            String name,
            List<String> lore
    ) {
        return fromData(
                material,
                1,
                name,
                lore,
                Collections.emptyList()
        );
    }

    public static ItemWrapper fromData(
            String material,
            int amount,
            String name
    ) {
        return fromData(
                material,
                amount,
                name,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static ItemWrapper fromData(
            String material,
            String name
    ) {
        return fromData(
                material,
                1,
                name,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static ItemWrapper fromData(
            String material
    ) {
        return fromData(
                material,
                1,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static ItemWrapper fromData(
            String material,
            int amount
    ) {
        return fromData(
                material,
                amount,
                null,
                Collections.emptyList(),
                Collections.emptyList()
        );
    }

    public static ItemWrapper fromData(
            ConfigurationHandle configuration,
            String path,
            TextReplacer replacer
    ) {
        if (configuration == null) {
            return fromData(
                    "POTION"
            );
        }

        return fromData(
                configuration.getSection(
                        path
                ),
                replacer
        );
    }

    public static ItemWrapper fromData(
            ConfigurationHandle configuration,
            String path
    ) {
        return fromData(
                configuration,
                path,
                TextReplacer.EMPTY
        );
    }

    public static ItemWrapper fromData(
            ConfigurationHandle configuration
    ) {
        return fromData(
                configuration,
                TextReplacer.EMPTY
        );
    }

    public static ItemWrapper fromData(
            ConfigurationHandle configuration,
            TextReplacer replacer
    ) {
        if (configuration == null) {
            return fromData(
                    "POTION"
            );
        }

        TextReplacer effectiveReplacer =
                replacer == null
                        ? TextReplacer.EMPTY
                        : replacer;

        String material =
                effectiveReplacer.apply(
                        configuration.getString(
                                "material",
                                "POTION"
                        )
                );

        String name =
                configuration.contains(
                        "name"
                )
                        ? effectiveReplacer.apply(
                        configuration.getString(
                                "name"
                        )
                )
                        : null;

        List<String> lore =
                replaceList(
                        effectiveReplacer,
                        configuration.getStringList(
                                "lore"
                        )
                );

        List<String> enchantments =
                replaceList(
                        effectiveReplacer,
                        configuration.getStringList(
                                "enchantments"
                        )
                );

        ItemWrapper wrapper =
                fromData(
                        material,
                        configuration.getInt(
                                "amount",
                                1
                        ),
                        name,
                        lore,
                        enchantments
                );

        /*
         * Modern explicit skin field.
         *
         * Old:
         * material: "texture:..."
         *
         * is also still supported by parseItemStack().
         */
        if (
                configuration.contains(
                        "skin"
                )
        ) {
            wrapper.skin(
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "skin"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "damage"
                )
        ) {
            wrapper.damage(
                    configuration.getInt(
                            "damage",
                            0
                    )
            );
        }

        if (
                configuration.contains(
                        "unbreakable"
                )
        ) {
            wrapper.unbreakable(
                    configuration.getBoolean(
                            "unbreakable",
                            false
                    )
            );
        }

        if (
                configuration.contains(
                        "custom-model-data"
                )
        ) {
            wrapper.customModelData(
                    configuration.getInt(
                            "custom-model-data",
                            0
                    )
            );
        }

        if (
                configuration.contains(
                        "item-flags"
                )
        ) {
            wrapper.itemFlags(
                    configuration.getStringList(
                            "item-flags"
                    )
            );
        }

        if (
                configuration.contains(
                        "stored-enchantments"
                )
        ) {
            wrapper.storedEnchantments(
                    replaceList(
                            effectiveReplacer,
                            configuration.getStringList(
                                    "stored-enchantments"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "armor-color"
                )
        ) {
            wrapper.armorMeta(
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "armor-color",
                                    "0,0,0"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "armor-trim.material"
                )
        ) {
            wrapper.armorTrim(
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "armor-trim.material",
                                    "diamond"
                            )
                    ),
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "armor-trim.pattern",
                                    "coast"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "potion-type"
                ) ||
                        configuration.contains(
                                "potion-color"
                        )
        ) {
            String potionType =
                    configuration.contains(
                            "potion-type"
                    )
                            ? effectiveReplacer.apply(
                            configuration.getString(
                                    "potion-type"
                            )
                    )
                            : null;

            String potionColor =
                    configuration.contains(
                            "potion-color"
                    )
                            ? effectiveReplacer.apply(
                            configuration.getString(
                                    "potion-color"
                            )
                    )
                            : null;

            wrapper.potionMeta(
                    potionType,
                    potionColor
            );
        }

        if (
                configuration.contains(
                        "ominous-level"
                )
        ) {
            wrapper.ominousLevel(
                    configuration.getInt(
                            "ominous-level",
                            1
                    )
            );
        }

        if (
                configuration.contains(
                        "banner-patterns"
                )
        ) {
            wrapper.bannerPatterns(
                    replaceList(
                            effectiveReplacer,
                            configuration.getStringList(
                                    "banner-patterns"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "book"
                )
        ) {
            String title =
                    configuration.contains(
                            "book.title"
                    )
                            ? effectiveReplacer.apply(
                            configuration.getString(
                                    "book.title"
                            )
                    )
                            : null;

            String author =
                    configuration.contains(
                            "book.author"
                    )
                            ? effectiveReplacer.apply(
                            configuration.getString(
                                    "book.author"
                            )
                    )
                            : null;

            List<String> pages =
                    replaceList(
                            effectiveReplacer,
                            configuration.getStringList(
                                    "book.pages"
                            )
                    );

            wrapper.bookMeta(
                    title,
                    author,
                    pages
            );
        }

        if (
                configuration.contains(
                        "spawn-egg-type"
                )
        ) {
            wrapper.spawnEggMeta(
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "spawn-egg-type"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "music-instrument"
                )
        ) {
            wrapper.musicInstrument(
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "music-instrument"
                            )
                    )
            );
        }

        if (
                configuration.contains(
                        "charge-color"
                )
        ) {
            wrapper.chargeMeta(
                    effectiveReplacer.apply(
                            configuration.getString(
                                    "charge-color"
                            )
                    )
            );
        }

        /*
         * Intentionally NOT loaded here:
         *
         * slot
         * slots
         * auto-equip
         * conditions
         * actions
         * allow-item-move
         * allow-item-drop
         * allow-item-drag
         *
         * Those are container/interaction concepts.
         */

        return wrapper;
    }

    /*
     * ------------------------------------------------------------------------
     * Configuration serialization
     * ------------------------------------------------------------------------
     */

    public ItemWrapper saveAt(
            ConfigurationHandle section
    ) {
        Objects.requireNonNull(
                section,
                "section"
        );

        checkItem();

        clearSerializedItemData(
                section
        );

        ItemMeta meta =
                item.getItemMeta();

        /*
         * Always serialize the real material.
         *
         * Legacy:
         *
         * material: texture:...
         *
         * will become:
         *
         * material: PLAYER_HEAD
         * skin: texture:...
         *
         * after the first save.
         */
        section.set(
                "material",
                item.getType()
                        .name()
        );

        section.set(
                "amount",
                item.getAmount()
        );

        if (skinSource != null) {
            section.set(
                    "skin",
                    skinSource
            );
        }

        if (meta == null) {
            return this;
        }

        /*
         * Prefer templates because they preserve
         * placeholders.
         */
        if (rawName != null) {
            section.set(
                    "name",
                    rawName
            );

        } else if (
                meta.hasDisplayName() &&
                        meta.displayName() != null
        ) {
            section.set(
                    "name",
                    MINI_MESSAGE.serialize(
                            Objects.requireNonNull(
                                    meta.displayName()
                            )
                    )
            );
        }

        if (!rawLore.isEmpty()) {
            section.set(
                    "lore",
                    List.copyOf(
                            rawLore
                    )
            );

        } else if (
                meta.hasLore() &&
                        meta.lore() != null
        ) {
            section.set(
                    "lore",
                    Objects.requireNonNull(
                                    meta.lore()
                            )
                            .stream()
                            .map(
                                    MINI_MESSAGE::serialize
                            )
                            .toList()
            );
        }

        if (meta.isUnbreakable()) {
            section.set(
                    "unbreakable",
                    true
            );
        }

        if (
                meta.hasCustomModelData()
        ) {
            section.set(
                    "custom-model-data",
                    meta.getCustomModelData()
            );
        }

        if (
                !meta.getEnchants()
                        .isEmpty()
        ) {
            section.set(
                    "enchantments",
                    meta.getEnchants()
                            .entrySet()
                            .stream()
                            .map(entry ->
                                    entry
                                            .getKey()
                                            .getKey()
                                            .toString()
                                            + ","
                                            + entry.getValue()
                            )
                            .toList()
            );
        }

        if (
                !meta.getItemFlags()
                        .isEmpty()
        ) {
            section.set(
                    "item-flags",
                    meta.getItemFlags()
                            .stream()
                            .map(
                                    Enum::name
                            )
                            .toList()
            );
        }

        if (
                meta instanceof Damageable damageable &&
                        damageable.hasDamage()
        ) {
            section.set(
                    "damage",
                    damageable.getDamage()
            );
        }

        if (
                meta instanceof ArmorMeta armorMeta &&
                        armorMeta.hasTrim() &&
                        armorMeta.getTrim() != null
        ) {
            ArmorTrim trim =
                    Objects.requireNonNull(
                            armorMeta.getTrim()
                    );

            section.set(
                    "armor-trim.material",
                    trim
                            .getMaterial()
                            .getKey()
                            .toString()
            );

            section.set(
                    "armor-trim.pattern",
                    trim
                            .getPattern()
                            .getKey()
                            .toString()
            );
        }

        if (
                meta instanceof LeatherArmorMeta leather
        ) {
            section.set(
                    "armor-color",
                    serializeColor(
                            leather.getColor()
                    )
            );
        }

        if (
                meta instanceof PotionMeta potion
        ) {
            if (
                    potion.getBasePotionType()
                            != null
            ) {
                section.set(
                        "potion-type",
                        Objects.requireNonNull(
                                        potion
                                                .getBasePotionType()
                                )
                                .getKey()
                                .toString()
                );
            }

            if (
                    potion.hasColor() &&
                            potion.getColor()
                                    != null
            ) {
                section.set(
                        "potion-color",
                        serializeColor(
                                Objects.requireNonNull(
                                        potion.getColor()
                                )
                        )
                );
            }
        }

        if (
                meta instanceof EnchantmentStorageMeta stored &&
                        stored.hasStoredEnchants()
        ) {
            section.set(
                    "stored-enchantments",
                    stored
                            .getStoredEnchants()
                            .entrySet()
                            .stream()
                            .map(entry ->
                                    entry
                                            .getKey()
                                            .getKey()
                                            .toString()
                                            + ","
                                            + entry.getValue()
                            )
                            .toList()
            );
        }

        if (
                meta instanceof OminousBottleMeta ominous &&
                        ominous.hasAmplifier()
        ) {
            section.set(
                    "ominous-level",
                    ominous.getAmplifier()
                            + 1
            );
        }

        if (
                meta instanceof BookMeta book
        ) {
            if (rawBookTitle != null) {
                section.set(
                        "book.title",
                        rawBookTitle
                );

            } else if (
                    book.hasTitle() &&
                            book.title() != null
            ) {
                section.set(
                        "book.title",
                        MINI_MESSAGE.serialize(
                                Objects.requireNonNull(
                                        book.title()
                                )
                        )
                );
            }

            if (rawBookAuthor != null) {
                section.set(
                        "book.author",
                        rawBookAuthor
                );

            } else if (
                    book.hasAuthor() &&
                            book.author() != null
            ) {
                section.set(
                        "book.author",
                        MINI_MESSAGE.serialize(
                                Objects.requireNonNull(
                                        book.author()
                                )
                        )
                );
            }

            if (!rawBookPages.isEmpty()) {
                section.set(
                        "book.pages",
                        List.copyOf(
                                rawBookPages
                        )
                );

            } else if (
                    book.hasPages()
            ) {
                section.set(
                        "book.pages",
                        book.pages()
                                .stream()
                                .map(
                                        MINI_MESSAGE::serialize
                                )
                                .toList()
                );
            }
        }

        if (
                meta instanceof SpawnEggMeta egg &&
                        egg.getCustomSpawnedType()
                                != null
        ) {
            section.set(
                    "spawn-egg-type",
                    Objects.requireNonNull(
                            egg.getCustomSpawnedType()
                    ).name()
            );
        }

        if (
                meta instanceof MusicInstrumentMeta instrument &&
                        instrument.getInstrument()
                                != null
        ) {
            section.set(
                    "music-instrument",
                    Objects.requireNonNull(
                                    instrument.getInstrument()
                            )
                            .getKey()
                            .toString()
            );
        }

        saveBannerPatterns(
                section,
                meta
        );

        if (
                meta instanceof FireworkEffectMeta charge &&
                        charge.hasEffect() &&
                        charge.getEffect() != null
        ) {
            section.set(
                    "charge-color",
                    serializeCharge(
                            Objects.requireNonNull(
                                    charge.getEffect()
                            )
                    )
            );
        }

        return this;
    }

    /*
     * ------------------------------------------------------------------------
     * Copy / clone
     * ------------------------------------------------------------------------
     */

    @Override
    public ItemWrapper clone() {
        return copy();
    }

    public ItemWrapper copy() {
        checkItem();

        ItemWrapper wrapper =
                new ItemWrapper(
                        item.clone(),
                        false
                );

        wrapper.rawName =
                rawName;

        wrapper.rawLore =
                new ArrayList<>(
                        rawLore
                );

        wrapper.rawBookTitle =
                rawBookTitle;

        wrapper.rawBookAuthor =
                rawBookAuthor;

        wrapper.rawBookPages =
                new ArrayList<>(
                        rawBookPages
                );

        wrapper.skinSource =
                skinSource;

        return wrapper;
    }

    public static ItemWrapper fromItem(
            ItemStack itemStack
    ) {
        return new ItemWrapper(
                itemStack
        );
    }

    /*
     * ------------------------------------------------------------------------
     * Internal helpers
     * ------------------------------------------------------------------------
     */

    private ItemStack parseItemStack(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return new ItemStack(
                    Material.POTION
            );
        }

        String clean =
                value.strip();

        /*
         * Legacy skin syntax:
         *
         * texture:...
         * textures:...
         * skin:...
         * player:...
         */
        if (
                looksLikeSkin(
                        clean
                )
        ) {
            ItemStack head =
                    new ItemStack(
                            Material.PLAYER_HEAD
                    );

            this.skinSource =
                    clean;

            fetch(
                    ItemSkinService.class
            ).applyTexture(
                    head,
                    clean
            );

            return head;
        }

        /*
         * Try normal / namespaced materials FIRST.
         *
         * This correctly supports:
         *
         * minecraft:diamond_sword
         */
        Material direct =
                Material.matchMaterial(
                        clean
                );

        if (direct != null) {
            return new ItemStack(
                    direct
            );
        }

        /*
         * Legacy:
         *
         * WOOL:14
         * DIAMOND_SWORD:5
         */
        int separator =
                clean.lastIndexOf(
                        ':'
                );

        if (
                separator > 0 &&
                        separator <
                                clean.length() - 1
        ) {
            String possibleDamage =
                    clean.substring(
                            separator + 1
                    );

            if (
                    Tools.isInteger(
                            possibleDamage
                    )
            ) {
                String materialName =
                        clean.substring(
                                0,
                                separator
                        );

                Material material =
                        parseMaterial(
                                materialName
                        );

                ItemStack stack =
                        new ItemStack(
                                material
                        );

                int damage =
                        Tools.toInteger(
                                possibleDamage,
                                0
                        );

                stack.editMeta(
                        Damageable.class,
                        meta ->
                                meta.setDamage(
                                        Math.max(
                                                0,
                                                damage
                                        )
                                )
                );

                return stack;
            }
        }

        return new ItemStack(
                parseMaterial(
                        clean
                )
        );
    }

    private Material parseMaterial(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return Material.POTION;
        }

        Material material =
                Material.matchMaterial(
                        value.strip()
                );

        if (material != null) {
            return material;
        }

        try {
            return Material.valueOf(
                    value
                            .strip()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (
                IllegalArgumentException exception
        ) {
            getLogger().error(
                    "Invalid material: "
                            + value
                            + ". Using POTION as fallback."
            );

            return Material.POTION;
        }
    }

    private PatternType parsePatternType(
            String value
    ) {
        NamespacedKey key =
                parseKey(
                        value
                );

        if (key != null) {
            PatternType modern =
                    Registry.BANNER_PATTERN
                            .get(key);

            if (modern != null) {
                return modern;
            }
        }

        /*
         * Compatibility with old enum-like names.
         */
        try {
            return PatternType.valueOf(
                    value
                            .strip()
                            .toUpperCase(
                                    Locale.ROOT
                            )
            );

        } catch (
                IllegalArgumentException ignored
        ) {
            return null;
        }
    }

    private NamespacedKey parseKey(
            String value
    ) {
        if (
                value == null ||
                        value.isBlank()
        ) {
            return null;
        }

        String normalized =
                value
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        if (
                normalized.contains(
                        ":"
                )
        ) {
            return NamespacedKey.fromString(
                    normalized
            );
        }

        return NamespacedKey.minecraft(
                normalized
        );
    }

    private Color parseColor(
            String input
    ) {
        if (
                input == null ||
                        input.isBlank()
        ) {
            return null;
        }

        String value =
                input.strip();

        try {
            if (
                    value.startsWith("#") &&
                            value.length() == 7
            ) {
                return Color.fromRGB(
                        Integer.parseInt(
                                value.substring(1),
                                16
                        )
                );
            }

            if (value.contains(",")) {
                String[] split =
                        value
                                .replace(
                                        " ",
                                        ""
                                )
                                .split(
                                        ",",
                                        3
                                );

                int red =
                        split.length >= 1
                                ? Tools.toInteger(
                                split[0],
                                0
                        )
                                : 0;

                int green =
                        split.length >= 2
                                ? Tools.toInteger(
                                split[1],
                                0
                        )
                                : 0;

                int blue =
                        split.length >= 3
                                ? Tools.toInteger(
                                split[2],
                                0
                        )
                                : 0;

                return Color.fromRGB(
                        clampColor(
                                red
                        ),
                        clampColor(
                                green
                        ),
                        clampColor(
                                blue
                        )
                );
            }

            int rgb =
                    JavaColorUtils
                            .getColor(value)
                            .getRGB()
                            & 0xFFFFFF;

            return Color.fromRGB(
                    rgb
            );

        } catch (
                Exception exception
        ) {
            getLogger().error(
                    "Invalid color: "
                            + input
            );

            return null;
        }
    }

    private int clampColor(
            int value
    ) {
        return Math.max(
                0,
                Math.min(
                        255,
                        value
                )
        );
    }

    private String serializeColor(
            Color color
    ) {
        return color.getRed()
                + ", "
                + color.getGreen()
                + ", "
                + color.getBlue();
    }

    private String serializeCharge(
            FireworkEffect effect
    ) {
        Color primary =
                effect.getColors()
                        .isEmpty()
                        ? Color.WHITE
                        : effect.getColors()
                          .getFirst();

        StringBuilder builder =
                new StringBuilder(
                        toHex(primary)
                );

        builder.append(':')
                .append(
                        effect.hasFlicker()
                );

        builder.append(':')
                .append(
                        effect.hasTrail()
                );

        if (
                !effect.getFadeColors()
                        .isEmpty()
        ) {
            builder.append(':')
                    .append(
                            toHex(
                                    effect
                                            .getFadeColors()
                                            .getFirst()
                            )
                    );
        }

        return builder.toString();
    }

    private String toHex(
            Color color
    ) {
        return String.format(
                "#%02X%02X%02X",
                color.getRed(),
                color.getGreen(),
                color.getBlue()
        );
    }

    private boolean looksLikeSkin(
            String value
    ) {
        String lower =
                value
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        return lower.startsWith(
                "texture:"
        ) ||
                lower.startsWith(
                        "texture;"
                ) ||
                lower.startsWith(
                        "textures:"
                ) ||
                lower.startsWith(
                        "textures;"
                ) ||
                lower.startsWith(
                        "skin:"
                ) ||
                lower.startsWith(
                        "skin;"
                ) ||
                lower.startsWith(
                        "player:"
                ) ||
                lower.startsWith(
                        "player;"
                );
    }

    private void captureTemplates() {
        checkItem();

        ItemMeta meta =
                item.getItemMeta();

        if (meta == null) {
            return;
        }

        if (
                meta.hasDisplayName() &&
                        meta.displayName() != null
        ) {
            rawName =
                    MINI_MESSAGE.serialize(
                            Objects.requireNonNull(
                                    meta.displayName()
                            )
                    );
        }

        if (
                meta.hasLore() &&
                        meta.lore() != null
        ) {
            rawLore =
                    Objects.requireNonNull(
                                    meta.lore()
                            )
                            .stream()
                            .map(
                                    MINI_MESSAGE::serialize
                            )
                            .collect(
                                    Collectors.toCollection(
                                            ArrayList::new
                                    )
                            );
        }

        if (
                meta instanceof BookMeta book
        ) {
            if (
                    book.hasTitle() &&
                            book.title() != null
            ) {
                rawBookTitle =
                        MINI_MESSAGE.serialize(
                                Objects.requireNonNull(
                                        book.title()
                                )
                        );
            }

            if (
                    book.hasAuthor() &&
                            book.author() != null
            ) {
                rawBookAuthor =
                        MINI_MESSAGE.serialize(
                                Objects.requireNonNull(
                                        book.author()
                                )
                        );
            }

            if (
                    book.hasPages()
            ) {
                rawBookPages =
                        book.pages()
                                .stream()
                                .map(
                                        MINI_MESSAGE::serialize
                                )
                                .collect(
                                        Collectors.toCollection(
                                                ArrayList::new
                                        )
                                );
            }
        }
    }

    private void saveBannerPatterns(
            ConfigurationHandle section,
            ItemMeta meta
    ) {
        List<Pattern> patterns =
                null;

        if (
                meta instanceof BannerMeta bannerMeta &&
                        !bannerMeta.getPatterns()
                                .isEmpty()
        ) {
            patterns =
                    bannerMeta.getPatterns();

        } else if (
                meta instanceof BlockStateMeta blockStateMeta &&
                        blockStateMeta.getBlockState()
                                instanceof Banner banner &&
                        !banner.getPatterns()
                                .isEmpty()
        ) {
            patterns =
                    banner.getPatterns();
        }

        if (
                patterns == null ||
                        patterns.isEmpty()
        ) {
            return;
        }

        section.set(
                "banner-patterns",
                patterns.stream()
                        .map(pattern ->
                                pattern
                                        .getColor()
                                        .name()
                                        + ","
                                        + pattern
                                        .getPattern()
                                        .getKey()
                                        .toString()
                        )
                        .toList()
        );
    }

    private void clearSerializedItemData(
            ConfigurationHandle section
    ) {
        for (
                String key :
                SERIALIZED_KEYS
        ) {
            section.set(
                    key,
                    null
            );
        }
    }

    private void checkItem() {
        if (
                item == null ||
                        item.getType()
                                .isAir()
        ) {
            item =
                    new ItemStack(
                            Material.POTION
                    );
        }
    }

    private static List<String> replaceList(
            TextReplacer replacer,
            Collection<String> source
    ) {
        if (
                source == null ||
                        source.isEmpty()
        ) {
            return new ArrayList<>();
        }

        List<String> result =
                new ArrayList<>(
                        source.size()
                );

        for (String value : source) {
            result.add(
                    replacer.apply(
                            value
                    )
            );
        }

        return result;
    }
}