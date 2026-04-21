/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.parallel;

import com.retromod.core.parallel.RetroModExecutors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RetroModExecutors}: the shared parallel-execution helper.
 *
 * <p>These tests don't verify a specific degree of parallelism (that depends on
 * the test runner's core count and the JVM scheduler). They verify correctness
 * invariants: every input produces exactly one output; order is preserved where
 * promised; exceptions don't abort the whole batch.</p>
 */
class RetroModExecutorsTest {

    @Test
    @DisplayName("parallelForEach visits every element exactly once")
    void parallelForEachEveryItem() {
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 200; i++) inputs.add(i);

        Set<Integer> visited = java.util.Collections.synchronizedSet(new HashSet<>());
        RetroModExecutors.parallelForEach(inputs, visited::add);

        assertEquals(inputs.size(), visited.size(),
                "Every element should be visited exactly once");
        for (int i = 0; i < 200; i++) {
            assertTrue(visited.contains(i), "Missing element: " + i);
        }
    }

    @Test
    @DisplayName("parallelMap preserves input order even under parallel execution")
    void parallelMapPreservesOrder() {
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 100; i++) inputs.add(i);

        // Double every input; deliberately uneven work to encourage scheduling variation
        List<Integer> results = RetroModExecutors.parallelMap(inputs, i -> {
            if (i % 7 == 0) {
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }
            return i * 2;
        });

        assertEquals(inputs.size(), results.size());
        for (int i = 0; i < inputs.size(); i++) {
            assertEquals(i * 2, results.get(i),
                    "Order must match input position (index " + i + ")");
        }
    }

    @Test
    @DisplayName("Exceptions in a task don't abort other tasks")
    void exceptionsIsolatedPerTask() {
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 50; i++) inputs.add(i);

        AtomicInteger completed = new AtomicInteger();
        RetroModExecutors.parallelForEach(inputs, i -> {
            if (i == 17) {
                throw new RuntimeException("Intentional test failure at index 17");
            }
            completed.incrementAndGet();
        });

        // 49 of 50 complete; the exception-throwing one is logged but not re-raised.
        assertEquals(49, completed.get(),
                "A single task failure must not abort the whole batch");
    }

    @Test
    @DisplayName("Empty and single-element inputs use the fast path")
    void emptyAndSingleInputsWork() {
        // Empty: no-op
        AtomicInteger count = new AtomicInteger();
        RetroModExecutors.parallelForEach(List.of(), (Object x) -> count.incrementAndGet());
        assertEquals(0, count.get());

        // Single: runs inline, still visits the element
        RetroModExecutors.parallelForEach(List.of("single"), s -> count.incrementAndGet());
        assertEquals(1, count.get());

        // parallelMap with empty list returns empty list
        List<String> mapped = RetroModExecutors.parallelMap(List.of(), x -> "unused");
        assertTrue(mapped.isEmpty());
    }

    @Test
    @DisplayName("getParallelism returns at least 1")
    void parallelismIsValid() {
        assertTrue(RetroModExecutors.getParallelism() >= 1,
                "Parallelism must always be >= 1");
    }

    @Test
    @DisplayName("Shared pool is the same instance across calls (single allocation)")
    void sharedPoolIsSingleton() {
        var pool1 = RetroModExecutors.sharedPool();
        var pool2 = RetroModExecutors.sharedPool();
        assertSame(pool1, pool2,
                "sharedPool() must return the same pool every time — allocating twice would double thread count");
    }

    @Test
    @DisplayName("Parallel execution actually uses multiple threads when parallelism > 1")
    void parallelExecutionUsesMultipleThreads() {
        // Only meaningful on machines with >1 core; skip on single-core CI otherwise
        if (!RetroModExecutors.isParallel()) return;

        ConcurrentLinkedQueue<String> threadsSeen = new ConcurrentLinkedQueue<>();
        List<Integer> inputs = new ArrayList<>();
        for (int i = 0; i < 64; i++) inputs.add(i);

        RetroModExecutors.parallelForEach(inputs, i -> {
            threadsSeen.add(Thread.currentThread().getName());
            // Small busy-work so the scheduler has reason to distribute tasks
            int sum = 0;
            for (int j = 0; j < 10_000; j++) sum += j;
            if (sum < 0) throw new AssertionError("unreachable"); // prevent dead-code elimination
        });

        Set<String> uniqueThreads = new HashSet<>(threadsSeen);
        assertTrue(uniqueThreads.size() > 1,
                "Parallel execution should spread across multiple threads; saw only: " + uniqueThreads);
    }
}
