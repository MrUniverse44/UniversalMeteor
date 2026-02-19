package me.blueslime.meteor.platforms.velocity.adapter;

import com.velocitypowered.api.proxy.ProxyServer;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

import java.io.File;

public interface VelocityPlatformAdapter<P extends PlatformPlugin> extends PlatformAdapter<P, ProxyServer> {

    @Override
    default PlatformAdapterBuilder<P, ProxyServer> createAdapter(Class<P> clazz, File directory, Object adapter) {
        return new VelocityPlatformAdapterBuilder<>(clazz, adapter)
            .register(File.class, "directory", directory, true);
    }

}
