package me.blueslime.meteor.paper.extras.menus;

import me.blueslime.meteor.color.renders.ComponentRenderer;

import me.blueslime.meteor.paper.extras.actions.ActionService;
import me.blueslime.meteor.paper.extras.actions.runtime.ActionPlan;

import me.blueslime.meteor.paper.extras.animation.AnimationFrame;
import me.blueslime.meteor.paper.extras.animation.AnimationService;
import me.blueslime.meteor.paper.extras.animation.AnimationTarget;
import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionEvaluation;

import me.blueslime.meteor.paper.extras.interaction.InteractionType;
import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;
import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;
import me.blueslime.meteor.paper.extras.interaction.runtime.InteractionExecutor;

import me.blueslime.meteor.paper.extras.interaction.placement.FirstEmptyPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.SlotPlacement;

import me.blueslime.meteor.paper.extras.item.ItemRenderer;
import me.blueslime.meteor.paper.extras.item.compiler.ItemCompiler;

import me.blueslime.meteor.paper.extras.menus.compiler.MenuCompiler;
import me.blueslime.meteor.paper.extras.menus.definition.MenuDefinition;
import me.blueslime.meteor.paper.extras.menus.listener.MenuListener;
import me.blueslime.meteor.paper.extras.menus.registry.MenuRegistry;

import me.blueslime.meteor.paper.extras.menus.session.MenuOpenResult;
import me.blueslime.meteor.paper.extras.menus.session.MenuOpenStatus;
import me.blueslime.meteor.paper.extras.menus.session.MenuSession;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;

import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContextSnapshot;

import me.blueslime.meteor.platforms.api.configuration.PlatformConfigurations;
import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.Bukkit;

import org.bukkit.entity.Player;

import org.bukkit.event.inventory.InventoryCloseEvent;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.io.File;

