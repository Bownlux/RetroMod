/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern;

import org.objectweb.asm.tree.ClassNode;

/**
 * A detector for one specific "shape" a mod class might have — a Forge event
 * listener, a {@code DeferredRegister} holder, a BlockEntity-like class, etc.
 *
 * <h3>Why patterns?</h3>
 * <p>Lots of mods implement the same concepts (register blocks, handle events,
 * define block entities) with slightly different code. If we can recognize
 * these concepts structurally — independent of exact class names or parent
 * hierarchies — we get two benefits:</p>
 * <ol>
 *   <li><b>Better diagnostics.</b> A gap report that says "your mod has 7
 *       Forge-event-listener classes" is more actionable than a list of
 *       individual unresolved references.</li>
 *   <li><b>Better adaptation.</b> Future versions can apply targeted fixes
 *       when a pattern is recognized — wire up registries, retarget event
 *       handlers, synthesize stubs. v1 is detection-only.</li>
 * </ol>
 *
 * <h3>Contract</h3>
 * <p>Implementations inspect the given {@link ClassNode} and return a
 * {@link PatternMatch} if the class matches the pattern, or {@code null} if
 * it doesn't. Implementations must:</p>
 * <ul>
 *   <li><b>Be read-only.</b> Never mutate the {@code ClassNode}; patterns can
 *       run in any order, and a mutated node would poison subsequent patterns.</li>
 *   <li><b>Be fast.</b> Patterns run on every mod class, so an O(n²) scan of
 *       methods would blow up on large classes. Aim for linear-or-better.</li>
 *   <li><b>Honor the scope filter.</b> If the class isn't in
 *       {@link MatchContext#modOwnClasses()}, patterns should typically return
 *       null immediately — we don't want to match MC or JDK classes.</li>
 *   <li><b>Be deterministic.</b> Same class + same context always produces the
 *       same match result. This keeps gap reports diff-stable across CI runs.</li>
 * </ul>
 *
 * <h3>Why {@code ClassNode}, not bytecode?</h3>
 * <p>Patterns need random access to methods, fields, and annotations. The
 * streaming {@code ClassVisitor} API makes that awkward. The tree API's
 * {@code ClassNode} holds everything in memory at once, which is a few KB per
 * class and lets patterns be written as simple loops over {@code cls.methods},
 * {@code cls.fields}, {@code cls.visibleAnnotations}.</p>
 */
public interface ClassPattern {

    /**
     * Stable machine-readable identifier for this pattern. Appears in
     * {@link PatternMatch#patternName()} and in the gap report.
     * Conventional format: camelCase with no spaces (e.g., {@code "ForgeEventListener"}).
     */
    String name();

    /**
     * One-sentence human-readable description for the gap report header line.
     */
    String description();

    /**
     * Test whether {@code cls} matches this pattern in the given context.
     *
     * @return a {@link PatternMatch} if the pattern matched, or {@code null}
     *         if it didn't. A non-null result with {@code confidence=0.0} is
     *         treated as "matched but low certainty" and SHOULD include the
     *         hit in the report so users can review; a genuine non-match
     *         should return {@code null}.
     */
    PatternMatch match(ClassNode cls, MatchContext ctx);
}
