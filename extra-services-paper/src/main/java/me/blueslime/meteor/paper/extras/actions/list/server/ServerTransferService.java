package me.blueslime.meteor.paper.extras.actions.list.server;

import me.blueslime.meteor.paper.extras.actions.ActionService;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.actions.dispatch.ActionExecutionResult;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.runtime.MainThreadBridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

public final class ServerTransferService {

    public static final String CHANNEL =
            "BungeeCord";

    private final Map<String, ServerTransferQueue> queues =
            new HashMap<>();

    /**
     * One queue membership per player globally.
     *
     * If the player selects another server,
     * their old request is replaced.
     */
    private final Map<UUID, ServerTransferRequest> memberships =
            new HashMap<>();

    private final Set<String> policyWarnings =
            ConcurrentHashMap.newKeySet();

    private final Map<String, byte[]> payloadCache =
            new ConcurrentHashMap<>();

    private final ReentrantLock lock =
            new ReentrantLock();

    private final AtomicBoolean running =
            new AtomicBoolean(false);

    private final ServerTransferSettings settings;

    private final MainThreadBridge mainThread;
    private final ActionService actions;
    private final JavaPlugin plugin;

    private volatile Thread processor;

    public ServerTransferService(
            JavaPlugin plugin,
            ActionService actions,
            MainThreadBridge mainThread,
            ServerTransferSettings settings
    ) {
        this.plugin =
                Objects.requireNonNull(
                        plugin,
                        "plugin"
                );

        this.actions =
                Objects.requireNonNull(
                        actions,
                        "actions"
                );

        this.mainThread =
                Objects.requireNonNull(
                        mainThread,
                        "mainThread"
                );

        this.settings =
                Objects.requireNonNull(
                        settings,
                        "settings"
                ).validate();
    }

