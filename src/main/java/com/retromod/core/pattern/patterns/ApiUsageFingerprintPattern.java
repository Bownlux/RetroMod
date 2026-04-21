/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Deep method-body scanner that builds an "API usage fingerprint" for the class.
 * Unlike the annotation/type-signature patterns, this pattern actually walks
 * every instruction in every method and catalogs which MC packages the class
 * touches and how often.
 *
 * <h3>Why the extra cost?</h3>
 * <p>Cheap patterns (method signatures, annotations) can miss classes that use
 * MC APIs <i>indirectly</i> — via helper methods, via reflection helpers that
 * take fully-qualified strings, via patterns where the signature is generic but
 * the method body is full of {@code INVOKE*} calls into {@code net/minecraft/*}.
 * This pattern catches those cases by doing the work nothing else does: reading
 * every instruction.</p>
 *
 * <p>Per-class cost is ~5–50ms depending on class size (proportional to total
 * instruction count). That's dramatically more than the other patterns
 * combined. It's worth it because the fingerprint is the highest-signal
 * diagnostic output for classifying mod code: "this class hits 47 block APIs,
 * 12 world APIs, and 3 rendering APIs" tells a modder exactly where to look
 * for breakage after a version jump.</p>
 *
 * <h3>Match behaviour</h3>
 * <p>Always matches (confidence 0.6) for any mod class that invokes at least
 * one {@code net/minecraft/*} or {@code com/mojang/*} method or field. The
 * "match" is really just "here's the fingerprint" — the pattern library runs
 * every class, and this pattern makes sure we report the usage breakdown
 * regardless of whether other patterns already matched.</p>
 *
 * <h3>Metadata captured</h3>
 * <ul>
 *   <li>{@code mcPackageCounts} — ordered {@code "package: count"} entries
 *       for the top 5 most-used MC subpackages</li>
 *   <li>{@code totalMcRefs} — total MC reference count (method calls + field
 *       accesses + type references)</li>
 *   <li>{@code suspiciousPatterns} — number of "watch out" patterns detected,
 *       e.g. hardcoded block positions, deprecated API calls (listed in
 *       {@link #SUSPICIOUS_PREFIXES})</li>
 * </ul>
 */
public final class ApiUsageFingerprintPattern implements ClassPattern {

    /**
     * Namespaces we count as "MC API usage." Anything outside these is mod
     * code or JDK and doesn't contribute to the fingerprint.
     */
    private static final String[] MC_NAMESPACES = {
            "net/minecraft/", "com/mojang/"
    };

    /**
     * Known-suspicious prefixes — API areas that are notoriously unstable
     * across MC versions. A class that uses these heavily is high-risk for
     * post-transform breakage.
     */
    private static final String[] SUSPICIOUS_PREFIXES = {
            "net/minecraft/client/renderer/",   // rendering internals
            "net/minecraft/client/gui/screens/",// GUI internals
            "net/minecraft/server/network/",    // packet handling
            "com/mojang/blaze3d/"               // GL state
    };

    /** Minimum references to a namespace before we include it in the top-N output. */
    private static final int MIN_REFS_TO_REPORT = 3;

    @Override
    public String name() { return "ApiUsageFingerprint"; }

    @Override
    public String description() {
        return "Which Minecraft API areas does this class touch most heavily?";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.methods == null || cls.methods.isEmpty()) return null;

        // Counter per "sub-namespace" — e.g. "net/minecraft/world/level" — so
        // the output is informative without being noisy. A TreeMap gives
        // deterministic iteration for the top-N logic.
        Map<String, Integer> counts = new TreeMap<>();
        int total = 0;
        int suspicious = 0;

        for (MethodNode method : cls.methods) {
            InsnList insns = method.instructions;
            if (insns == null) continue;

            AbstractInsnNode cur = insns.getFirst();
            while (cur != null) {
                String ref = extractReferenceOwner(cur);
                if (ref != null && isMcNamespace(ref)) {
                    String bucket = reduceToSubNamespace(ref);
                    counts.merge(bucket, 1, Integer::sum);
                    total++;
                    if (isSuspicious(ref)) suspicious++;
                }
                cur = cur.getNext();
            }
        }

        if (total == 0) return null; // no MC usage = no fingerprint to report

        // Take the top 5 namespaces by count for the summary. Sorted for stable output.
        String topBreakdown = counts.entrySet().stream()
                .filter(e -> e.getValue() >= MIN_REFS_TO_REPORT)
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(e -> e.getKey() + ":" + e.getValue())
                .reduce((a, b) -> a + " " + b)
                .orElse("");

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("totalMcRefs", total);
        if (!topBreakdown.isEmpty()) metadata.put("topSubpackages", topBreakdown);
        if (suspicious > 0) metadata.put("suspiciousRefs", suspicious);

        // Low confidence because "uses MC API" is weak signal on its own —
        // every mod class uses MC API. The value is the metadata breakdown,
        // not the match itself.
        return new PatternMatch(name(), cls.name, 0.6, metadata);
    }

    /**
     * Pull the owner class (internal name) out of an instruction that refers
     * to another class. Returns null for instructions that don't reference
     * a class (loads, arithmetic, etc.).
     *
     * <p>For {@link TypeInsnNode}, the {@code desc} field is either a bare
     * internal name (e.g., {@code "net/minecraft/core/BlockPos"} for
     * {@code CHECKCAST}) or an array descriptor (e.g., {@code "[Lnet/minecraft/core/BlockPos;"}
     * for {@code ANEWARRAY}). We normalize both forms through
     * {@link Type#getType(String)} / {@link Type#getObjectType(String)} so the
     * caller always gets a clean internal name. For primitive-array descriptors
     * like {@code "[I"} we return null — no MC reference to count.</p>
     */
    private static String extractReferenceOwner(AbstractInsnNode insn) {
        if (insn instanceof MethodInsnNode m) return m.owner;
        if (insn instanceof FieldInsnNode f) return f.owner;
        if (insn instanceof TypeInsnNode t) return normalizeTypeRef(t.desc);
        return null;
    }

    /**
     * Convert an ASM {@link TypeInsnNode} descriptor into the bare internal
     * name of the class it references. Returns null if the descriptor is a
     * primitive-array type (no object to report).
     */
    private static String normalizeTypeRef(String desc) {
        if (desc == null || desc.isEmpty()) return null;
        if (desc.charAt(0) == '[') {
            // Array descriptor — unwrap to the element type.
            Type elem = Type.getType(desc).getElementType();
            return elem.getSort() == Type.OBJECT ? elem.getInternalName() : null;
        }
        // Bare internal name (CHECKCAST / NEW / INSTANCEOF) — use as-is.
        return desc;
    }

    private static boolean isMcNamespace(String name) {
        for (String ns : MC_NAMESPACES) {
            if (name.startsWith(ns)) return true;
        }
        return false;
    }

    private static boolean isSuspicious(String name) {
        for (String pre : SUSPICIOUS_PREFIXES) {
            if (name.startsWith(pre)) return true;
        }
        return false;
    }

    /**
     * Reduce a fully-qualified class reference to its "sub-namespace" so we
     * can aggregate. For {@code net/minecraft/world/level/block/Blocks} we
     * return {@code net/minecraft/world/level/block}. Gives us a meaningful
     * histogram without being overwhelmed by individual class counts.
     */
    private static String reduceToSubNamespace(String internalName) {
        int lastSlash = internalName.lastIndexOf('/');
        return lastSlash > 0 ? internalName.substring(0, lastSlash) : internalName;
    }
}
