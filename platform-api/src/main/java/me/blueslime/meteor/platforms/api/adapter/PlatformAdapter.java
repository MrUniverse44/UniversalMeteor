package me.blueslime.meteor.platforms.api.adapter;

import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.io.File;
import java.nio.file.Path;

public interface PlatformAdapter<P extends PlatformPlugin<L>, I, L> {

    PlatformAdapterBuilder<P, I, L> createAdapter(Class<P> clazz, File adapter);

    default PlatformAdapterBuilder<P, I, L> createAdapter(Class<P> clazz, Path path) {
        return createAdapter(clazz, path.toFile());
    }

}
