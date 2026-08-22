package me.blueslime.meteor.platforms.standalone.data;

import me.blueslime.meteor.platforms.api.data.PluginData;

public class AppPlatformData extends PluginData {

    /**
     * Creates a new AppPlatformData instance.
     * @param appName The name of the application.
     * @param description The description of the application.
     * @param version The version of the application.
     * @param authors The authors of the application.
     */
    public AppPlatformData(String appName, String description, String version, String... authors) {
        super(appName, authors, description, version);
    }
}

