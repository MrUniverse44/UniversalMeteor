package me.blueslime.meteor.platforms.api.data;

public abstract class PluginData {
    private final String description;
    private final String pluginName;
    private final String[] authors;
    private final String version;

    public PluginData(String pluginName, String[] authors, String description, String version) {
        this.description = description;
        this.pluginName = pluginName;
        this.authors = authors;
        this.version = version;
    }

    public String getPluginName() {
        return pluginName;
    }

    public String[] getAuthors() {
        return authors;
    }

    public String getDescription() {
        return description;
    }

    public String getVersion() {
        return version;
    }
}
