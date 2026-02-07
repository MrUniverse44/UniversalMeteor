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
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class OfflinePlayerArgumentType implements ArgumentType<OfflinePlayer> {
    private static final Collection<String> EXAMPLES = List.of("Steve", "Alex");

    private static final DynamicCommandExceptionType PLAYER_NOT_FOUND = new DynamicCommandExceptionType(
            name -> new LiteralMessage("Player '" + name + "' was never in the server.")
    );

    private OfflinePlayerArgumentType() {}

    public static OfflinePlayerArgumentType playerArg() {
        return new OfflinePlayerArgumentType();
    }

    public static OfflinePlayer getPlayer(CommandContext<?> context, String name) {
        return context.getArgument(name, OfflinePlayer.class);
    }

    @SuppressWarnings({"ConstantValue", "deprecation"})
    @Override
    public OfflinePlayer parse(StringReader reader) throws CommandSyntaxException {
        String name = reader.readUnquotedString();
        OfflinePlayer player = Bukkit.getOfflinePlayer(name);

        if (player == null || player.getUniqueId() == null) {
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
        return OfflinePlayerArgumentType.class.hashCode();
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}

