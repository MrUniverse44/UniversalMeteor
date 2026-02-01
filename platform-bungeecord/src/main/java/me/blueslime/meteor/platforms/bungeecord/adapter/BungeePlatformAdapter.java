package me.blueslime.meteor.platforms.bungeecord.adapter;

import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.plugin.Plugin;

import java.io.File;

public interface BungeePlatformAdapter<P extends PlatformPlugin<Listener>> extends PlatformAdapter<P, Plugin, Listener> {

    @Override
    default PlatformAdapterBuilder<P, Plugin, Listener> createAdapter(Class<P> clazz, File directory) {
        return new BungeePlatformAdapterBuilder<>(clazz)
            .register(File.class, "directory", directory, true);
    }

}
