package me.blueslime.meteor.paper.extras;

import me.blueslime.meteor.implementation.service.Service;

import me.blueslime.meteor.paper.extras.actions.ActionService;
import me.blueslime.meteor.paper.extras.animation.AnimationService;

import me.blueslime.meteor.paper.extras.conditions.ConditionService;

import me.blueslime.meteor.paper.extras.inventories.InventoryService;
import me.blueslime.meteor.paper.extras.inventories.InventoryServiceSettings;

import me.blueslime.meteor.paper.extras.item.ItemRenderer;
import me.blueslime.meteor.paper.extras.item.skin.ItemSkinService;

import me.blueslime.meteor.paper.extras.languages.handlers.DynamicLanguageService;
import me.blueslime.meteor.paper.extras.languages.handlers.StaticLanguageService;

import me.blueslime.meteor.paper.extras.menus.MenuService;
import me.blueslime.meteor.paper.extras.menus.MenuServiceSettings;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;

import me.blueslime.meteor.paper.extras.scoreboard.ScoreboardService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class PaperExtraServices {

    private PaperExtraServices() {}

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Legacy helper.
     */
    public static Service findLocales(
            boolean dynamic,
            String... supportedLanguages
    ) {
        return dynamic
                ? new DynamicLanguageService(
                supportedLanguages
        )
                : new StaticLanguageService(
                supportedLanguages.length >= 1
                ? supportedLanguages[0]
                : "en_US"
        );
    }

    /**
     * Compatibility with the old API.
     */
    @Deprecated
    public static Service[] findAll(
            InventoryServiceSettings inventorySettings,
            String scoreboardFileName,
            String scoreboardResourcePath
    ) {
        return builder()
                .inventories(
                        inventorySettings
                )
                .menus()
                .scoreboards(
                        scoreboardFileName,
                        scoreboardResourcePath
                )
                .build();
    }

    /**
     * Compatibility with the old API.
     */
    @Deprecated
    public static Service[] findAllWithoutScoreboards(
            InventoryServiceSettings inventorySettings
    ) {
        return builder()
                .inventories(
                        inventorySettings
                )
                .menus()
                .build();
    }

    public static final class Builder {

        private Service languageService;

        private boolean runtime;

        private boolean conditions;

        private boolean actions;

        private boolean itemSkin;

        private boolean itemRenderer;

        private boolean animations;

        private InventoryServiceSettings inventorySettings;

        private MenuServiceSettings menuSettings;

        private boolean menus;

        private String scoreboardFileName;

        private String scoreboardResourcePath;

        private boolean scoreboards;

        private Builder() {}

        /*
         * ------------------------------------------------------------
         * Language
         * ------------------------------------------------------------
         */

        public Builder languages(
                boolean dynamic,
                String... supportedLanguages
        ) {
            this.languageService =
                    PaperExtraServices.findLocales(
                            dynamic,
                            supportedLanguages
                    );

            return this;
        }

        public Builder languages(
                Service service
        ) {
            this.languageService =
                    Objects.requireNonNull(
                            service
                    );

            return this;
        }

        /*
         * ------------------------------------------------------------
         * Low-level features
         * ------------------------------------------------------------
         */

        public Builder runtime() {
            this.runtime =
                    true;

            return this;
        }

        public Builder conditions() {
            requireRuntime();

            this.conditions =
                    true;

            return this;
        }

        public Builder actions() {
            requireRuntime();

            this.actions =
                    true;

            return this;
        }

        /**
         * ItemWrapper + ItemRenderer + skin support.
         */
        public Builder items() {
            requireRuntime();

            this.itemSkin =
                    true;

            this.itemRenderer =
                    true;

            return this;
        }

        public Builder animations() {
            this.animations =
                    true;

            return this;
        }

        /*
         * ------------------------------------------------------------
         * High-level features
         * ------------------------------------------------------------
         */

        public Builder inventories(
                InventoryServiceSettings settings
        ) {
            this.inventorySettings =
                    Objects.requireNonNull(
                            settings
                    );

            requireInteractiveItems();

            return this;
        }

        public Builder menus() {
            return menus(
                    MenuServiceSettings
                            .builder()
                            .validate()
            );
        }

        public Builder menus(
                MenuServiceSettings settings
        ) {
            this.menus =
                    true;

            this.menuSettings =
                    Objects.requireNonNull(
                            settings
                    );

            requireInteractiveItems();

            return this;
        }

        public Builder scoreboards(
                String fileName,
                String resourcePath
        ) {
            this.scoreboards =
                    true;

            this.scoreboardFileName =
                    Objects.requireNonNull(
                            fileName
                    );

            this.scoreboardResourcePath =
                    Objects.requireNonNull(
                            resourcePath
                    );

            /*
             * Scoreboards use the shared condition
             * runtime but nothing from Actions/Items.
             */
            requireRuntime();

            this.conditions =
                    true;

            return this;
        }

        /*
         * ------------------------------------------------------------
         * Build
         * ------------------------------------------------------------
         */

        public Service[] build() {
            List<Service> services =
                    new ArrayList<>();

            /*
             * 0. Optional localization.
             *
             * Consumers can query it during initialize().
             */
            if (languageService != null) {
                services.add(
                        languageService
                );
            }

            /*
             * 1. Shared execution infrastructure.
             */
            if (runtime) {
                services.add(
                        new ExecutionRuntimeService()
                );
            }

            /*
             * 2. Independent item primitive.
             *
             * ItemRenderer depends on this.
             */
            if (itemSkin) {
                services.add(
                        new ItemSkinService()
                );
            }

            /*
             * 3. Compiled runtimes.
             */
            if (conditions) {
                services.add(
                        new ConditionService()
                );
            }

            if (actions) {
                services.add(
                        new ActionService()
                );
            }

            /*
             * 4. Item rendering infrastructure.
             */
            if (itemRenderer) {
                services.add(
                        new ItemRenderer()
                );
            }

            /*
             * 5. Global animation runtime.
             */
            if (animations) {
                services.add(
                        new AnimationService()
                );
            }

            /*
             * 6. Final consumers.
             */
            if (inventorySettings != null) {
                services.add(
                        new InventoryService(
                                inventorySettings
                        )
                );
            }

            if (menus) {
                services.add(
                        new MenuService(
                                menuSettings
                        )
                );
            }

            if (scoreboards) {
                services.add(
                        new ScoreboardService(
                                scoreboardFileName,
                                scoreboardResourcePath
                        )
                );
            }

            return services.toArray(
                    Service[]::new
            );
        }

        private void requireRuntime() {
            this.runtime =
                    true;
        }

        private void requireInteractiveItems() {
            requireRuntime();

            this.conditions =
                    true;

            this.actions =
                    true;

            this.itemSkin =
                    true;

            this.itemRenderer =
                    true;

            this.animations =
                    true;
        }
    }
}