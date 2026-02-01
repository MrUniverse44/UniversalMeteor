package me.blueslime.meteor.platforms.spigot.adapter;

import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public interface SpigotPlatformAdapter<P extends PlatformPlugin<Listener>> extends PlatformAdapter<P, JavaPlugin, Listener> {

    @Override
    default PlatformAdapterBuilder<P, JavaPlugin, Listener> createAdapter(Class<P> clazz, File directory) {
        return new SpigotPlatformAdapterBuilder<>(clazz)
            .register(File.class, "directory", directory, true);
    }

}
