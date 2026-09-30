package me.blueslime.meteor.paper.extras.actions;

import me.blueslime.meteor.paper.extras.actions.api.Action;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompiler;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionParser;
import me.blueslime.meteor.paper.extras.actions.dispatch.*;
import me.blueslime.meteor.paper.extras.actions.list.*;
import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.actions.cooldown.CooldownAction;
import me.blueslime.meteor.paper.extras.actions.cooldown.CooldownService;
import me.blueslime.meteor.paper.extras.services.actions.dispatch.*;
import me.blueslime.meteor.paper.extras.actions.list.server.ServerAction;
import me.blueslime.meteor.paper.extras.actions.list.server.ServerTransferService;
import me.blueslime.meteor.paper.extras.actions.list.server.ServerTransferSettings;
import me.blueslime.meteor.paper.extras.actions.registry.ActionRegistry;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;
import me.blueslime.meteor.paper.extras.runtime.MainThreadBridge;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ActionService
        implements PlatformService {

    private final ActionRegistry registry = new ActionRegistry();

    private final ActionParser parser = new ActionParser();

    private final CooldownService cooldowns = new CooldownService();

    private final Set<String> internalActions =
            ConcurrentHashMap.newKeySet();

    private final Set<String> externalActions =
            ConcurrentHashMap.newKeySet();

    private final ExecutorService executor;

    private final ExecutionRuntimeService runtime;

    private final MainThreadBridge mainThread;

    private final ActionDispatcher dispatcher;

    private final ExecutionTextResolver textResolver;

    private final ActionCompiler compiler;

    private final AtomicBoolean initialized =
            new AtomicBoolean(false);

    private final ServerTransferService serverTransfers;

    public ActionService() {
        registerImpl(ActionService.class, this, true);

        JavaPlugin plugin =
                getPlugin().to(
                        JavaPlugin.class
                );

        ThreadFactory threadFactory =
                Thread.ofVirtual()
                        .name(
                                "meteor-action-",
                                0
                        )
                        .factory();

        this.executor =
                Executors.newThreadPerTaskExecutor(
                        threadFactory
                );

        this.dispatcher =
                new ActionDispatcher(
                        plugin,
                        executor,

                        // virtual action plans simultáneos
                        512
                );

        this.compiler =
                new ActionCompiler(
                        registry,
                        parser
                );

        this.runtime =
                fetch(
                        ExecutionRuntimeService.class
                );

        this.mainThread =
                runtime.mainThread();

        this.textResolver =
                runtime.text();

        this.serverTransfers =
                new ServerTransferService(
                        plugin,
                        this,
                        mainThread,
                        ServerTransferSettings
                                .builder()
                                .processInterval(
                                        Duration.ofMillis(
                                                50
                                        )
                                )
                                .maxQueueSizePerServer(
                                        10_000
                                )
                                .maxGlobalQueueSize(
                                        50_000
                                )
                                .maxMessagesPerSyncBatch(
                                        32
                                )
                );


        registerLane(
                ActionLanes.PLAYER_INTERACTION
        );

        registerLane(
                ActionLanes.WORLD_MUTATION
        );

        registerLane(
                ActionLanes.SYSTEM
        );

        registerInternalAction(
            new MessageAction(
                    textResolver
            ),
            new ActionBarAction(
                    textResolver
            ),
            new ConsoleAction(
                    textResolver
            ),
            new ChatAction(
                    textResolver
            ),
            new CloseMenuAction(),
            new PlaySoundAction(
                    textResolver
            ),
            new MenuAction(
                textResolver
            ),
            new PlayerCommandAction(
                    textResolver
            ),
            new CooldownAction(
                    cooldowns
            ),
            new ServerAction(
                serverTransfers,
                textResolver
            )
        );
    }

    @Override
    public void initialize() {
        if (!initialized.compareAndSet(
                false,
                true
        )) {
            return;
        }

        dispatcher.start();
        serverTransfers.start();
    }

    public void registerInternalAction(
            Action... actions
    ) {
        for (Action action : actions) {
            registry.register(action);

            internalActions.add(
                    normalize(action.id())
            );
        }
    }

    public void registerAction(
            Action... actions
    ) {
        for (Action action : actions) {
            registry.register(action);

            externalActions.add(
                    normalize(action.id())
            );
        }
    }

    public Action unregisterAction(
            String id
    ) {
        String normalized =
                normalize(id);

        if (internalActions.contains(normalized)) {
            throw new IllegalArgumentException(
                    "Internal action '"
                            + id
                            + "' cannot be unregistered"
            );
        }

        externalActions.remove(
                normalized
        );

        return registry.unregister(id);
    }

    public void clearExternalActions() {
        for (
                String id :
                new HashSet<>(externalActions)
        ) {
            registry.unregister(id);
        }

        externalActions.clear();
    }

    public List<Action> getActions() {
        return internalActions.stream()
                .map(registry::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public List<Action> getExternalActions() {
        return externalActions.stream()
                .map(registry::find)
                .flatMap(Optional::stream)
                .toList();
    }

    public void registerLane(
            ExecutionLane lane
    ) {
        dispatcher.registerLane(lane);
    }

    public ActionPlan compile(
            Collection<String> actions
    ) {
        return compiler.compile(actions);
    }

    public ActionPlan compile(
            String action
    ) {
        return compiler.compile(action);
    }

    public ActionPlan compileInline(
            String actions
    ) {
        return compiler.compileInline(
                actions
        );
    }

    public ExecutionContext.Builder context() {
        return ExecutionContext.builder(
                mainThread
        );
    }

    public ExecutionContext.Builder context(
            Player player
    ) {
        return context().player(player);
    }

    public CompletableFuture<ActionExecutionResult> execute(
            ActionPlan plan,
            ExecutionContext context,
            ActionExecutionOptions options
    ) {
        return dispatcher.submit(
                plan,
                context,
                options
        );
    }

    public CompletableFuture<ActionExecutionResult> execute(
            ActionPlan plan,
            ExecutionContext context,
            ExecutionLane lane
    ) {
        return execute(
                plan,
                context,
                ActionExecutionOptions.lane(
                        lane
                )
        );
    }

    public CompletableFuture<ActionExecutionResult> execute(
            ActionPlan plan,
            ExecutionContext context
    ) {
        return execute(
                plan,
                context,
                ActionLanes.SYSTEM
        );
    }

    public CompletableFuture<ActionExecutionResult> executeForPlayer(
            ActionPlan plan,
            ExecutionContext context,
            UUID playerId
    ) {
        return execute(
                plan,
                context,
                ActionExecutionOptions
                        .lane(
                                ActionLanes.PLAYER_INTERACTION
                        )
                        .serializedBy(
                                playerId.toString()
                        )
        );
    }

    public CompletableFuture<ActionExecutionResult> executeForPlayer(
            ActionPlan plan,
            ExecutionContext context,
            UUID playerId,
            String coalesceKey
    ) {
        return execute(
                plan,
                context,
                ActionExecutionOptions
                        .lane(
                                ActionLanes.PLAYER_INTERACTION
                        )
                        .serializedBy(
                                playerId.toString()
                        )
                        .coalesceBy(
                                coalesceKey
                        )
        );
    }

    public CompletableFuture<ActionExecutionResult> executeWorldMutation(
            ActionPlan plan,
            ExecutionContext context
    ) {
        return execute(
                plan,
                context,
                ActionLanes.WORLD_MUTATION
        );
    }

    public MainThreadBridge getMainThreadBridge() {
        return mainThread;
    }

    public CooldownService getCooldownService() {
        return cooldowns;
    }

    public ActionRegistry getRegistry() {
        return registry;
    }

    @Override
    public void reload() {
        /*
         * ActionService is a runtime infrastructure
         * service.
         *
         * Actions and virtual-thread infrastructure
         * should not be destroyed during a simple
         * configuration reload.
         *
         * Menu/inventory compilers will simply
         * generate new ActionPlans.
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

        serverTransfers.shutdown();

        dispatcher.shutdown();

        cooldowns.clear();

        executor.shutdownNow();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private String normalize(String id) {
        return id.trim()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}