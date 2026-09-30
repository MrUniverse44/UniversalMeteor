package me.blueslime.meteor.paper.extras.runtime.context;

import java.util.UUID;

public final class ContextKeys {

    public static final ContextKey<UUID> PLAYER_ID =
            ContextKey.of(
                    "meteor:player_id",
                    UUID.class
            );

    public static final ContextKey<String> PLAYER_NAME =
            ContextKey.of(
                    "meteor:player_name",
                    String.class
            );

    public static final ContextKey<LocationSnapshot> LOCATION =
            ContextKey.of(
                    "meteor:location",
                    LocationSnapshot.class
            );

    private ContextKeys() {}
}
