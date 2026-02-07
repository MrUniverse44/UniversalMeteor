package me.blueslime.meteor.platforms.velocity.plugin;

import me.blueslime.meteor.platforms.api.plugin.handle.PlatformHandle;
import com.velocitypowered.api.plugin.PluginContainer;
import com.velocitypowered.api.proxy.ProxyServer;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VelocityPlatformHandle implements PlatformHandle {

    private final ProxyServer plugin;

    public VelocityPlatformHandle(ProxyServer plugin) {
        this.plugin = plugin;
    }

    /**
     * Gets the ClassLoader that will serve as the <strong>parent</strong> for the module.
     * <p>
     * In the {@code ModuleIsolatedClassLoader}, this loader is prioritized for specific<br>
     * Packages (defined in {@code parentFirstPackages}). If a class belongs to a<br>
     * "parent-first" package (like {@code org.bukkit.} or {@code java.}), this loader<br>
     * is queried immediately.
     * <p>
     * <b>Usage in Loader:</b>
     * <pre>{@code
     * // Passed as the 'parent' argument to URLClassLoader
     * new ModuleIsolatedClassLoader(..., handle.getParentPluginClassLoader(), ...);
     * }</pre>
     *
     * @return the primary ClassLoader (usually the core plugin's loader) to delegate to.
     */
    @Override
    public ClassLoader getParentPluginClassLoader() {
        ClassLoader serverClassLoader = ProxyServer.class.getClassLoader();
        return serverClassLoader != null ? serverClassLoader : plugin.getClass().getClassLoader();
    }

    /**
     * Gets the main ClassLoader of the plugin instance.
     * <p>
     * While often identical to {@link #getParentPluginClassLoader()}, this method<br>
     * specifically, targets the loader capable of finding the plugin's own internal classes.<br>
     * It ensures the platform can differentiate between the <i>api</i> loader and the<br>
     * <i>implementation</i> loader if the server architecture requires it.
     *
     * @return the current plugin's ClassLoader.
     */
    @Override
    public ClassLoader getPluginClassLoader() {
        return plugin.getClass().getClassLoader();
    }

    /**
     * Retrieves a list of fallback ClassLoaders to be consulted during the loading process.
     * <p>
     * The {@code ModuleIsolatedClassLoader} iterates through this list if the class<br>
     * Is not found in the parent (or if the parent check was skipped). This is essential<br>
     * for modules that depend on other plugins (Soft Dependencies) without shading them.
     * <p>
     * <b>Loader Logic:</b>
     * <pre>{@code
     * for (ClassLoader fb : fallbacks) {
     * try {
     * return fb.loadClass(name); // Tries to find the class here
     * } catch (ClassNotFoundException ignored) {}
     * }
     * }</pre>
     *
     * @return a mutable list of additional ClassLoaders to query.
     */
    @Override
    public List<ClassLoader> getFallbacks() {
        ClassLoader myCL = VelocityPlatformHandle.class.getClassLoader();

        List<ClassLoader> fallbacks = plugin.getPluginManager().getPlugins()
            .stream()
            .filter(Objects::nonNull)
            .filter(c -> c.getInstance().isPresent())
            .map(p -> p.getInstance().get().getClass().getClassLoader())
            .filter(Objects::nonNull)
            .filter(cl -> cl != myCL)
            .distinct()
            .toList();

        List<ClassLoader> fallbacksCopy = new ArrayList<>(fallbacks);

        ClassLoader serverCL = ProxyServer.class.getClassLoader();
        if (serverCL != null) fallbacksCopy.add(serverCL);

        return fallbacksCopy;
    }

    /**
     * Determines the classpath URLs required to load a specific module JAR file.
     * <p>
     * This method converts the file into a {@link URL} array. Implementations may<br>
     * extend this to scan the JAR's 'Manifest' for a Class-Path or include<br>
     * nested libraries found within the JAR.
     * <p>
     * <b>Usage Example:</b>
     * <pre>{@code
     * URL[] urls = handle.getPossibleDependencies(jarFile);
     * ModuleIsolatedClassLoader loader = new ModuleIsolatedClassLoader(..., urls, ...);
     * }</pre>
     *
     * @param jarFile the module file (.jar) to be processed.
     * @return an array of {@link URL}s representing the classpath for this module.
     * @throws MalformedURLException if the file path cannot be converted to a valid URL.
     */
    @Override
    public URL[] getPossibleDependencies(File jarFile) throws MalformedURLException {
        List<URL> urlList = new ArrayList<>();
        urlList.add(jarFile.toURI().toURL());

        for (PluginContainer p : plugin.getPluginManager().getPlugins()) {
            try {
                Object inst = p.getInstance().orElse(null);
                if (inst != null) {
                    URL loc = p.getClass().getProtectionDomain().getCodeSource().getLocation();
                    if (loc != null && !urlList.contains(loc)) {
                        urlList.add(loc);
                    }
                }
            } catch (SecurityException ignored) {}
        }

        return urlList.toArray(new URL[0]);
    }
}
