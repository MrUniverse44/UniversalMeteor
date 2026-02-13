package me.blueslime.meteor.platforms.api.plugin;

import me.blueslime.meteor.implementation.Implementer;
import me.blueslime.meteor.implementation.service.Service;
import me.blueslime.meteor.platforms.api.Platforms;
import me.blueslime.meteor.platforms.api.commands.PlatformCommands;
import me.blueslime.meteor.platforms.api.configuration.PlatformConfigurations;
import me.blueslime.meteor.platforms.api.data.PluginData;
import me.blueslime.meteor.platforms.api.events.PlatformEvents;
import me.blueslime.meteor.platforms.api.info.PluginInfo;
import me.blueslime.meteor.platforms.api.logger.PlatformLogger;
import me.blueslime.meteor.platforms.api.service.ServiceContainer;
import me.blueslime.meteor.platforms.api.tasks.PlatformTasks;
import me.blueslime.meteor.utilities.consumer.PluginConsumer;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * PlatformPlugin<br>
 * <br>
 * Platform-agnostic main class that centralizes module registration, lifecycle and<br>
 * Configuration handling. Concrete platform mains (Bukkit/Velocity/...) act as adapters<br>
 * that create a PlatformPlugin instance and provide platform-specific services<br>
 * (logger, config backend, task scheduler, etc).<br>
 */
public abstract class PlatformPlugin implements Implementer {

    private final List<Consumer<Platforms>> platformDetectCallbacks = new CopyOnWriteArrayList<>();
    private final List<ServiceContainer> queuedContainers = new ArrayList<>();

    private final Map<Class<?>, Service> services = new LinkedHashMap<>();

    private final Platforms platform;

    protected PlatformConfigurations configurations = PlatformConfigurations.DEFAULT;
    protected PlatformEvents events;
    protected PlatformLogger logger;
    protected PluginData pluginData;
    protected PlatformTasks tasks;
    protected PlatformCommands commands;

    public PlatformPlugin(PluginInfo info) {
        this.platform = info.getPlatform() == null ? Platforms.UNIVERSAL : info.getPlatform();
        this.pluginData = info.getPluginData();
        this.commands = info.getCommands();
        this.events = info.getPlatformEvents();
        this.logger = info.getLogger();
        this.tasks = info.getTasks();
    }

    /**
     * Initialize the platform core. Adapters should call this once platform runtime is ready.<br>
     * This will:<br>
     *  - run platform-detection callbacks<br>
     *  - register queued ModuleContainers and queued module classes<br>
     *  - call {@link #onInitialize()} hook for subclass-specific init<
     */
    public final void initialize() {
        registerImpl(PlatformLogger.class, logger, true);
        registerImpl(PlatformPlugin.class, this, true);
        registerImpl(PlatformEvents.class, events, true);
        registerImpl(PlatformTasks.class, tasks, true);
        registerImpl(Platforms.class, platform, true);
        registerImpl(PluginData.class, pluginData, true);
        registerImpl(PlatformCommands.class, commands, true);

        onPreInitialize();

        for (Consumer<Platforms> cb : platformDetectCallbacks) {
            try {
                cb.accept(platform);
            } catch (Throwable t) {
                // swallow to avoid breaking initialization flow — adapter's logger can log in override
                onCallbackException(t);
            }
        }

        for (ServiceContainer container : new ArrayList<>(queuedContainers)) {
            if (container.appliesTo(platform)) {
                registerService(container.getServiceClasses());
            }
        }

        onInitialize();
        loadServices();
    }

    protected void onPreInitialize() {}

    /** Hook called after the generic initialize steps. Override in subclasses. */
    protected void onInitialize() {}

    /** Hook when a platform-detection callback throws. Override to log if you want. */
    protected void onCallbackException(Throwable t) {
        logger.error(t, t.getMessage() != null ? t.getMessage() : "");
    }

