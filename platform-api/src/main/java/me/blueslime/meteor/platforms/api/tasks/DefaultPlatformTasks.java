package me.blueslime.meteor.platforms.api.tasks;

import me.blueslime.meteor.platforms.api.tasks.handle.DefaultTaskHandle;
import me.blueslime.meteor.platforms.api.tasks.handle.TaskHandle;
import me.blueslime.meteor.platforms.api.tasks.options.TaskOptions;

import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultPlatformTasks implements PlatformTasks {
    // * CACHE
    private final Map<String, DefaultTaskHandle> tasks = new ConcurrentHashMap<>();
    // * SCHEDULERS
    private final ScheduledThreadPoolExecutor scheduledPool;
    private final ExecutorService syncExecutor;
    // * ATOMIC DATA
    private final AtomicLong primaryThreadId = new AtomicLong(-1);
    private final AtomicLong idCounter = new AtomicLong(0);

    public DefaultPlatformTasks() {
        this(Runtime.getRuntime().availableProcessors(), 1);
    }

    public DefaultPlatformTasks(int asyncPoolSize, int syncThreads) {
        final int actualAsyncPoolSize = Math.max(1, asyncPoolSize);

        this.scheduledPool = new ScheduledThreadPoolExecutor(actualAsyncPoolSize);
        this.scheduledPool.setRemoveOnCancelPolicy(true);

        if (syncThreads <= 1) {

            this.syncExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "DefaultPlatformTasks-Main");
                t.setDaemon(false);
                return t;
            });

            this.syncExecutor.submit(() -> primaryThreadId.set(Thread.currentThread().threadId()));
        } else {
            AtomicInteger threadCounter = new AtomicInteger(1);

            this.syncExecutor = Executors.newFixedThreadPool(syncThreads, r -> {
                Thread t = new Thread(r, "DefaultPlatformTasks-Worker-" + threadCounter.getAndIncrement());
                t.setDaemon(false);
                return t;
            });

            primaryThreadId.set(-1L);
        }
    }

    /**
     * Schedule a task using the provided TaskOptions. Implementations must implement this<br>
     * and map the TaskOptions into the platform's scheduler.
     *
     * @param task    runnable to execute
     * @param options options controlling execution (sync/async/delay/repeat/etc.)
     * @return handle representing the scheduled task
     */
    @SuppressWarnings("ConstantValue")
    @Override
    public TaskHandle schedule(Runnable task, TaskOptions options) {
        String id = String.valueOf(idCounter.incrementAndGet());
        boolean sync = options != null && options.isSync();

        CompletableFuture<Void> future = new CompletableFuture<>();
        Runnable wrapped = () -> {
            try {
                task.run();
                future.complete(null);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                throw t;
            } finally {
                if (options == null || options.getRepeatDelay() <= 0) {
                    tasks.remove(id);
                }
            }
        };

        DefaultTaskHandle handle = new DefaultTaskHandle(id, options, future);

        if (sync) {
            if (options != null && options.getDelay() > 0) {
                long delayMillis = options.getTimeUnit().toMillis(options.getDelay());
                ScheduledFuture<?> sf = scheduledPool.schedule(() -> syncExecutor.submit(wrapped), delayMillis, TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(sf);
            } else {
                Future<?> f = syncExecutor.submit(wrapped);
                handle.attachFuture(f);
            }

            if (options != null && options.getRepeatDelay() > 0) {
                long periodMillis = options.getTimeUnit().toMillis(options.getRepeatDelay());
                ScheduledFuture<?> periodic = scheduledPool.scheduleAtFixedRate(() -> syncExecutor.submit(wrapped),
                        options.getDelay() > 0 ? options.getTimeUnit().toMillis(options.getDelay()) : 0,
                        periodMillis,
                        TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(periodic);
            }
        } else {
            if (options != null && options.getRepeatDelay() > 0) {
                long initial = options.getDelay() > 0 ? options.getTimeUnit().toMillis(options.getDelay()) : 0;
                long periodMillis = options.getTimeUnit().toMillis(options.getRepeatDelay());
                ScheduledFuture<?> periodic = scheduledPool.scheduleAtFixedRate(wrapped, initial, periodMillis, TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(periodic);
            } else if (options != null && options.getDelay() > 0) {
                ScheduledFuture<?> sf = scheduledPool.schedule(wrapped, options.getTimeUnit().toMillis(options.getDelay()), TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(sf);
            } else {
                Future<?> f = scheduledPool.submit(wrapped);
                handle.attachFuture(f);
            }
        }

        tasks.put(id, handle);
        return handle;
    }

    /**
     * Submit a Callable task and get a CompletableFuture for its result. Implementations<br>
     * should execute the callable according to the provided TaskOptions (sync/async/delays).
     *
     * @param callable callable to run
     * @param options  scheduling options
     * @return TaskHandle containing a CompletableFuture for the callable's result
     */
    @SuppressWarnings("ConstantValue")
    @Override
    public <T> TaskHandle submit(Callable<T> callable, TaskOptions options) {
        String id = String.valueOf(idCounter.incrementAndGet());
        boolean sync = options != null && options.isSync();

        CompletableFuture<T> future = new CompletableFuture<>();
        DefaultTaskHandle handle = new DefaultTaskHandle(id, options, future);

        Runnable runCallable = () -> {
            try {
                T result = callable.call();
                future.complete(result);
            } catch (Throwable t) {
                future.completeExceptionally(t);
                throw new RuntimeException(t);
            } finally {
                if (options == null || options.getRepeatDelay() <= 0) {
                    tasks.remove(id);
                }
            }
        };

        if (sync) {
            if (options != null && options.getDelay() > 0) {
                ScheduledFuture<?> sf = scheduledPool.schedule(() -> syncExecutor.submit(runCallable), options.getTimeUnit().toMillis(options.getDelay()), TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(sf);
            } else {
                Future<?> f = syncExecutor.submit(runCallable);
                handle.attachFuture(f);
            }
            if (options != null && options.getRepeatDelay() > 0) {
                long periodMillis = options.getTimeUnit().toMillis(options.getRepeatDelay());
                ScheduledFuture<?> periodic = scheduledPool.scheduleAtFixedRate(() -> syncExecutor.submit(runCallable),
                        options.getDelay() > 0 ? options.getTimeUnit().toMillis(options.getDelay()) : 0,
                        periodMillis, TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(periodic);
            }
        } else {
            if (options != null && options.getDelay() > 0) {
                ScheduledFuture<?> sf = scheduledPool.schedule(runCallable, options.getTimeUnit().toMillis(options.getDelay()), TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(sf);
            } else {
                Future<?> f = scheduledPool.submit(runCallable);
                handle.attachFuture(f);
            }
            if (options != null && options.getRepeatDelay() > 0) {
                long periodMillis = options.getTimeUnit().toMillis(options.getRepeatDelay());
                ScheduledFuture<?> periodic = scheduledPool.scheduleAtFixedRate(runCallable,
                        options.getDelay() > 0 ? options.getTimeUnit().toMillis(options.getDelay()) : 0,
                        periodMillis, TimeUnit.MILLISECONDS);
                handle.attachScheduledFuture(periodic);
            }
        }

        tasks.put(id, handle);
        return handle;
    }

    /**
     * Returns true if currently running on the primary/main thread of the platform.
     *
     * @return true when on primary thread
     */
    @Override
    public boolean isPrimaryThread() {
        long currentId = Thread.currentThread().threadId();
        long primaryId = primaryThreadId.get();

        if (primaryId == -1) {
            return true;
        }

        return currentId == primaryId;
    }

    /**
     * Cancel all tasks currently tracked by this PlatformTasks instance.
     * Implementations must attempt to cancel every active task and clear internal registries.
     * This only affects tasks scheduled through this PlatformTasks instance (module-scoped instances
     * should cancel their own tasks).
     */
    @Override
    public void cancelAll() {
        for (Map.Entry<String, DefaultTaskHandle> e : tasks.entrySet()) {
            try {
                e.getValue().cancel(false);
            } catch (Exception ignored) {}
        }
        tasks.clear();
    }
}
