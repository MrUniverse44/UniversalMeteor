package me.blueslime.meteor.paper.extras.item.identity;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record ItemIdentity(
        ItemIdentityType type,
        UUID ownerId,
        UUID sessionId,
        String containerId,
        String itemId
) {

    public ItemIdentity {
        Objects.requireNonNull(
                type,
                "type"
        );

        Objects.requireNonNull(
                ownerId,
                "ownerId"
        );

        Objects.requireNonNull(
                sessionId,
                "sessionId"
        );

        if (
                containerId == null ||
                        containerId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "containerId cannot be empty"
            );
        }

        if (
                itemId == null ||
                        itemId.isBlank()
        ) {
            throw new IllegalArgumentException(
                    "itemId cannot be empty"
            );
        }

        containerId =
                containerId
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );

        itemId =
                itemId
                        .strip()
                        .toLowerCase(
                                Locale.ROOT
                        );
    }
}