    /**
     * Add a callback that will be invoked during initialize() with the detected platform.<br>
     * Useful for branching registration logic based on a platform.
     */
    public PlatformPlugin onPlatformDetect(Consumer<Platforms> callback) {
        if (callback != null) platformDetectCallbacks.add(callback);
        return this;
    }

    /**
     * Queue module classes grouped by platform(s). They will be registered automatically<br>
     * when {@link #initialize()} is called if the container applies to the detected platform.
     */
    public PlatformPlugin registerService(ServiceContainer... containers) {
        if (containers == null) return this;
        queuedContainers.addAll(Arrays.asList(containers));
        return this;
    }

    /**
     * Queue module classes to register for all platforms (unconditional).
     */
    @SuppressWarnings("UnusedReturnValue")
    @SafeVarargs
    public final PlatformPlugin registerService(Class<? extends Service>... servicesToRegister) {
        if (servicesToRegister == null) return this;
        Set<Service> serviceSet = new LinkedHashSet<>();
        for (Class<? extends Service> clazz : servicesToRegister) {
            Service instance = PluginConsumer.ofUnchecked(
                () -> createInstance(clazz),
                this::onCallbackException,
                () -> null
            );
            if (instance != null) serviceSet.add(instance);
        }
        return registerService(serviceSet.toArray(new Service[0]));
    }

    /**
     * Register module instances immediately.
     */
    public PlatformPlugin registerService(Service... serviceInstances) {
        if (serviceInstances == null) return this;
        for (Service m : serviceInstances) {
            if (m == null) continue;
            putService(m);
        }
        return this;
    }

    /**
     * Register module classes immediately by creating instances via reflection.
     */
    @SafeVarargs
    public final PlatformPlugin registerServiceNow(Class<? extends Service>... moduleClasses) {
        if (moduleClasses == null) return this;
        for (Class<? extends Service> c : moduleClasses) {
            Service s = createInstance(c);
            if (s != null) putService(s);
        }
        return this;
    }

    private void putService(Service service) {
        services.put(service.getClass(), service);
        onServiceRegistered(service);
    }

    /** Called after a service is registered (override to wire registration maps, logger, etc). */
    protected void onServiceRegistered(Service service) {
        if (service.isPersistent()) {
            autoRegister(service.getClass(), service);
        }
    }

    @SuppressWarnings("unchecked")
    protected final <T extends Service> void autoRegister(Class<T> clazz, Service service) {
        registerImpl(clazz, (T) service, true);
        service.registerImplementedService(service);
    }

    // --- lifecycle helpers to load / initialize modules (similar to your old loadModules) ---

    /**
     * Initialize all registered modules (calls module.initialize()).<br>
     * Subclasses can call this when the platform-ready point is reached.
     */
    public void loadServices() {
        for (Service service : new ArrayList<>(services.values())) {
            service.initialize();
        }
    }

    public void reload() {
        for (Service service : new ArrayList<>(services.values())) {
            service.reload();
        }
    }

    public void shutdown() {
        for (Service service : new ArrayList<>(services.values())) {
            service.shutdown();
        }
    }

    public PlatformConfigurations getConfigurationProvider() {
        return configurations;
    }

    public PlatformLogger getLogger() {
        return logger;
    }

    public PlatformCommands getCommands() {
        return commands;
    }

    public PlatformTasks getTaskScheduler() {
        return tasks;
    }

    public PlatformEvents getEvents() {
        return events;
    }

    public Platforms getCurrentPlatform() {
        return platform;
    }

    /**
     * Adapter/platform implementations should call this to provide a real configuration manager<br>
     * (for Bukkit: YAML files; for other platforms a different backend).
     */
    protected void setConfigurations(PlatformConfigurations configurations) {
        this.configurations = configurations == null ? PlatformConfigurations.DEFAULT : configurations;
    }

    public Map<Class<?>, Service> getRegisteredServices() { return Collections.unmodifiableMap(services); }

    public Platforms getType() { return platform; }

    public PluginData getPluginData() {
        return pluginData;
    }
}

