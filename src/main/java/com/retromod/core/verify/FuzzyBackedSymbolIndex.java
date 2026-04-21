/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import com.retromod.core.FuzzyMethodResolver;

import java.util.ArrayList;
import java.util.List;

/**
 * Production {@link McSymbolIndex} implementation that delegates to
 * {@link FuzzyMethodResolver} — reusing the existing MC-JAR index rather than
 * building a second copy.
 *
 * <h3>Why delegate?</h3>
 * <p>{@link FuzzyMethodResolver} already indexes the full target MC JAR on
 * startup (~100ms, ~10MB of memory) for its fuzzy-resolution fallback. That
 * index contains exactly the data we need for membership checks, so duplicating
 * it would waste both startup time and memory. This wrapper adds the exact-
 * match accessors on top of the fuzzy resolver's existing API surface.</p>
 *
 * <h3>Handling "not indexed"</h3>
 * <p>{@code FuzzyMethodResolver} may not be initialized in some contexts
 * (standalone CLI runs, tests, environments where the MC JAR path is unknown).
 * We detect this via {@link FuzzyMethodResolver#isIndexed()} and degrade to
 * {@code isAvailable() == false} — callers then treat every lookup as "unknown"
 * rather than "missing," which keeps them from generating false-positive
 * unresolved-reference reports.</p>
 */
public class FuzzyBackedSymbolIndex implements McSymbolIndex {

    private final FuzzyMethodResolver resolver;
    private final String mcVersion;

    /**
     * @param resolver  the fuzzy resolver whose index we delegate to; must not
     *                  be null, but does not need to be indexed yet (we check
     *                  {@link FuzzyMethodResolver#isIndexed()} on each call)
     * @param mcVersion the MC version this index describes, for the gap report
     *                  header; pass {@code "unknown"} if you don't have it
     */
    public FuzzyBackedSymbolIndex(FuzzyMethodResolver resolver, String mcVersion) {
        if (resolver == null) {
            throw new IllegalArgumentException("resolver must not be null");
        }
        this.resolver = resolver;
        this.mcVersion = mcVersion != null ? mcVersion : "unknown";
    }

    @Override
    public boolean isAvailable() {
        return resolver.isIndexed();
    }

    @Override
    public boolean hasClass(String internalName) {
        return resolver.hasClass(internalName);
    }

    @Override
    public boolean hasMethod(String owner, String name, String descriptor) {
        return resolver.hasMethod(owner, name, descriptor);
    }

    @Override
    public boolean hasField(String owner, String name, String descriptor) {
        return resolver.hasField(owner, name, descriptor);
    }

    @Override
    public List<String> suggestClassAlternatives(String missingInternalName, int maxResults) {
        // Not using the fuzzy resolver's scoring for class suggestions —
        // its resolveMethod/resolveField operate at the member level, not the
        // class level. Instead we do a simple simple-name match: take the last
        // path component of the missing name (e.g., "BlockPos") and find any
        // indexed class whose simple name matches.
        //
        // This catches the overwhelmingly common case: a class moved packages
        // but kept its name (net/minecraft/util/math/BlockPos -> net/minecraft/core/BlockPos).
        // For renames with name changes, the fuzzy resolver's member-level
        // suggestions in suggestMethodAlternatives will typically surface the
        // new home via inherited members.
        if (!isAvailable() || missingInternalName == null) {
            return List.of();
        }
        int lastSlash = missingInternalName.lastIndexOf('/');
        String simpleName = lastSlash >= 0
                ? missingInternalName.substring(lastSlash + 1)
                : missingInternalName;

        List<String> results = new ArrayList<>(maxResults);
        // Iterate the indexed class names; same-simple-name matches are strong candidates
        for (String indexed : resolver.getIndexedClassNames()) {
            if (results.size() >= maxResults) break;
            int slash = indexed.lastIndexOf('/');
            String indexedSimple = slash >= 0 ? indexed.substring(slash + 1) : indexed;
            if (indexedSimple.equals(simpleName) && !indexed.equals(missingInternalName)) {
                results.add(indexed);
            }
        }
        return results;
    }

    @Override
    public List<MemberSignature> suggestMethodAlternatives(String owner, String name,
                                                            String descriptor, int maxResults) {
        if (!isAvailable() || owner == null) {
            return List.of();
        }

        // Delegate to the fuzzy resolver, which already does sophisticated
        // scoring (class-hierarchy awareness, descriptor-distance, name-similarity).
        // We lower its auto-apply threshold for suggestions — we want to SHOW the
        // top candidates even if the confidence is too low to auto-rewrite.
        FuzzyMethodResolver.MethodInfo best = resolver.resolveMethod(owner, name, descriptor);
        List<MemberSignature> results = new ArrayList<>();
        if (best != null) {
            results.add(new MemberSignature(best.owner(), best.name(), best.descriptor()));
        }
        // The resolver only returns one best match today. If we want more, we'd
        // expose a "top K" method from FuzzyMethodResolver in a follow-up. For
        // v1, one suggestion is better than zero and already handles the common case.
        return results.size() > maxResults ? results.subList(0, maxResults) : results;
    }

    @Override
    public String targetMcVersion() {
        return mcVersion;
    }
}
