package me.blueslime.meteor.platforms.paper.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.handle.AbstractPlatformTaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import org.bukkit.scheduler.BukkitTask;

public final class PaperTaskHandle extends AbstractPlatformTaskHandle<BukkitTask> {

    public PaperTaskHandle(
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