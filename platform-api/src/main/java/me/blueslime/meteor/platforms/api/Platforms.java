package me.blueslime.meteor.platforms.api;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;

@SuppressWarnings("DeprecatedIsStillUsed")
public enum Platforms {
    /**
     * Please change it to {@link #SPIGOT} or {@link #MODERN_PAPER} depending on your case.
     */
    @Deprecated
    BUKKIT,
    SPIGOT,
    VELOCITY,
    SPONGE,
    BUNGEECORD,
    MODERN_PAPER,
    /**
     * Please is recommended to update to {@link #MODERN_PAPER}, but if you can't, we support it.
     */
    @Deprecated
    LEGACY_PAPER,
    FABRIC,
    FORGE,
    HYTALE,
    NEO_FORGE,
    UNIVERSAL;

    private static Platforms DETECTED = null;

    public static Platforms getDetected() {
        if (DETECTED == null) {
            DETECTED = getAutomatically();
        }
        return DETECTED;
    }

    public static Platforms detectPaper() {
        if (validClassOutsidePlugins("io.papermc.paper.plugin.configuration.PluginMeta")) {
            return Platforms.MODERN_PAPER;
        } else {
            return Platforms.LEGACY_PAPER;
        }
    }

    @SuppressWarnings("unused")
    public boolean isBungee() {
        return (this == BUNGEECORD);
    }

    public boolean isBackend() {
        return (this != BUNGEECORD) && (this != VELOCITY);
    }

    public boolean isProxy() {
        return !isBackend();
    }

    public boolean isPaper() {
        return (this == MODERN_PAPER) || (this == LEGACY_PAPER);
    }

    public boolean isUniversal() {
        return (this == UNIVERSAL);
    }

    public boolean is(Platforms platform) {
        return this == platform;
    }

    public static Platforms getAutomatically() {
        if (validClassOutsidePlugins("com.hypixel.hytale.server.core.plugin.JavaPlugin")) {
            return Platforms.HYTALE;
        }
        if (validClassOutsidePlugins("com.velocitypowered.api.proxy.ProxyServer")) {
            return Platforms.VELOCITY;
        }
        if (validClassOutsidePlugins("net.md_5.bungee.api.ProxyServer")) {
            return Platforms.BUNGEECORD;
        }
        if (validClassOutsidePlugins("com.destroystokyo.paper.PaperConfig") || validClassOutsidePlugins("io.papermc.paper.plugin.configuration.PluginMeta")) {
            if (validClassOutsidePlugins("io.papermc.paper.plugin.configuration.PluginMeta")) {
                return Platforms.MODERN_PAPER;
            } else {
                return Platforms.LEGACY_PAPER;
            }
        }
        if (validClassOutsidePlugins("org.bukkit.Bukkit")) {
            return Platforms.SPIGOT;
        }
        if (validClassOutsidePlugins("org.spongepowered.api.Sponge")) {
            return Platforms.SPONGE;
        }
        if (validClassOutsidePlugins("net.fabricmc.loader.api.FabricLoader")) {
            return Platforms.FABRIC;
        }
        if (validClassOutsidePlugins("net.minecraftforge.fml.ModList")) {
            return Platforms.FORGE;
        }
        if (validClassOutsidePlugins("net.neoforged.someclass")) {
            return Platforms.NEO_FORGE;
        }
        return Platforms.UNIVERSAL;
    }

    /**
     * Prevent wrong detections
     */
    private static boolean validClassOutsidePlugins(String className) {
        try {
            Class<?> clazz = Class.forName(className, false, Platforms.class.getClassLoader());
            CodeSource cs = clazz.getProtectionDomain().getCodeSource();

            if (cs == null) {
                return true;
            }

            URL loc = cs.getLocation();
            if (loc == null) {
                return true;
            }

            URI uri = loc.toURI();
            Path jarPath = Paths.get(uri);
            File file = jarPath.toFile();

            return !file.getAbsolutePath().toLowerCase().contains(File.separator + "plugins" + File.separator)
                && !file.getAbsolutePath().toLowerCase().contains(File.separator + "mods" + File.separator);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static Class<?> getClass(String location) {
        try {
            return Class.forName(location);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
