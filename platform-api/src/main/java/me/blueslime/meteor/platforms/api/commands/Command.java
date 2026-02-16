package me.blueslime.meteor.platforms.api.commands;

import me.blueslime.meteor.platforms.api.service.PlatformService;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

public abstract class Command extends Subcommand implements PlatformService {

    @Override
    public String getId() {
        return getName();
    }

    public abstract @NotNull String getName();

    public abstract Collection<String> getAliases();

    public @NotNull String getDescription() {
        return "";
    }
}