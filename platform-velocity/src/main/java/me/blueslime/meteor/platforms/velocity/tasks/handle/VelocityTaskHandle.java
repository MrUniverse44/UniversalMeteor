package me.blueslime.meteor.platforms.velocity.tasks.handle;

import com.velocitypowered.api.scheduler.ScheduledTask;

import me.blueslime.meteor.platforms.api.tasks.handle.AbstractPlatformTaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

public final class VelocityTaskHandle extends AbstractPlatformTaskHandle<ScheduledTask> {

    public VelocityTaskHandle(
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