import java.util.*;
import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class MenuService
        implements PlatformService {

    private final MenuServiceSettings settings;

    private final MenuRegistry registry =
            new MenuRegistry();

    private AnimationService animations;

    private final ConcurrentMap<
            UUID,
            MenuSession
            > sessions =
            new ConcurrentHashMap<>();

    private final ConcurrentMap<
            UUID,
            AtomicLong
            > generations =
            new ConcurrentHashMap<>();

    private final ExecutorService executor;

    private final Semaphore openPermits;

    private final AtomicBoolean initialized =
            new AtomicBoolean(false);

    private final AtomicBoolean listenerRegistered =
            new AtomicBoolean(false);

    private ExecutionRuntimeService runtime;

    private ConditionService conditions;

    private ActionService actions;

    private ItemRenderer renderer;

    private InteractionExecutor interactionExecutor;

    private MenuCompiler compiler;

    public MenuService() {
        this(
                MenuServiceSettings
                        .builder()
                        .validate()
        );
    }

    public MenuService(
            MenuServiceSettings settings
    ) {
        this.settings =
                Objects.requireNonNull(
                        settings
                ).validate();

        ThreadFactory factory =
                Thread.ofVirtual()
                        .name(
                                "meteor-menu-",
                                0
                        )
                        .factory();

        this.animations =
                fetch(
                        AnimationService.class
                );

        this.executor =
                Executors.newThreadPerTaskExecutor(
                        factory
                );

        this.openPermits =
                new Semaphore(
                        settings.maxConcurrentOpens()
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

        this.runtime =
                fetch(
                        ExecutionRuntimeService.class
                );

        this.conditions =
                fetch(
                        ConditionService.class
                );

        this.actions =
                fetch(
                        ActionService.class
                );

        this.renderer =
                fetch(
                        ItemRenderer.class
                );

        this.interactionExecutor =
                new InteractionExecutor(
                        conditions,
                        actions
                );

        ItemCompiler itemCompiler =
                new ItemCompiler(
                        actions,
                        conditions
                );

        this.compiler =
                new MenuCompiler(
                        itemCompiler,
                        actions,
                        conditions
                );

        loadDefinitions();

        if (
                listenerRegistered.compareAndSet(
                        false,
                        true
                )
        ) {
            getEvents().registerListener(
                    new MenuListener(
                            this
                    )
            );
        }
    }

    private void loadDefinitions() {
        File folder =
                getFileOfDirectory(
                        settings.folder()
                );

        if (
                !folder.exists() &&
                        !folder.mkdirs()
        ) {
            throw new IllegalStateException(
                    "Unable to create menu folder: "
                            + folder
            );
        }

        File[] files =
                folder.listFiles(
                        (directory, name) ->
                                name
                                        .toLowerCase(
                                                Locale.ROOT
                                        )
                                        .endsWith(
                                                settings.extension()
                                        )
                );

        if (files == null) {
            registry.replaceAll(
                    List.of()
            );

            return;
        }

        Arrays.sort(
                files,
                Comparator.comparing(
                        File::getName
                )
        );

        PlatformConfigurations provider =
                getPlugin()
                        .getConfigurationProvider();

        List<MenuDefinition> definitions =
                new ArrayList<>();

        /*
         * Compile EVERYTHING first.
         *
         * registry.replaceAll() is only called
         * if every file compiled successfully.
         */
        for (File file : files) {
            String id =
                    removeExtension(
                            file.getName()
                    );

            ConfigurationHandle configuration =
                    provider.load(
                            file
                    );

            definitions.add(
                    compiler.compile(
                            id,
                            configuration
                    )
            );
        }

        registry.replaceAll(
                definitions
        );
    }

    public Optional<MenuDefinition> find(
            String id
    ) {
        return registry.find(
                id
        );
    }

    public CompletableFuture<MenuOpenResult> open(
            String menuId,
            Player player
    ) {
        Objects.requireNonNull(
                player
        );

        return open(
                menuId,
                player.getUniqueId(),
                player.getName()
        );
    }

    /**
     * Useful for Actions where retaining a live Player
     * outside Bukkit thread is undesirable.
     */
    public CompletableFuture<MenuOpenResult> open(
            String menuId,
            UUID playerId
    ) {
        Objects.requireNonNull(
                playerId
        );

        String name =
                runtime
                        .mainThread()
                        .waitFor(() -> {
                            Player player =
                                    Bukkit.getPlayer(
                                            playerId
                                    );

                            return player == null
                                    ? null
                                    : player.getName();
                        });

        if (name == null) {
            return CompletableFuture.completedFuture(
                    MenuOpenResult.of(
                            MenuOpenStatus.PLAYER_OFFLINE
                    )
            );
        }

        return open(
                menuId,
                playerId,
                name
        );
    }

    public CompletableFuture<MenuOpenResult> open(
            String menuId,
            UUID playerId,
            String playerName
    ) {
        MenuDefinition definition =
                registry
                        .find(
                                menuId
                        )
                        .orElse(null);

        if (definition == null) {
            return CompletableFuture.completedFuture(
                    MenuOpenResult.of(
                            MenuOpenStatus.NOT_FOUND
                    )
            );
        }

        if (
                !openPermits.tryAcquire()
        ) {
            return CompletableFuture.completedFuture(
                    MenuOpenResult.of(
                            MenuOpenStatus.REJECTED
                    )
            );
        }

        long generation =
                generations
                        .computeIfAbsent(
                                playerId,
                                ignored ->
                                        new AtomicLong()
                        )
                        .incrementAndGet();

        CompletableFuture<MenuOpenResult> future =
                new CompletableFuture<>();

        try {
            executor.submit(() -> {
                try {
                    MenuOpenResult result =
                            prepareAndOpen(
                                    definition,
                                    playerId,
                                    playerName,
                                    generation
                            );

                    future.complete(
                            result
                    );

                } catch (Throwable throwable) {
                    future.completeExceptionally(
                            throwable
                    );

                } finally {
                    openPermits.release();
                }
            });

        } catch (
                RejectedExecutionException exception
        ) {
            openPermits.release();

            future.complete(
                    MenuOpenResult.of(
                            MenuOpenStatus.REJECTED
                    )
            );
        }

        return future;
    }

    private MenuOpenResult prepareAndOpen(
            MenuDefinition definition,
            UUID playerId,
            String playerName,
            long generation
    ) {
        ExecutionContext baseContext =
                runtime
                        .context()
                        .player(
                                playerId,
                                playerName
                        )
                        .variable(
                                "menuId",
                                definition.id()
                        )
                        .variable(
                                "menu_id",
                                definition.id()
                        )
                        .build();

        ConditionEvaluation openEvaluation =
                conditions.evaluateNow(
                        definition.openConditions(),
                        baseContext
                );

        if (!openEvaluation.passed()) {
            executeLifecycle(
                    definition.deniedOpenActions(),
                    baseContext,
                    playerId
            );

            return MenuOpenResult.of(
                    MenuOpenStatus.DENIED
            );
        }

        ExecutionContextSnapshot snapshot =
                baseContext.snapshot();

        List<PreparedMenuItem> visible =
                new ArrayList<>();

        for (
                InteractiveItemDefinition item :
                definition.orderedItems()
        ) {
            if (
                    !isCurrentGeneration(
                            playerId,
                            generation
                    )
            ) {
                return MenuOpenResult.of(
                        MenuOpenStatus.SUPERSEDED
                );
            }

            ExecutionContext itemContext =
                    createItemContext(
                            snapshot,
                            item.id()
                    );

            ConditionEvaluation visibility =
                    conditions.evaluateNow(
                            item.visibility(),
                            itemContext
                    );

            if (!visibility.passed()) {
                continue;
            }

            visible.add(
                    new PreparedMenuItem(
                            item,
                            itemContext
                    )
            );
        }

        String preparedTitle =
                runtime
                        .text()
                        .resolveContext(
                                baseContext,
                                definition.titleTemplate()
                        );

        return runtime
                .mainThread()
                .waitFor(() ->
                        commitOpen(
                                definition,
                                snapshot,
                                visible,
                                preparedTitle,
                                playerId,
                                generation
                        )
                );
    }

    private MenuOpenResult commitOpen(
            MenuDefinition definition,
            ExecutionContextSnapshot snapshot,
            List<PreparedMenuItem> visible,
            String preparedTitle,
            UUID playerId,
            long generation
    ) {
        if (
                !isCurrentGeneration(
                        playerId,
                        generation
                )
        ) {
            return MenuOpenResult.of(
                    MenuOpenStatus.SUPERSEDED
            );
        }

        Player player =
                Bukkit.getPlayer(
                        playerId
                );

        if (player == null) {
            return MenuOpenResult.of(
                    MenuOpenStatus.PLAYER_OFFLINE
            );
        }

        String title =
                runtime
                        .text()
                        .resolvePlaceholders(
                                player,
                                preparedTitle
                        );

        MenuSession session =
                new MenuSession(
                        playerId,
                        definition,
                        snapshot,
                        ComponentRenderer.translate(
                                title
                        )
                );

        /*
         * Explicit slots first.
         *
         * This means FirstEmptyPlacement can never
         * consume a slot reserved by a later explicit
         * item.
         */
        visible
                .stream()
                .filter(prepared ->
                        prepared
                                .definition()
                                .placement()
                                instanceof SlotPlacement
                )
                .forEach(prepared ->
                        place(
                                session,
                                player,
                                prepared
                        )
                );

        visible
                .stream()
                .filter(prepared ->
                        prepared
                                .definition()
                                .placement()
                                instanceof FirstEmptyPlacement
                )
                .forEach(prepared ->
                        place(
                                session,
                                player,
                                prepared
                        )
                );

        /*
         * Publish new session BEFORE openInventory().
         *
         * Opening it will synchronously close the old
         * inventory. Its InventoryCloseEvent uses
         * compare-and-remove, so it cannot remove this
         * new session.
         */
        MenuSession previous =
                sessions.put(
                        playerId,
                        session
                );

        if (previous != null) {
            previous.invalidate();
        }

        player.openInventory(
                session.getInventory()
        );

        /*
         * Another plugin may cancel opening.
         */
        if (
                player
                        .getOpenInventory()
                        .getTopInventory()
                        .getHolder()
                        != session
        ) {
            sessions.remove(
                    playerId,
                    session
            );

            session.invalidate();

            animations.cancelGroup(
                    session.sessionId()
            );

            return MenuOpenResult.of(
                    MenuOpenStatus.SUPERSEDED
            );
        }

        registerAnimations(
            session
        );

        return MenuOpenResult.opened(
                session
        );
    }

    private void registerAnimations(
            MenuSession session
    ) {
        for (
                InteractiveItemDefinition definition :
                session.boundItems()
        ) {
            registerAnimation(
                    session,
                    definition
            );
        }
    }

    private void registerAnimation(
            MenuSession session,
            InteractiveItemDefinition definition
    ) {
        if (!definition.animated()) {
            return;
        }

        animations.register(
                session.sessionId(),
                definition.id(),
                definition.animation(),
                new AnimationTarget() {

                    @Override
                    public boolean active() {
                        return session.isActive()
                                && sessions.get(
                                session.playerId()
                        ) == session;
                    }

                    @Override
                    public boolean apply(
                            AnimationFrame frame,
                            int frameIndex
                    ) {
                        return applyAnimationFrame(
                                session,
                                definition,
                                frame,
                                frameIndex
                        );
                    }
                }
        );
    }

    private boolean applyAnimationFrame(
            MenuSession session,
            InteractiveItemDefinition definition,
            AnimationFrame frame,
            int frameIndex
    ) {
        if (
                !session.isActive() ||
                        sessions.get(
                                session.playerId()
                        ) != session
        ) {
            return false;
        }

        Player player =
                Bukkit.getPlayer(
                        session.playerId()
                );

        if (player == null) {
            return false;
        }

        Set<Integer> currentSlots =
                session.slotsOf(
                        definition.id()
                );

        if (currentSlots.isEmpty()) {
            return false;
        }

        ExecutionContext context =
                runtime
                        .context()
                        .inherit(
                                session.contextSnapshot()
                        )
                        .variable(
                                "menuId",
                                session.definition().id()
                        )
                        .variable(
                                "itemId",
                                definition.id()
                        )
                        .variable(
                                "animationFrame",
                                frameIndex
                        )
                        .variable(
                                "animation_frame",
                                frameIndex
                        )
                        .build();

        ItemStack rendered =
                renderer.renderSync(
                        frame.item(),
                        context,
                        player
                );

        /*
         * No placement override:
         *
         * only change the ItemStack in its current slots.
         */
        if (!frame.movesItem()) {
            for (int slot : currentSlots) {
                session
                        .getInventory()
                        .setItem(
                                slot,
                                rendered.clone()
                        );
            }

            return true;
        }

        Set<Integer> targetSlots =
                resolveMenuAnimationSlots(
                        session,
                        definition,
                        frame.placement()
                );

        if (targetSlots.isEmpty()) {
            /*
             * Invalid/colliding movement.
             *
             * Keep previous position but still update
             * the visual frame.
             */
            for (int slot : currentSlots) {
                session
                        .getInventory()
                        .setItem(
                                slot,
                                rendered.clone()
                        );
            }

            return true;
        }

        for (int oldSlot : currentSlots) {
            session
                    .getInventory()
                    .setItem(
                            oldSlot,
                            null
                    );
        }

        session.unbindItem(
                definition.id()
        );

        for (int target : targetSlots) {
            session
                    .getInventory()
                    .setItem(
                            target,
                            rendered.clone()
                    );

            session.bind(
                    target,
                    definition
            );
        }

        return true;
    }

    private Set<Integer> resolveMenuAnimationSlots(
            MenuSession session,
            InteractiveItemDefinition definition,
            ItemPlacement placement
    ) {
        if (
                placement instanceof SlotPlacement slots
        ) {
            LinkedHashSet<Integer> result =
                    new LinkedHashSet<>();

            for (int slot : slots.slots()) {
                if (
                        slot < 0 ||
                                slot >=
                                        session
                                                .getInventory()
                                                .getSize() ||
                                !session.canBind(
                                        slot,
                                        definition.id()
                                )
                ) {
                    return Set.of();
                }

                result.add(slot);
            }

            return result;
        }

        if (
                placement instanceof FirstEmptyPlacement
        ) {
            int slot =
                    session.firstAvailableSlot(
                            definition.id()
                    );

            return slot < 0
                    ? Set.of()
                    : Set.of(slot);
        }

        return Set.of();
    }

    public CompletableFuture<Boolean> refreshItem(
            UUID playerId,
            String itemId
    ) {
        MenuSession session =
                sessions.get(
                        playerId
                );

        if (
                session == null ||
                        !session.isActive()
        ) {
            return CompletableFuture.completedFuture(
                    false
            );
        }

        InteractiveItemDefinition definition =
                session
                        .definition()
                        .item(itemId)
                        .orElse(null);

        if (definition == null) {
            return CompletableFuture.completedFuture(
                    false
            );
        }

        CompletableFuture<Boolean> future =
                new CompletableFuture<>();

        executor.submit(() -> {
            try {
                if (
                        sessions.get(playerId)
                                != session ||
                                !session.isActive()
                ) {
                    future.complete(false);
                    return;
                }

                ExecutionContext context =
                        createItemContext(
                                session.contextSnapshot(),
                                definition.id()
                        );

                ConditionEvaluation evaluation =
                        conditions.evaluateNow(
                                definition.visibility(),
                                context
                        );

                boolean result =
                        runtime
                                .mainThread()
                                .waitFor(() -> {
                                    if (
                                            sessions.get(playerId)
                                                    != session ||
                                                    !session.isActive()
                                    ) {
                                        return false;
                                    }

                                    animations.cancel(
                                            session.sessionId(),
                                            definition.id()
                                    );

                                    Set<Integer> previous =
                                            session.unbindItem(
                                                    definition.id()
                                            );

                                    for (int slot : previous) {
                                        session
                                                .getInventory()
                                                .setItem(
                                                        slot,
                                                        null
                                                );
                                    }

                                    if (!evaluation.passed()) {
                                        return true;
                                    }

                                    Player player =
                                            Bukkit.getPlayer(
                                                    playerId
                                            );

                                    if (player == null) {
                                        return false;
                                    }

                                    place(
                                            session,
                                            player,
                                            new PreparedMenuItem(
                                                    definition,
                                                    context
                                            )
                                    );

                                    registerAnimation(
                                            session,
                                            definition
                                    );

                                    return true;
                                });

                future.complete(result);

            } catch (Throwable throwable) {
                future.completeExceptionally(
                        throwable
                );
            }
        });

        return future;
    }

    public CompletableFuture<Boolean> refreshAll(
            UUID playerId
    ) {
        MenuSession session =
                sessions.get(
                        playerId
                );

        if (
                session == null ||
                        !session.isActive()
        ) {
            return CompletableFuture.completedFuture(
                    false
            );
        }

        CompletableFuture<Boolean> future =
                new CompletableFuture<>();

        executor.submit(() -> {
            try {
                List<PreparedMenuItem> visible =
                        new ArrayList<>();

                for (
                        InteractiveItemDefinition definition :
                        session
                                .definition()
                                .orderedItems()
                ) {
                    ExecutionContext context =
                            createItemContext(
                                    session.contextSnapshot(),
                                    definition.id()
                            );

                    if (
                            conditions
                                    .evaluateNow(
                                            definition.visibility(),
                                            context
                                    )
                                    .passed()
                    ) {
                        visible.add(
                                new PreparedMenuItem(
                                        definition,
                                        context
                                )
                        );
                    }
                }

                boolean result =
                        runtime
                                .mainThread()
                                .waitFor(() -> {
                                    if (
                                            sessions.get(playerId)
                                                    != session ||
                                                    !session.isActive()
                                    ) {
                                        return false;
                                    }

                                    Player player =
                                            Bukkit.getPlayer(
                                                    playerId
                                            );

                                    if (player == null) {
                                        return false;
                                    }

                                    animations.cancelGroup(
                                            session.sessionId()
                                    );

                                    session
                                            .getInventory()
                                            .clear();

                                    session.clearBindings();

                                    visible
                                            .stream()
                                            .filter(value ->
                                                    value
                                                            .definition()
                                                            .placement()
                                                            instanceof SlotPlacement
                                            )
                                            .forEach(value ->
                                                    place(
                                                            session,
                                                            player,
                                                            value
                                                    )
                                            );

                                    visible
                                            .stream()
                                            .filter(value ->
                                                    value
                                                            .definition()
                                                            .placement()
                                                            instanceof FirstEmptyPlacement
                                            )
                                            .forEach(value ->
                                                    place(
                                                            session,
                                                            player,
                                                            value
                                                    )
                                            );

                                    registerAnimations(
                                            session
                                    );

                                    return true;
                                });

                future.complete(result);

            } catch (Throwable throwable) {
                future.completeExceptionally(
                        throwable
                );
            }
        });

        return future;
    }

    private void place(
            MenuSession session,
            Player player,
            PreparedMenuItem prepared
    ) {
        InteractiveItemDefinition definition =
                prepared.definition();

        ItemStack rendered =
                renderer.renderSync(
                        definition.item(),
                        prepared.context(),
                        player
                );

        Inventory inventory =
                session.getInventory();

        switch (
                definition.placement()
        ) {
            case SlotPlacement slots -> {
                for (
                        int slot :
                        slots.slots()
                ) {
                    inventory.setItem(
                            slot,
                            rendered.clone()
                    );

                    session.bind(
                            slot,
                            definition
                    );
                }
            }

            case FirstEmptyPlacement ignored -> {
                int slot =
                        inventory.firstEmpty();

                if (slot < 0) {
                    return;
                }

                inventory.setItem(
                        slot,
                        rendered
                );

                session.bind(
                        slot,
                        definition
                );
            }

            default ->
                    throw new IllegalStateException(
                            "Unsupported placement inside MenuSession: "
                                    + definition
                                    .placement()
                                    .type()
                    );
        }
    }

    public Optional<MenuSession> session(
            UUID playerId
    ) {
        return Optional.ofNullable(
                sessions.get(
                        playerId
                )
        );
    }

    public CompletableFuture<Void> interact(
            MenuSession session,
            int rawSlot,
            InteractionType interaction
    ) {
        if (
                session == null ||
                        !session.isActive()
        ) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        InteractiveItemDefinition definition =
                session
                        .itemAt(
                                rawSlot
                        )
                        .orElse(null);

        if (definition == null) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        ExecutionContext context =
                runtime
                        .context()
                        .inherit(
                                session.contextSnapshot()
                        )
                        .variable(
                                "menuId",
                                session
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "menu_id",
                                session
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "itemId",
                                definition.id()
                        )
                        .variable(
                                "item_id",
                                definition.id()
                        )
                        .variable(
                                "slot",
                                rawSlot
                        )
                        .variable(
                                "interaction",
                                interaction.name()
                        )
                        .build();

        return interactionExecutor.execute(
                definition,
                interaction,
                context
        );
    }

    public void handleOpen(
            MenuSession session
    ) {
        if (
                session == null ||
                        !session.isActive() ||
                        !session.markOpened()
        ) {
            return;
        }

        ExecutionContext context =
                runtime
                        .context()
                        .inherit(
                                session.contextSnapshot()
                        )
                        .variable(
                                "menuId",
                                session
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "menu_id",
                                session
                                        .definition()
                                        .id()
                        )
                        .build();

        executeLifecycle(
                session
                        .definition()
                        .openActions(),
                context,
                session.playerId()
        );
    }

    public void handleClose(
            MenuSession session,
            InventoryCloseEvent.Reason reason
    ) {
        finishSession(
                session,
                reason == null
                        ? "UNKNOWN"
                        : reason.name()
        );
    }

    public void handleQuit(
            Player player
    ) {
        MenuSession session =
                sessions.get(
                        player.getUniqueId()
                );

        if (session == null) {
            return;
        }

        finishSession(
                session,
                "DISCONNECT"
        );
    }

    private void finishSession(
            MenuSession session,
            String reason
    ) {
        if (
                session == null ||
                        !session.markClosed()
        ) {
            return;
        }

        animations.cancelGroup(
                session.sessionId()
        );

        session.invalidate();

        sessions.remove(
                session.playerId(),
                session
        );

        ExecutionContext context =
                runtime
                        .context()
                        .inherit(
                                session.contextSnapshot()
                        )
                        .variable(
                                "menuId",
                                session
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "menu_id",
                                session
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "closeReason",
                                reason
                        )
                        .variable(
                                "close_reason",
                                reason
                        )
                        .build();

        executeLifecycle(
                session
                        .definition()
                        .closeActions(),
                context,
                session.playerId()
        );
    }

    public CompletableFuture<Void> close(
            UUID playerId
    ) {
        MenuSession session =
                sessions.get(
                        playerId
                );

        if (session == null) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        generations
                .computeIfAbsent(
                        playerId,
                        ignored ->
                                new AtomicLong()
                )
                .incrementAndGet();

        return runtime
                .mainThread()
                .submit(() -> {
                    Player player =
                            Bukkit.getPlayer(
                                    playerId
                            );

                    if (
                            player != null &&
                                    player
                                            .getOpenInventory()
                                            .getTopInventory()
                                            .getHolder()
                                            == session
                    ) {
                        /*
                         * InventoryCloseEvent will finish
                         * the session.
                         */
                        player.closeInventory();

                    } else {
                        finishSession(
                                session,
                                "PLUGIN"
                        );
                    }
                });
    }

    public CompletableFuture<MenuOpenResult> refresh(
            UUID playerId
    ) {
        MenuSession current =
                sessions.get(
                        playerId
                );

        if (current == null) {
            return CompletableFuture.completedFuture(
                    MenuOpenResult.of(
                            MenuOpenStatus.NOT_FOUND
                    )
            );
        }

        return open(
                current
                        .definition()
                        .id(),
                playerId
        );
    }

    private void executeLifecycle(
            ActionPlan plan,
            ExecutionContext context,
            UUID playerId
    ) {
        if (
                plan == null ||
                        plan.isEmpty()
        ) {
            return;
        }

        actions.executeForPlayer(
                plan,
                context,
                playerId
        );
    }

    @Override
    public void reload() {
        List<RefreshRequest> active =
                sessions
                        .values()
                        .stream()
                        .filter(
                                MenuSession::isActive
                        )
                        .map(session ->
                                new RefreshRequest(
                                        session.playerId(),
                                        session
                                                .definition()
                                                .id()
                                )
                        )
                        .toList();

        /*
         * Transactional compile + swap.
         */
        loadDefinitions();

        if (
                !settings
                        .refreshSessionsOnReload()
        ) {
            return;
        }

        for (
                RefreshRequest request :
                active
        ) {
            if (
                    registry
                            .find(
                                    request.menuId()
                            )
                            .isPresent()
            ) {
                open(
                        request.menuId(),
                        request.playerId()
                );

            } else {
                close(
                        request.playerId()
                );
            }
        }
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

        if (
                settings.closeOnShutdown()
        ) {
            runtime
                    .mainThread()
                    .waitFor(() -> {
                        for (
                                MenuSession session :
                                List.copyOf(
                                        sessions.values()
                                )
                        ) {
                            session.invalidate();

                            animations.cancelGroup(
                                    session.sessionId()
                            );

                            Player player =
                                    Bukkit.getPlayer(
                                            session.playerId()
                                    );

                            if (
                                    player != null &&
                                            player
                                                    .getOpenInventory()
                                                    .getTopInventory()
                                                    .getHolder()
                                                    == session
                            ) {
                                player.closeInventory();
                            }
                        }
                    });
        }

        sessions.clear();
        generations.clear();
        registry.clear();

        executor.shutdownNow();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    public MenuRegistry registry() {
        return registry;
    }

    private ExecutionContext createItemContext(
            ExecutionContextSnapshot snapshot,
            String itemId
    ) {
        return runtime
                .context()
                .inherit(
                        snapshot
                )
                .variable(
                        "itemId",
                        itemId
                )
                .variable(
                        "item_id",
                        itemId
                )
                .build();
    }

    private boolean isCurrentGeneration(
            UUID playerId,
            long generation
    ) {
        AtomicLong current =
                generations.get(
                        playerId
                );

        return current != null &&
                current.get() ==
                        generation;
    }

    private String removeExtension(
            String name
    ) {
        String extension =
                settings.extension();

        if (
                name
                        .toLowerCase(
                                Locale.ROOT
                        )
                        .endsWith(
                                extension
                        )
        ) {
            return name.substring(
                    0,
                    name.length()
                            - extension.length()
            );
        }

        return name;
    }

    private record PreparedMenuItem(
            InteractiveItemDefinition definition,
            ExecutionContext context
    ) {}

    private record RefreshRequest(
            UUID playerId,
            String menuId
    ) {}
}