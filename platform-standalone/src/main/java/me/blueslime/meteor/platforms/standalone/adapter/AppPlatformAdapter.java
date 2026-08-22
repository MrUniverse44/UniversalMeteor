package me.blueslime.meteor.platforms.standalone.adapter;

import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.standalone.Bootstrap;

import java.io.File;

public interface AppPlatformAdapter<P extends PlatformPlugin> extends PlatformAdapter<P, Bootstrap> {

    @Override
    default PlatformAdapterBuilder<P, Bootstrap> createAdapter(Class<P> clazz, File directory, Object adapter) {
        return null;
    }

}