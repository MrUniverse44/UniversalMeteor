package me.blueslime.meteor.platforms.velocity.adapter;

import com.velocitypowered.api.proxy.ProxyServer;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapter;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;

public interface VelocityPlatformAdapter<P extends PlatformPlugin<Object>> extends PlatformAdapter<P, ProxyServer, Object> {

    @Override
    default PlatformAdapterBuilder<P, ProxyServer, Object> createAdapter(Class<P> clazz) {
        return new VelocityPlatformAdapterBuilder<>(clazz);
    }

}
