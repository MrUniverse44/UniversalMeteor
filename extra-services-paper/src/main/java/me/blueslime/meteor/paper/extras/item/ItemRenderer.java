package me.blueslime.meteor.paper.extras.item;

import me.blueslime.meteor.color.renders.ComponentRenderer;

import me.blueslime.meteor.paper.extras.item.definition.ItemDefinition;
import me.blueslime.meteor.paper.extras.item.skin.ItemSkinService;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.Bukkit;

import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class ItemRenderer
        implements PlatformService {

    private final ExecutionTextResolver textResolver;

    private final ItemSkinService skinService;

    public ItemRenderer() {
        ExecutionRuntimeService runtime =
                fetch(
                        ExecutionRuntimeService.class
                );

        this.textResolver =
                runtime.text();

        this.skinService =
                fetch(
                        ItemSkinService.class
                );
    }

    public ItemStack render(
            ItemDefinition definition,
            ExecutionContext context
    ) {
        Objects.requireNonNull(
                definition,
                "definition"
        );

        Objects.requireNonNull(
                context,
                "context"
        );

        if (definition.staticItem()) {
            return definition.createBaseItem();
        }

        /*
         * Context resolution can happen before entering
         * Bukkit's primary thread.
         *
         * Examples:
         *
         * <player>
         * <itemId>
         * ${scopeValue}
         */
        PreparedItem prepared =
                prepare(
                        definition,
                        context
                );

        UUID playerId =
                context.get(
                        ContextKeys.PLAYER_ID
                );

        return context.waitSync(() -> {
            Player player =
                    playerId == null
                            ? null
                            : Bukkit.getPlayer(
                            playerId
                    );

            return renderPrepared(
                    definition,
                    player,
                    prepared
            );
        });
    }

    /**
     * Useful when the caller is already executing
     * on Bukkit's primary thread.
     */
    public ItemStack renderSync(
            ItemDefinition definition,
            ExecutionContext context,
            Player player
    ) {
        Objects.requireNonNull(
                definition,
                "definition"
        );

        Objects.requireNonNull(
                context,
                "context"
        );

        if (definition.staticItem()) {
            return definition.createBaseItem();
        }

        return renderPrepared(
                definition,
                player,
                prepare(
                        definition,
                        context
                )
        );
    }


    private PreparedItem prepare(
            ItemDefinition definition,
            ExecutionContext context
    ) {
        return new PreparedItem(
                resolveContext(
                        context,
                        definition.nameTemplate()
                ),

                resolveContext(
                        context,
                        definition.loreTemplates()
                ),

                resolveContext(
                        context,
                        definition.bookTitleTemplate()
                ),

                resolveContext(
                        context,
                        definition.bookAuthorTemplate()
                ),

                resolveContext(
                        context,
                        definition.bookPageTemplates()
                ),

                resolveContext(
                        context,
                        definition.skinTemplate()
                )
        );
    }

    /*
     * ------------------------------------------------------------------------
     * Final runtime render
     * ------------------------------------------------------------------------
     */

    private ItemStack renderPrepared(
            ItemDefinition definition,
            Player player,
            PreparedItem prepared
    ) {
        ItemStack item =
                definition.createBaseItem();

        applyDisplayMeta(
                item,
                player,
                prepared
        );

        applyBookMeta(
                item,
                player,
                prepared
        );

        applySkin(
                item,
                player,
                prepared.skin()
        );

        return item;
    }

    private void applyDisplayMeta(
            ItemStack item,
            Player player,
            PreparedItem prepared
    ) {
        if (
                prepared.name() == null &&
                        prepared.lore().isEmpty()
        ) {
            return;
        }

        item.editMeta(meta -> {
            if (
                    prepared.name() != null
            ) {
                String resolvedName =
                        resolvePapi(
                                player,
                                prepared.name()
                        );

                meta.displayName(
                        ComponentRenderer.translate(
                                resolvedName
                        )
                );
            }

            if (
                    !prepared.lore()
                            .isEmpty()
            ) {
                meta.lore(
                        prepared.lore()
                                .stream()
                                .map(line ->
                                        resolvePapi(
                                                player,
                                                line
                                        )
                                )
                                .map(
                                        ComponentRenderer::translate
                                )
                                .toList()
                );
            }
        });
    }

    private void applyBookMeta(
            ItemStack item,
            Player player,
            PreparedItem prepared
    ) {
        if (
                prepared.bookTitle() == null &&
                        prepared.bookAuthor() == null &&
                        prepared.bookPages().isEmpty()
        ) {
            return;
        }

        item.editMeta(
                BookMeta.class,
                meta -> {
                    if (
                            prepared.bookTitle() != null
                    ) {
                        meta.title(
                                ComponentRenderer.translate(
                                        resolvePapi(
                                                player,
                                                prepared.bookTitle()
                                        )
                                )
                        );
                    }

                    if (
                            prepared.bookAuthor() != null
                    ) {
                        meta.author(
                                ComponentRenderer.translate(
                                        resolvePapi(
                                                player,
                                                prepared.bookAuthor()
                                        )
                                )
                        );
                    }

                    if (
                            !prepared.bookPages()
                                    .isEmpty()
                    ) {
                        meta.pages(
                                prepared.bookPages()
                                        .stream()
                                        .map(page ->
                                                resolvePapi(
                                                        player,
                                                        page
                                                )
                                        )
                                        .map(
                                                ComponentRenderer::translate
                                        )
                                        .toList()
                        );
                    }
                }
        );
    }

    private void applySkin(
            ItemStack item,
            Player player,
            String skinTemplate
    ) {
        if (
                skinTemplate == null ||
                        skinTemplate.isBlank()
        ) {
            return;
        }

        /*
         * prepare() already resolved:
         *
         * <player>
         * ${scopeValue}
         * <itemId>
         * etc.
         *
         * PAPI needs the live Player and is resolved here:
         *
         * %player_name%
         * %player_world%
         * ...
         */
        String resolvedSkin =
                resolvePapi(
                        player,
                        skinTemplate
                );

        if (
                resolvedSkin == null ||
                        resolvedSkin.isBlank()
        ) {
            return;
        }

        if (
                hasUnresolvedRuntimeValue(
                        resolvedSkin
                )
        ) {
            getLogger().warn(
                    "Unable to render player head skin because "
                            + "the runtime value could not be resolved: '"
                            + resolvedSkin
                            + "'"
            );

            return;
        }

        skinService.applyTexture(
                item,
                resolvedSkin
        );
    }

    private boolean hasUnresolvedRuntimeValue(
            String value
    ) {
        return value.indexOf('%') >= 0
                ||
                value.contains("${")
                ||
                (
                        value.indexOf('<') >= 0 &&
                                value.indexOf('>') >= 0
                );
    }


    private String resolveContext(
            ExecutionContext context,
            String input
    ) {
        if (input == null) {
            return null;
        }

        return textResolver.resolveContext(
                context,
                input
        );
    }

    private List<String> resolveContext(
            ExecutionContext context,
            List<String> source
    ) {
        if (
                source == null ||
                        source.isEmpty()
        ) {
            return List.of();
        }

        List<String> result =
                new ArrayList<>(
                        source.size()
                );

        for (String line : source) {
            result.add(
                    textResolver.resolveContext(
                            context,
                            line
                    )
            );
        }

        return List.copyOf(
                result
        );
    }

    private String resolvePapi(
            Player player,
            String input
    ) {
        if (input == null) {
            return null;
        }

        return textResolver.resolvePlaceholders(
                player,
                input
        );
    }

    /*
     * ------------------------------------------------------------------------
     * Prepared runtime data
     * ------------------------------------------------------------------------
     */

    private record PreparedItem(
            String name,
            List<String> lore,

            String bookTitle,
            String bookAuthor,
            List<String> bookPages,

            String skin
    ) {

        private PreparedItem {
            lore =
                    lore == null
                            ? List.of()
                            : List.copyOf(
                            lore
                    );

            bookPages =
                    bookPages == null
                            ? List.of()
                            : List.copyOf(
                            bookPages
                    );
        }
    }
}