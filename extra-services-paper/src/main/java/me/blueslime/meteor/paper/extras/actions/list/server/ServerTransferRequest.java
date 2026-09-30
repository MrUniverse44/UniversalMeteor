package me.blueslime.meteor.paper.extras.actions.list.server;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContextSnapshot;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ServerTransferRequest {

    private final UUID requestId =
            UUID.randomUUID();

    private final UUID playerId;

    private final String playerName;
    private final String serverName;

    private final ServerTransferPolicy policy;
    private final ServerTransferLifecycle lifecycle;

    private final ExecutionContextSnapshot contextSnapshot;

    private final long queuedAtNanos;

    private final AtomicBoolean active =
            new AtomicBoolean(true);

    private final AtomicReference<ServerTransferExitReason> exitReason =
            new AtomicReference<>();

    private volatile ServerTransferState state =
            ServerTransferState.WAITING;

    private volatile long lastAttemptNanos =
            Long.MIN_VALUE;

    private volatile long lastPendingNanos;

    private volatile int attempts;

    public ServerTransferRequest(
            UUID playerId,
            String playerName,
            String serverName,
            ServerTransferPolicy policy,
            ServerTransferLifecycle lifecycle,
            ExecutionContextSnapshot contextSnapshot
    ) {
        this.playerId =
                Objects.requireNonNull(
                        playerId,
                        "playerId"
                );

        this.playerName =
                playerName == null
                        ? playerId.toString()
                        : playerName;

        this.serverName =
                Objects.requireNonNull(
                        serverName,
                        "serverName"
                ).strip();

        this.policy =
                Objects.requireNonNull(
                        policy,
                        "policy"
                );

        this.lifecycle =
                Objects.requireNonNull(
                        lifecycle,
                        "lifecycle"
                );

        this.contextSnapshot =
                Objects.requireNonNull(
                        contextSnapshot,
                        "contextSnapshot"
                );

        this.queuedAtNanos =
                System.nanoTime();

        this.lastPendingNanos =
                queuedAtNanos;
    }

    public UUID requestId() {
        return requestId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String playerName() {
        return playerName;
    }

    public String serverName() {
        return serverName;
    }

    public ServerTransferPolicy policy() {
        return policy;
    }

    public ServerTransferLifecycle lifecycle() {
        return lifecycle;
    }

    public ExecutionContextSnapshot contextSnapshot() {
        return contextSnapshot;
    }

    public long queuedAtNanos() {
        return queuedAtNanos;
    }

    public ServerTransferState state() {
        return state;
    }

    public int attempts() {
        return attempts;
    }

    public boolean isActive() {
        return active.get();
    }

    public ServerTransferExitReason exitReason() {
        return exitReason.get();
    }

    boolean deactivate(
            ServerTransferExitReason reason
    ) {
        if (
                !active.compareAndSet(
                        true,
                        false
                )
        ) {
            return false;
        }

        exitReason.set(reason);

        return true;
    }

    void markTransferring() {
        state =
                ServerTransferState.TRANSFERRING;
    }

    boolean isAttemptDue(
            long now,
            long retryNanos
    ) {
        if (
                state !=
                        ServerTransferState.TRANSFERRING
        ) {
            return false;
        }

        if (attempts == 0) {
            return true;
        }

        return now - lastAttemptNanos
                >= retryNanos;
    }

    boolean markAttempt(
            long now
    ) {
        boolean first =
                attempts == 0;

        attempts++;

        lastAttemptNanos =
                now;

        return first;
    }

    boolean isPendingDue(
            long now,
            long pendingNanos
    ) {
        return now - lastPendingNanos
                >= pendingNanos;
    }

    void markPending(
            long now
    ) {
        lastPendingNanos =
                now;
    }
}
