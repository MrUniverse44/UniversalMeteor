package me.blueslime.meteor.platforms.api.adapter;

import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

public interface PlatformAdapter<P extends PlatformPlugin<L>, I, L> {

    PlatformAdapterBuilder<P, I, L> createAdapter(Class<P> clazz, P plugin);

}
