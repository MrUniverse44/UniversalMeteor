package me.blueslime.meteor.modules.api;

import me.blueslime.meteor.modules.api.api.PlatformModule;
import me.blueslime.meteor.modules.api.api.module.status.ModuleStatus;
import me.blueslime.meteor.modules.api.loader.ModuleLoader;
import me.blueslime.meteor.modules.api.loader.jar.ModuleContainer;
import me.blueslime.meteor.modules.api.loader.jar.ModuleIndividualContainer;
import me.blueslime.meteor.modules.api.loader.state.ModuleState;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.plugin.handle.PlatformHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import java.io.File;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The core orchestrator for the modular system.
 * <p>
 * This class handles the lifecycle (loading, enabling, disabling, unloading) of all modules.
 * It ensures that modules are loaded in the correct order based on their dependencies and priorities.
 * <p>
 * <b>Usage Example:</b>
 * <pre>{@code
 * File modulesFolder = new File("modules") //YOUR MODULES FOLDER HERE;
 *
 * // * CONSTRUCTOR PARAMETERS:
 * // *   (File.class = Modules Folder, boolean.class = Should Debug)
 * PlatformModules modules = new PlatformModules(modulesFolder, true);
 *
 * // Async initialization
 * modules.initializeAsync().thenRun(() -> {
 *     plugin.getLogger().info("Modules loaded!");
 * });
 * }</pre>
 */
@SuppressWarnings("UnusedReturnValue")
public class PlatformModules implements PlatformService {

    // * CACHE
    private final Map<String, ModuleIndividualContainer> loadedModules = new ConcurrentHashMap<>();
    private final ModuleLoader moduleLoader;
    private final PlatformLogger logger;
    // * DATA
    private final boolean shouldDebug;
    private final File modulesFolder;

    private volatile boolean initialized;

    /**
     * Create the platform modules
     * @param modulesFolder to search jar files
     * @param shouldDebug in console
     */
    public PlatformModules(File modulesFolder, boolean shouldDebug) {
        this.modulesFolder = modulesFolder;
        this.shouldDebug = shouldDebug;

        // * Platform Events
        PlatformHandle handle = fetch(PlatformHandle.class);
        this.logger = fetch(PlatformLogger.class);

        // Initialize the loader with the handle
        this.moduleLoader = new ModuleLoader(this, handle, logger, shouldDebug);

        if (!modulesFolder.exists() && !modulesFolder.mkdirs()) {
            logger.error("Critical: Could not create modules directory at " + modulesFolder.getPath());
        }
    }

