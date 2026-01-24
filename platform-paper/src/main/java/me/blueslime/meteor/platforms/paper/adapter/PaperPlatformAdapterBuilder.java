package me.blueslime.meteor.platforms.paper.adapter;

import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.adapter.PlatformAdapterBuilder;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.paper.data.LegacyPaperPluginData;
import me.blueslime.meteor.platforms.paper.data.ModernPaperPluginData;
import me.blueslime.meteor.platforms.paper.events.PaperPlatformEvents;
import me.blueslime.meteor.platforms.paper.tasks.PaperPlatformTasks;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class PaperPlatformAdapterBuilder<P extends PlatformPlugin<Listener>> extends PlatformAdapterBuilder<P, JavaPlugin, Listener> {

    public PaperPlatformAdapterBuilder(Class<P> mainClass) {
        super(mainClass);
    }

    @Override
    public PlatformAdapterBuilder<P, JavaPlugin, Listener> registerMainClass(JavaPlugin pluginInstance) {
        final Platforms paper = Platforms.detectPaper();
        final PluginData data = paper.is(Platforms.MODERN_PAPER)
            ? new ModernPaperPluginData(pluginInstance)
            : new LegacyPaperPluginData(pluginInstance);

        info.setPluginData(data);
        info.setPlatform(paper);
        info.setPlatformEvents(new PaperPlatformEvents(pluginInstance));
        info.setTasks(new PaperPlatformTasks(pluginInstance));
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
