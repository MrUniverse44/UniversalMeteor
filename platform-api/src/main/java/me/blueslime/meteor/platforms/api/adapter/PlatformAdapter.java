package me.blueslime.meteor.platforms.api.adapter;

import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.io.File;
import java.nio.file.Path;

public interface PlatformAdapter<P extends PlatformPlugin, I> {

    PlatformAdapterBuilder<P, I> createAdapter(Class<P> clazz, File directory, Object adapter);

    default PlatformAdapterBuilder<P, I> createAdapter(Class<P> clazz, Path path, Object adapter) {
        return createAdapter(clazz, path.toFile(), adapter);
    }

}
