package me.blueslime.meteor.platforms.spiper.brigadier;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.LiteralMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class PlayerArgumentType implements ArgumentType<Player> {
    private static final Collection<String> EXAMPLES = List.of("Steve", "Alex");

    private static final DynamicCommandExceptionType PLAYER_NOT_FOUND = new DynamicCommandExceptionType(
        name -> new LiteralMessage("Player '" + name + "' is not online.")
    );

    private PlayerArgumentType() {}

    public static PlayerArgumentType playerArg() {
        return new PlayerArgumentType();
    }

    public static Player getPlayer(CommandContext<?> context, String name) {
        return context.getArgument(name, Player.class);
    }

    @Override
    public Player parse(StringReader reader) throws CommandSyntaxException {
        String name = reader.readUnquotedString();
        Player player = Bukkit.getPlayerExact(name);

        if (player == null) {
            throw PLAYER_NOT_FOUND.createWithContext(reader, name);
        }
        return player;
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
        String remaining = builder.getRemaining().toLowerCase();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase().startsWith(remaining)) {
                builder.suggest(player.getName());
            }
        }
        return builder.buildFuture();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PlayerArgumentType;
    }

    @Override
    public int hashCode() {
        return PlayerArgumentType.class.hashCode();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
