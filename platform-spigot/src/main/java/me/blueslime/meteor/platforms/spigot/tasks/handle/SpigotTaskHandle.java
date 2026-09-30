package me.blueslime.meteor.platforms.spigot.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.handle.AbstractPlatformTaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import org.bukkit.scheduler.BukkitTask;

public final class SpigotTaskHandle extends AbstractPlatformTaskHandle<BukkitTask> {

    public SpigotTaskHandle(
            String id,
            TaskOptions options
    ) {
        super(
            id,
            options
        );
    }

    @Override
    protected void cancelScheduled(
            BukkitTask scheduled
    ) {
        scheduled.cancel();
    }
}