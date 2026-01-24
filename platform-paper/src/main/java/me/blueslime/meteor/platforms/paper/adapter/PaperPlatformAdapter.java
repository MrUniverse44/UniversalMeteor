package me.blueslime.meteor.platforms.paper.adapter;

import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public interface PaperPlatformAdapter<P extends PlatformPlugin<Listener>> extends PlatformAdapter<P, JavaPlugin, Listener> {

    @Override
    default PlatformAdapterBuilder<P, JavaPlugin, Listener> createAdapter(Class<P> clazz, P plugin) {
        return new PaperPlatformAdapterBuilder<>(clazz);
    }

}
