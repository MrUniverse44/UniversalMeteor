package me.blueslime.meteor.paper.extras.runtime;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ExecutionRuntimeService implements PlatformService {

    private final ExecutionRuntimeSettings settings;

    private final MainThreadBridge mainThread;

    private final ExecutionTextResolver textResolver;

    private final AtomicBoolean initialized =
            new AtomicBoolean(false);

    public ExecutionRuntimeService() {
        this(
                ExecutionRuntimeSettings
                        .builder()
                        .validate()
        );
    }

    public ExecutionRuntimeService(
            ExecutionRuntimeSettings settings
    ) {
        this.settings =
                Objects.requireNonNull(
                        settings,
                        "settings"
                ).validate();

        JavaPlugin plugin =
                getPlugin().to(
                        JavaPlugin.class
                );

        this.mainThread =
                new MainThreadBridge(
                        plugin,
                        settings.maxSyncOperationsPerTick(),
                        settings.maxPendingSyncOperations(),
                        settings.maxSyncBudget(),
                        settings.slowSyncWarning()
                );

        this.textResolver =
                new ExecutionTextResolver(
                        plugin
                );

        registerImpl(ExecutionRuntimeService.class, this, true);
    }

    @Override
    public void initialize() {
        if (
                !initialized.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        mainThread.start();
    }

    @Override
    public void reload() {
        /*
         * Runtime infrastructure is persistent.
         */
    }

    @Override
    public void shutdown() {
        if (
                !initialized.compareAndSet(
                        true,
                        false
                )
        ) {
            return;
        }

        mainThread.shutdown();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    public ExecutionContext.Builder context() {
        return ExecutionContext.builder(
                mainThread
        );
    }

    public MainThreadBridge mainThread() {
        return mainThread;
    }

    public ExecutionTextResolver text() {
        return textResolver;
    }

    public ExecutionRuntimeSettings settings() {
        return settings;
    }

    public boolean isInitialized() {
        return initialized.get();
    }
}