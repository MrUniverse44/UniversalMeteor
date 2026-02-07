package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.entity.Sender;
import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class Command implements PlatformService {

    private final List<Subcommand> rootSubcommands = new ArrayList<>();
    private CommandExecutor baseExecutor; // Ejecutor cuando no hay argumentos

    protected abstract void register();

    protected void registerSubcommand(Subcommand... builders) {
        this.rootSubcommands.addAll(Arrays.asList(builders));
    }

    protected void registerSubcommand(Class<? extends Subcommand>... classes) {
        for (Class<? extends Subcommand> clazz : classes) {
            try {
                Subcommand instance = createInstance(clazz);
                this.rootSubcommands.add(instance);
            } catch (Exception e) {
                getLogger().error(e, "Can't create subclass of: " + clazz.getSimpleName(), " due to internal issues");
            }
        }
    }

    protected void setBaseAction(CommandExecutor executor) {
        this.baseExecutor = executor;
    }

    public abstract void executeBase(Sender sender);

    public void buildToPlatform() {
    }

    public abstract @NotNull String getName();

    public abstract Collection<String> getAliases();

    public @NotNull String getDescription() {
        return "";
    }

    public List<Subcommand> getSubcommands() {
        return rootSubcommands;
    }
}