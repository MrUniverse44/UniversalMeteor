package me.blueslime.meteor.paper.extras.inventories;

import me.blueslime.meteor.paper.extras.inventories.compiler.InventoryLayout;

public final class InventoryServiceSettings {

    private boolean loadConfig =
            true;

    private String resourceName =
            "/items.yml";

    private String fileName =
            "items.yml";

    private String rootPath =
            "items";

    private InventoryLayout layout =
            InventoryLayout.AUTO;

    private boolean refreshSessionsOnReload =
            true;

    private boolean clearOnQuit =
            true;

    private boolean clearOnShutdown =
            true;

    private int maxConcurrentApplies =
            128;

    private InventoryServiceSettings() {}

    public static InventoryServiceSettings builder() {
        return new InventoryServiceSettings();
    }

    public InventoryServiceSettings loadConfigurations(
            boolean value
    ) {
        this.loadConfig =
                value;

        return this;
    }

    public InventoryServiceSettings resourcePath(
            String value
    ) {
        this.resourceName =
                value;

        return this;
    }

    public InventoryServiceSettings fileName(
            String value
    ) {
        this.fileName =
                value;

        return this;
    }

    public InventoryServiceSettings rootPath(
            String value
    ) {
        this.rootPath =
                value;

        return this;
    }

    public InventoryServiceSettings layout(
            InventoryLayout value
    ) {
        this.layout =
                value;

        return this;
    }

    public InventoryServiceSettings refreshSessionsOnReload(
            boolean value
    ) {
        this.refreshSessionsOnReload =
                value;

        return this;
    }

    public InventoryServiceSettings clearOnQuit(
            boolean value
    ) {
        this.clearOnQuit =
                value;

        return this;
    }

    public InventoryServiceSettings clearOnShutdown(
            boolean value
    ) {
        this.clearOnShutdown =
                value;

        return this;
    }

    public InventoryServiceSettings maxConcurrentApplies(
            int value
    ) {
        this.maxConcurrentApplies =
                value;

        return this;
    }

    public InventoryServiceSettings validate() {
        if (
                fileName == null ||
                        fileName.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "fileName cannot be empty"
            );
        }

        if (
                rootPath == null ||
                        rootPath.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "rootPath cannot be empty"
            );
        }

        if (
                maxConcurrentApplies <= 0
        ) {
            throw new IllegalArgumentException(
                    "maxConcurrentApplies must be > 0"
            );
        }

        return this;
    }

    public boolean shouldLoadConfigurations() {
        return loadConfig;
    }

    public String getResourcePath() {
        return resourceName;
    }

    public String getFileName() {
        return fileName;
    }

    public String getRootPath() {
        return rootPath;
    }

    public InventoryLayout getLayout() {
        return layout;
    }

    public boolean shouldRefreshSessionsOnReload() {
        return refreshSessionsOnReload;
    }

    public boolean shouldClearOnQuit() {
        return clearOnQuit;
    }

    public boolean shouldClearOnShutdown() {
        return clearOnShutdown;
    }

    public int getMaxConcurrentApplies() {
        return maxConcurrentApplies;
    }
}
