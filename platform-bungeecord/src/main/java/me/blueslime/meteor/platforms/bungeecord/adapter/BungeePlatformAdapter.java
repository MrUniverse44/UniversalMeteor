package me.blueslime.meteor.platforms.bungeecord.adapter;

import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

public interface BungeePlatformAdapter<P extends PlatformPlugin<Listener>> extends PlatformAdapter<P, Plugin, Listener> {

    @Override
    default PlatformAdapterBuilder<P, Plugin, Listener> createAdapter(Class<P> clazz) {
        return new BungeePlatformAdapterBuilder<>(clazz);
    }

}
