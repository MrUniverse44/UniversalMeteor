package me.blueslime.meteor.paper.extras.runtime.context;

import me.blueslime.meteor.paper.extras.runtime.CancellationToken;
import me.blueslime.meteor.paper.extras.runtime.MainThreadBridge;
import me.blueslime.meteor.paper.extras.runtime.exception.MissingExecutionContextException;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public final class ExecutionContext {

    private final ExecutionValues values;

    private final ExecutionVariables variables;

    private final CancellationToken cancellation;

    private final MainThreadBridge mainThread;

    private ExecutionContext(
            ExecutionValues values,
            ExecutionVariables variables,
            CancellationToken cancellation,
            MainThreadBridge mainThread
    ) {
        this.values = values;
        this.variables = variables;
        this.cancellation = cancellation;
        this.mainThread = mainThread;
    }

    public <T> T require(
            ContextKey<T> key
    ) {
        return values
                .find(key)
                .orElseThrow(
                        () ->
                                new MissingExecutionContextException(
                                        key
                                )
                );
    }

    public <T> T get(
            ContextKey<T> key
    ) {
        return values.get(key);
    }

    public boolean contains(
            ContextKey<?> key
    ) {
        return values.contains(
                key
        );
    }

    public ExecutionValues values() {
        return values;
    }

    public ExecutionVariables variables() {
        return variables;
    }

    public CancellationToken cancellation() {
        return cancellation;
    }

    public MainThreadBridge mainThread() {
        return mainThread;
    }

    public ExecutionContextSnapshot snapshot() {
        return new ExecutionContextSnapshot(
                values.asMap(),
                variables.snapshot()
        );
    }

    public void sync(
            Runnable runnable
    ) {
        cancellation.throwIfCancelled();

        mainThread.waitFor(
                runnable
        );

        cancellation.throwIfCancelled();
    }

    public <T> T waitSync(
            Supplier<T> supplier
    ) {
        cancellation.throwIfCancelled();

        T result =
                mainThread.waitFor(
                        supplier
                );

        cancellation.throwIfCancelled();

        return result;
    }

    public void syncPlayer(
            Consumer<Player> consumer
    ) {
        UUID playerId =
                require(
                        ContextKeys.PLAYER_ID
                );

        sync(() -> {
            Player player =
                    Bukkit.getPlayer(
                            playerId
                    );

            if (player != null) {
                consumer.accept(
                        player
                );
            }
        });
    }

    public <T> T waitSyncPlayer(
            Function<Player, T> function
    ) {
        UUID playerId =
                require(
                        ContextKeys.PLAYER_ID
                );

        return waitSync(() -> {
            Player player =
                    Bukkit.getPlayer(
                            playerId
                    );

            if (player == null) {
                return null;
            }

            return function.apply(
                    player
            );
        });
    }

    public void syncLocation(
            Consumer<Location> consumer
    ) {
        LocationSnapshot snapshot =
                require(
                        ContextKeys.LOCATION
                );

        sync(() -> {
            Location location =
                    resolveLocation(
                            snapshot
                    );

            if (location != null) {
                consumer.accept(
                        location
                );
            }
        });
    }

    public <T> T waitSyncLocation(
            Function<Location, T> function
    ) {
        LocationSnapshot snapshot =
                require(
                        ContextKeys.LOCATION
                );

        return waitSync(() -> {
            Location location =
                    resolveLocation(
                            snapshot
                    );

            if (location == null) {
                return null;
            }

            return function.apply(
                    location
            );
        });
    }

    private Location resolveLocation(
            LocationSnapshot snapshot
    ) {
        UUID worldId =
                snapshot.worldId();

        if (worldId == null) {
            return null;
        }

        World world =
                Bukkit.getWorld(
                        worldId
                );

        if (world == null) {
            return null;
        }

        return snapshot.toLocation(
                world
        );
    }

    public static Builder builder(
            MainThreadBridge mainThread
    ) {
        return new Builder(
                mainThread
        );
    }

    public static final class Builder {

        private final MainThreadBridge mainThread;

        private final Map<String, Object> values =
                new HashMap<>();

        private final ExecutionVariables variables =
                new ExecutionVariables();

        private CancellationToken cancellation =
                new CancellationToken();

        private Builder(
                MainThreadBridge mainThread
        ) {
            this.mainThread =
                    Objects.requireNonNull(
                            mainThread,
                            "mainThread"
                    );
        }

        public Builder inherit(
                ExecutionContextSnapshot snapshot
        ) {
            if (snapshot == null) {
                return this;
            }

            values.putAll(
                    snapshot.values()
            );

            variables.putAll(
                    snapshot.variables()
            );

            return this;
        }

        public <T> Builder put(
                ContextKey<T> key,
                T value
        ) {
            Objects.requireNonNull(
                    key,
                    "key"
            );

            if (value == null) {
                values.remove(
                        key.id()
                );

                return this;
            }

            if (
                    !key.type()
                            .isInstance(value)
            ) {
                throw new IllegalArgumentException(
                        "Context value '"
                                + key.id()
                                + "' must be "
                                + key.type().getName()
                                + " but received "
                                + value.getClass().getName()
                );
            }

            values.put(
                    key.id(),
                    value
            );

            return this;
        }

        public Builder player(
                Player player
        ) {
            Objects.requireNonNull(
                    player,
                    "player"
            );

            return player(
                    player.getUniqueId(),
                    player.getName()
            );
        }

        public Builder player(
                UUID playerId,
                String playerName
        ) {
            put(
                    ContextKeys.PLAYER_ID,
                    playerId
            );

            if (playerName != null) {
                put(
                        ContextKeys.PLAYER_NAME,
                        playerName
                );
            }

            return this;
        }

        public Builder location(
                Location location
        ) {
            Objects.requireNonNull(
                    location,
                    "location"
            );

            return put(
                    ContextKeys.LOCATION,
                    LocationSnapshot.from(
                            location
                    )
            );
        }

        public Builder variable(
                String key,
                Object value
        ) {
            variables.set(
                    key,
                    value
            );

            return this;
        }

        public Builder cancellation(
                CancellationToken cancellation
        ) {
            this.cancellation =
                    Objects.requireNonNull(
                            cancellation,
                            "cancellation"
                    );

            return this;
        }

        public ExecutionContext build() {
            return new ExecutionContext(
                    new ExecutionValues(
                            values
                    ),
                    variables,
                    cancellation,
                    mainThread
            );
        }
    }
}