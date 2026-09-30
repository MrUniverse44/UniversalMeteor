package me.blueslime.meteor.paper.extras.actions;

import me.blueslime.meteor.paper.extras.actions.api.Action;

import me.blueslime.meteor.paper.extras.actions.compiler.ActionCompiler;
import me.blueslime.meteor.paper.extras.actions.compiler.ActionParser;

import me.blueslime.meteor.paper.extras.actions.cooldown.CooldownAction;
import me.blueslime.meteor.paper.extras.actions.cooldown.CooldownService;

import me.blueslime.meteor.paper.extras.actions.dispatch.*;

import me.blueslime.meteor.paper.extras.actions.list.*;

import me.blueslime.meteor.paper.extras.actions.list.server.ServerAction;
import me.blueslime.meteor.paper.extras.actions.list.server.ServerTransferService;
import me.blueslime.meteor.paper.extras.actions.list.server.ServerTransferSettings;

import me.blueslime.meteor.paper.extras.actions.registry.ActionRegistry;

import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;
import me.blueslime.meteor.paper.extras.runtime.MainThreadBridge;

import me.blueslime.meteor.paper.extras.runtime.context.ContextKeys;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;

import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import me.blueslime.meteor.paper.extras.runtime.value.RuntimeValueCompiler;

import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.entity.Player;

