/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Shared parallel-execution infrastructure for RetroMod's batch passes.
 *
 * <h3>Why a dedicated pool?</h3>
 * <p>The common-pool {@link ForkJoinPool#commonPool()} is shared with any
 * other library the JVM is running, including Java's own parallel streams.
 * A dedicated pool means our batch work doesn't starve (or get starved by)
 * unrelated code. It also makes it trivial to size and observe the pool
 * in isolation.</p>
 *
 * <h3>Sizing</h3>
 * <p>Default: {@code Runtime.getRuntime().availableProcessors()}, i.e. every
 * logical core on the machine. The typical use case — running {@code retromod
 * gaps ./mods} or a pre-launch transform — is a batch burst where the user
 * wants the work finished as fast as possible, then all cores return to idle.
 * That's exactly the "opening a cold desktop app" behaviour (spike to 100%
 * briefly, done) the project aims for.</p>
 *
 * <p>Override the size via {@code -Dretromod.parallelism=N}:</p>
 * <ul>
 *   <li>{@code N=1} — serial execution. Escape hatch for low-memory machines
 *       where multiple concurrent class transforms would OOM, or for
 *       deterministic debugging where interleaved logs get in the way.</li>
 *   <li>{@code N=0} — alias for "default = all cores."</li>
 *   <li>{@code N>=2} — explicit cap. Useful in CI where the machine is shared
 *       with other jobs.</li>
 * </ul>
 *
 * <h3>When parallelism is NOT used</h3>
 * <p>The JVM's per-class {@code ClassFileTransformer} path (our agent's
 * runtime JIT transformation) is inherently single-class: the JVM asks for
 * one class, we return one class. There's nothing to parallelize <i>against</i>
 * — other classes aren't loaded yet. This executor helps only the batch
 * flows: CLI commands, Fabric pre-launch, Forge/NeoForge constructor.</p>
 *
 * <h3>Thread safety</h3>
 * <p>The pool is created once on first use and reused across the JVM session.
 * Shutting it down mid-run would corrupt any in-flight task, so we let it
 * live until JVM exit (the threads are daemon threads, so they don't block
 * shutdown).</p>
 */
public final class RetroModExecutors {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Parallel");

    /**
     * Configured parallelism — available cores unless overridden.
     * Resolved once, at class init, so subsequent property changes don't
     * mysteriously change pool behaviour mid-session.
     */
    private static final int PARALLELISM = resolveParallelism();

    /** Lazily-initialized shared pool, guarded by class-level monitor. */
    private static volatile ForkJoinPool sharedPool;

    private RetroModExecutors() {}

    /**
     * Effective parallelism level. {@code 1} means "serial." Greater values
     * mean concurrent execution with up to that many threads.
     */
    public static int getParallelism() {
        return PARALLELISM;
    }

    /**
     * Is parallel execution enabled (i.e., &gt; 1 thread)?
     */
    public static boolean isParallel() {
        return PARALLELISM > 1;
    }

