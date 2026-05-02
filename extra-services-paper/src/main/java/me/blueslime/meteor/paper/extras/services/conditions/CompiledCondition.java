package me.blueslime.meteor.paper.extras.services.conditions;

import org.bukkit.entity.Player;

@FunctionalInterface
public interface CompiledCondition {
    boolean test(Player player);
}
