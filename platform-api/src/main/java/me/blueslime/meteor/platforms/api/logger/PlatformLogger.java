package me.blueslime.meteor.platforms.api.logger;

import me.blueslime.meteor.platforms.api.logger.type.PlatformLoggerType;

import java.util.EnumMap;
import java.util.function.Consumer;

public class PlatformLogger implements IPlatformLogger{

    private final EnumMap<PlatformLoggerType, String> prefixMap = new EnumMap<>(PlatformLoggerType.class);
    private final Consumer<String> sender;
    private final String pluginName;
    private final String moduleName;

    public PlatformLogger(String pluginName, String moduleName, Consumer<String> sender) {
        this.pluginName = pluginName;
        this.moduleName = moduleName;
        this.sender = sender;
    }

    public PlatformLogger(String pluginName, Consumer<String> sender) {
        this.pluginName = pluginName;
        this.moduleName = null;
        this.sender = sender;
    }

    @Override
    public PlatformLogger createModuleLogger(String moduleName) {
        return new PlatformLogger(this.pluginName, moduleName, this.sender);
    }

    /**
     * Low-level message sender.<br>
     * Implementations must forward the provided messages to the platform output<br>
     * (console, logger, component system, etc.).<br>
     *
     * @param messages formatted messages to send
     */
    @Override
    public void send(String... messages) {
        if (messages == null || messages.length == 0) return;
        String unified = String.join("\n", messages);
        sender.accept(unified);

    }

    /**
     * Sets a custom prefix for a specific {@link PlatformLoggerType}.<br>
     * The prefix is applied to every line sent for that log level.<br>
     *
     * @param log    log type
     * @param prefix prefix to apply, or {@code null} to clear
     * @return this logger instance
     */
    @Override
    public PlatformLogger setPrefix(PlatformLoggerType log, String prefix) {
        if (prefix == null) prefixMap.remove(log);
        else prefixMap.put(log, prefix);
        return this;
    }

    /**
     * Returns the prefix for the given {@link PlatformLoggerType}.<br>
     * Implementations may provide a default prefix if none was explicitly set.<br>
     *
     * @param type log type
     * @return prefix string
     */
    @Override
    public String getPrefix(PlatformLoggerType type) {
        return prefixMap.computeIfAbsent(type, t -> {
            if (moduleName == null || moduleName.isEmpty()) {
                return type.getPrefixFrom(pluginName);
            } else {
                return type.getPrefixFrom(pluginName, moduleName);
            }
        });
    }
}
