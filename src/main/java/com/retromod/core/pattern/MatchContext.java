/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern;

import com.retromod.core.verify.LoaderApiRenames;
import com.retromod.core.verify.McSymbolIndex;

import java.util.Collections;
import java.util.Set;

/**
 * Read-only context passed to {@link ClassPattern} implementations so they can
 * make informed decisions without reaching back into the rest of RetroMod.
 *
 * <h3>What's inside</h3>
 * <ul>
 *   <li>{@code modOwnClasses} — classes defined by the mod. Patterns that want
 *       to distinguish "mod code" from "MC or JDK code" query this. Never null.</li>
 *   <li>{@code loaderRenames} — the curated loader-API rename table. Patterns
 *       that look at annotations/types from Fabric/NeoForge/Forge consult this
 *       to recognize both old and renamed forms. Never null (empty when
 *       not configured).</li>
 *   <li>{@code mcIndex} — symbol index for the target MC JAR. Patterns that
 *       need to verify a referenced class actually exists in MC (e.g.,
 *       "does this class extend a real BlockEntity?") query this. May be
 *       in an unavailable state (see {@link McSymbolIndex#isAvailable()}) —
 *       patterns must handle that gracefully.</li>
 * </ul>
 *
 * <h3>Why a record with no defaults</h3>
 * <p>Making callers construct one explicitly prevents silent "pattern worked
 * in tests but matches nothing in production because modOwnClasses was empty"
 * bugs. Every field is required at the call site.</p>
 */
public record MatchContext(
        Set<String> modOwnClasses,
        LoaderApiRenames loaderRenames,
        McSymbolIndex mcIndex
) {

    public MatchContext {
        modOwnClasses = modOwnClasses == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(modOwnClasses);
    }

    /**
     * Empty context — a context with no mod classes and no loader/MC data.
     * Useful for unit tests of patterns that don't care about any of those
     * signals.
     */
    public static MatchContext empty() {
        return new MatchContext(
                Collections.emptySet(),
                LoaderApiRenames.forTesting(null, null, null),
                new McSymbolIndex() {
                    @Override public boolean isAvailable() { return false; }
                    @Override public boolean hasClass(String n) { return false; }
                    @Override public boolean hasMethod(String o, String n, String d) { return false; }
                    @Override public boolean hasField(String o, String n, String d) { return false; }
                    @Override public java.util.List<String> suggestClassAlternatives(String n, int max) {
                        return java.util.List.of();
                    }
                    @Override public java.util.List<MemberSignature> suggestMethodAlternatives(
                            String o, String n, String d, int max) {
                        return java.util.List.of();
                    }
                    @Override public String targetMcVersion() { return "unknown"; }
                });
    }
}
