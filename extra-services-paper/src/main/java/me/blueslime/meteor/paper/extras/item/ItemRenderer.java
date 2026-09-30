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

public final class ItemRenderer implements PlatformService {

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
     * Useful for MenuSession.
     *
     * When MenuSession is already inside ONE sync
     * operation, every item can be rendered without
     * producing another bridge request.
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

    private ItemStack renderPrepared(
            ItemDefinition definition,
            Player player,
            PreparedItem prepared
    ) {
        ItemStack item =
                definition.createBaseItem();

        if (
                prepared.name() != null ||
                        !prepared.lore().isEmpty()
        ) {
            item.editMeta(meta -> {
                if (
                        prepared.name() != null
                ) {
                    meta.displayName(
                            ComponentRenderer.translate(
                                    resolvePapi(
                                            player,
                                            prepared.name()
                                    )
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

        if (
                prepared.bookTitle() != null ||
                        prepared.bookAuthor() != null ||
                        !prepared.bookPages()
                                .isEmpty()
        ) {
            item.editMeta(
                    BookMeta.class,
                    meta -> {
                        if (
                                prepared.bookTitle()
                                        != null
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
                                prepared.bookAuthor()
                                        != null
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

        /*
         * Reapply skin only when its resolved source
         * differs from the stored static template.
         *
         * Useful for:
         *
         * skin: "player:<player>"
         */
        if (
                prepared.skin() != null &&
                        !prepared.skin().isBlank() &&
                        !prepared.skin().equals(
                                definition.skinTemplate()
                        )
        ) {
            skinService.applyTexture(
                    item,
                    prepared.skin()
            );
        }

        return item;
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
        return textResolver.resolvePlaceholders(
                player,
                input
        );
    }

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
                    List.copyOf(
                            lore
                    );

            bookPages =
                    List.copyOf(
                            bookPages
                    );
        }
    }
}