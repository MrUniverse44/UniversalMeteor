package me.blueslime.meteor.paper.extras.inventories;

import me.blueslime.meteor.paper.extras.actions.ActionService;

import me.blueslime.meteor.paper.extras.animation.AnimationFrame;
import me.blueslime.meteor.paper.extras.animation.AnimationService;
import me.blueslime.meteor.paper.extras.animation.AnimationTarget;
import me.blueslime.meteor.paper.extras.conditions.ConditionService;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionEvaluation;

import me.blueslime.meteor.paper.extras.interaction.InteractionType;
import me.blueslime.meteor.paper.extras.interaction.InteractiveItemDefinition;
import me.blueslime.meteor.paper.extras.interaction.runtime.InteractionExecutor;

import me.blueslime.meteor.paper.extras.interaction.placement.ArmorPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.FirstEmptyPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.ItemPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.OffhandPlacement;
import me.blueslime.meteor.paper.extras.interaction.placement.SlotPlacement;

import me.blueslime.meteor.paper.extras.inventories.compiler.InventoryCompiler;
import me.blueslime.meteor.paper.extras.inventories.compiler.InventoryLayout;

import me.blueslime.meteor.paper.extras.inventories.definition.PlayerInventoryDefinition;

import me.blueslime.meteor.paper.extras.inventories.listener.PlayerInventoryListener;

import me.blueslime.meteor.paper.extras.inventories.registry.InventoryRegistry;

import me.blueslime.meteor.paper.extras.inventories.session.PlayerInventorySession;
import me.blueslime.meteor.paper.extras.inventories.session.ResolvedInventoryItem;

import me.blueslime.meteor.paper.extras.item.ItemRenderer;

import me.blueslime.meteor.paper.extras.item.compiler.ItemCompiler;

import me.blueslime.meteor.paper.extras.item.identity.ItemIdentity;
import me.blueslime.meteor.paper.extras.item.identity.ItemIdentityService;
import me.blueslime.meteor.paper.extras.item.identity.ItemIdentityType;

import me.blueslime.meteor.paper.extras.languages.LanguageService;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;

import me.blueslime.meteor.paper.extras.runtime.compiler.CompilationReporter;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContextSnapshot;

import me.blueslime.meteor.platforms.api.configuration.handle.ConfigurationHandle;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import org.bukkit.Bukkit;

