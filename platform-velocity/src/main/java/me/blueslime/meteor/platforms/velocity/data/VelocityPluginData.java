package me.blueslime.meteor.platforms.velocity.data;

import com.velocitypowered.api.plugin.PluginDescription;
import me.blueslime.meteor.platforms.api.data.PluginData;

public class VelocityPluginData extends PluginData {

    public VelocityPluginData(PluginDescription description) {
        super(description.getName().orElse("Unknown"), description.getAuthors().toArray(new String[0]), description.getDescription().orElse(""), description.getVersion().orElse(""));
    }

}
