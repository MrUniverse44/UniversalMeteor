package me.blueslime.meteor.modules.api.loader;

import me.blueslime.meteor.modules.api.PlatformModules;
import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.modules.api.api.module.Module;
import me.blueslime.meteor.modules.api.api.module.data.ModuleData;
import me.blueslime.meteor.modules.api.api.module.priority.ModulePriority;
import me.blueslime.meteor.modules.api.loader.jar.ModuleContainer;
import me.blueslime.meteor.modules.api.loader.jar.ModuleIndividualContainer;
import me.blueslime.meteor.modules.api.loader.jar.ModuleLoaderContainer;
import me.blueslime.meteor.modules.api.loader.method.MethodSignature;
import me.blueslime.meteor.modules.api.loader.state.ModuleState;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.PlatformPlugin;
import me.blueslime.meteor.platforms.api.plugin.handle.PlatformHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Handles the low-level scanning, class loading, and instantiation of modules from JAR files.
 * <p>
 * This class leverages the {@link PlatformHandle} to resolve dependencies and ClassLoaders.
 */
public class ModuleLoader implements PlatformService {

    private static final Set<MethodSignature> ABSTRACT_EXPANSION_METHODS = Arrays.stream(PlatformModule.class.getDeclaredMethods())
        .filter(m -> Modifier.isAbstract(m.getModifiers()))
        .map(m -> new MethodSignature(m.getName(), m.getParameterTypes()))
        .collect(Collectors.toSet());

    private final ConcurrentMap<Path, List<String>> classNameCache = new ConcurrentHashMap<>();
    private final PlatformModules moduleManager;
    private final PlatformHandle handle;
    private final PlatformLogger logger;
    private final boolean shouldDebug;
    private final ExecutorService ioExecutor;