    public void start() {
        if (
                !running.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        /*
         * Paper recommends registering the legacy
         * "BungeeCord" name. Paper maps it internally
         * to bungeecord:main where necessary.
         */
        mainThread.waitFor(() ->
                plugin
                        .getServer()
                        .getMessenger()
                        .registerOutgoingPluginChannel(
                                plugin,
                                CHANNEL
                        )
        );

        processor =
                Thread.ofVirtual()
                        .name(
                                "meteor-server-transfer"
                        )
                        .start(
                                this::processLoop
                        );
    }

    /**
     * Main API used by ServerAction.
     * <br>
     * Rules:
     * <br>
     * - no previous queue:
     *      JOIN
     * <br>
     * - same destination:
     *      CANCEL previous queue
     * <br>
     * - different destination:
     *      old.onQuit(REPLACED)
     *      remove old
     *      join new
     *      new.onJoin
     */
    public ServerTransferEnqueueResult enqueueOrToggle(
            ServerTransferRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request"
        );

        LifecycleDispatch oldQuit =
                null;

        LifecycleDispatch newJoin =
                null;

        ServerTransferEnqueueResult result;

        long now =
                System.nanoTime();

        lock.lock();

        try {
            ServerTransferRequest current =
                    memberships.get(
                            request.playerId()
                    );

            String targetKey =
                    normalizeServer(
                            request.serverName()
                    );

            /*
             * Same destination clicked again:
             *
             * toggle OFF.
             */
            if (
                    current != null &&
                            normalizeServer(
                                    current.serverName()
                            ).equals(targetKey)
            ) {
                ServerTransferQueue currentQueue =
                        queues.get(
                                targetKey
                        );

                int oldPosition =
                        currentQueue == null
                                ? -1
                                : currentQueue.positionOf(
                                current.playerId()
                        );

                int oldAmount =
                        currentQueue == null
                                ? 0
                                : currentQueue.size();

                if (currentQueue != null) {
                    currentQueue.remove(
                            current
                    );

                    if (
                            currentQueue.isEmpty()
                    ) {
                        queues.remove(
                                targetKey
                        );
                    }
                }

                memberships.remove(
                        current.playerId()
                );

                current.deactivate(
                        ServerTransferExitReason.CANCELLED
                );

                oldQuit =
                        new LifecycleDispatch(
                                current,
                                current.lifecycle().onQuit(),
                                oldPosition,
                                oldAmount,
                                ServerTransferExitReason.CANCELLED
                        );

                result =
                        new ServerTransferEnqueueResult(
                                ServerTransferEnqueueResult.Status.CANCELLED,
                                current.serverName(),
                                oldPosition,
                                oldAmount
                        );

            } else {
                ServerTransferQueue target =
                        queues.get(
                                targetKey
                        );

                /*
                 * If another server queue is full,
                 * DO NOT remove the player's current
                 * queue.
                 */
                if (
                        target != null &&
                                target.size() >=
                                        settings.maxQueueSizePerServer()
                ) {
                    return new ServerTransferEnqueueResult(
                            ServerTransferEnqueueResult.Status.REJECTED,
                            request.serverName(),
                            -1,
                            target.size()
                    );
                }

                /*
                 * A replacement doesn't increase the
                 * global amount.
                 */
                if (
                        current == null &&
                                memberships.size() >=
                                        settings.maxGlobalQueueSize()
                ) {
                    return new ServerTransferEnqueueResult(
                            ServerTransferEnqueueResult.Status.REJECTED,
                            request.serverName(),
                            -1,
                            memberships.size()
                    );
                }

                boolean replaced =
                        current != null;

                /*
                 * Remove previous destination first.
                 */
                if (current != null) {
                    String oldKey =
                            normalizeServer(
                                    current.serverName()
                            );

                    ServerTransferQueue oldQueue =
                            queues.get(
                                    oldKey
                            );

                    int oldPosition =
                            oldQueue == null
                                    ? -1
                                    : oldQueue.positionOf(
                                    current.playerId()
                            );

                    int oldAmount =
                            oldQueue == null
                                    ? 0
                                    : oldQueue.size();

                    if (oldQueue != null) {
                        oldQueue.remove(
                                current
                        );

                        if (oldQueue.isEmpty()) {
                            queues.remove(
                                    oldKey
                            );
                        }
                    }

                    current.deactivate(
                            ServerTransferExitReason.REPLACED
                    );

                    oldQuit =
                            new LifecycleDispatch(
                                    current,
                                    current.lifecycle().onQuit(),
                                    oldPosition,
                                    oldAmount,
                                    ServerTransferExitReason.REPLACED
                            );
                }

                /*
                 * Create target queue if necessary.
                 */
                target =
                        queues.computeIfAbsent(
                                targetKey,
                                ignored ->
                                        new ServerTransferQueue(
                                                request.serverName(),
                                                request.policy(),
                                                now
                                        )
                        );

                warnPolicyConflict(
                        target,
                        request
                );

                int position =
                        target.add(
                                request
                        );

                memberships.put(
                        request.playerId(),
                        request
                );

                int amount =
                        target.size();

                newJoin =
                        new LifecycleDispatch(
                                request,
                                request.lifecycle().onJoin(),
                                position,
                                amount,
                                null
                        );

                result =
                        new ServerTransferEnqueueResult(
                                replaced
                                        ? ServerTransferEnqueueResult.Status.REPLACED
                                        : ServerTransferEnqueueResult.Status.JOINED,
                                request.serverName(),
                                position,
                                amount
                        );
            }

        } finally {
            lock.unlock();
        }

        /*
         * Never execute ActionPlans while holding
         * queue locks.
         */
        if (oldQuit != null) {
            dispatchLifecycle(
                    oldQuit,
                    false
            );
        }

        if (newJoin != null) {
            dispatchLifecycle(
                    newJoin,
                    false
            );
        }

        return result;
    }

    public boolean cancel(
            UUID playerId
    ) {
        LifecycleDispatch quit = null;

        lock.lock();

        try {
            ServerTransferRequest request =
                    memberships.remove(
                            playerId
                    );

            if (request == null) {
                return false;
            }

            String key =
                    normalizeServer(
                            request.serverName()
                    );

            ServerTransferQueue queue =
                    queues.get(key);

            int position =
                    queue == null
                            ? -1
                            : queue.positionOf(
                            playerId
                    );

            int amount =
                    queue == null
                            ? 0
                            : queue.size();

            if (queue != null) {
                queue.remove(request);

                if (queue.isEmpty()) {
                    queues.remove(key);
                }
            }

            request.deactivate(
                    ServerTransferExitReason.CANCELLED
            );

            quit =
                    new LifecycleDispatch(
                            request,
                            request.lifecycle().onQuit(),
                            position,
                            amount,
                            ServerTransferExitReason.CANCELLED
                    );

        } finally {
            lock.unlock();
        }

        dispatchLifecycle(
                quit,
                false
        );

        return true;
    }

