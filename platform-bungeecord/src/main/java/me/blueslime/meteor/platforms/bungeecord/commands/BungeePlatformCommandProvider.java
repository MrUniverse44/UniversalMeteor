package me.blueslime.meteor.platforms.bungeecord.commands;

import me.blueslime.meteor.platforms.api.commands.Command;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.commands.provider.PlatformCommandProvider;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;

public class BungeePlatformCommandProvider implements PlatformCommandProvider {

    private final Plugin plugin;
    private final Map<String, BungeeCommandExecute> registeredCommands = new HashMap<>();

    public BungeePlatformCommandProvider(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void register(Command command, PlatformCommands registry) {
        BungeeCommandExecute bungeeCommand = new BungeeCommandExecute(command, registry);

        ProxyServer.getInstance().getPluginManager().registerCommand(plugin, bungeeCommand);

        registeredCommands.put(command.getName(), bungeeCommand);
    }

    @Override
    public void unregister(Command command) {
        BungeeCommandExecute executed = registeredCommands.remove(command.getName());
        if (executed != null) {
            ProxyServer.getInstance().getPluginManager().unregisterCommand(executed);
        }
    }
}