package me.blueslime.meteor.platforms.velocity.adapter;

import com.velocitypowered.api.proxy.ProxyServer;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.io.File;

public interface VelocityPlatformAdapter<P extends PlatformPlugin<Object>> extends PlatformAdapter<P, ProxyServer, Object> {

    @Override
    default PlatformAdapterBuilder<P, ProxyServer, Object> createAdapter(Class<P> clazz, File directory) {
        return new VelocityPlatformAdapterBuilder<>(clazz)
            .register(File.class, "directory", directory, true);
    }

}
