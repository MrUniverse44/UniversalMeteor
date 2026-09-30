package me.blueslime.meteor.paper.extras.inventories.session;

import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;

import me.blueslime.meteor.paper.extras.item.identity.ItemIdentity;

public record ResolvedInventoryItem(
        PlayerInventorySession session,
        InteractiveItemDefinition definition,
        ItemIdentity identity
) {}