    public ModuleLoader(PlatformModules manager, PlatformHandle handle, PlatformLogger logger, boolean shouldDebug) {
        this.moduleManager = manager;
        this.handle = handle;
        this.logger = logger;
        this.shouldDebug = shouldDebug;

        // Use virtual threads if available (Java 21), otherwise cached pool
        this.ioExecutor = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("ModuleIO-", 0).factory()
        );
    }

    public ExecutorService getExecutor() {
        return ioExecutor;
    }

    /**
     * Scans the modules folder for JAR files and converts them into ModuleContainers.
     *
     * @return a future containing the list of found module containers.
     */
    public CompletableFuture<List<ModuleContainer>> findModulesOnDisk() {
        File folder = moduleManager.getModulesFolder();
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".jar"));

        if (files == null || files.length == 0) {
            return CompletableFuture.completedFuture(Collections.emptyList());
        }

        var futures = Arrays.stream(files)
                .map(file -> CompletableFuture.supplyAsync(() -> findModulesInIndividualFile(file), ioExecutor))
                .toList();

        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream()
            .map(CompletableFuture::join)
            .filter(c -> !c.modules().isEmpty()) // Filter out invalid jars
            .toList());
    }

    /**
     * Processes a single JAR file, creating its ClassLoader and finding module classes.
     *
     * @param jarFile the JAR file to process.
     * @return a container with the loaded classes and the classloader.
     */
    public ModuleContainer findModulesInIndividualFile(File jarFile) {
        try {
            var loaderContainer = createClassLoaderAndFindClasses(jarFile);
            var moduleClasses = loaderContainer.getModuleClasses();

            if (moduleClasses.isEmpty()) {
                if (shouldDebug) logger.warn("No PlatformModule classes found in " + jarFile.getName());
                closeQuietly(loaderContainer.getClassLoader());
                return new ModuleContainer(List.of(), jarFile, null);
            }

            // Validate abstract methods
            var validClasses = new ArrayList<Class<? extends PlatformModule>>();
            for (var clazz : moduleClasses) {
                if (validateModuleClass(clazz)) {
                    validClasses.add(clazz);
                } else {
                    if (shouldDebug) logger.warn("Module class " + clazz.getSimpleName() + " is missing abstract methods implementation.");
                }
            }

            return new ModuleContainer(validClasses, jarFile, loaderContainer.getClassLoader());

        } catch (Throwable e) {
            logger.error(e, "Failed to load JAR: " + jarFile.getName());
            return new ModuleContainer(List.of(), jarFile, null);
        }
    }

    private ModuleLoaderContainer createClassLoaderAndFindClasses(File file) throws IOException {
        if (!file.exists()) return new ModuleLoaderContainer(List.of(), null);

        // --- KEY CHANGE: Use PlatformHandle for dependencies ---
        URL[] urls = handle.getPossibleDependencies(file);
        ClassLoader parent = handle.getParentPluginClassLoader();
        List<ClassLoader> fallbacks = handle.getFallbacks();

        // Create the isolated loader
        ModuleIsolatedClassLoader moduleCL = new ModuleIsolatedClassLoader(
            shouldDebug, urls, parent, fallbacks, null, logger
        );

        List<Class<? extends PlatformModule>> classes = new ArrayList<>();
        List<String> classNames = getClassNamesFromJarCached(file);

        try {
            for (String fqcn : classNames) {
                try {
                    Class<?> loaded = moduleCL.loadClass(fqcn);
                    if (PlatformModule.class.isAssignableFrom(loaded)
                            && !loaded.isInterface()
                            && !loaded.isAnnotation()
                            && !Modifier.isAbstract(loaded.getModifiers())) {

                        @SuppressWarnings("unchecked")
                        var casted = (Class<? extends PlatformModule>) loaded;
                        classes.add(casted);
                    }
                } catch (Throwable t) {
                    // Ignore classes that fail to load (optional dependencies, etc.)
                    if (shouldDebug) logger.debug("Could not load class " + fqcn + ": " + t.getMessage());
                }
            }

            if (classes.isEmpty()) {
                moduleCL.close();
                return new ModuleLoaderContainer(List.of(), null);
            }
            return new ModuleLoaderContainer(classes, moduleCL);

        } catch (Throwable t) {
            moduleCL.close();
            throw t;
        }
    }

    /**
     * Instantiates the modules found within a container.
     */
    public List<ModuleIndividualContainer> loadModules(
            List<Class<? extends PlatformModule>> moduleClasses,
            File modulesFolder,
            File jarFile,
            ModuleIsolatedClassLoader classLoader
    ) {
        var result = new ArrayList<ModuleIndividualContainer>();

        for (var moduleClass : moduleClasses) {
            try {
                ModuleData data = getModuleData(moduleClass);
                File moduleDataFolder = new File(modulesFolder, data.folderName().isEmpty() ? data.id() : data.folderName());

                if (!moduleDataFolder.exists() && !moduleDataFolder.mkdirs() && shouldDebug) {
                    logger.warn("Could not create folder for module: " + data.id());
                }

                // Extract resources (YAMLs)
                extractResources(jarFile, moduleDataFolder.toPath());

                // Instantiate on the Main Thread if needed, or safely here
                // Note: Instantiation usually requires no Bukkit API, so we do it here.
                // We must use the module's classloader as context.
                PlatformModule instance = instantiateModule(moduleClass, moduleDataFolder, classLoader, data.name());

                if (instance != null) {
                    result.add(new ModuleIndividualContainer(instance, data, ModuleState.LOADED, jarFile, classLoader));
                }

            } catch (Throwable t) {
                logger.error(t, "Failed to initialize module: " + moduleClass.getSimpleName());
            }
        }
        return result;
    }

    private PlatformModule instantiateModule(Class<? extends PlatformModule> clazz, File folder, ClassLoader cl, String loggerName) {
        AtomicReference<PlatformModule> ref = new AtomicReference<>();

        // We run this block to ensure the Thread Context ClassLoader is correct during constructor
        var previous = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(cl);
        try {
            var constructor = clazz.getDeclaredConstructor(File.class, PlatformPlugin.class, PlatformLogger.class);
            constructor.setAccessible(true);
            ref.set(constructor.newInstance(folder, getPlugin(), getLogger().createModuleLogger(loggerName)));
        } catch (Throwable t) {
            logger.error(t, "Constructor failure for " + clazz.getName());
        } finally {
            Thread.currentThread().setContextClassLoader(previous);
        }
        return ref.get();
    }

    public void unloadModuleSync(ModuleIndividualContainer container) {
        if (container == null) return;

        // 1. Unload logic (events) - Must be main thread ideally, or handled by PlatformModules
        // This method strictly cleans up resources now.

        container.setPlatformModule(null);
        container.setState(ModuleState.DISABLED);

        // 2. Clear cache
        if (container.getJarFile() != null) {
            classNameCache.remove(container.getJarFile().toPath());
        }

        // 3. Close Loader if unique
        ClassLoader cl = container.getClassLoader();
        container.setClassLoader(null);

        if (cl instanceof ModuleIsolatedClassLoader micl) {
            // Check if other modules share this loader (unlikely in this isolated architecture but possible)
            boolean shared = moduleManager.getAllModuleContainers().stream()
                .anyMatch(other -> other != container && other.getClassLoader() == cl);
            if (!shared) {
                closeQuietly(micl);
                if (shouldDebug) logger.info("Closed ClassLoader for " + container.getData().name());
            }
        }
    }

    private List<String> getClassNamesFromJarCached(File jarFile) throws IOException {
        Path path = jarFile.toPath();
        if (classNameCache.containsKey(path)) return classNameCache.get(path);

        var names = new ArrayList<String>();
        try (JarFile jar = new JarFile(jarFile)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.endsWith(".class") && !name.contains("$")) { // skip inner classes for simplicity if desired
                    names.add(name.replace('/', '.').substring(0, name.length() - 6));
                }
            }
        }

        classNameCache.put(path, List.copyOf(names));
        return names;
    }

    private void extractResources(File jarFile, Path targetDir) {
        try (JarFile jar = new JarFile(jarFile)) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                if (!name.endsWith(".yml") && !name.endsWith(".yaml")) continue;

                Path outPath = resolveResourcePath(name, targetDir);
                if (outPath == null) continue; // Skip invalid or existing

                if (!Files.exists(outPath)) {
                    Files.createDirectories(outPath.getParent());
                    try (var in = jar.getInputStream(entry)) {
                        Files.copy(in, outPath);
                        if (shouldDebug) logger.debug("Extracted: " + name);
                    }
                }
            }
        } catch (IOException e) {
            logger.error(e, "Resource extraction failed for " + jarFile.getName());
        }
    }

    /**
     * Logic to map internal JAR paths (menu-x, inventory-x) to disk paths.
     */
    private Path resolveResourcePath(String entryName, Path targetRoot) {
        String cleanName = entryName;

        // sanitize slashes
        cleanName = cleanName.replace("\\", "/").replace("//", "/");

        Path target = targetRoot.resolve(cleanName).normalize();

        // Security check: prevent zip slip
        if (!target.startsWith(targetRoot)) return null;

        return target;
    }

    private boolean validateModuleClass(Class<?> clazz) {
        var methods = Arrays.stream(clazz.getDeclaredMethods())
                .map(m -> new MethodSignature(m.getName(), m.getParameterTypes()))
                .collect(Collectors.toSet());
        return methods.containsAll(ABSTRACT_EXPANSION_METHODS);
    }

    private void closeQuietly(Object c) {
        if (c instanceof AutoCloseable ac) {
            try { ac.close(); } catch (Exception ignored) {}
        }
    }

    public static ModuleData getModuleData(Class<?> clazz) {
        if (clazz != null && clazz.isAnnotationPresent(Module.class)) {
            Module ann = clazz.getAnnotation(Module.class);
            return new ModuleData(true, ann.id(), ann.name(), ann.version(), ann.authors(),
                    ann.description(), ann.dependencies(), ann.priority(), ann.platform(),
                    ann.bukkitDependencies(), ann.folderName());
        }
        String name = clazz == null ? "unknown" : clazz.getSimpleName();
        return new ModuleData(false, name, name, "1.0.0", new String[0], "",
                new String[0], ModulePriority.NORMAL, "ALL", new String[0], name);
    }

    public void shutdown() {
        ioExecutor.shutdownNow();
        classNameCache.clear();
    }
}