import org.bukkit.entity.Player;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class InventoryService implements PlatformService {

    private final InventoryServiceSettings settings;

    private AnimationService animations;

    private final InventoryRegistry registry =
            new InventoryRegistry();

    private final ConcurrentMap<
            UUID,
            PlayerInventorySession
            > sessions =
            new ConcurrentHashMap<>();

    /**
     * Prevents an older slow apply() from overwriting
     * a newer apply() for the same player.
     */
    private final ConcurrentMap<
            UUID,
            AtomicLong
            > generations =
            new ConcurrentHashMap<>();

    private final AtomicBoolean initialized =
            new AtomicBoolean(false);

    private final AtomicBoolean listenerRegistered =
            new AtomicBoolean(false);

    private final ExecutorService executor;

    private final Semaphore applyPermits;

    private ExecutionRuntimeService runtime;

    private ConditionService conditions;

    private ActionService actions;

    private ItemRenderer renderer;

    private ItemIdentityService identities;

    private InteractionExecutor interactionExecutor;

    private InventoryCompiler compiler;

    public InventoryService() {
        this(
                InventoryServiceSettings
                        .builder()
                        .validate()
        );
    }

    public InventoryService(
            InventoryServiceSettings settings
    ) {
        this.settings =
                Objects.requireNonNull(
                        settings,
                        "settings"
                ).validate();

        ThreadFactory factory =
                Thread.ofVirtual()
                        .name(
                                "meteor-inventory-",
                                0
                        )
                        .factory();

        this.executor =
                Executors.newThreadPerTaskExecutor(
                        factory
                );

        this.applyPermits =
                new Semaphore(
                        settings
                                .getMaxConcurrentApplies()
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

        this.animations =
                fetch(
                        AnimationService.class
                );

        this.identities =
                new ItemIdentityService(
                        getPlugin().to(
                                JavaPlugin.class
                        )
                );

        this.interactionExecutor =
                new InteractionExecutor(
                        conditions,
                        actions
                );

        CompilationReporter compilationReporter =
                (
                        type,
                        id,
                        path,
                        exception
                ) -> {
                    getLogger().error(
                            exception,
                            "[Configuration] Disabled "
                                    + type
                                    + " '"
                                    + id
                                    + "' because it could not be compiled. "
                                    + "Path: "
                                    + path
                                    + ". Reason: "
                                    + rootMessage(
                                    exception
                            )
                    );
                };

        ItemCompiler itemCompiler =
                new ItemCompiler(
                        actions,
                        conditions,
                        compilationReporter
                );

        this.compiler =
                new InventoryCompiler(
                        itemCompiler,
                        compilationReporter
                );

        if (
                settings
                        .shouldLoadConfigurations()
        ) {
            loadDefinitions();
        }

        if (
                listenerRegistered.compareAndSet(
                        false,
                        true
                )
        ) {
            getEvents().registerListener(
                    new PlayerInventoryListener(
                            this
                    )
            );
        }
    }

    private void loadDefinitions() {
        try {
            ConfigurationHandle configuration =
                    getPlugin()
                            .getConfigurationProvider()
                            .load(
                                    getFileOfDirectory(
                                            settings.getFileName()
                                    ),
                                    settings.getResourcePath()
                            );

            InventoryLayout layout =
                    resolveLayout();

            String fallbackLocale =
                    resolveFallbackLocale();

            List<PlayerInventoryDefinition> definitions =
                    compiler.compile(
                            configuration,
                            settings.getRootPath(),
                            layout
                    );

            /*
             * Publication only happens AFTER compilation
             * completed successfully.
             *
             * On reload, if a catastrophic file-level failure
             * occurs, the old registry stays alive.
             */
            registry.replaceAll(
                    definitions,
                    fallbackLocale
            );

            getLogger().info(
                    "Loaded "
                            + definitions.size()
                            + " inventory definition(s)"
            );

        } catch (
                RuntimeException exception
        ) {
            getLogger().error(
                    exception,
                    "Unable to load inventories from '"
                            + settings.getFileName()
                            + "'. "
                            + "The inventory service will remain active "
                            + "and the previous registry will be preserved. "
                            + "Reason: "
                            + rootMessage(
                            exception
                    )
            );
        }
    }

    private String rootMessage(
            Throwable throwable
    ) {
        if (throwable == null) {
            return "Unknown error";
        }

        Throwable current =
                throwable;

        String last =
                null;

        while (current != null) {
            if (
                    current.getMessage() != null &&
                            !current.getMessage().isBlank()
            ) {
                last =
                        current.getMessage();
            }

            current =
                    current.getCause();
        }

        return last != null
                ? last
                : throwable.getClass().getSimpleName();
    }

    private InventoryLayout resolveLayout() {
        InventoryLayout configured =
                settings.getLayout();

        if (
                configured !=
                        InventoryLayout.AUTO
        ) {
            return configured;
        }

        if (
                isImplemented(
                        LanguageService.class
                )
        ) {
            LanguageService languages =
                    fetch(
                            LanguageService.class
                    );

            return languages.isStatic()
                    ? InventoryLayout.STATIC
                    : InventoryLayout.LOCALIZED;
        }

        return InventoryLayout.STATIC;
    }

    private String resolveFallbackLocale() {
        if (
                !isImplemented(
                        LanguageService.class
                )
        ) {
            return "en";
        }

        return fetch(
                LanguageService.class
        )
                .getFallbackLocale()
                .getLanguage();
    }

    private String resolveLocale(
            Player player
    ) {
        if (
                !isImplemented(
                        LanguageService.class
                )
        ) {
            return resolveFallbackLocale();
        }

        return fetch(
                LanguageService.class
        )
                .fromPlayer(player)
                .getLanguage();
    }

    /**
     * New main API.
     */
    public CompletableFuture<PlayerInventorySession> apply(
            String inventoryId,
            Player player
    ) {
        Objects.requireNonNull(
                player,
                "player"
        );

        String locale =
                resolveLocale(
                        player
                );

        return apply(
                inventoryId,
                player.getUniqueId(),
                player.getName(),
                locale
        );
    }

    private CompletableFuture<PlayerInventorySession> apply(
            String inventoryId,
            UUID playerId,
            String playerName,
            String locale
    ) {
        PlayerInventoryDefinition definition =
                registry
                        .find(
                                inventoryId,
                                locale
                        )
                        .orElse(null);

        if (definition == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException(
                            "Inventory '"
                                    + inventoryId
                                    + "' was not found for locale '"
                                    + locale
                                    + "'"
                    )
            );
        }

        if (
                !applyPermits.tryAcquire()
        ) {
            return CompletableFuture.failedFuture(
                    new RejectedExecutionException(
                            "Inventory apply limit reached"
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

        CompletableFuture<PlayerInventorySession> future =
                new CompletableFuture<>();

        try {
            executor.submit(() -> {
                try {
                    PlayerInventorySession session =
                            prepareAndCommit(
                                    definition,
                                    playerId,
                                    playerName,
                                    locale,
                                    generation
                            );

                    future.complete(
                            session
                    );

                } catch (Throwable throwable) {
                    future.completeExceptionally(
                            throwable
                    );

                } finally {
                    applyPermits.release();
                }
            });

        } catch (
                RejectedExecutionException exception
        ) {
            applyPermits.release();

            future.completeExceptionally(
                    exception
            );
        }

        return future;
    }

    private PlayerInventorySession prepareAndCommit(
            PlayerInventoryDefinition definition,
            UUID playerId,
            String playerName,
            String locale,
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
                                "inventoryId",
                                definition.id()
                        )
                        .variable(
                                "inventory_id",
                                definition.id()
                        )
                        .variable(
                                "locale",
                                locale
                        )
                        .build();

        ExecutionContextSnapshot baseSnapshot =
                baseContext.snapshot();

        List<PreparedInventoryItem> visible =
                new ArrayList<>();

        /*
         * Conditions may waitSync() when they require
         * Bukkit/PAPI, but this orchestration itself
         * remains on the virtual thread.
         */
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
                throw new CancellationException(
                        "Inventory apply superseded by a newer request"
                );
            }

            ExecutionContext context =
                    createItemContext(
                            baseSnapshot,
                            item.id()
                    );

            ConditionEvaluation evaluation =
                    conditions.evaluateNow(
                            item.visibility(),
                            context
                    );

            if (!evaluation.passed()) {
                continue;
            }

            visible.add(
                    new PreparedInventoryItem(
                            item,
                            context
                    )
            );
        }

        /*
         * ONE final main-thread operation performs
         * rendering + inventory mutations.
         */
        return runtime
                .mainThread()
                .waitFor(() -> {
                    if (
                            !isCurrentGeneration(
                                    playerId,
                                    generation
                            )
                    ) {
                        throw new CancellationException(
                                "Inventory apply superseded"
                        );
                    }

                    Player player =
                            Bukkit.getPlayer(
                                    playerId
                            );

                    if (player == null) {
                        throw new CancellationException(
                                "Player is no longer online"
                        );
                    }

                    PlayerInventorySession previous =
                            sessions.remove(
                                    playerId
                            );

                    if (previous != null) {
                        previous.invalidate();

                        clearSessionItems(
                                player,
                                previous
                        );
                    }

                    PlayerInventorySession session =
                            new PlayerInventorySession(
                                    playerId,
                                    locale,
                                    definition,
                                    baseSnapshot
                            );

                    for (
                            PreparedInventoryItem prepared :
                            visible
                    ) {
                        place(
                                player,
                                session,
                                prepared
                        );
                    }

                    sessions.put(
                            playerId,
                            session
                    );

                    for (
                            PreparedInventoryItem prepared :
                            visible
                    ) {
                        registerAnimation(
                                session,
                                prepared.definition()
                        );
                    }

                    return session;
                });
    }

    private Set<Integer> findItemSlots(
            Player player,
            PlayerInventorySession session,
            String itemId
    ) {
        LinkedHashSet<Integer> result =
                new LinkedHashSet<>();

        PlayerInventory inventory =
                player.getInventory();

        for (
                int slot = 0;
                slot <= 42;
                slot++
        ) {
            ItemStack stack;

            try {
                stack =
                        inventory.getItem(
                                slot
                        );

            } catch (
                    IndexOutOfBoundsException ignored
            ) {
                continue;
            }

            if (
                    stack == null ||
                            stack.getType().isAir()
            ) {
                continue;
            }

            ItemIdentity identity =
                    identities
                            .read(stack)
                            .orElse(null);

            if (
                    identity == null ||
                            identity.type()
                                    != ItemIdentityType.PLAYER_INVENTORY ||
                            !identity
                                    .ownerId()
                                    .equals(
                                            session.playerId()
                                    ) ||
                            !identity
                                    .sessionId()
                                    .equals(
                                            session.sessionId()
                                    ) ||
                            !identity
                                    .containerId()
                                    .equals(
                                            session.definition().id()
                                    ) ||
                            !identity
                                    .itemId()
                                    .equals(
                                            itemId
                                    )
            ) {
                continue;
            }

            result.add(slot);
        }

        return result;
    }

    private void registerAnimation(
            PlayerInventorySession session,
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
            PlayerInventorySession session,
            InteractiveItemDefinition definition,
            AnimationFrame frame,
            int frameIndex
    ) {
        if (
                sessions.get(
                        session.playerId()
                ) != session ||
                        !session.isActive()
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
                findItemSlots(
                        player,
                        session,
                        definition.id()
                );

        if (currentSlots.isEmpty()) {
            /*
             * Item no longer exists in player's inventory.
             *
             * It may have been dropped/deleted/etc.
             */
            return false;
        }

        ExecutionContext context =
                runtime
                        .context()
                        .inherit(
                                session.contextSnapshot()
                        )
                        .variable(
                                "inventoryId",
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

        ItemIdentity identity =
                new ItemIdentity(
                        ItemIdentityType.PLAYER_INVENTORY,
                        session.playerId(),
                        session.sessionId(),
                        session.definition().id(),
                        definition.id()
                );

        identities.tag(
                rendered,
                identity
        );

        PlayerInventory inventory =
                player.getInventory();

        /*
         * No movement override:
         *
         * update wherever the player currently keeps
         * this item.
         */
        if (!frame.movesItem()) {
            for (int slot : currentSlots) {
                inventory.setItem(
                        slot,
                        rendered.clone()
                );
            }

            return true;
        }

        /*
         * Frame explicitly requests movement.
         */
        for (int slot : currentSlots) {
            inventory.setItem(
                    slot,
                    null
            );
        }

        boolean placed =
                placeAnimatedInventoryItem(
                        player,
                        frame.placement(),
                        rendered
                );

        if (!placed) {
            /*
             * Restore previous positions.
             */
            for (int slot : currentSlots) {
                inventory.setItem(
                        slot,
                        rendered.clone()
                );
            }
        }

        return true;
    }

    private boolean placeAnimatedInventoryItem(
            Player player,
            ItemPlacement placement,
            ItemStack item
    ) {
        PlayerInventory inventory =
                player.getInventory();

        return switch (placement) {
            case SlotPlacement slots -> {
                int storageSize =
                        inventory
                                .getStorageContents()
                                .length;

                boolean valid =
                        slots
                                .slots()
                                .stream()
                                .allMatch(slot ->
                                        slot >= 0 &&
                                                slot < storageSize
                                );

                if (!valid) {
                    yield false;
                }

                for (int slot : slots.slots()) {
                    inventory.setItem(
                            slot,
                            item.clone()
                    );
                }

                yield true;
            }

            case ArmorPlacement armor -> {
                armor
                        .slot()
                        .equip(
                                player,
                                item.clone()
                        );

                yield true;
            }

            case OffhandPlacement ignored -> {
                inventory.setItemInOffHand(
                        item.clone()
                );

                yield true;
            }

            case FirstEmptyPlacement ignored -> {
                int slot =
                        inventory.firstEmpty();

                if (slot < 0) {
                    yield false;
                }

                inventory.setItem(
                        slot,
                        item.clone()
                );

                yield true;
            }
        };
    }

    private void place(
            Player player,
            PlayerInventorySession session,
            PreparedInventoryItem prepared
    ) {
        InteractiveItemDefinition definition =
                prepared.definition();

        ItemStack rendered =
                renderer.renderSync(
                        definition.item(),
                        prepared.context(),
                        player
                );

        boolean placed =
                placeRendered(
                        player,
                        session,
                        definition,
                        rendered
                );

        if (!placed) {
            getLogger().warn(
                    "Could not place item '"
                            + definition.id()
                            + "' in inventory '"
                            + session.definition().id()
                            + "' for player '"
                            + player.getName()
                            + "'"
            );
        }
    }

    public CompletableFuture<Boolean> refreshItem(
            UUID playerId,
            String itemId
    ) {
        PlayerInventorySession session =
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
                ExecutionContext context =
                        createItemContext(
                                session.contextSnapshot(),
                                definition.id()
                        );

                ConditionEvaluation visibility =
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

                                    Player player =
                                            Bukkit.getPlayer(
                                                    playerId
                                            );

                                    if (player == null) {
                                        return false;
                                    }

                                    animations.cancel(
                                            session.sessionId(),
                                            definition.id()
                                    );

                                    Set<Integer> current =
                                            findItemSlots(
                                                    player,
                                                    session,
                                                    definition.id()
                                            );

                                    if (!visibility.passed()) {
                                        for (int slot : current) {
                                            player
                                                    .getInventory()
                                                    .setItem(
                                                            slot,
                                                            null
                                                    );
                                        }

                                        return true;
                                    }

                                    ItemStack rendered =
                                            renderer.renderSync(
                                                    definition.item(),
                                                    context,
                                                    player
                                            );

                                    if (!current.isEmpty()) {
                                        ItemIdentity identity =
                                                new ItemIdentity(
                                                        ItemIdentityType.PLAYER_INVENTORY,
                                                        playerId,
                                                        session.sessionId(),
                                                        session.definition().id(),
                                                        definition.id()
                                                );

                                        identities.tag(
                                                rendered,
                                                identity
                                        );

                                        for (int slot : current) {
                                            player
                                                    .getInventory()
                                                    .setItem(
                                                            slot,
                                                            rendered.clone()
                                                    );
                                        }

                                    } else {
                                        placeRendered(
                                                player,
                                                session,
                                                definition,
                                                rendered
                                        );
                                    }

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

    /**
     * Compatibility with old API.
     */
    public void give(
            String id,
            Player player
    ) {
        apply(
                id,
                player
        );
    }

    private boolean placeRendered(
            Player player,
            PlayerInventorySession session,
            InteractiveItemDefinition definition,
            ItemStack rendered
    ) {
        if (
                player == null ||
                        session == null ||
                        definition == null ||
                        rendered == null ||
                        rendered.getType().isAir()
        ) {
            return false;
        }

        /*
         * Always guarantee that a managed inventory item
         * has the identity of the current session.
         *
         * This also makes this method safe to reuse from
         * refreshItem(), place(), future refreshAll(), etc.
         */
        ItemIdentity identity =
                new ItemIdentity(
                        ItemIdentityType.PLAYER_INVENTORY,
                        player.getUniqueId(),
                        session.sessionId(),
                        session.definition().id(),
                        definition.id()
                );

        identities.tag(
                rendered,
                identity
        );

        PlayerInventory inventory =
                player.getInventory();

        ItemPlacement placement =
                definition.placement();

        return switch (placement) {

            case SlotPlacement slots -> {
                int storageSize =
                        inventory
                                .getStorageContents()
                                .length;

                boolean placed =
                        false;

                for (int slot : slots.slots()) {
                    /*
                     * Normal SlotPlacement only targets
                     * the storage section.
                     *
                     * Armor/offhand have their own placement
                     * implementations.
                     */
                    if (
                            slot < 0 ||
                                    slot >= storageSize
                    ) {
                        getLogger().warn(
                                "Invalid storage slot "
                                        + slot
                                        + " for item '"
                                        + definition.id()
                                        + "' in inventory '"
                                        + session.definition().id()
                                        + "'"
                        );

                        continue;
                    }

                    inventory.setItem(
                            slot,
                            rendered.clone()
                    );

                    placed = true;
                }

                yield placed;
            }

            case ArmorPlacement armor -> {
                armor
                        .slot()
                        .equip(
                                player,
                                rendered.clone()
                        );

                yield true;
            }

            case OffhandPlacement ignored -> {
                inventory.setItemInOffHand(
                        rendered.clone()
                );

                yield true;
            }

            case FirstEmptyPlacement ignored -> {
                int slot =
                        inventory.firstEmpty();

                if (slot < 0) {
                    yield false;
                }

                inventory.setItem(
                        slot,
                        rendered.clone()
                );

                yield true;
            }
        };
    }

    public Optional<PlayerInventorySession> session(
            UUID playerId
    ) {
        return Optional.ofNullable(
                sessions.get(
                        playerId
                )
        );
    }

    public Optional<ResolvedInventoryItem> resolve(
            Player player,
            ItemStack stack
    ) {
        if (
                player == null ||
                        stack == null
        ) {
            return Optional.empty();
        }

        ItemIdentity identity =
                identities
                        .read(stack)
                        .orElse(null);

        if (
                identity == null ||
                        identity.type()
                                != ItemIdentityType.PLAYER_INVENTORY ||
                        !identity
                                .ownerId()
                                .equals(
                                        player.getUniqueId()
                                )
        ) {
            return Optional.empty();
        }

        PlayerInventorySession session =
                sessions.get(
                        player.getUniqueId()
                );

        if (
                session == null ||
                        !session.isActive() ||
                        !session
                                .sessionId()
                                .equals(
                                        identity.sessionId()
                                ) ||
                        !session
                                .definition()
                                .id()
                                .equals(
                                        identity.containerId()
                                )
        ) {
            return Optional.empty();
        }

        InteractiveItemDefinition definition =
                session
                        .definition()
                        .item(
                                identity.itemId()
                        )
                        .orElse(null);

        if (definition == null) {
            return Optional.empty();
        }

        return Optional.of(
                new ResolvedInventoryItem(
                        session,
                        definition,
                        identity
                )
        );
    }

    public CompletableFuture<Void> interact(
            ResolvedInventoryItem resolved,
            InteractionType type
    ) {
        PlayerInventorySession session =
                resolved.session();

        if (!session.isActive()) {
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
                                "itemId",
                                resolved
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "item_id",
                                resolved
                                        .definition()
                                        .id()
                        )
                        .variable(
                                "interaction",
                                type.name()
                        )
                        .build();

        return interactionExecutor.execute(
                resolved.definition(),
                type,
                context
        );
    }

    public CompletableFuture<Void> remove(
            Player player
    ) {
        if (player == null) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        UUID playerId =
                player.getUniqueId();

        /*
         * Invalidate pending apply operations.
         */
        generations
                .computeIfAbsent(
                        playerId,
                        ignored ->
                                new AtomicLong()
                )
                .incrementAndGet();

        PlayerInventorySession session =
                sessions.remove(
                        playerId
                );

        if (session == null) {
            return CompletableFuture.completedFuture(
                    null
            );
        }

        session.invalidate();

        animations.cancelGroup(
                session.sessionId()
        );

        return runtime
                .mainThread()
                .submit(() ->
                        clearSessionItems(
                                player,
                                session
                        )
                );
    }

    /**
     * Called directly from PlayerQuitEvent while the
     * player's inventory is still accessible.
     */
    public void handleQuit(
            Player player
    ) {
        UUID playerId =
                player.getUniqueId();

        generations
                .computeIfAbsent(
                        playerId,
                        ignored ->
                                new AtomicLong()
                )
                .incrementAndGet();

        PlayerInventorySession session =
                sessions.remove(
                        playerId
                );

        if (session == null) {
            return;
        }

        animations.cancelGroup(
                session.sessionId()
        );

        session.invalidate();

        if (
                settings.shouldClearOnQuit()
        ) {
            clearSessionItems(
                    player,
                    session
            );
        }
    }

    private void clearSessionItems(
            Player player,
            PlayerInventorySession session
    ) {
        PlayerInventory inventory =
                player.getInventory();

        /*
         * Paper 1.21.10:
         *
         * 0-35 storage
         * 36-39 armor
         * 40    offhand
         *
         * 41/42 are body/saddle slots. We scan them too
         * so future placement types don't leave stale
         * identities behind.
         */
        for (
                int slot = 0;
                slot <= 42;
                slot++
        ) {
            ItemStack current;

            try {
                current =
                        inventory.getItem(
                                slot
                        );

            } catch (
                    IndexOutOfBoundsException ignored
            ) {
                continue;
            }

            if (
                    current == null ||
                            current.getType()
                                    .isAir()
            ) {
                continue;
            }

            if (
                    identities.belongsTo(
                            current,
                            session.playerId(),
                            session.sessionId()
                    )
            ) {
                inventory.setItem(
                        slot,
                        null
                );
            }
        }
    }

    public InventoryRegistry registry() {
        return registry;
    }

    public Collection<PlayerInventorySession> sessions() {
        return List.copyOf(
                sessions.values()
        );
    }

    @Override
    public void reload() {
        if (
                !settings
                        .shouldLoadConfigurations()
        ) {
            return;
        }

        /*
         * Snapshot active sessions BEFORE registry swap.
         */
        List<RefreshRequest> refresh =
                sessions.values()
                        .stream()
                        .filter(
                                PlayerInventorySession::isActive
                        )
                        .map(session ->
                                new RefreshRequest(
                                        session.playerId(),
                                        session.definition().id()
                                )
                        )
                        .toList();

        /*
         * Transactional registry replacement.
         */
        loadDefinitions();

        if (
                !settings
                        .shouldRefreshSessionsOnReload()
        ) {
            return;
        }

        for (
                RefreshRequest request :
                refresh
        ) {
            Player player =
                    Bukkit.getPlayer(
                            request.playerId()
                    );

            if (player == null) {
                continue;
            }

            apply(
                    request.inventoryId(),
                    player
            );
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
                settings
                        .shouldClearOnShutdown()
        ) {
            for (
                    PlayerInventorySession session :
                    List.copyOf(
                            sessions.values()
                    )
            ) {
                Player player =
                        Bukkit.getPlayer(
                                session.playerId()
                        );

                if (player != null) {
                    clearSessionItems(
                            player,
                            session
                    );
                }

                session.invalidate();

                animations.cancelGroup(
                        session.sessionId()
                );
            }
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

    private record PreparedInventoryItem(
            InteractiveItemDefinition definition,
            ExecutionContext context
    ) {}

    private record RefreshRequest(
            UUID playerId,
            String inventoryId
    ) {}
}