    /**
     * Initializes the module system asynchronously.
     * <p>
     * This scans the disk, loads JARs, resolves dependencies, and enables modules
     * without blocking the main thread during I/O operations.
     *
     * @return a future that completes when the system is fully initialized.
     */
    public CompletableFuture<Void> initializeAsync() {
        if (initialized) {
            if (shouldDebug) logger.warn("System is already initialized.");
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.runAsync(() -> {
            logger.info("Initializing module system (Async)...");
            loadAllModules();
            enableAllModules();
            initialized = true;
            logger.info("Module system initialized. Total modules: " + loadedModules.size());
        }, moduleLoader.getExecutor()); // Use the loader's executor
    }

    /**
     * Shuts down the entire module system.
     * <p>
     * Disables modules in reverse dependency order and releases classloaders.
     */
    public void shutdown() {
        if (!initialized) return;
        logger.info("Shutting down module system...");

        disableAllModules();
        unloadAllModules();

        moduleLoader.shutdown();
        initialized = false;
        logger.info("Module system shutdown complete.");
    }

    /**
     * Loads all modules found in the modules folder.
     * <p>
     * This method performs I/O and should ideally be called asynchronously.
     */
    public void loadAllModules() {
        logger.info("Scanning for modules...");

        // Get all modules from disk
        moduleLoader.findModulesOnDisk().thenAccept(this::registerFoundModules).join();
    }

    /**
     * Processes a list of raw module containers and registers them.
     * This separates the "Finding" phase from the "Registering" phase.
     */
    private void registerFoundModules(List<ModuleContainer> containers) {
        var validContainers = new ArrayList<ModuleIndividualContainer>();

        for (var container : containers) {
            // Ask the loader to process the raw JAR into individual module instances
            var loaded = moduleLoader.loadModules(
                    container.modules(),
                    modulesFolder,
                    container.jarFile(),
                    container.classLoader()
            );
            validContainers.addAll(loaded);
        }

        // Sort by dependency before putting them into the map to ensure order logic later
        var sorted = sortModulesByDependencies(validContainers);

        for (var module : sorted) {
            var id = module.getData().id();
            if (loadedModules.putIfAbsent(id, module) != null) {
                if (shouldDebug) logger.warn("Duplicate module ID found: " + id + ". Skipping " + module.getJarFile().getName());
                moduleLoader.unloadModuleSync(module); // Unload the duplicate immediately
            }
        }
    }

    /**
     * Enables all registered modules in the correct dependency order.
     */
    public void enableAllModules() {
        // We re-sort the values currently in the map to ensure safe enabling
        var sortedModules = sortModulesByDependencies(new ArrayList<>(loadedModules.values()));

        for (var container : sortedModules) {
            enableModule(container.getData().id());
        }
    }

    /**
     * Disables all modules in reverse dependency order.
     */
    public void disableAllModules() {
        var modules = new ArrayList<>(loadedModules.values());
        // Reverse order: dependents must be disabled before their dependencies
        Collections.reverse(modules);

        for (var container : modules) {
            disableModule(container.getData().id());
        }
    }

    /**
     * Unloads all modules, releasing resources and clearing the registry.
     */
    public void unloadAllModules() {
        // Create a copy of keys to avoid concurrent modification issues
        var ids = List.copyOf(loadedModules.keySet());
        for (String id : ids) {
            unloadModule(id);
        }
        loadedModules.clear();
    }

    /**
     * Enables a specific module by its ID.
     *
     * @param moduleId the unique ID of the module.
     * @return true if enabled successfully, false otherwise.
     */
    public boolean enableModule(String moduleId) {
        var container = loadedModules.get(moduleId);
        if (container == null || container.getState() == ModuleState.ENABLED) return false;

        if (!checkDependencies(container)) {
            logger.warn("Cannot enable module '" + moduleId + "': Missing dependencies.");
            return false;
        }

        getTaskScheduler().runSync(
            () -> {
                try {
                    container.getPlatformModule().onStatusUpdate(ModuleStatus.ENABLED);
                    container.setState(ModuleState.ENABLED);
                    if (shouldDebug) logger.info("Enabled module: " + moduleId);
                } catch (Throwable t) {
                    logger.error(t, "Failed to enable module: " + moduleId);
                    container.setState(ModuleState.ERROR);
                }
            }
        );

        return container.getState() == ModuleState.ENABLED;
    }

    /**
     * Disables a specific module by its ID.
     *
     * @param moduleId the unique ID of the module.
     * @return true if disabled successfully.
     */
    public boolean disableModule(String moduleId) {
        var container = loadedModules.get(moduleId);
        if (container == null || container.getState() != ModuleState.ENABLED) return false;

        getTaskScheduler().runSync(
            () -> {
                try {
                    container.getPlatformModule().onStatusUpdate(ModuleStatus.UNLOAD);
                    container.setState(ModuleState.DISABLED);
                    if (shouldDebug) logger.info("Disabled module: " + moduleId);
                } catch (Throwable t) {
                    logger.error(t, "Error disabling module: " + moduleId);
                    container.setState(ModuleState.ERROR);
                }
            }
        );

        return true;
    }

    /**
     * Fully unloads a module from memory.
     *
     * @param moduleId the unique ID of the module.
     * @return true if unloaded.
     */
    public boolean unloadModule(String moduleId) {
        var container = loadedModules.remove(moduleId);
        if (container == null) return false;

        getTaskScheduler().runSync(() -> moduleLoader.unloadModuleSync(container));
        return true;
    }

    /**
     * Sorts a list of modules based on their dependencies using a topological approach.
     * Also respects priority.
     */
    private List<ModuleIndividualContainer> sortModulesByDependencies(List<ModuleIndividualContainer> containers) {
        // First, sort by priority (Higher priority comes first in the list)
        containers.sort(Comparator.comparingInt((ModuleIndividualContainer c) -> c.getData().priority()).reversed());

        var result = new ArrayList<ModuleIndividualContainer>();
        var processed = new HashSet<String>();
        var pending = new HashSet<String>(); // To detect simple cycles

        for (var container : containers) {
            visitDependency(container, containers, result, processed, pending);
        }
        return result;
    }

    private void visitDependency(
        ModuleIndividualContainer current,
        List<ModuleIndividualContainer> all,
        List<ModuleIndividualContainer> result,
        Set<String> processed,
        Set<String> pending
    ) {

        String id = current.getData().id();
        if (processed.contains(id)) return;
        if (pending.contains(id)) {
            if (shouldDebug) logger.warn("Circular dependency detected involving: " + id);
            return;
        }

        pending.add(id);

        for (String depId : current.getData().dependencies()) {
            if (depId.isBlank()) continue;

            // Find the dependency object in the provided list
            for (var potentialDep : all) {
                if (potentialDep.getData().id().equals(depId)) {
                    visitDependency(potentialDep, all, result, processed, pending);
                    break;
                }
            }
        }

        pending.remove(id);
        processed.add(id);
        result.add(current);
    }

    private boolean checkDependencies(ModuleIndividualContainer container) {
        for (String depId : container.getData().dependencies()) {
            if (depId.isBlank()) continue;
            if (!isModuleEnabled(depId)) {
                return false;
            }
        }
        return true;
    }

    public PlatformModule getModule(String id) {
        var c = loadedModules.get(id);
        return c != null ? c.getPlatformModule() : null;
    }

    public Collection<PlatformModule> getAllModules() {
        return loadedModules.values().stream().map(ModuleIndividualContainer::getPlatformModule).toList();
    }

    public boolean isModuleEnabled(String id) {
        var c = loadedModules.get(id);
        return c != null && c.getState() == ModuleState.ENABLED;
    }

    public File getModulesFolder() { return modulesFolder; }

    public Set<ModuleIndividualContainer> getAllModuleContainers() {
        return new HashSet<>(loadedModules.values());
    }
}
