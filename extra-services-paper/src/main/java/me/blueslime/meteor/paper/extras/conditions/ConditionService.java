package me.blueslime.meteor.paper.extras.conditions;

import me.blueslime.meteor.paper.extras.conditions.api.Condition;

import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionCompiler;
import me.blueslime.meteor.paper.extras.conditions.compiler.ConditionParser;

import me.blueslime.meteor.paper.extras.conditions.list.ContextCondition;
import me.blueslime.meteor.paper.extras.conditions.list.ExistsCondition;
import me.blueslime.meteor.paper.extras.conditions.list.PermissionCondition;
import me.blueslime.meteor.paper.extras.conditions.list.PlaceholderCondition;

import me.blueslime.meteor.paper.extras.conditions.registry.ConditionRegistry;

import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionEvaluation;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionMode;
import me.blueslime.meteor.paper.extras.conditions.runtime.ConditionPlan;

import me.blueslime.meteor.paper.extras.runtime.ExecutionRuntimeService;
import me.blueslime.meteor.paper.extras.runtime.context.ExecutionContext;
import me.blueslime.meteor.paper.extras.runtime.text.ExecutionTextResolver;

import me.blueslime.meteor.platforms.api.Project;
import me.blueslime.meteor.platforms.api.service.PlatformService;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ConditionService
        implements PlatformService {

    private final ConditionServiceSettings settings;

    private final ConditionRegistry registry =
            new ConditionRegistry();

    private final ConditionParser parser =
            new ConditionParser();

    private final ConditionCompiler compiler;

    private final ExecutionRuntimeService runtime;

    private final ExecutionTextResolver textResolver;

    private final ExecutorService executor;

    private final Semaphore activeEvaluations;

    private final Set<String> internalConditions =
            ConcurrentHashMap.newKeySet();

    private final Set<String> externalConditions =
            ConcurrentHashMap.newKeySet();

    private final AtomicBoolean initialized =
            new AtomicBoolean(false);

    public ConditionService() {
        this(
                ConditionServiceSettings
                        .builder()
                        .validate()
        );
    }

    public ConditionService(
            ConditionServiceSettings settings
    ) {
        this.settings =
                Objects.requireNonNull(
                        settings,
                        "settings"
                ).validate();

        this.runtime =
                fetch(
                        ExecutionRuntimeService.class
                );

        this.textResolver =
                runtime.text();

        this.compiler =
                new ConditionCompiler(
                        registry,
                        parser
                );


        String name = fetch(Project.class).name();

        ThreadFactory factory =
                Thread.ofVirtual()
                        .name(
                                name + "-condition-",
                                0
                        )
                        .factory();

        this.executor =
                Executors
                        .newThreadPerTaskExecutor(
                                factory
                        );

        this.activeEvaluations =
                new Semaphore(
                        settings
                                .maxConcurrentEvaluations()
                );

        registerInternalCondition(
                new PermissionCondition(
                        textResolver
                ),

                new PlaceholderCondition(
                        textResolver
                ),

                new ContextCondition(
                        textResolver
                ),

                new ExistsCondition()
        );
    }

    @Override
    public void initialize() {
        initialized.compareAndSet(
                false,
                true
        );
    }

    public void registerInternalCondition(
            Condition... conditions
    ) {
        for (
                Condition condition :
                conditions
        ) {
            registry.register(
                    condition
            );

            internalConditions.add(
                    normalize(
                            condition.id()
                    )
            );
        }
    }

    public void registerCondition(
            Condition... conditions
    ) {
        for (
                Condition condition :
                conditions
        ) {
            registry.register(
                    condition
            );

            externalConditions.add(
                    normalize(
                            condition.id()
                    )
            );
        }
    }

    public Condition unregisterCondition(
            String id
    ) {
        String normalized =
                normalize(id);

        if (
                internalConditions.contains(
                        normalized
                )
        ) {
            throw new IllegalArgumentException(
                    "Internal condition '"
                            + id
                            + "' cannot be unregistered"
            );
        }

        externalConditions.remove(
                normalized
        );

        return registry.unregister(
                id
        );
    }

    public void clearExternalConditions() {
        for (
                String id :
                new HashSet<>(
                        externalConditions
                )
        ) {
            registry.unregister(
                    id
            );
        }

        externalConditions.clear();
    }

    public List<Condition> getConditions() {
        return internalConditions
                .stream()
                .map(
                        registry::find
                )
                .flatMap(
                        Optional::stream
                )
                .toList();
    }

    public List<Condition> getExternalConditions() {
        return externalConditions
                .stream()
                .map(
                        registry::find
                )
                .flatMap(
                        Optional::stream
                )
                .toList();
    }

    public ConditionPlan compile(
            Collection<String> conditions
    ) {
        return compiler.compile(
                conditions
        );
    }

    public ConditionPlan compile(
            Collection<String> conditions,
            ConditionMode mode
    ) {
        return compiler.compile(
                conditions,
                mode
        );
    }

    public ConditionPlan compile(
            String condition
    ) {
        return compiler.compile(
                condition
        );
    }

    /**
     * Executes immediately in the current thread.
     *
     * Best used when you're ALREADY inside a virtual
     * action/render thread.
     *
     * waitSync() calls inside conditions still jump
     * to Bukkit thread correctly.
     */
    public ConditionEvaluation evaluateNow(
            ConditionPlan plan,
            ExecutionContext context
    ) {
        Objects.requireNonNull(
                plan,
                "plan"
        );

        Objects.requireNonNull(
                context,
                "context"
        );

        return plan.evaluate(
                context
        );
    }

    /**
     * Entry point from Bukkit events/menu opening/etc.
     *
     * The ConditionPlan executes on a virtual thread.
     */
    public CompletableFuture<ConditionEvaluation> evaluate(
            ConditionPlan plan,
            ExecutionContext context
    ) {
        Objects.requireNonNull(
                plan,
                "plan"
        );

        Objects.requireNonNull(
                context,
                "context"
        );

        if (!initialized.get()) {
            return CompletableFuture.completedFuture(
                    ConditionEvaluation.rejected(
                            plan.mode()
                    )
            );
        }

        if (
                !activeEvaluations.tryAcquire()
        ) {
            return CompletableFuture.completedFuture(
                    ConditionEvaluation.rejected(
                            plan.mode()
                    )
            );
        }

        CompletableFuture<ConditionEvaluation> future =
                new CompletableFuture<>();

        try {
            executor.submit(() -> {
                try {
                    ConditionEvaluation evaluation =
                            plan.evaluate(
                                    context
                            );

                    future.complete(
                            evaluation
                    );

                } catch (Throwable throwable) {
                    future.completeExceptionally(
                            throwable
                    );

                } finally {
                    activeEvaluations.release();
                }
            });

        } catch (
                RejectedExecutionException exception
        ) {
            activeEvaluations.release();

            future.complete(
                    ConditionEvaluation.rejected(
                            plan.mode()
                    )
            );
        }

        return future;
    }

    public boolean testNow(
            ConditionPlan plan,
            ExecutionContext context
    ) {
        return evaluateNow(
                plan,
                context
        ).passed();
    }

    public CompletableFuture<Boolean> test(
            ConditionPlan plan,
            ExecutionContext context
    ) {
        return evaluate(
                plan,
                context
        ).thenApply(
                ConditionEvaluation::passed
        );
    }

    public ConditionRegistry getRegistry() {
        return registry;
    }

    public ConditionCompiler getCompiler() {
        return compiler;
    }

    public ExecutionRuntimeService getRuntime() {
        return runtime;
    }

    @Override
    public void reload() {
        /*
         * Definitions are recompiled by their owners
         * (menus/inventories).
         *
         * The condition runtime itself is persistent.
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

        executor.shutdownNow();
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    private String normalize(
            String id
    ) {
        return id
                .strip()
                .toLowerCase(
                        Locale.ROOT
                );
    }
}
