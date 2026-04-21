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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Aggregated result of verifying every transformed class in a single mod.
 * Holds the list of {@link UnresolvedReference}s the verifier found, plus
 * summary counts, and knows how to render itself for both human-readable
 * (console / log file) and machine-readable output.
 *
 * <h3>Lifecycle</h3>
 * <p>A fresh report is created per mod at the start of transformation.
 * {@link #add(UnresolvedReference)} is called for every miss found. At the end
 * of the mod, the report is rendered (to logs, to disk) and optionally merged
 * into a {@link CrossModGapReport} for cross-mod aggregation.</p>
 *
 * <h3>Thread safety</h3>
 * <p>Not thread-safe. Callers must ensure single-threaded writes per report
 * instance, or externally synchronize. In the typical CLI batch flow,
 * transformation of a mod's classes is sequential, so this is not an issue.
 * For parallel transformation (if added later), each thread should use its
 * own report and merge at the end.</p>
 */
public final class VerificationReport {

    private final String modId;
    private final String targetMcVersion;
    private final int classesScanned;

    private final List<UnresolvedReference> missingClasses = new ArrayList<>();
    private final List<UnresolvedReference> missingMethods = new ArrayList<>();
    private final List<UnresolvedReference> missingFields = new ArrayList<>();
    private final List<UnresolvedReference> badSignatures = new ArrayList<>();

    /**
     * Pattern matches accumulated during verification. Keyed by pattern name
     * so the report can group matches by pattern type. Insertion-ordered so
     * patterns are emitted in the order they first matched.
     */
    private final Map<String, List<PatternMatch>> patternMatches = new LinkedHashMap<>();

    /**
     * Count of bridge methods synthesized for this mod (for the summary line).
     * Incremented by callers; zero if bridge synthesis is disabled.
     */
    private int bridgesSynthesized = 0;

    /**
     * @param modId            the mod's identifier (from {@code fabric.mod.json}
     *                         or {@code mods.toml}); used in report headers
     * @param targetMcVersion  the MC version we verified against
     * @param classesScanned   how many mod classes were scanned (for the header line)
     */
    public VerificationReport(String modId, String targetMcVersion, int classesScanned) {
        this.modId = Objects.requireNonNullElse(modId, "<unknown-mod>");
        this.targetMcVersion = Objects.requireNonNullElse(targetMcVersion, "unknown");
        this.classesScanned = Math.max(0, classesScanned);
    }

    public String modId() { return modId; }
    public String targetMcVersion() { return targetMcVersion; }
    public int classesScanned() { return classesScanned; }

    /**
     * Add an unresolved reference to the report, bucketed by {@link UnresolvedReference.Kind}.
     *
     * <p>Thread-safe via per-report synchronization — concurrent calls from
     * multiple worker threads during parallel verification won't corrupt the
     * bucket lists. Callers never read from the lists while add() can be in
     * flight (results are read only after {@code matchAllClasses} completes).</p>
     */
    public synchronized void add(UnresolvedReference ref) {
        if (ref == null) return;
        switch (ref.kind()) {
            case MISSING_CLASS -> missingClasses.add(ref);
            case MISSING_METHOD -> missingMethods.add(ref);
            case MISSING_FIELD -> missingFields.add(ref);
            case BAD_SIGNATURE -> badSignatures.add(ref);
        }
    }

    public List<UnresolvedReference> missingClasses() { return Collections.unmodifiableList(missingClasses); }
    public List<UnresolvedReference> missingMethods() { return Collections.unmodifiableList(missingMethods); }
    public List<UnresolvedReference> missingFields() { return Collections.unmodifiableList(missingFields); }
    public List<UnresolvedReference> badSignatures() { return Collections.unmodifiableList(badSignatures); }

    /**
     * Add a pattern match to the report. Matches are grouped by
     * {@link PatternMatch#patternName()} so the output is sectioned by
     * pattern type.
     *
     * <p>Thread-safe via per-report synchronization (see
     * {@link #add(UnresolvedReference)} for the rationale).</p>
     */
    public synchronized void addPatternMatch(PatternMatch match) {
        if (match == null) return;
        patternMatches.computeIfAbsent(match.patternName(), k -> new ArrayList<>()).add(match);
    }

    /**
     * Unmodifiable view of pattern matches grouped by pattern name.
     *
     * <p>Reads the {@code patternMatches} map under the report monitor — the
     * same lock {@link #addPatternMatch} writes under — to establish a
     * happens-before relationship between concurrent writers and this reader.
     * Without this, a reader could observe a partially published map in the
     * JMM.</p>
     */
    public synchronized Map<String, List<PatternMatch>> patternMatches() {
        Map<String, List<PatternMatch>> copy = new LinkedHashMap<>();
        for (var e : patternMatches.entrySet()) {
            copy.put(e.getKey(), Collections.unmodifiableList(new ArrayList<>(e.getValue())));
        }
        return Collections.unmodifiableMap(copy);
    }

    /** Total count across all pattern types (for the summary line). */
    public synchronized int totalPatternMatches() {
        int sum = 0;
        for (List<PatternMatch> l : patternMatches.values()) sum += l.size();
        return sum;
    }

    /** Record how many bridges the synthesizer emitted for this mod. */
    public void setBridgesSynthesized(int count) {
        this.bridgesSynthesized = Math.max(0, count);
    }

    public int bridgesSynthesized() {
        return bridgesSynthesized;
    }

    /** Total unresolved references across all kinds. */
    public int totalUnresolved() {
        return missingClasses.size() + missingMethods.size()
                + missingFields.size() + badSignatures.size();
    }

    /** True if there were no unresolved references — the mod passes verification. */
    public boolean isClean() {
        return totalUnresolved() == 0;
    }

    /**
     * Render a concise summary line suitable for INFO-level logging.
     * Format: {@code "[modid] 0 unresolved refs, 5 patterns matched, 3 bridges synthesized (247 classes scanned)"}
     */
    public String summaryLine() {
        int total = totalUnresolved();
        int patterns = totalPatternMatches();
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("[%s] %d unresolved ref%s", modId, total, total == 1 ? "" : "s"));
        if (patterns > 0) {
            sb.append(String.format(", %d pattern match%s", patterns, patterns == 1 ? "" : "es"));
        }
        if (bridgesSynthesized > 0) {
            sb.append(String.format(", %d bridge%s synthesized",
                    bridgesSynthesized, bridgesSynthesized == 1 ? "" : "s"));
        }
        sb.append(String.format(" (%d class%s scanned, target MC %s)",
                classesScanned, classesScanned == 1 ? "" : "es", targetMcVersion));
        return sb.toString();
    }

    /**
     * Render the full report to a text form. Suitable for writing to
     * {@code config/retromod/reports/<modid>-gaps.txt} or printing at WARN
     * level when there are unresolved refs.
     *
     * <p>The output is stable — same input produces same bytes — so it can
     * be diffed across runs to see whether new gaps appeared after a RetroMod
     * update.</p>
     */
    public void writeTo(Appendable out) throws IOException {
        out.append("=== RetroMod verification — ").append(modId).append(" ===\n");
        out.append("Target MC: ").append(targetMcVersion).append('\n');
        out.append("Classes scanned: ").append(Integer.toString(classesScanned)).append('\n');
        out.append("Unresolved references: ").append(Integer.toString(totalUnresolved())).append('\n');
        out.append('\n');

        if (isClean() && patternMatches.isEmpty() && bridgesSynthesized == 0) {
            out.append("No unresolved references. Mod should run.\n");
            return;
        }

        writeSection(out, "MISSING CLASSES", missingClasses);
        writeSection(out, "MISSING METHODS", missingMethods);
        writeSection(out, "MISSING FIELDS", missingFields);
        writeSection(out, "BAD SIGNATURES", badSignatures);

        writePatternMatches(out);

        if (bridgesSynthesized > 0) {
            out.append("BRIDGES SYNTHESIZED: ").append(Integer.toString(bridgesSynthesized)).append('\n');
            out.append("  (RetroMod added bridge methods for mod classes overriding renamed MC methods)\n");
            out.append('\n');
        }
    }

    /**
     * Write the pattern-matches section. One subsection per pattern type,
     * sorted by insertion order (which matches pattern-library order, i.e.,
     * most-confident patterns first).
     */
    private void writePatternMatches(Appendable out) throws IOException {
        if (patternMatches.isEmpty()) return;

        out.append("PATTERN MATCHES (").append(Integer.toString(totalPatternMatches())).append("):\n");
        for (Map.Entry<String, List<PatternMatch>> entry : patternMatches.entrySet()) {
            List<PatternMatch> matches = entry.getValue();
            // Stable per-pattern ordering by class name for diffability
            List<PatternMatch> sorted = new ArrayList<>(matches);
            sorted.sort(Comparator.comparing(PatternMatch::className));
            out.append("  ")
               .append(entry.getKey())
               .append(" (")
               .append(Integer.toString(sorted.size()))
               .append(" class")
               .append(sorted.size() == 1 ? "" : "es")
               .append("):\n");
            for (PatternMatch m : sorted) {
                out.append("    ").append(m.prettyPrint()).append('\n');
            }
        }
        out.append('\n');
    }

    private static void writeSection(Appendable out, String title,
                                      List<UnresolvedReference> refs) throws IOException {
        if (refs.isEmpty()) return;

        // Sort for deterministic output: by pretty-printed reference, then source.
        List<UnresolvedReference> sorted = new ArrayList<>(refs);
        sorted.sort(Comparator
                .comparing(UnresolvedReference::prettyPrint)
                .thenComparing(UnresolvedReference::sourceClass)
                .thenComparingInt(UnresolvedReference::sourceLine));

        out.append(title).append(" (").append(Integer.toString(sorted.size())).append("):\n");
        for (UnresolvedReference ref : sorted) {
            out.append("  ").append(ref.prettyPrint()).append('\n');
            out.append("    referenced from: ")
               .append(ref.sourceClass()).append('.').append(ref.sourceMethod());
            if (ref.sourceLine() >= 0) {
                out.append(" (line ").append(Integer.toString(ref.sourceLine())).append(")");
            }
            out.append('\n');
            if (!ref.suggestions().isEmpty()) {
                out.append("    suggestions: ")
                   .append(String.join(", ", ref.suggestions()))
                   .append('\n');
            }
        }
        out.append('\n');
    }
}
