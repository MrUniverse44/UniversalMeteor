package me.blueslime.meteor.paper.extras.actions.dispatch;

import me.blueslime.meteor.paper.extras.actions.api.ActionResult;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.actions.exception.ActionCancelledException;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.logging.Level;

public final class ActionDispatcher {

    private final Map<String, LaneState> lanes =
            new ConcurrentHashMap<>();

    private final Set<ActionRequest> activeRequests =
            ConcurrentHashMap.newKeySet();

    private final Semaphore globalActive;

    private final ExecutorService executor;
    private final JavaPlugin plugin;

    private final AtomicBoolean running =
            new AtomicBoolean(false);

    private final AtomicLong tick =
            new AtomicLong();

    private volatile BukkitTask task;

    public ActionDispatcher(
            JavaPlugin plugin,
            ExecutorService executor,
            int maxGlobalActive
    ) {
        this.plugin =
                Objects.requireNonNull(
                        plugin,
                        "plugin"
                );

        this.executor =
                Objects.requireNonNull(
                        executor,
                        "executor"
                );

        if (maxGlobalActive <= 0) {
            throw new IllegalArgumentException(
                    "maxGlobalActive must be > 0"
            );
        }

        this.globalActive =
                new Semaphore(maxGlobalActive);
    }

    public void registerLane(
            ExecutionLane lane
    ) {
        Objects.requireNonNull(
                lane,
                "lane"
        );

        LaneState existing =
                lanes.putIfAbsent(
                        normalize(lane.id()),
                        new LaneState(lane)
                );

        if (existing != null) {
            throw new IllegalStateException(
                    "Execution lane '"
                            + lane.id()
                            + "' already exists"
            );
        }
    }

    public void start() {
        if (!running.compareAndSet(
                false,
                true
        )) {
            return;
        }

        task = Bukkit.getScheduler()
                .runTaskTimer(
                        plugin,
                        this::tick,
                        1L,
                        1L
                );
    }

