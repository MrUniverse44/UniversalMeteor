package me.blueslime.meteor.platforms.spigot.commands;

import com.mojang.brigadier.arguments.*;
import me.blueslime.meteor.platforms.api.commands.*;
import me.blueslime.meteor.platforms.api.commands.provider.PlatformCommandProvider;
import me.blueslime.meteor.platforms.spiper.brigadier.BrigadierInjector;
import me.blueslime.meteor.platforms.spiper.brigadier.PlayerArgumentType;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandMap;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;

public class SpigotPlatformCommandProvider implements PlatformCommandProvider {

    private BrigadierInjector brigadierInjector;
    private final JavaPlugin plugin;
    private CommandMap commandMap;

    public SpigotPlatformCommandProvider(JavaPlugin plugin) {
        this.plugin = plugin;
        setupCommandMap();

        if (isBrigadierSupported()) {
            this.brigadierInjector = new BrigadierInjector();
        }
    }

    private boolean isBrigadierSupported() {
        try {
            Class.forName("com.mojang.brigadier.CommandDispatcher");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private void setupCommandMap() {
        try {
            Field bukkitCommandMap = Bukkit.getServer().getClass().getDeclaredField("commandMap");
            bukkitCommandMap.setAccessible(true);
            this.commandMap = (CommandMap) bukkitCommandMap.get(Bukkit.getServer());
        } catch (Exception e) {
            getLogger().error(e, "Could not get command map");
        }
    }

    @Override
    public void register(Command command, PlatformCommands registry) {
        SpigotCommandExecute wrapper = new SpigotCommandExecute(command, registry);

        commandMap.register(plugin.getName().toLowerCase(), wrapper);

        if (brigadierInjector != null) {
            brigadierInjector.register(command, registry, wrapper);
        }
    }

    @Override
    public void unregister(Command command) {
        //TODO
    }

    @Override
    public void registerTypes(PlatformCommands platformCommands) {
        ArgumentTypeHandler<Integer> integer = new ArgumentTypeHandler<>() {
            @Override
            public Integer parse(String input) throws NumberFormatException { return Integer.parseInt(input); }
            @Override
            public Object getBrigadierType() {
                return IntegerArgumentType.integer();
            }
        };

        platformCommands.registerType(Integer.class, integer);
        platformCommands.registerType(int.class, integer);

        ArgumentTypeHandler<Double> doubles = new ArgumentTypeHandler<>() {
            @Override
            public Double parse(String input) throws NumberFormatException { return Double.parseDouble(input); }
            @Override
            public Object getBrigadierType() { return DoubleArgumentType.doubleArg(); }
        };

        platformCommands.registerType(Double.class, doubles);
        platformCommands.registerType(double.class, doubles);

        ArgumentTypeHandler<Boolean> booleans = new ArgumentTypeHandler<>() {
            @Override
            public Boolean parse(String input) {
                if (input.equalsIgnoreCase("true") || input.equalsIgnoreCase("on")) return true;
                if (input.equalsIgnoreCase("false") || input.equalsIgnoreCase("off")) return false;
                throw new IllegalArgumentException("Expected boolean");
            }
            @Override
            public Object getBrigadierType() { return BoolArgumentType.bool(); }
        };

        platformCommands.registerType(Boolean.class, booleans);
        platformCommands.registerType(boolean.class, booleans);

        platformCommands.registerType(String.class, new ArgumentTypeHandler<>() {
            @Override
            public String parse(String input) { return input; }
            @Override
            public Object getBrigadierType() { return StringArgumentType.string(); }
        });

        ArgumentTypeHandler<Float> floats = new ArgumentTypeHandler<>() {
            @Override
            public Float parse(String input) throws NumberFormatException { return Float.parseFloat(input); }
            @Override
            public Object getBrigadierType() { return FloatArgumentType.floatArg(); }
        };

        platformCommands.registerType(Float.class, floats);
        platformCommands.registerType(float.class, floats);

        ArgumentTypeHandler<Long> longs = new ArgumentTypeHandler<>() {
            @Override
            public Long parse(String input) throws NumberFormatException { return Long.parseLong(input); }
            @Override
            public Object getBrigadierType() { return LongArgumentType.longArg(); }
        };

        platformCommands.registerType(Long.class, longs);
        platformCommands.registerType(long.class, longs);

        platformCommands.registerType(Player.class, new ArgumentTypeHandler<>() {
            @Override
            public Player parse(String input) { return Bukkit.getPlayerExact(input); }
            @Override
            public Object getBrigadierType() { return PlayerArgumentType.playerArg(); }
        });

        platformCommands.registerType(OfflinePlayer.class, new ArgumentTypeHandler<>() {
            @SuppressWarnings("deprecation")
            @Override
            public OfflinePlayer parse(String input) { return Bukkit.getOfflinePlayer(input); }
            @Override
            public Object getBrigadierType() { return PlayerArgumentType.playerArg(); }
        });
    }
}
