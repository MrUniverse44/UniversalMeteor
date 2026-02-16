package me.blueslime.meteor.platforms.velocity.commands;

import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandMeta;
import com.velocitypowered.api.proxy.ProxyServer;
import me.blueslime.meteor.platforms.api.commands.Command;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.commands.provider.PlatformCommandProvider;

public class VelocityPlatformCommandProvider implements PlatformCommandProvider {

    private final ProxyServer server;
    private final BrigadierInjector injector;

    public VelocityPlatformCommandProvider(ProxyServer server) {
        this.server = server;
        this.injector = new BrigadierInjector();
    }

    @Override
    public void register(Command command, PlatformCommands registry) {
        CommandManager commandManager = server.getCommandManager();

        CommandMeta.Builder metaBuilder = commandManager.metaBuilder(command.getName());
        for (String alias : command.getAliases()) {
            metaBuilder.aliases(alias);
        }
        CommandMeta meta = metaBuilder.build();

        try {
            commandManager.register(meta, injector.build(command, registry));
        } catch (Exception e) {
            System.err.println("Error registering Brigadier command, falling back to Legacy: " + e.getMessage());
            commandManager.register(meta, new VelocityCommandExecute(command, registry));
        }
    }

    @Override
    public void unregister(Command command) {
        server.getCommandManager().unregister(command.getName());
    }
}
