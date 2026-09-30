package me.blueslime.meteor.paper.extras.actions.list.server;

import java.util.*;

final class ServerTransferQueue {

    private final String serverName;

    private final ServerTransferPolicy policy;

    private final List<ServerTransferRequest> requests =
            new ArrayList<>();

    private long windowStartedNanos;

    private int startedThisWindow;

    ServerTransferQueue(
            String serverName,
            ServerTransferPolicy policy,
            long now
    ) {
        this.serverName =
                serverName;

        this.policy =
                policy;

        this.windowStartedNanos =
                now;
    }

    String serverName() {
        return serverName;
    }

    ServerTransferPolicy policy() {
        return policy;
    }

    int size() {
        return requests.size();
    }

    boolean isEmpty() {
        return requests.isEmpty();
    }

    int add(
            ServerTransferRequest request
    ) {
        requests.add(request);

        return requests.size();
    }

    int positionOf(
            UUID playerId
    ) {
        for (
                int index = 0;
                index < requests.size();
                index++
        ) {
            if (
                    requests
                            .get(index)
                            .playerId()
                            .equals(playerId)
            ) {
                return index + 1;
            }
        }

        return -1;
    }

    boolean remove(
            ServerTransferRequest request
    ) {
        return requests.remove(
                request
        );
    }

    Cycle process(
            Set<UUID> onlinePlayers,
            long now
    ) {
        List<Removed> removed =
                new ArrayList<>();

        /*
         * FIRST:
         *
         * Remove ONLY players that are no longer
         * connected to this backend.
         *
         * It does not matter whether Connect was
         * already sent or not.
         */
        Iterator<ServerTransferRequest> iterator =
                requests.iterator();

        while (iterator.hasNext()) {
            ServerTransferRequest request =
                    iterator.next();

            if (
                    !onlinePlayers.contains(
                            request.playerId()
                    )
            ) {
                iterator.remove();

                request.deactivate(ServerTransferExitReason.LEFT_BACKEND);

                removed.add(
                        new Removed(request)
                );
            }
        }

        resetWindowIfNecessary(
                now
        );

        if (requests.isEmpty()) {
            return new Cycle(
                    removed,
                    List.of(),
                    List.of()
            );
        }

        int transferring = 0;

        for (
                ServerTransferRequest request :
                requests
        ) {
            if (
                    request.state() ==
                            ServerTransferState.TRANSFERRING
            ) {
                transferring++;
            }
        }

        /*
         * Admit new players to the TRANSFERRING group.
         *
         * maxPerWindow has TWO purposes:
         *
         * 1. rate limit new transfers per window.
         * 2. bound how many unresolved transfers may
         *    occupy transfer slots simultaneously.
         *
         * Example max=2:
         *
         * [1 TRANSFERRING]
         * [2 TRANSFERRING]
         * [3 WAITING]
         *
         * Player 3 cannot advance while 1 and 2
         * remain connected to the lobby.
         */
        while (
                transferring <
                        policy.maxPerWindow() &&
                        startedThisWindow <
                                policy.maxPerWindow()
        ) {
            ServerTransferRequest next =
                    findNextWaiting();

            if (next == null) {
                break;
            }

            next.markTransferring();

            transferring++;

            startedThisWindow++;
        }

        int amount =
                requests.size();

        long retryNanos =
                policy.retryAt()
                        .toNanos();

        long pendingNanos =
                policy.pendingAt()
                        .toNanos();

        List<Attempt> attempts =
                new ArrayList<>();

        Set<UUID> firstAttempts =
                new HashSet<>();

        /*
         * Calculate attempts.
         *
         * Existing TRANSFERRING players are retried,
         * but NEVER removed simply because an attempt
         * has already been sent.
         */
        for (
                int index = 0;
                index < requests.size();
                index++
        ) {
            ServerTransferRequest request =
                    requests.get(index);

            if (
                    !request.isAttemptDue(
                            now,
                            retryNanos
                    )
            ) {
                continue;
            }

            boolean firstAttempt =
                    request.markAttempt(
                            now
                    );

            if (firstAttempt) {
                firstAttempts.add(
                        request.playerId()
                );
            }

            attempts.add(
                    new Attempt(
                            request,
                            index + 1,
                            amount,
                            firstAttempt
                    )
            );
        }

        List<Pending> pending =
                new ArrayList<>();

        /*
         * onPending continues running for WAITING
         * players AND for TRANSFERRING players which
         * are still stuck in this backend.
         */
        for (
                int index = 0;
                index < requests.size();
                index++
        ) {
            ServerTransferRequest request =
                    requests.get(index);

            /*
             * Don't fire onPending on the exact same
             * cycle as the first onTransfer.
             */
            if (
                    firstAttempts.contains(
                            request.playerId()
                    )
            ) {
                continue;
            }

            if (
                    !request.isPendingDue(
                            now,
                            pendingNanos
                    )
            ) {
                continue;
            }

            request.markPending(
                    now
            );

            pending.add(
                    new Pending(
                            request,
                            index + 1,
                            amount
                    )
            );
        }

        return new Cycle(
                removed,
                attempts,
                pending
        );
    }

    private ServerTransferRequest findNextWaiting() {
        for (
                ServerTransferRequest request :
                requests
        ) {
            if (
                    request.state() ==
                            ServerTransferState.WAITING
            ) {
                return request;
            }
        }

        return null;
    }

    private void resetWindowIfNecessary(
            long now
    ) {
        long resetNanos =
                policy.resetAt()
                        .toNanos();

        if (
                now - windowStartedNanos
                        < resetNanos
        ) {
            return;
        }

        windowStartedNanos =
                now;

        startedThisWindow =
                0;
    }

    record Cycle(
            List<Removed> removed,
            List<Attempt> attempts,
            List<Pending> pending
    ) {}

    record Removed(
            ServerTransferRequest request
    ) {}

    record Attempt(
            ServerTransferRequest request,
            int position,
            int amount,
            boolean firstAttempt
    ) {}

    record Pending(
            ServerTransferRequest request,
            int position,
            int amount
    ) {}
}
