package me.blueslime.meteor.modules.api.loader;

import me.blueslime.meteor.platforms.api.logger.PlatformLogger;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@SuppressWarnings("SpellCheckingInspection")
public final class ModuleIsolatedClassLoader extends URLClassLoader {

    private final List<ClassLoader> fallbacks;
    private final List<String> parentFirstPackages;
    private final PlatformLogger logger;
    private final boolean shouldDebug;


    public ModuleIsolatedClassLoader(
        boolean shouldDebug,
        URL[] urls,
        ClassLoader parent,
        List<ClassLoader> fallbacks,
        Collection<String> parentFirstPackages,
        PlatformLogger logger
    ) {
        super(urls, parent);
        this.fallbacks = fallbacks == null ? new CopyOnWriteArrayList<>() : new CopyOnWriteArrayList<>(fallbacks);
        this.parentFirstPackages = parentFirstPackages == null
            ? Arrays.asList(
                "java.",
                "javax.",
                "sun.",
                "org.bukkit.",
                "net.minecraft.",
                "com.hypixel.hytale.",
                "io.papermc.paper.",
                "com.velocitypowered.",
                "net.md_5.bungee.",
                "com.destroystokyo.",
                "org.spongepowered.",
                "net.fabricmc.",
                "net.minecraftforge.",
                "net.neoforged."
            )
            : new ArrayList<>(parentFirstPackages);
        this.logger = logger;
        this.shouldDebug = shouldDebug;
        if (this.shouldDebug && this.logger != null) {
            try {
                this.logger.debug("ModuleIsolatedClassLoader created with parent=" + parent + " fallbacks=" + this.fallbacks + " parentFirstPackages=" + this.parentFirstPackages);
            } catch (Throwable ignored) {}
        }
    }

    private boolean shouldUseParentFirst(String name) {
        for (String pref : parentFirstPackages) {
            if (name.startsWith(pref)) return true;
        }
        return false;
    }

    private boolean resourceExistsIn(ClassLoader cl, String resourceName) {
        try {
            if (cl == null) return false;
            URL r = cl.getResource(resourceName);
            return r != null;
        } catch (Throwable t) {
            if (logger != null) logger.error(t, "resourceExistsIn check failed");
            return false;
        }
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded != null) {
                if (resolve) resolveClass(loaded);
                return loaded;
            }

            final String resource = name.replace('.', '/') + ".class";

            if (shouldUseParentFirst(name)) {
                try {
                    if (shouldDebug && logger != null) logger.debug("Parent-first loading for " + name);
                    Class<?> pc = super.loadClass(name, false);
                    if (resolve) resolveClass(pc);
                    return pc;
                } catch (ClassNotFoundException ignored) {
                }
            }

            ClassLoader parent = getParent();
            try {
                if (parent != null) {
                    boolean parentHas = resourceExistsIn(parent, resource);
                    if (parentHas) {
                        try {
                            if (shouldDebug && logger != null) logger.debug("Parent has resource " + resource + " -> trying parent.loadClass(" + name + ")");
                            Class<?> pcls = parent.loadClass(name);
                            if (resolve) resolveClass(pcls);
                            return pcls;
                        } catch (ClassNotFoundException cnf) {
                            if (shouldDebug && logger != null) logger.debug("Parent resource existed but parent.loadClass failed for " + name);
                        } catch (LinkageError le) {
                            if (logger != null) logger.error(le, "LinkageError loading " + name + " from parent");
                        }
                    }
                }
            } catch (Throwable t) {
                if (logger != null) logger.error(t, "Error checking parent resource for " + name);
            }

            for (ClassLoader fb : fallbacks) {
                if (fb == null) continue;
                try {
                    if (shouldDebug && logger != null) logger.debug("Trying fallback " + fb + " to load " + name);
                    Class<?> fcls = fb.loadClass(name);
                    if (resolve) resolveClass(fcls);
                    if (shouldDebug && logger != null) logger.debug("Fallback " + fb + " successfully loaded " + name);
                    return fcls;
                } catch (ClassNotFoundException cnf) {
                    if (shouldDebug && logger != null) logger.debug("Fallback " + fb + " does not have " + name);
                    // try next fallback
                } catch (LinkageError le) {
                    if (logger != null) logger.error(le, "LinkageError loading " + name + " from fallback " + fb);
                } catch (Throwable t) {
                    if (logger != null) logger.error(t, "Error while asking fallback to load " + name);
                }
            }

            // 5) try to find class in this classloader (module jar)
            try {
                Class<?> found = findClass(name);
                if (resolve) resolveClass(found);
                return found;
            } catch (ClassNotFoundException | NoClassDefFoundError e) {
                try {
                    if (shouldDebug && logger != null) logger.debug("Falling back to super.loadClass for " + name);
                    Class<?> pcls = super.loadClass(name, false);
                    if (resolve) resolveClass(pcls);
                    return pcls;
                } catch (ClassNotFoundException cnf) {
                    throw new ClassNotFoundException(name, e);
                } catch (LinkageError le) {
                    if (logger != null) logger.error(le, "LinkageError during final super.loadClass for " + name);
                    throw new ClassNotFoundException(name, e);
                }
            }
        }
    }

    @Override
    public URL getResource(String name) {
        // parent resources primero
        URL r = super.getResource(name);
        if (r != null) return r;

        for (ClassLoader fb : fallbacks) {
            try {
                URL u = fb.getResource(name);
                if (u != null) return u;
            } catch (Throwable t) {
                if (logger != null) logger.error(t, "Error getting resource from fallback");
            }
        }

        return findResource(name);
    }

    @Override
    public void close() throws IOException {
        super.close();
    }

    public void addFallback(ClassLoader fallback) {
        if (fallback != null) fallbacks.add(fallback);
    }
}
