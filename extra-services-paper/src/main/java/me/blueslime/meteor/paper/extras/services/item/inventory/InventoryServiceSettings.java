package me.blueslime.meteor.paper.extras.services.item.inventory;

public class InventoryServiceSettings {
    private boolean loadConfig = true;
    private String resourceName = "/items.yml";
    private String fileName = "items.yml";

    private InventoryServiceSettings() {

    }

    public static InventoryServiceSettings builder() {
        return new InventoryServiceSettings();
    }

    /**
     * Enable this entire system
     * @param loadConfig will load files depending if this is enabled or not
     * @return settings instance
     */
    public InventoryServiceSettings loadConfigurations(boolean loadConfig) {
        this.loadConfig = loadConfig;
        return this;
    }

    /**
     * Replaces default file to be generated
     * @param resourceName to load the final file
     * @return settings instance
     */
    public InventoryServiceSettings resourcePath(String resourceName) {
        this.resourceName = resourceName;
        return this;
    }

    /**
     * Replaces default file name to be generated
     * @param fileName for the final file that will be loaded using the resource path
     * @return settings instance
     */
    public InventoryServiceSettings fileName(String fileName) {
        this.fileName = fileName;
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
}
