package me.blueslime.meteor.platforms.paper.adapter;

import me.blueslime.meteor.color.renders.VelocitySpongeRenderer;
import me.blueslime.meteor.implementation.Implements;
import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.paper.commands.PaperPlatformCommandProvider;
import me.blueslime.meteor.platforms.paper.data.LegacyPaperPluginData;
import me.blueslime.meteor.platforms.paper.data.ModernPaperPluginData;
import me.blueslime.meteor.platforms.paper.events.PaperPlatformEvents;
import me.blueslime.meteor.platforms.paper.tasks.PaperPlatformTasks;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public class PaperPlatformAdapterBuilder<P extends PlatformPlugin> extends PlatformAdapterBuilder<P, JavaPlugin> {

    public PaperPlatformAdapterBuilder(Class<P> mainClass, File directory, Object adapter) {
        super(mainClass, directory, adapter);
    }

    @Override
    public PlatformAdapterBuilder<P, JavaPlugin> registerMainClass(JavaPlugin pluginInstance) {
        final Platforms paper = Platforms.detectPaper();
        final PluginData data = paper.is(Platforms.MODERN_PAPER)
            ? new ModernPaperPluginData(pluginInstance)
            : new LegacyPaperPluginData(pluginInstance);

        Implements.setEntry(JavaPlugin.class, pluginInstance, true);

        info.setPluginData(data);
        info.setCommands(new PlatformCommands(new PaperPlatformCommandProvider(pluginInstance)));
        info.setPlatform(paper);
        info.setPlatformEvents(new PaperPlatformEvents(pluginInstance));
        info.setTasks(new PaperPlatformTasks(pluginInstance));
        info.setLogger(
            new PlatformLogger(
                data.getPluginName(),
                message -> {
                    ConsoleCommandSender console = pluginInstance.getServer().getConsoleSender();

                    console.sendMessage(
                        VelocitySpongeRenderer.create(message)
                    );
                }
            )
        );
        return this;
    }

}
