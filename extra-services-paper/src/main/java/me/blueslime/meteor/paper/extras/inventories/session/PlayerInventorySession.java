package me.blueslime.meteor.paper.extras.inventories.session;

import me.blueslime.meteor.paper.extras.inventories.definition.PlayerInventoryDefinition;

import me.blueslime.meteor.paper.extras.item.identity.ItemIdentity;
import me.blueslime.meteor.paper.extras.item.identity.ItemIdentityType;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContextSnapshot;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PlayerInventorySession {

    private final UUID sessionId =
            UUID.randomUUID();

    private final UUID playerId;

    private final String requestedLocale;

    private final PlayerInventoryDefinition definition;

    private final ExecutionContextSnapshot contextSnapshot;

    private final long createdAtNanos =
            System.nanoTime();

    private final AtomicBoolean active =
            new AtomicBoolean(true);

    public PlayerInventorySession(
            UUID playerId,
            String requestedLocale,
            PlayerInventoryDefinition definition,
            ExecutionContextSnapshot contextSnapshot
    ) {
        this.playerId =
                Objects.requireNonNull(
                        playerId,
                        "playerId"
                );

        this.requestedLocale =
                requestedLocale;

        this.definition =
                Objects.requireNonNull(
                        definition,
                        "definition"
                );

        this.contextSnapshot =
                Objects.requireNonNull(
                        contextSnapshot,
                        "contextSnapshot"
                );
    }

    public boolean owns(
            ItemIdentity identity
    ) {
        return identity != null &&
                identity.type()
                        == ItemIdentityType.PLAYER_INVENTORY &&
                identity.ownerId()
                        .equals(playerId) &&
                identity.sessionId()
                        .equals(sessionId) &&
                identity.containerId()
                        .equals(definition.id());
    }

    public UUID sessionId() {
        return sessionId;
    }

    public UUID playerId() {
        return playerId;
    }

    public String requestedLocale() {
        return requestedLocale;
    }

    public PlayerInventoryDefinition definition() {
        return definition;
    }

    public ExecutionContextSnapshot contextSnapshot() {
        return contextSnapshot;
    }

    public long createdAtNanos() {
        return createdAtNanos;
    }

    public boolean isActive() {
        return active.get();
    }

    public boolean invalidate() {
        return active.compareAndSet(
                true,
                false
        );
    }
}