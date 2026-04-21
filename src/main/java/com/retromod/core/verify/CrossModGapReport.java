/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import com.retromod.core.pattern.PatternMatch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Aggregates {@link VerificationReport}s across many mods into a single "gap"
 * report that ranks MC references by how many different mods miss them.
 *
 * <h3>Why aggregate?</h3>
 * <p>A per-mod report tells you "this mod is broken." The cross-mod view tells
 * you "these 20 missing references account for 80% of all breakage in the
 * ecosystem — write polyfills for them and most mods start working." That's
 * the data that drives shim/polyfill prioritization decisions.</p>
 *
 * <h3>Aggregation key</h3>
 * <p>References are grouped by {@link UnresolvedReference#identityKey()} —
 * which includes kind + owner + name + descriptor, but NOT source location.
 * The same method-not-found reference from 50 different mods collapses into
 * one entry with {@code count=50}.</p>
 *
 * <h3>Usage</h3>
 * <pre>
 * CrossModGapReport agg = new CrossModGapReport(targetMcVersion);
 * for (Path modJar : mods) {
 *     VerificationReport perMod = verifier.verify(...);
 *     agg.merge(perMod);
 * }
 * agg.writeTo(System.out);
 * </pre>
 *
 * <h3>Thread safety</h3>
 * <p><b>Not thread-safe.</b> The aggregator uses plain {@link HashMap} /
 * {@link HashSet} + int counter, which means {@link #merge} calls must be
 * serialized externally. The CLI {@code gaps} command processes mods
 * sequentially, so a single aggregator instance is safe there. If mod
 * iteration is ever parallelized, callers must either use one aggregator
 * per worker thread and merge them afterward, OR externally synchronize
 * every {@code merge} call. Per-class work inside a single mod is already
 * parallelized — that concurrency is contained within the per-mod
 * {@link VerificationReport}, which IS thread-safe.</p>
 */
public final class CrossModGapReport {

    private final String targetMcVersion;
    private final Map<String, AggregatedEntry> byIdentity = new HashMap<>();
    /** Pattern-match aggregation: pattern name → mods that produced that match. */
    private final Map<String, PatternAggregation> patternAggregation = new HashMap<>();
    private final Set<String> modsSeen = new HashSet<>();
    private int totalClassesScanned;

    public CrossModGapReport(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion == null ? "unknown" : targetMcVersion;
    }

    /** Merge one mod's per-mod report into the aggregation. */
    public void merge(VerificationReport report) {
        if (report == null) return;
        modsSeen.add(report.modId());
        totalClassesScanned += report.classesScanned();

        for (UnresolvedReference ref : report.missingClasses()) mergeRef(ref, report.modId());
        for (UnresolvedReference ref : report.missingMethods()) mergeRef(ref, report.modId());
        for (UnresolvedReference ref : report.missingFields()) mergeRef(ref, report.modId());
        for (UnresolvedReference ref : report.badSignatures()) mergeRef(ref, report.modId());

        // Aggregate pattern matches by pattern name. The per-pattern count tells
        // us which class shapes are common across the ecosystem (e.g., "47 mods
        // have at least one Mixin class" — useful for prioritizing the
        // MixinTargetPattern's handling completeness).
        for (Map.Entry<String, List<PatternMatch>> entry : report.patternMatches().entrySet()) {
            patternAggregation.computeIfAbsent(entry.getKey(), k -> new PatternAggregation(k))
                               .observe(report.modId(), entry.getValue().size());
        }
    }

    private void mergeRef(UnresolvedReference ref, String modId) {
        byIdentity.computeIfAbsent(ref.identityKey(), k -> new AggregatedEntry(ref))
                   .observe(modId);
    }

    /**
     * Render the cross-mod report ranked by "most-missed first." Includes a
     * header line with scan stats and one entry per unique reference.
     */
    public void writeTo(Appendable out) throws IOException {
        List<AggregatedEntry> sorted = new ArrayList<>(byIdentity.values());
        // Primary sort: frequency desc. Secondary: pretty-print asc for stable output.
        sorted.sort(Comparator
                .comparingInt((AggregatedEntry e) -> -e.count())
                .thenComparing(e -> e.reference.prettyPrint()));

        out.append("=== RetroMod cross-mod gap report ===\n");
        out.append("Target MC: ").append(targetMcVersion).append('\n');
        out.append("Mods scanned: ").append(Integer.toString(modsSeen.size())).append('\n');
        out.append("Classes scanned: ").append(Integer.toString(totalClassesScanned)).append('\n');
        out.append("Unique unresolved references: ").append(Integer.toString(sorted.size())).append('\n');
        out.append('\n');

        if (sorted.isEmpty()) {
            out.append("No cross-mod gaps. Every mod verified clean.\n");
            return;
        }

        out.append("Top gaps (ranked by number of mods affected):\n");
        int rank = 1;
        for (AggregatedEntry e : sorted) {
            out.append(String.format("  %3d. (%3d mod%s) %s%n",
                    rank++, e.count(), e.count() == 1 ? " " : "s",
                    formatEntry(e)));
            if (!e.reference.suggestions().isEmpty()) {
                out.append("         → ")
                   .append(String.join(", ", e.reference.suggestions()))
                   .append('\n');
            }
        }

        // Pattern-match summary: which structural shapes were seen across how
        // many mods, and how prevalent they are. Separate section from the
        // unresolved-refs list because pattern matches are informational
        // ("this shape exists here") rather than actionable breakage.
        if (!patternAggregation.isEmpty()) {
            List<PatternAggregation> patterns = new ArrayList<>(patternAggregation.values());
            patterns.sort(Comparator.comparingInt((PatternAggregation p) -> -p.modCount())
                                      .thenComparing(PatternAggregation::patternName));
            out.append('\n');
            out.append("Pattern matches (structural shapes detected in mod code):\n");
            for (PatternAggregation p : patterns) {
                out.append(String.format("  %-32s %3d mod%s, %d total match%s%n",
                        p.patternName(),
                        p.modCount(), p.modCount() == 1 ? " " : "s",
                        p.totalMatches(), p.totalMatches() == 1 ? "" : "es"));
            }
        }
    }

    private static String formatEntry(AggregatedEntry e) {
        // Prefix with kind so the same symbol appearing as both class-miss and
        // method-miss reads unambiguously.
        return "[" + e.reference.kind().name() + "] " + e.reference.prettyPrint();
    }

    /**
     * @return unmodifiable map of identity-key → aggregated entry, for callers
     *         that want to slice the data programmatically instead of reading
     *         the formatted text output
     */
    public Map<String, AggregatedEntry> entries() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(byIdentity));
    }

    /**
     * Cross-mod summary for a single pattern: how many distinct mods produced
     * any match for this pattern, and the total count of matches across all
     * mods. Both numbers are useful — "47 mods had Mixin classes" vs
     * "47 mods had 312 Mixin classes between them" answer different questions
     * about code shape prevalence.
     */
    public static final class PatternAggregation {
        private final String patternName;
        private final Set<String> modsWithMatch = new HashSet<>();
        private int totalMatches = 0;

        PatternAggregation(String patternName) {
            this.patternName = patternName;
        }

        void observe(String modId, int matchesInThisMod) {
            modsWithMatch.add(modId);
            totalMatches += matchesInThisMod;
        }

        public String patternName() { return patternName; }
        public int modCount() { return modsWithMatch.size(); }
        public int totalMatches() { return totalMatches; }
        public Set<String> affectedMods() { return Collections.unmodifiableSet(modsWithMatch); }
    }

    /**
     * One reference plus the set of mods that hit it. Count is derived from the
     * set size — using a set ensures the same mod reporting the same miss from
     * 10 call sites doesn't inflate the count to 10.
     */
    public static final class AggregatedEntry {
        private final UnresolvedReference reference;
        private final Set<String> affectedMods = new HashSet<>();

        AggregatedEntry(UnresolvedReference ref) {
            this.reference = ref;
        }

        void observe(String modId) {
            affectedMods.add(modId);
        }

        public UnresolvedReference reference() {
            return reference;
        }

        public int count() {
            return affectedMods.size();
        }

        public Set<String> affectedMods() {
            return Collections.unmodifiableSet(affectedMods);
        }
    }
}