    public boolean isQueued(
            UUID playerId
    ) {
        lock.lock();

        try {
            return memberships.containsKey(
                    playerId
            );

        } finally {
            lock.unlock();
        }
    }

    public int positionOf(
            UUID playerId
    ) {
        lock.lock();

        try {
            ServerTransferRequest request =
                    memberships.get(
                            playerId
                    );

            if (request == null) {
                return -1;
            }

            ServerTransferQueue queue =
                    queues.get(
                            normalizeServer(
                                    request.serverName()
                            )
                    );

            return queue == null
                    ? -1
                    : queue.positionOf(
                    playerId
            );

        } finally {
            lock.unlock();
        }
    }

    public int queueSize(
            String serverName
    ) {
        lock.lock();

        try {
            ServerTransferQueue queue =
                    queues.get(
                            normalizeServer(
                                    serverName
                            )
                    );

            return queue == null
                    ? 0
                    : queue.size();

        } finally {
            lock.unlock();
        }
    }

    private void processLoop() {
        long interval =
                settings
                        .processInterval()
                        .toNanos();

        while (
                running.get() &&
                        !Thread.currentThread()
                                .isInterrupted()
        ) {
            long started =
                    System.nanoTime();

            try {
                if (hasRequests()) {
                    processCycle();
                }

            } catch (Throwable throwable) {
                if (running.get()) {
                    plugin.getLogger().log(
                            Level.SEVERE,
                            "Error processing server transfer queues",
                            throwable
                    );
                }
            }

            long elapsed =
                    System.nanoTime()
                            - started;

            long remaining =
                    interval - elapsed;

            if (remaining <= 0L) {
                continue;
            }

            try {
                TimeUnit.NANOSECONDS.sleep(
                        remaining
                );

            } catch (
                    InterruptedException exception
            ) {
                Thread.currentThread()
                        .interrupt();

                break;
            }
        }
    }

    private boolean hasRequests() {
        lock.lock();

        try {
            return !memberships.isEmpty();

        } finally {
            lock.unlock();
        }
    }

