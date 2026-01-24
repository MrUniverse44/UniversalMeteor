package me.blueslime.meteor.platforms.api.logger.type;

public enum PlatformLoggerType {
    INFO,
    WARN,
    DEBUG,
    ERROR;

    public String getPrefixFrom(String pluginName, String moduleName) {
        return switch (this) {
            case WARN -> "&e" + pluginName + " &7» &6Module: &f" + moduleName + " &8&o→ &f";
            case DEBUG -> "&b" + pluginName + " &7» &6Module: &f" + moduleName + " &8&o→ &f";
            case ERROR -> "&6" + pluginName + " &7» &6Module: &f" + moduleName + " &8&o→ &f";
            default -> "&9" + pluginName + " &7» &6Module: &f" + moduleName + " &8&o→ &f";
        };
    }

    public String getPrefixFrom(String pluginName) {
        return switch (this) {
            case WARN -> "&e" + pluginName + " &7» &f";
            case DEBUG -> "&b" + pluginName + " &7» &f";
            case ERROR -> "&6" + pluginName + " &7» &f";
            default -> "&9" + pluginName + " &7» &f";
        };
    }
}