    public CompletableFuture<ActionExecutionResult> submit(
            ActionPlan plan,
            ExecutionContext context,
            ActionExecutionOptions options
    ) {
        Objects.requireNonNull(plan);
        Objects.requireNonNull(context);
        Objects.requireNonNull(options);

        if (!running.get()) {
            return CompletableFuture.completedFuture(
                    ActionExecutionResult.rejected()
            );
        }

        LaneState lane =
                lanes.get(
                        normalize(options.laneId())
                );

        if (lane == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Unknown execution lane '"
                                    + options.laneId()
                                    + "'"
                    )
            );
        }

        ActionRequest request =
                new ActionRequest(
                        plan,
                        context,
                        options
                );

        if (
                lane.configuration.serialPerKey() &&
                        options.serializationKey() != null &&
                        !options.serializationKey().isBlank()
        ) {
            submitSerial(
                    lane,
                    request
            );
        } else {
            submitGlobal(
                    lane,
                    request
            );
        }

        return request.result;
    }

    private void submitGlobal(
            LaneState lane,
            ActionRequest request
    ) {
        if (
                lane.configuration.startImmediately() &&
                        tryAcquireExecutionSlot(lane)
        ) {
            launch(
                    lane,
                    request,
                    null
            );

            return;
        }

        enqueueGlobal(
                lane,
                request
        );
    }

    private void submitSerial(
            LaneState lane,
            ActionRequest request
    ) {
        String key =
                request.options
                        .serializationKey();

        Mailbox mailbox =
                lane.mailboxes.computeIfAbsent(
                        key,
                        Mailbox::new
                );

        boolean startNow = false;

        synchronized (mailbox) {
            if (
                    isCoalesced(
                            mailbox,
                            request
                    )
            ) {
                request.result.complete(
                        ActionExecutionResult.coalesced()
                );

                return;
            }

            long currentTick =
                    tick.get();

            if (
                    !mailbox.running &&
                            mailbox.queue.isEmpty() &&
                            mailbox.lastStartTick != currentTick &&
                            lane.configuration.startImmediately() &&
                            tryAcquireExecutionSlot(lane)
            ) {
                mailbox.running = true;

                mailbox.runningCoalesceKey =
                        request.options.coalesceKey();

                mailbox.lastStartTick =
                        currentTick;

                startNow = true;

            } else {
                if (
                        !enqueueMailbox(
                                lane,
                                mailbox,
                                request
                        )
                ) {
                    return;
                }

                if (!mailbox.running) {
                    markReady(
                            lane,
                            mailbox
                    );
                }
            }
        }

        if (startNow) {
            launch(
                    lane,
                    request,
                    () ->
                            completeMailbox(
                                    lane,
                                    mailbox
                            )
            );
        }
    }

    private void enqueueGlobal(
            LaneState lane,
            ActionRequest request
    ) {
        synchronized (lane.globalLock) {
            if (
                    request.options.coalesceKey() != null &&
                            lane.configuration.overflowPolicy()
                                    == ActionOverflowPolicy.COALESCE
            ) {
                for (
                        ActionRequest pending :
                        lane.globalQueue
                ) {
                    if (
                            Objects.equals(
                                    pending.options.coalesceKey(),
                                    request.options.coalesceKey()
                            )
                    ) {
                        request.result.complete(
                                ActionExecutionResult.coalesced()
                        );

                        return;
                    }
                }
            }

            if (
                    lane.pending.get() >=
                            lane.configuration.maxPending()
            ) {
                if (
                        !handleGlobalOverflow(
                                lane,
                                request
                        )
                ) {
                    return;
                }
            }

            lane.globalQueue.offer(request);
            lane.pending.incrementAndGet();
        }
    }

    private boolean handleGlobalOverflow(
            LaneState lane,
            ActionRequest request
    ) {
        return switch (
                lane.configuration.overflowPolicy()
                ) {
            case DROP_OLDEST -> {
                ActionRequest dropped =
                        lane.globalQueue.poll();

                if (dropped != null) {
                    lane.pending.decrementAndGet();

                    dropped.result.complete(
                            ActionExecutionResult.dropped()
                    );

                    yield true;
                }

                request.result.complete(
                        ActionExecutionResult.rejected()
                );

                yield false;
            }

            case DROP_NEWEST, COALESCE -> {
                request.result.complete(
                        ActionExecutionResult.dropped()
                );

                yield false;
            }

            case REJECT -> {
                request.result.complete(
                        ActionExecutionResult.rejected()
                );

                yield false;
            }
        };
    }

    private boolean enqueueMailbox(
            LaneState lane,
            Mailbox mailbox,
            ActionRequest request
    ) {
        boolean globalFull =
                lane.pending.get() >=
                        lane.configuration.maxPending();

        boolean mailboxFull =
                mailbox.queue.size() >=
                        lane.configuration.maxPendingPerKey();

        if (!globalFull && !mailboxFull) {
            mailbox.queue.offer(request);
            lane.pending.incrementAndGet();
            return true;
        }

        return switch (
                lane.configuration.overflowPolicy()
                ) {
            case DROP_OLDEST -> {
                ActionRequest dropped =
                        mailbox.queue.poll();

                if (dropped != null) {
                    lane.pending.decrementAndGet();

                    dropped.result.complete(
                            ActionExecutionResult.dropped()
                    );

                    if (
                            lane.pending.get() <
                                    lane.configuration.maxPending()
                    ) {
                        mailbox.queue.offer(request);
                        lane.pending.incrementAndGet();

                        yield true;
                    }
                }

                request.result.complete(
                        ActionExecutionResult.rejected()
                );

                yield false;
            }

            case DROP_NEWEST -> {
                request.result.complete(
                        ActionExecutionResult.dropped()
                );

                yield false;
            }

            case COALESCE -> {
                request.result.complete(
                        ActionExecutionResult.dropped()
                );

                yield false;
            }

            case REJECT -> {
                request.result.complete(
                        ActionExecutionResult.rejected()
                );

                yield false;
            }
        };
    }

    private boolean isCoalesced(
            Mailbox mailbox,
            ActionRequest request
    ) {
        String key =
                request.options.coalesceKey();

        if (
                key == null ||
                        lanePolicyIsNotCoalescing(request)
        ) {
            return false;
        }

        if (
                Objects.equals(
                        mailbox.runningCoalesceKey,
                        key
                )
        ) {
            return true;
        }

        for (
                ActionRequest pending :
                mailbox.queue
        ) {
            if (
                    Objects.equals(
                            pending.options.coalesceKey(),
                            key
                    )
            ) {
                return true;
            }
        }

        return false;
    }

    private boolean lanePolicyIsNotCoalescing(
            ActionRequest request
    ) {
        LaneState lane =
                lanes.get(
                        normalize(
                                request.options.laneId()
                        )
                );

        return lane == null ||
                lane.configuration.overflowPolicy()
                        != ActionOverflowPolicy.COALESCE;
    }

    private void tick() {
        long currentTick =
                tick.incrementAndGet();

        for (
                LaneState lane :
                lanes.values()
        ) {
            lane.startsThisTick.set(0);

            drainMailboxes(
                    lane,
                    currentTick
            );

            drainGlobal(lane);
        }
    }

    private void drainMailboxes(
            LaneState lane,
            long currentTick
    ) {
        int attempts =
                lane.readyMailboxes.size();

        for (
                int i = 0;
                i < attempts;
                i++
        ) {
            Mailbox mailbox =
                    lane.readyMailboxes.poll();

            if (mailbox == null) {
                break;
            }

            ActionRequest request = null;

            synchronized (mailbox) {
                mailbox.readyQueued = false;

                if (
                        mailbox.running ||
                                mailbox.queue.isEmpty()
                ) {
                    continue;
                }

                if (
                        mailbox.lastStartTick ==
                                currentTick
                ) {
                    markReady(
                            lane,
                            mailbox
                    );

                    continue;
                }

                if (!tryAcquireExecutionSlot(lane)) {
                    markReady(
                            lane,
                            mailbox
                    );

                    break;
                }

                request =
                        mailbox.queue.poll();

                if (request == null) {
                    releaseUnusedSlot(lane);
                    continue;
                }

                lane.pending.decrementAndGet();

                mailbox.running = true;

                mailbox.runningCoalesceKey =
                        request.options.coalesceKey();

                mailbox.lastStartTick =
                        currentTick;
            }

            ActionRequest finalRequest =
                    request;

            launch(
                    lane,
                    finalRequest,
                    () ->
                            completeMailbox(
                                    lane,
                                    mailbox
                            )
            );
        }
    }

    private void drainGlobal(
            LaneState lane
    ) {
        while (true) {
            ActionRequest request;

            synchronized (lane.globalLock) {
                request =
                        lane.globalQueue.peek();
            }

            if (request == null) {
                return;
            }

            if (!tryAcquireExecutionSlot(lane)) {
                return;
            }

            synchronized (lane.globalLock) {
                request =
                        lane.globalQueue.poll();

                if (request != null) {
                    lane.pending.decrementAndGet();
                }
            }

            if (request == null) {
                releaseUnusedSlot(lane);
                continue;
            }

            launch(
                    lane,
                    request,
                    null
            );
        }
    }

    private boolean tryAcquireExecutionSlot(
            LaneState lane
    ) {
        if (!lane.active.tryAcquire()) {
            return false;
        }

        if (!globalActive.tryAcquire()) {
            lane.active.release();
            return false;
        }

        int current =
                lane.startsThisTick.incrementAndGet();

        if (
                current >
                        lane.configuration.maxStartsPerTick()
        ) {
            lane.startsThisTick.decrementAndGet();

            globalActive.release();
            lane.active.release();

            return false;
        }

        return true;
    }

    private void releaseUnusedSlot(
            LaneState lane
    ) {
        lane.startsThisTick.decrementAndGet();

        globalActive.release();
        lane.active.release();
    }

    private void releaseExecutionSlot(
            LaneState lane
    ) {
        globalActive.release();
        lane.active.release();
    }

    private void launch(
            LaneState lane,
            ActionRequest request,
            Runnable completion
    ) {
        activeRequests.add(request);

        try {
            request.task =
                    executor.submit(
                            () -> {
                                try {
                                    request.context
                                            .cancellation()
                                            .throwIfCancelled();

                                    ActionResult result =
                                            request.plan.execute(
                                                    request.context
                                            );

                                    if (
                                            result ==
                                                    ActionResult.STOP
                                    ) {
                                        request.result.complete(
                                                ActionExecutionResult.stopped()
                                        );
                                    } else {
                                        request.result.complete(
                                                ActionExecutionResult.success()
                                        );
                                    }

                                } catch (
                                        ActionCancelledException exception
                                ) {
                                    request.result.complete(
                                            ActionExecutionResult.cancelled()
                                    );

                                } catch (Throwable throwable) {
                                    plugin.getLogger().log(
                                            Level.SEVERE,
                                            "Error executing ActionPlan",
                                            throwable
                                    );

                                    request.result.complete(
                                            ActionExecutionResult.failed(
                                                    throwable
                                            )
                                    );

                                } finally {
                                    activeRequests.remove(
                                            request
                                    );

                                    releaseExecutionSlot(
                                            lane
                                    );

                                    if (completion != null) {
                                        completion.run();
                                    }
                                }
                            }
                    );

        } catch (
                RejectedExecutionException exception
        ) {
            activeRequests.remove(request);

            releaseExecutionSlot(lane);

            request.result.complete(
                    ActionExecutionResult.rejected()
            );

            if (completion != null) {
                completion.run();
            }
        }
    }

    private void completeMailbox(
            LaneState lane,
            Mailbox mailbox
    ) {
        synchronized (mailbox) {
            mailbox.running = false;
            mailbox.runningCoalesceKey = null;

            if (mailbox.queue.isEmpty()) {
                lane.mailboxes.remove(
                        mailbox.key,
                        mailbox
                );

                return;
            }

            markReady(
                    lane,
                    mailbox
            );
        }
    }

    private void markReady(
            LaneState lane,
            Mailbox mailbox
    ) {
        synchronized (mailbox) {
            if (mailbox.readyQueued) {
                return;
            }

            mailbox.readyQueued = true;

            lane.readyMailboxes.offer(
                    mailbox
            );
        }
    }

    public void shutdown() {
        if (!running.compareAndSet(
                true,
                false
        )) {
            return;
        }

        BukkitTask currentTask = task;

        if (currentTask != null) {
            currentTask.cancel();
        }

        task = null;

        for (
                ActionRequest request :
                activeRequests
        ) {
            request.cancel(
                    "ActionDispatcher shutdown"
            );
        }

        for (
                LaneState lane :
                lanes.values()
        ) {
            synchronized (lane.globalLock) {
                ActionRequest request;

                while (
                        (request =
                                lane.globalQueue.poll())
                                != null
                ) {
                    lane.pending.decrementAndGet();

                    request.cancel(
                            "ActionDispatcher shutdown"
                    );
                }
            }

            for (
                    Mailbox mailbox :
                    lane.mailboxes.values()
            ) {
                synchronized (mailbox) {
                    ActionRequest request;

                    while (
                            (request =
                                    mailbox.queue.poll())
                                    != null
                    ) {
                        lane.pending.decrementAndGet();

                        request.cancel(
                                "ActionDispatcher shutdown"
                        );
                    }
                }
            }

            lane.mailboxes.clear();
            lane.readyMailboxes.clear();
        }
    }

    private String normalize(String input) {
        return input
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    private static final class LaneState {

        private final ExecutionLane configuration;

        private final Semaphore active;

        private final AtomicInteger pending =
                new AtomicInteger();

        private final AtomicInteger startsThisTick =
                new AtomicInteger();

        private final Queue<ActionRequest> globalQueue =
                new ConcurrentLinkedQueue<>();

        private final Object globalLock =
                new Object();

        private final Map<String, Mailbox> mailboxes =
                new ConcurrentHashMap<>();

        private final Queue<Mailbox> readyMailboxes =
                new ConcurrentLinkedQueue<>();

        private LaneState(
                ExecutionLane configuration
        ) {
            this.configuration =
                    configuration;

            this.active =
                    new Semaphore(
                            configuration.maxActive()
                    );
        }
    }

    private static final class Mailbox {

        private final String key;

        private final ArrayDeque<ActionRequest> queue =
                new ArrayDeque<>();

        private boolean running;
        private boolean readyQueued;

        private String runningCoalesceKey;

        private long lastStartTick =
                Long.MIN_VALUE;

        private Mailbox(String key) {
            this.key = key;
        }
    }

    private static final class ActionRequest {

        private final ActionPlan plan;
        private final ExecutionContext context;

        private final ActionExecutionOptions options;

        private final CompletableFuture<ActionExecutionResult> result =
                new CompletableFuture<>();

        private volatile Future<?> task;

        private ActionRequest(
                ActionPlan plan,
                ExecutionContext context,
                ActionExecutionOptions options
        ) {
            this.plan = plan;
            this.context = context;
            this.options = options;
        }

        private void cancel(String reason) {
            context.cancellation()
                    .cancel(reason);

            Future<?> currentTask =
                    task;

            if (currentTask != null) {
                currentTask.cancel(true);
            }

            result.complete(
                    ActionExecutionResult.cancelled()
            );
        }
    }
}