    private void processCycle() {
        /*
         * ONE sync operation gets a snapshot of
         * every player currently connected to this
         * backend.
         */
        Set<UUID> onlinePlayers =
                mainThread.waitFor(() -> {
                    Set<UUID> result =
                            new HashSet<>();

                    for (
                            Player player :
                            Bukkit.getOnlinePlayers()
                    ) {
                        result.add(
                                player.getUniqueId()
                        );
                    }

                    return result;
                });

        long now =
                System.nanoTime();

        List<ServerTransferQueue.Attempt> attempts =
                new ArrayList<>();

        List<ServerTransferQueue.Pending> pending =
                new ArrayList<>();

        lock.lock();

        try {
            Iterator<
                    Map.Entry<
                            String,
                            ServerTransferQueue
                            >
                    > queueIterator =
                    queues
                            .entrySet()
                            .iterator();

            while (
                    queueIterator.hasNext()
            ) {
                Map.Entry<
                        String,
                        ServerTransferQueue
                        > entry =
                        queueIterator.next();

                ServerTransferQueue queue =
                        entry.getValue();

                ServerTransferQueue.Cycle cycle =
                        queue.process(
                                onlinePlayers,
                                now
                        );

                /*
                 * Player disappeared from this backend.
                 *
                 * Only NOW is their queue position freed.
                 */
                for (
                        ServerTransferQueue.Removed removal :
                        cycle.removed()
                ) {
                    ServerTransferRequest request =
                            removal.request();

                    memberships.remove(
                            request.playerId(),
                            request
                    );
                }

                attempts.addAll(
                        cycle.attempts()
                );

                pending.addAll(
                        cycle.pending()
                );

                if (queue.isEmpty()) {
                    queueIterator.remove();
                }
            }

        } finally {
            lock.unlock();
        }

        /*
         * onPending does not block queue processing.
         */
        for (
                ServerTransferQueue.Pending event :
                pending
        ) {
            dispatchLifecycle(
                    new LifecycleDispatch(
                            event.request(),
                            event.request()
                                    .lifecycle()
                                    .onPending(),
                            event.position(),
                            event.amount(),
                            null
                    ),
                    false
            );
        }

        /*
         * First onTransfer is executed before the
         * first actual Connect message.
         *
         * We await all of them concurrently.
         */
        List<CompletableFuture<ActionExecutionResult>>
                transferCallbacks =
                new ArrayList<>();

        for (
                ServerTransferQueue.Attempt attempt :
                attempts
        ) {
            if (
                    !attempt.firstAttempt()
            ) {
                continue;
            }

            CompletableFuture<ActionExecutionResult>
                    future =
                    dispatchLifecycle(
                            new LifecycleDispatch(
                                    attempt.request(),
                                    attempt.request()
                                            .lifecycle()
                                            .onTransfer(),
                                    attempt.position(),
                                    attempt.amount(),
                                    null
                            ),
                            true
                    );

            transferCallbacks.add(
                    future
            );
        }

        if (
                !transferCallbacks.isEmpty()
        ) {
            CompletableFuture
                    .allOf(
                            transferCallbacks.toArray(
                                    CompletableFuture[]::new
                            )
                    )
                    .join();
        }

        /*
         * onTransfer may itself have cancelled or
         * replaced a queue.
         *
         * Therefore isActive() is checked again
         * immediately before sending.
         */
        sendAttempts(
                attempts
        );
    }

    private void sendAttempts(
            List<ServerTransferQueue.Attempt> attempts
    ) {
        if (attempts.isEmpty()) {
            return;
        }

        List<ServerTransferQueue.Attempt> active =
                attempts.stream()
                        .filter(
                                attempt ->
                                        attempt.request()
                                                .isActive()
                        )
                        .toList();

        if (active.isEmpty()) {
            return;
        }

        int batchSize =
                settings.maxMessagesPerSyncBatch();

        for (
                int from = 0;
                from < active.size();
                from += batchSize
        ) {
            int to =
                    Math.min(
                            active.size(),
                            from + batchSize
                    );

            List<ServerTransferQueue.Attempt> batch =
                    active.subList(
                            from,
                            to
                    );

            mainThread.waitFor(() -> {
                for (
                        ServerTransferQueue.Attempt attempt :
                        batch
                ) {
                    ServerTransferRequest request =
                            attempt.request();

                    if (!request.isActive()) {
                        continue;
                    }

                    Player player =
                            Bukkit.getPlayer(
                                    request.playerId()
                            );

                    /*
                     * Do NOT remove it here if null.
                     *
                     * The next processor cycle will
                     * observe that UUID missing from the
                     * online snapshot and remove it in
                     * one consistent place.
                     */
                    if (player == null) {
                        continue;
                    }

                    byte[] payload =
                            payloadFor(
                                    request.serverName()
                            );

                    player.sendPluginMessage(
                            plugin,
                            CHANNEL,
                            payload
                    );
                }

                return null;
            });
        }
    }