import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class ActionService
        implements PlatformService {

    private final ActionRegistry registry =
            new ActionRegistry();

    private final ActionParser parser =
            new ActionParser();

    private final CooldownService cooldowns =
            new CooldownService();

    private final Set<String> internalActions =
            ConcurrentHashMap.newKeySet();

    private final Set<String> externalActions =
            ConcurrentHashMap.newKeySet();

    private final ExecutorService executor;

    private final ExecutionRuntimeService runtime;

    private final MainThreadBridge mainThread;

    private final ExecutionTextResolver textResolver;

    private final RuntimeValueCompiler runtimeValues;

    private final ActionCompiler compiler;

    private final ActionDispatcher dispatcher;

    private final ServerTransferService serverTransfers;

    private final AtomicBoolean initialized =
            new AtomicBoolean(false);

    public ActionService() {
        registerImpl(
                ActionService.class,
                this,
                true
        );

        JavaPlugin plugin =
                getPlugin().to(
                        JavaPlugin.class
                );

        this.runtime =
                fetch(
                        ExecutionRuntimeService.class
                );

        this.mainThread =
                runtime.mainThread();

        this.textResolver =
                runtime.text();

        this.runtimeValues =
                new RuntimeValueCompiler(
                        (input, context) ->
                                resolveRuntimeText(
                                        context,
                                        input
                                )
                );

        this.compiler =
                new ActionCompiler(
                        registry,
                        parser,
                        runtimeValues
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

                        /*
                         * Maximum simultaneously-active
                         * ActionPlans.
                         */
                        512
                );

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
        if (
                !initialized.compareAndSet(
                        false,
                        true
                )
        ) {
            return;
        }

        dispatcher.start();

        serverTransfers.start();
    }

    public void registerInternalAction(
            Action... actions
    ) {
        if (
                actions == null ||
                        actions.length == 0
        ) {
            return;
        }

        for (Action action : actions) {
            if (action == null) {
                continue;
            }

            registry.register(
                    action
            );

            internalActions.add(
                    normalize(
                            action.id()
                    )
            );
        }
    }

    public void registerAction(
            Action... actions
    ) {
        if (
            actions == null ||
            actions.length == 0
        ) {
            return;
        }

        for (Action action : actions) {
            if (action == null) {
                continue;
            }

            registry.register(
                    action
            );

            externalActions.add(
                    normalize(
                            action.id()
                    )
            );
        }
    }

    public Action unregisterAction(
            String id
    ) {
        String normalized =
                normalize(
                        id
                );

        if (
                internalActions.contains(
                        normalized
                )
        ) {
            throw new IllegalArgumentException(
                    "Internal action '"
                            + id
                            + "' cannot be unregistered"
            );
        }

        externalActions.remove(
                normalized
        );

        return registry.unregister(
                id
        );
    }

    public void clearExternalActions() {
        for (
                String id :
                new HashSet<>(
                        externalActions
                )
        ) {
            registry.unregister(
                    id
            );
        }

        externalActions.clear();
    }

    public List<Action> getActions() {
        return internalActions
                .stream()
                .map(
                        registry::find
                )
                .flatMap(
                        Optional::stream
                )
                .toList();
    }

    public List<Action> getExternalActions() {
        return externalActions
                .stream()
                .map(
                        registry::find
                )
                .flatMap(
                        Optional::stream
                )
                .toList();
    }

    public ActionRegistry getRegistry() {
        return registry;
    }

    public void registerLane(
            ExecutionLane lane
    ) {
        dispatcher.registerLane(
                lane
        );
    }

    public ActionPlan compile(
            Collection<String> actions
    ) {
        return compiler.compile(
                actions
        );
    }

    public ActionPlan compile(
            String action
    ) {
        return compiler.compile(
                action
        );
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
        return context()
                .player(
                        player
                );
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


    /**
     * Resolves dynamic text used by RuntimeValueCompiler.
     * <br>
     * Resolution order:
     * <br>
     * 1. Meteor execution variables/context.
     * 2. PlaceholderAPI, when a player is available.
     * <br>
     * PlaceholderAPI resolution is performed through waitSyncPlayer()
     * because third-party PlaceholderAPI expansions cannot generally be
     * assumed to be async-safe.
     */
    private String resolveRuntimeText(
            ExecutionContext context,
            String input
    ) {
        if (input == null) {
            return "";
        }

        context
                .cancellation()
                .throwIfCancelled();

        /*
         * First resolve Meteor-owned values.
         *
         * Examples:
         *
         * <player>
         * <player_name>
         * {inventoryId}
         * <itemId>
         */
        String resolved =
                textResolver.resolveContext(
                        context,
                        input
                );

        /*
         * Avoid a main-thread jump when the string obviously
         * contains no PlaceholderAPI-style expression.
         */
        if (!containsPlaceholderExpression(resolved)) {
            return resolved;
        }

        /*
         * PlaceholderAPI values such as:
         *
         * %player_x%
         * %player_y%
         * %player_world%
         *
         * require an actual player.
         */
        if (
                !context.contains(
                        ContextKeys.PLAYER_ID
                )
        ) {
            return resolved;
        }

        String placeholderResolved =
                context.waitSyncPlayer(
                        player ->
                                textResolver.resolvePlaceholders(
                                        player,
                                        resolved
                                )
                );

        context.cancellation().throwIfCancelled();

        /*
         * The player may have disconnected between scheduling
         * and the main-thread lookup.
         */
        return placeholderResolved == null
                ? resolved
                : placeholderResolved;
    }

    /**
     * Cheap detection before performing a main-thread bridge.
     * <br><br>
     * We require two '%' characters so strings such as:
     * <br>
     * "100%"
     * <br><br>
     * don't trigger PlaceholderAPI work.
     */
    private boolean containsPlaceholderExpression(String input) {
        if (input == null || input.isEmpty()) {
            return false;
        }

        int start = input.indexOf('%');

        if (start < 0) {
            return false;
        }

        int end = input.indexOf('%', start + 1);

        return end > start + 1;
    }

    public MainThreadBridge getMainThreadBridge() {
        return mainThread;
    }

    public CooldownService getCooldownService() {
        return cooldowns;
    }

    public RuntimeValueCompiler getRuntimeValueCompiler() {
        return runtimeValues;
    }

    public ExecutionTextResolver getTextResolver() {
        return textResolver;
    }

    @Override
    public void reload() {
        /*
         * ActionService is runtime infrastructure.
         *
         * Existing compiled plans remain immutable.
         *
         * Menu/inventory/configuration reloads simply compile
         * and publish new ActionPlans.
         *
         * The RuntimeValueCompiler itself has no mutable
         * configuration to reload.
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
        if (id == null) {
            throw new IllegalArgumentException("Action id cannot be null");
        }

        return id.trim().toLowerCase(Locale.ROOT);
    }
}