    /**
     * Shared pool, created lazily. Never null.
     */
    public static ForkJoinPool sharedPool() {
        ForkJoinPool local = sharedPool;
        if (local != null) return local;
        synchronized (RetroModExecutors.class) {
            if (sharedPool != null) return sharedPool;
            // asyncMode=true favours FIFO scheduling, which matches "process
            // a queue of classes" semantics better than the default LIFO.
            // Daemon threads so the JVM can shut down even if we leak a task.
            sharedPool = new ForkJoinPool(PARALLELISM,
                    new NamedDaemonForkJoinWorkerThreadFactory("retromod-worker-"),
                    (t, e) -> LOGGER.warn("Worker {} died: {}", t.getName(), e.getMessage()),
                    true);
            LOGGER.info("RetroMod parallel executor initialized with {} threads", PARALLELISM);
            return sharedPool;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CONVENIENCE METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Run {@code action} on every element of {@code items}, in parallel when
     * enabled. Serial-equivalent when {@link #PARALLELISM} is 1.
     *
     * <p>Exceptions thrown by the action are caught and logged; one bad task
     * doesn't abort the rest. Callers that care about per-item failure should
     * collect results via {@link #parallelMap} and inspect them.</p>
     */
    public static <T> void parallelForEach(Collection<T> items, Consumer<T> action) {
        if (items == null || items.isEmpty()) return;
        if (!isParallel() || items.size() == 1) {
            // Fast path: no pool overhead when serial.
            for (T item : items) {
                try {
                    action.accept(item);
                } catch (Exception e) {
                    LOGGER.warn("Task failed: {}", e.getMessage());
                }
            }
            return;
        }

        // Use invokeAll so we block until every task completes. Without that,
        // the caller could proceed while tasks are still running — correctness
        // bugs for code assuming "loop done means all work done."
        List<ForEachTask<T>> tasks = new ArrayList<>(items.size());
        for (T item : items) tasks.add(new ForEachTask<>(item, action));
        try {
            sharedPool().invokeAll(tasks).forEach(future -> {
                try { future.get(); } catch (InterruptedException | ExecutionException ignored) {}
            });
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.warn("Parallel forEach encountered error: {}", e.getMessage());
        }
    }

    /**
     * Run a {@code Map}-valued parallel forEach: each entry is processed
     * concurrently by {@code action}. Uses the same error handling as
     * {@link #parallelForEach}.
     */
    public static <K, V> void parallelForEachEntry(Map<K, V> items, BiConsumer<K, V> action) {
        if (items == null || items.isEmpty()) return;
        if (!isParallel() || items.size() == 1) {
            for (Map.Entry<K, V> entry : items.entrySet()) {
                try {
                    action.accept(entry.getKey(), entry.getValue());
                } catch (Exception e) {
                    LOGGER.warn("Task failed for key {}: {}", entry.getKey(), e.getMessage());
                }
            }
            return;
        }
        parallelForEach(items.entrySet(), entry -> action.accept(entry.getKey(), entry.getValue()));
    }

    /**
     * Parallel map: apply {@code fn} to every input and collect the results.
     * Preserves input order (unlike unordered parallel streams).
     *
     * <p>Results for failed tasks are {@code null}; callers can filter or
     * treat null as "skip." This is deliberately simpler than checked-exception
     * propagation — RetroMod's use cases treat per-class failures as "skip
     * that class, keep going" rather than "abort the whole batch."</p>
     */
    public static <T, R> List<R> parallelMap(List<T> inputs, Function<T, R> fn) {
        if (inputs == null || inputs.isEmpty()) return List.of();
        if (!isParallel() || inputs.size() == 1) {
            List<R> out = new ArrayList<>(inputs.size());
            for (T input : inputs) {
                try {
                    out.add(fn.apply(input));
                } catch (Exception e) {
                    LOGGER.warn("Map task failed: {}", e.getMessage());
                    out.add(null);
                }
            }
            return out;
        }

        // Parallel path: preserve order by assigning indices upfront.
        @SuppressWarnings("unchecked")
        R[] results = (R[]) new Object[inputs.size()];
        AtomicInteger failed = new AtomicInteger();
        List<IndexedMapTask<T, R>> tasks = new ArrayList<>(inputs.size());
        for (int i = 0; i < inputs.size(); i++) {
            tasks.add(new IndexedMapTask<>(i, inputs.get(i), fn, results, failed));
        }
        try {
            sharedPool().invokeAll(tasks).forEach(future -> {
                try { future.get(); } catch (InterruptedException | ExecutionException ignored) {}
            });
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (failed.get() > 0) {
            LOGGER.debug("parallelMap: {} of {} tasks failed", failed.get(), inputs.size());
        }
        // IMPORTANT: Arrays.asList, not List.of — List.of rejects nulls with NPE,
        // and we deliberately write null for failed tasks (see IndexedMapTask.call).
        // Arrays.asList preserves nulls, then new ArrayList<>(...) gives a mutable copy.
        return new ArrayList<>(Arrays.asList(results));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INTERNALS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Resolve the parallelism level from system property with sensible fallbacks.
     */
    private static int resolveParallelism() {
        String raw = System.getProperty("retromod.parallelism");
        if (raw == null || raw.isBlank()) {
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
        try {
            int n = Integer.parseInt(raw.trim());
            if (n == 0) return Math.max(1, Runtime.getRuntime().availableProcessors());
            return Math.max(1, n);
        } catch (NumberFormatException e) {
            LOGGER.warn("Invalid -Dretromod.parallelism={}, falling back to all cores", raw);
            return Math.max(1, Runtime.getRuntime().availableProcessors());
        }
    }

    /**
     * Thread factory that names workers predictably and sets them as daemons.
     * Predictable naming is useful for debugging — profilers and thread dumps
     * show which threads are RetroMod's.
     */
    private static final class NamedDaemonForkJoinWorkerThreadFactory
            implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        NamedDaemonForkJoinWorkerThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public java.util.concurrent.ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            java.util.concurrent.ForkJoinWorkerThread t =
                    ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
            t.setName(prefix + counter.incrementAndGet());
            t.setDaemon(true);
            return t;
        }
    }

    /**
     * RecursiveAction that runs a single consumer call. Used by
     * {@link #parallelForEach} — one task per input item.
     */
    private static final class ForEachTask<T> extends RecursiveAction
            implements java.util.concurrent.Callable<Void> {
        private final T item;
        private final Consumer<T> action;

        ForEachTask(T item, Consumer<T> action) {
            this.item = item;
            this.action = action;
        }

        @Override
        protected void compute() {
            try {
                action.accept(item);
            } catch (Exception e) {
                LOGGER.warn("ForEach task failed: {}", e.getMessage());
            }
        }

        @Override
        public Void call() {
            compute();
            return null;
        }
    }

    /**
     * Callable that runs {@code fn(input)} and writes the result to a shared
     * array at a predetermined index. Preserves input order despite parallel
     * execution.
     */
    private static final class IndexedMapTask<T, R>
            implements java.util.concurrent.Callable<Void> {
        private final int index;
        private final T input;
        private final Function<T, R> fn;
        private final R[] results;
        private final AtomicInteger failed;

        IndexedMapTask(int index, T input, Function<T, R> fn, R[] results, AtomicInteger failed) {
            this.index = index;
            this.input = input;
            this.fn = fn;
            this.results = results;
            this.failed = failed;
        }

        @Override
        public Void call() {
            try {
                results[index] = fn.apply(input);
            } catch (Exception e) {
                LOGGER.debug("Map task {} failed: {}", index, e.getMessage());
                results[index] = null;
                failed.incrementAndGet();
            }
            return null;
        }
    }
}