    private CompletableFuture<ActionExecutionResult>
    dispatchLifecycle(
            LifecycleDispatch dispatch,
            boolean important
    ) {
        if (
                dispatch == null ||
                        dispatch.plan() == null ||
                        dispatch.plan().isEmpty()
        ) {
            return CompletableFuture.completedFuture(
                    ActionExecutionResult.success()
            );
        }

        ServerTransferRequest request =
                dispatch.request();

        if (
                !important &&
                        !request.isActive() &&
                        dispatch.reason() == null
        ) {
            return CompletableFuture.completedFuture(
                    ActionExecutionResult.cancelled()
            );
        }

        long now =
                System.nanoTime();

        long waitedMillis =
                Math.max(
                        0L,
                        (
                                now -
                                        request.queuedAtNanos()
                        ) / 1_000_000L
                );

        long waitedSeconds =
                waitedMillis / 1000L;

        ExecutionContext context =
                actions.context()
                        .inherit(
                                request.contextSnapshot()
                        )
                        .player(
                                request.playerId(),
                                request.playerName()
                        )

                        .variable(
                                "position",
                                dispatch.position()
                        )
                        .variable(
                                "amount",
                                dispatch.amount()
                        )

                        .variable(
                                "serverName",
                                request.serverName()
                        )
                        .variable(
                                "server_name",
                                request.serverName()
                        )

                        .variable(
                                "waitMillis",
                                waitedMillis
                        )
                        .variable(
                                "wait_millis",
                                waitedMillis
                        )

                        .variable(
                                "waitSeconds",
                                waitedSeconds
                        )
                        .variable(
                                "wait_seconds",
                                waitedSeconds
                        )

                        .variable(
                                "attempt",
                                request.attempts()
                        )

                        .variable(
                                "queueState",
                                request.state()
                                        .name()
                        )
                        .variable(
                                "queue_state",
                                request.state()
                                        .name()
                        )

                        .variable(
                                "queueReason",
                                dispatch.reason() == null
                                        ? ""
                                        : dispatch.reason()
                                          .name()
                        )

                        .variable(
                                "queue_reason",
                                dispatch.reason() == null
                                        ? ""
                                        : dispatch.reason()
                                          .name()
                        )

                        .build();

        /*
         * onPending can be emitted repeatedly.
         *
         * Coalesce it so an overloaded client does
         * not accumulate hundreds of stale messages.
         */
        if (
                dispatch.plan() ==
                        request.lifecycle()
                                .onPending()
        ) {
            return actions.executeForPlayer(
                    dispatch.plan(),
                    context,
                    request.playerId(),
                    "server-queue:pending:"
                            + request.playerId()
            );
        }

        return actions.executeForPlayer(
                dispatch.plan(),
                context,
                request.playerId()
        );
    }

    private byte[] payloadFor(
            String serverName
    ) {
        return payloadCache.computeIfAbsent(
                serverName,
                this::createPayload
        );
    }

    private byte[] createPayload(
            String serverName
    ) {
        try (
                ByteArrayOutputStream bytes =
                        new ByteArrayOutputStream();

                DataOutputStream output =
                        new DataOutputStream(bytes)
        ) {
            output.writeUTF(
                    "Connect"
            );

            output.writeUTF(
                    serverName
            );

            output.flush();

            return bytes.toByteArray();

        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Unable to create BungeeCord Connect payload",
                    exception
            );
        }
    }

    private void warnPolicyConflict(
            ServerTransferQueue queue,
            ServerTransferRequest request
    ) {
        if (
                queue.policy()
                        .equals(
                                request.policy()
                        )
        ) {
            return;
        }

        String key =
                normalizeServer(
                        request.serverName()
                );

        if (
                !policyWarnings.add(key)
        ) {
            return;
        }

        plugin.getLogger().warning(
                "Multiple <server> actions use different queue "
                        + "policies for destination '"
                        + request.serverName()
                        + "'. The policy of the currently active "
                        + "queue will be used until that queue becomes empty."
        );
    }

    private String normalizeServer(
            String server
    ) {
        return server
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }

    public void shutdown() {
        if (
                !running.compareAndSet(
                        true,
                        false
                )
        ) {
            return;
        }

        Thread current =
                processor;

        if (current != null) {
            current.interrupt();
        }

        processor =
                null;

        lock.lock();

        try {
            for (
                    ServerTransferRequest request :
                    memberships.values()
            ) {
                request.deactivate(
                        ServerTransferExitReason.SHUTDOWN
                );
            }

            memberships.clear();
            queues.clear();

        } finally {
            lock.unlock();
        }

        payloadCache.clear();
        policyWarnings.clear();
    }

    private record LifecycleDispatch(
            ServerTransferRequest request,
            ActionPlan plan,
            int position,
            int amount,
            ServerTransferExitReason reason
    ) {}
}
