package me.blueslime.meteor.paper.extras.animation;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public final class AnimationService
        implements PlatformService {

    private final AnimationServiceSettings settings;

    private final PriorityQueue<AnimationInstance> queue =
            new PriorityQueue<>(
                    Comparator.comparingLong(
                            AnimationInstance::nextTick
                    )
            );

    private final Map<
            UUID,
            AnimationInstance
            > instances =
            new HashMap<>();

    private final Map<
            AnimationKey,
            AnimationInstance
            > keyedInstances =
            new HashMap<>();

    private final Map<
            UUID,
            Set<UUID>
            > groups =
            new HashMap<>();

    private final Queue<Runnable> commands =
            new ConcurrentLinkedQueue<>();

    private final AtomicBoolean running =
            new AtomicBoolean(false);

    private volatile BukkitTask task;

    private long currentTick;

    public AnimationService() {
        this(
                AnimationServiceSettings
                        .builder()
                        .validate()
        );
    }

    public AnimationService(
            AnimationServiceSettings settings
    ) {
        this.settings =
                Objects.requireNonNull(
                        settings
                ).validate();
    }

    @Override
    public void initialize() {
        if (
                !running.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        task =
                Bukkit
                        .getScheduler()
                        .runTaskTimer(
                                getPlugin().to(
                                        org.bukkit.plugin.java.JavaPlugin.class
                                ),
                                this::tick,
                                1L,
                                1L
                        );
    }

    public AnimationHandle register(
            UUID groupId,
            String key,
            AnimationDefinition definition,
            AnimationTarget target
    ) {
        Objects.requireNonNull(
                groupId
        );

        Objects.requireNonNull(
                key
        );

        Objects.requireNonNull(
                definition
        );

        Objects.requireNonNull(
                target
        );

        if (!definition.enabled()) {
            return new AnimationHandle(
                    UUID.randomUUID(),
                    groupId,
                    key,
                    this
            );
        }

        UUID id =
                UUID.randomUUID();

        String normalizedKey =
                normalize(key);

        AnimationHandle handle =
                new AnimationHandle(
                        id,
                        groupId,
                        normalizedKey,
                        this
                );

        Runnable command =
                () ->
                        add(
                                id,
                                groupId,
                                normalizedKey,
                                definition,
                                target,
                                handle
                        );

        submitCommand(
                command
        );

        return handle;
    }

    private void add(
            UUID id,
            UUID groupId,
            String key,
            AnimationDefinition definition,
            AnimationTarget target,
            AnimationHandle handle
    ) {
        AnimationKey animationKey =
                new AnimationKey(
                        groupId,
                        key
                );

        AnimationInstance previous =
                keyedInstances.remove(
                        animationKey
                );

        if (previous != null) {
            removeInstance(
                    previous
            );
        }

        AnimationInstance instance =
                new AnimationInstance(
                        id,
                        groupId,
                        key,
                        definition,
                        target,
                        handle,
                        currentTick
                );

        instances.put(
                id,
                instance
        );

        keyedInstances.put(
                animationKey,
                instance
        );

        groups
                .computeIfAbsent(
                        groupId,
                        ignored ->
                                new HashSet<>()
                )
                .add(id);

        queue.offer(
                instance
        );
    }

    public void cancel(
            UUID id
    ) {
        if (id == null) {
            return;
        }

        submitCommand(() -> {
            AnimationInstance instance =
                    instances.get(id);

            if (instance != null) {
                removeInstance(
                        instance
                );
            }
        });
    }

    public void cancel(
            UUID groupId,
            String key
    ) {
        if (
                groupId == null ||
                        key == null
        ) {
            return;
        }

        AnimationKey animationKey =
                new AnimationKey(
                        groupId,
                        normalize(key)
                );

        submitCommand(() -> {
            AnimationInstance instance =
                    keyedInstances.get(
                            animationKey
                    );

            if (instance != null) {
                removeInstance(
                        instance
                );
            }
        });
    }

    public void cancelGroup(
            UUID groupId
    ) {
        if (groupId == null) {
            return;
        }

        submitCommand(() -> {
            Set<UUID> ids =
                    groups.remove(
                            groupId
                    );

            if (
                    ids == null ||
                            ids.isEmpty()
            ) {
                return;
            }

            for (
                    UUID id :
                    List.copyOf(ids)
            ) {
                AnimationInstance instance =
                        instances.get(id);

                if (instance != null) {
                    removeInstance(
                            instance
                    );
                }
            }
        });
    }

    private void tick() {
        if (!running.get()) {
            return;
        }

        currentTick++;

        drainCommands();

        long started =
                System.nanoTime();

        long budget =
                settings
                        .tickBudget()
                        .toNanos();

        int updates = 0;

        while (
                updates <
                        settings.maxUpdatesPerTick()
        ) {
            if (
                    System.nanoTime()
                            - started
                            >= budget
            ) {
                break;
            }

            AnimationInstance instance =
                    queue.peek();

            if (
                    instance == null ||
                            instance.nextTick() >
                                    currentTick
            ) {
                break;
            }

            queue.poll();

            if (
                    instance.cancelled() ||
                            instances.get(
                                    instance.id()
                            ) != instance
            ) {
                continue;
            }

            boolean keep;

            try {
                keep =
                        instance.process(
                                currentTick
                        );

            } catch (Throwable throwable) {
                getLogger().error(
                        "Animation '"
                                + instance.key()
                                + "' failed: "
                                + throwable.getMessage()
                );

                keep = false;
            }

            if (keep) {
                queue.offer(
                        instance
                );

            } else {
                removeInstance(
                        instance
                );
            }

            updates++;
        }
    }

    private void drainCommands() {
        int processed = 0;

        while (
                processed <
                        settings.maxCommandsPerTick()
        ) {
            Runnable command =
                    commands.poll();

            if (command == null) {
                break;
            }

            try {
                command.run();

            } catch (Throwable throwable) {
                getLogger().error(
                        "Animation command failed: "
                                + throwable.getMessage()
                );
            }

            processed++;
        }
    }

    private void removeInstance(
            AnimationInstance instance
    ) {
        instance.cancel();

        instances.remove(
                instance.id(),
                instance
        );

        keyedInstances.remove(
                new AnimationKey(
                        instance.groupId(),
                        instance.key()
                ),
                instance
        );

        Set<UUID> group =
                groups.get(
                        instance.groupId()
                );

        if (group != null) {
            group.remove(
                    instance.id()
            );

            if (group.isEmpty()) {
                groups.remove(
                        instance.groupId()
                );
            }
        }

        /*
         * PriorityQueue removal is O(n).
         *
         * Don't do it here.
         *
         * The stale instance is lazily discarded
         * when it reaches the head.
         */
    }

    private void submitCommand(
            Runnable command
    ) {
        if (!running.get()) {
            return;
        }

        if (Bukkit.isPrimaryThread()) {
            command.run();
            return;
        }

        commands.offer(
                command
        );
    }

    public int activeAnimations() {
        return instances.size();
    }

    public long currentTick() {
        return currentTick;
    }

    @Override
    public void reload() {}

    @Override
    public void shutdown() {
        if (
                !running.compareAndSet(
                        true,
                        false
                )
        ) {
            return;
        }

        BukkitTask current =
                task;

        task = null;

        if (current != null) {
            current.cancel();
        }

        for (
                AnimationInstance instance :
                instances.values()
        ) {
            instance.cancel();
        }

        instances.clear();
        keyedInstances.clear();
        groups.clear();
        queue.clear();
        commands.clear();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private String normalize(
            String key
    ) {
        return key
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    private record AnimationKey(
            UUID groupId,
            String key
    ) {}
}