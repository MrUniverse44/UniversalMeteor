package me.blueslime.meteor.paper.extras.menus;

public final class MenuServiceSettings {

    private String folder =
            "menus";

    private String extension =
            ".yml";

    private int maxConcurrentOpens =
            128;

    private boolean refreshSessionsOnReload =
            true;

    private boolean closeOnShutdown =
            true;

    private MenuServiceSettings() {}

    public static MenuServiceSettings builder() {
        return new MenuServiceSettings();
    }

    public MenuServiceSettings folder(
            String folder
    ) {
        this.folder =
                folder;

        return this;
    }

    public MenuServiceSettings extension(
            String extension
    ) {
        this.extension =
                extension.startsWith(".")
                        ? extension
                        : "." + extension;

        return this;
    }

    public MenuServiceSettings maxConcurrentOpens(
            int value
    ) {
        this.maxConcurrentOpens =
                value;

        return this;
    }

    public MenuServiceSettings refreshSessionsOnReload(
            boolean value
    ) {
        this.refreshSessionsOnReload =
                value;

        return this;
    }

    public MenuServiceSettings closeOnShutdown(
            boolean value
    ) {
        this.closeOnShutdown =
                value;

        return this;
    }

    public String folder() {
        return folder;
    }

    public String extension() {
        return extension;
    }

    public int maxConcurrentOpens() {
        return maxConcurrentOpens;
    }

    public boolean refreshSessionsOnReload() {
        return refreshSessionsOnReload;
    }

    public boolean closeOnShutdown() {
        return closeOnShutdown;
    }

    public MenuServiceSettings validate() {
        if (
                folder == null ||
                        folder.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "Menu folder cannot be empty"
            );
        }

        if (maxConcurrentOpens <= 0) {
            throw new IllegalArgumentException(
                    "maxConcurrentOpens must be > 0"
            );
        }

        return this;
    }
}