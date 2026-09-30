package me.blueslime.meteor.paper.extras.item.definition;

import me.blueslime.meteor.paper.extras.item.ItemWrapper;

import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

public final class ItemDefinition {

    private final ItemStack baseItem;

    private final String nameTemplate;
    private final List<String> loreTemplates;

    private final String bookTitleTemplate;
    private final String bookAuthorTemplate;
    private final List<String> bookPageTemplates;

    private final String skinTemplate;

    private final ItemRenderPolicy renderPolicy;

    private final boolean dynamic;

    private ItemDefinition(
        ItemStack baseItem,

        String nameTemplate,
        List<String> loreTemplates,

        String bookTitleTemplate,
        String bookAuthorTemplate,
        List<String> bookPageTemplates,

        String skinTemplate,

        ItemRenderPolicy renderPolicy
    ) {
        this.baseItem =
                Objects.requireNonNull(
                        baseItem,
                        "baseItem"
                ).clone();

        this.nameTemplate =
                nameTemplate;

        this.loreTemplates =
                List.copyOf(
                        loreTemplates
                );

        this.bookTitleTemplate =
                bookTitleTemplate;

        this.bookAuthorTemplate =
                bookAuthorTemplate;

        this.bookPageTemplates =
                List.copyOf(
                        bookPageTemplates
                );

        this.skinTemplate =
                skinTemplate;

        this.renderPolicy =
                Objects.requireNonNull(
                    renderPolicy,
                    "renderPolicy"
                );

        this.dynamic =
                determineDynamic();
    }

    public static ItemDefinition from(
            ItemWrapper wrapper
    ) {
        return from(
                wrapper,
                ItemRenderPolicy.AUTO
        );
    }

    public static ItemDefinition from(
            ItemWrapper wrapper,
            ItemRenderPolicy policy
    ) {
        Objects.requireNonNull(
                wrapper,
                "wrapper"
        );

        return new ItemDefinition(
                wrapper.build(),

                wrapper.getRawName(),
                wrapper.getRawLore(),

                wrapper.getRawBookTitle(),
                wrapper.getRawBookAuthor(),
                wrapper.getRawBookPages(),

                wrapper.getSkinSource(),

                policy
        );
    }

    /**
     * Creates an independent ItemStack.
     * <br>
     * Never expose the internal base ItemStack.
     */
    public ItemStack createBaseItem() {
        return baseItem.clone();
    }

    public String nameTemplate() {
        return nameTemplate;
    }

    public List<String> loreTemplates() {
        return loreTemplates;
    }

    public String bookTitleTemplate() {
        return bookTitleTemplate;
    }

    public String bookAuthorTemplate() {
        return bookAuthorTemplate;
    }

    public List<String> bookPageTemplates() {
        return bookPageTemplates;
    }

    public String skinTemplate() {
        return skinTemplate;
    }

    public ItemRenderPolicy renderPolicy() {
        return renderPolicy;
    }

    public boolean dynamic() {
        return dynamic;
    }

    public boolean staticItem() {
        return !dynamic;
    }

    public boolean hasNameTemplate() {
        return nameTemplate != null;
    }

    public boolean hasLoreTemplates() {
        return !loreTemplates.isEmpty();
    }

    public boolean hasBookTemplates() {
        return bookTitleTemplate != null ||
                bookAuthorTemplate != null ||
                !bookPageTemplates.isEmpty();
    }

    public boolean hasSkinTemplate() {
        return skinTemplate != null &&
                !skinTemplate.isBlank();
    }

    private boolean determineDynamic() {
        return switch (renderPolicy) {
            case STATIC ->
                    false;

            case DYNAMIC ->
                    true;

            case AUTO ->
                    hasRuntimeTemplates();
        };
    }

    private boolean hasRuntimeTemplates() {
        /*
         * A template being present is intentionally
         * enough for AUTO to mark the item as dynamic.
         *
         * Later ItemCompiler could become more clever
         * and distinguish static MiniMessage formatting
         * from actual runtime placeholders.
         */
        return nameTemplate != null ||
                !loreTemplates.isEmpty() ||
                bookTitleTemplate != null ||
                bookAuthorTemplate != null ||
                !bookPageTemplates.isEmpty() ||
                hasDynamicSkinTemplate();
    }

    private boolean hasDynamicSkinTemplate() {
        if (
                skinTemplate == null ||
                        skinTemplate.isBlank()
        ) {
            return false;
        }

        /*
         * Skin templates normally don't contain
         * MiniMessage, therefore these markers are
         * a reasonable dynamic indication.
         */
        return skinTemplate.contains("%") ||
                skinTemplate.contains("{") ||
                skinTemplate.contains("<player") ||
                skinTemplate.contains("<player_name");
    }
}