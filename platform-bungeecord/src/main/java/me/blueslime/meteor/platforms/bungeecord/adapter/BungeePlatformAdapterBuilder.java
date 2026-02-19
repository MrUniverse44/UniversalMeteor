package me.blueslime.meteor.platforms.bungeecord.adapter;

import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.bungeecord.commands.BungeePlatformCommandProvider;
import me.blueslime.meteor.platforms.bungeecord.data.BungeePluginData;
import me.blueslime.meteor.platforms.bungeecord.events.BungeePlatformEvents;
import me.blueslime.meteor.platforms.bungeecord.tasks.BungeePlatformTasks;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.plugin.Plugin;

public class BungeePlatformAdapterBuilder<P extends PlatformPlugin> extends PlatformAdapterBuilder<P, Plugin> {

    public BungeePlatformAdapterBuilder(Class<P> mainClass, Object adapter) {
        super(mainClass, adapter);
    }

    @Override
    public PlatformAdapterBuilder<P, Plugin> registerMainClass(Plugin pluginInstance) {
        final PluginData data = new BungeePluginData(pluginInstance);

        Implements.setEntry(Plugin.class, pluginInstance, true);

        info.setPluginData(data);
        info.setPlatform(Platforms.BUNGEECORD);
        info.setPlatformEvents(new BungeePlatformEvents(pluginInstance));
        info.setCommands(new PlatformCommands(new BungeePlatformCommandProvider(pluginInstance)));
        info.setTasks(new BungeePlatformTasks(pluginInstance));
        info.setLogger(
            new PlatformLogger(
                data.getPluginName(),
                message -> {
                    CommandSender console = pluginInstance.getProxy().getConsole();

                    console.sendMessage(
                        new TextComponent(message)
                    );
                }
            )
        );
        return this;
    }

}
