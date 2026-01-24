package me.blueslime.meteor.platforms.spigot.adapter;

import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.spigot.data.SpigotPluginData;
import me.blueslime.meteor.platforms.spigot.events.SpigotPlatformEvents;
import me.blueslime.meteor.platforms.spigot.tasks.SpigotPlatformTasks;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class SpigotPlatformAdapterBuilder<P extends PlatformPlugin<Listener>> extends PlatformAdapterBuilder<P, JavaPlugin, Listener> {

    public SpigotPlatformAdapterBuilder(Class<P> mainClass) {
        super(mainClass);
    }

    @Override
    public PlatformAdapterBuilder<P, JavaPlugin, Listener> registerMainClass(JavaPlugin pluginInstance) {
        final PluginData data = new SpigotPluginData(pluginInstance);

        info.setPluginData(data);
        info.setPlatform(Platforms.SPIGOT);
        info.setPlatformEvents(new SpigotPlatformEvents(pluginInstance));
        info.setTasks(new SpigotPlatformTasks(pluginInstance));
        info.setLogger(
            new PlatformLogger(
                data.getPluginName(),
                message -> {
                    ConsoleCommandSender console = pluginInstance.getServer().getConsoleSender();

                    console.sendMessage(
                        message
                    );
                }
            )
        );
        return this;
    }

}
