package me.blueslime.meteor.platforms.bungeecord.tasks.handle;

import me.blueslime.meteor.platforms.api.tasks.handle.AbstractPlatformTaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import net.md_5.bungee.api.scheduler.ScheduledTask;

public final class BungeeTaskHandle extends AbstractPlatformTaskHandle<ScheduledTask> {

    public BungeeTaskHandle(
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
            ScheduledTask scheduled
    ) {
        scheduled.cancel();
    }
}