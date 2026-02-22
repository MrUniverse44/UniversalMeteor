package me.blueslime.meteor.platforms.velocity.adapter;

import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.plugin.PluginDescription;
import com.velocitypowered.api.proxy.ConsoleCommandSource;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.velocity.commands.VelocityPlatformCommandProvider;
import me.blueslime.meteor.platforms.velocity.data.VelocityPluginData;
import me.blueslime.meteor.platforms.velocity.events.VelocityPlatformEvents;
import me.blueslime.meteor.platforms.velocity.tasks.VelocityPlatformTasks;
import net.kyori.adventure.text.Component;

import java.io.File;

public class VelocityPlatformAdapterBuilder<P extends PlatformPlugin> extends PlatformAdapterBuilder<P, ProxyServer> {

    public VelocityPlatformAdapterBuilder(Class<P> mainClass, File directory, Object adapter) {
        super(mainClass, directory, adapter);
    }

    public PlatformAdapterBuilder<P, ProxyServer> registerPluginData(PluginContainer plugin) {
        final PluginData data = new VelocityPluginData(plugin.getDescription());
        info.setPluginData(data);
        return this;
    }

    public PlatformAdapterBuilder<P, ProxyServer> registerPluginData(PluginDescription description) {
        final PluginData data = new VelocityPluginData(description);
        info.setPluginData(data);
        return this;
    }

    public PlatformAdapterBuilder<P, ProxyServer> registerAdapter(Object plugin) {
        Implements.setEntry(Object.class, "adapter", plugin, true);
        return this;
    }

    @Override
    public PlatformAdapterBuilder<P, ProxyServer> registerMainClass(ProxyServer proxyInstance) {
        final Platforms paper = Platforms.detectPaper();

        Implements.setEntry(ProxyServer.class, proxyInstance, true);

        info.setPlatform(paper);
        info.setPlatformEvents(new VelocityPlatformEvents(proxyInstance));
        info.setCommands(new PlatformCommands(new VelocityPlatformCommandProvider(proxyInstance)));
        info.setTasks(new VelocityPlatformTasks(proxyInstance));
        info.setLogger(
            new PlatformLogger(
                info.getPluginData().getPluginName(),
                message -> {
                    ConsoleCommandSource console = proxyInstance.getConsoleCommandSource();

                    console.sendMessage(
                        Component.text()
                    );
                }
            )
        );
        return this;
    }

}
