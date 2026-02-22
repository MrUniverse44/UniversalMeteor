package me.blueslime.meteor.platforms.spigot.adapter;

import me.blueslime.meteor.color.renders.BungeeRenderer;
import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.spigot.commands.SpigotPlatformCommandProvider;
import me.blueslime.meteor.platforms.spigot.data.SpigotPluginData;
import me.blueslime.meteor.platforms.spigot.events.SpigotPlatformEvents;
import me.blueslime.meteor.platforms.spigot.tasks.SpigotPlatformTasks;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class SpigotPlatformAdapterBuilder<P extends PlatformPlugin> extends PlatformAdapterBuilder<P, JavaPlugin> {

    public SpigotPlatformAdapterBuilder(Class<P> mainClass, File directory, Object adapter) {
        super(mainClass, directory, adapter);
    }

    @Override
    public PlatformAdapterBuilder<P, JavaPlugin> registerMainClass(JavaPlugin pluginInstance) {
        final PluginData data = new SpigotPluginData(pluginInstance);

        Implements.setEntry(JavaPlugin.class, pluginInstance, true);

        info.setPluginData(data);
        info.setPlatform(Platforms.SPIGOT);
        info.setCommands(new PlatformCommands(new SpigotPlatformCommandProvider(pluginInstance)));
        info.setPlatformEvents(new SpigotPlatformEvents(pluginInstance));
        info.setTasks(new SpigotPlatformTasks(pluginInstance));
        info.setLogger(
            new PlatformLogger(
                data.getPluginName(),
                message -> {
                    ConsoleCommandSender console = pluginInstance.getServer().getConsoleSender();

                    console.spigot().sendMessage(
                        BungeeRenderer.create(message)
                    );
                }
            )
        );
        return this;
    }

}
