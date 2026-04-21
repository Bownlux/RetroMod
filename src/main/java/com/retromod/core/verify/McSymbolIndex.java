/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import java.util.List;

/**
 * Read-only oracle for "does this class/method/field exist in the target
 * Minecraft version?"
 *
 * <p>This interface is the shared foundation for two features that both need
 * to answer membership questions against the target MC API surface:</p>
 * <ul>
 *   <li>{@link ReferenceVerifier} — scans transformed bytecode and reports any
 *       reference the index says doesn't exist.</li>
 *   <li>{@link ReflectionStringRemapper} — before rewriting a reflection LDC
 *       string, can ask the index whether the rewritten target would actually
 *       resolve.</li>
 * </ul>
 *
 * <h3>Why an interface (not just a class)?</h3>
 * <p>Testability. The production implementation reads a full Minecraft JAR and
 * can't be exercised in unit tests without shipping ~100MB of test fixtures.
 * With an interface, tests can supply a tiny stub index with just the
 * classes/members the test cares about. It also leaves room for a future
 * runtime-backed implementation that checks against the live classloader
 * instead of a JAR scan.</p>
 *
 * <h3>What counts as "existing"?</h3>
 * <p>"Exists" means the exact member (class by internal name; method by
 * {@code owner + name + descriptor}; field by {@code owner + name + descriptor})
 * is either declared on the target class OR inherited through a superclass/
 * interface chain. So {@code hasMethod("net/minecraft/world/level/Level",
 * "getBlockState", "(...)")} is {@code true} even if {@code getBlockState} is
 * declared on a {@code LevelReader} interface that {@code Level} extends —
 * because that's a legal call site at compile time.</p>
 *
 * <p>"Doesn't exist" simply means "not found in the index." If the index hasn't
 * been populated (e.g., the MC JAR wasn't available), every lookup returns
 * {@code false}. Callers that need to distinguish "not indexed" from "truly
 * missing" should check {@link #isAvailable()} first.</p>
 *
 * <h3>Coverage scope</h3>
 * <p>The index covers what we can authoritatively enumerate:
 * <ul>
 *   <li>Everything under {@code net/minecraft/**} and {@code com/mojang/**}
 *       in the target MC JAR.</li>
 * </ul>
 * Loader-API renames (Fabric, NeoForge, Forge) are <b>not</b> in this index —
 * those are handled by {@link LoaderApiRenames}, which is a curated hand-maintained
 * table rather than an auto-indexed one. See that class for rationale.</p>
 */
public interface McSymbolIndex {

    /**
     * @return {@code true} if the index has been populated with MC JAR data.
     *         When {@code false}, every {@code hasXxx} call returns {@code false}
     *         — callers should treat that as "unknown" rather than "missing."
     */
    boolean isAvailable();

    /**
     * Exact class-existence check.
     *
     * @param internalName JVM internal name, slash-separated
     *                     (e.g., {@code "net/minecraft/core/BlockPos"})
     * @return {@code true} iff the class is present in the indexed MC JAR
     */
    boolean hasClass(String internalName);

    /**
     * Exact method-existence check, including inherited methods.
     *
     * @param owner      JVM internal class name
     * @param name       method name
     * @param descriptor JVM method descriptor (e.g., {@code "(IIF)V"})
     * @return {@code true} iff a matching method exists on {@code owner}
     *         or any ancestor (superclass or interface)
     */
    boolean hasMethod(String owner, String name, String descriptor);

    /**
     * Exact field-existence check, including inherited fields.
     *
     * @param owner      JVM internal class name
     * @param name       field name
     * @param descriptor JVM field descriptor (e.g., {@code "Ljava/lang/String;"})
     * @return {@code true} iff a matching field exists on {@code owner}
     *         or any ancestor
     */
    boolean hasField(String owner, String name, String descriptor);

    /**
     * Suggest up to {@code maxResults} alternative class names that exist in
     * the index and resemble the missing one. Used by the gap report to hint
     * at "did you mean…" replacements.
     *
     * <p>Implementations may use any similarity heuristic. A simple substring-
     * match on the simple name (everything after the last slash) is typically
     * sufficient — {@code net/minecraft/util/math/BlockPos} → {@code net/minecraft/core/BlockPos}
     * is an obvious simple-name match.</p>
     *
     * @param missingInternalName the class we couldn't find
     * @param maxResults          cap on number of suggestions (typically 3-5)
     * @return list of suggested replacements, possibly empty, never null
     */
    List<String> suggestClassAlternatives(String missingInternalName, int maxResults);

    /**
     * Suggest up to {@code maxResults} alternative methods that exist on the
     * given owner (or its hierarchy) and resemble the missing name.
     *
     * <p>Does NOT include the exact target itself — only alternatives, for cases
     * where the member name or descriptor changed.</p>
     *
     * @param owner         the class the reference was against
     * @param name          the method name we couldn't find
     * @param descriptor    the descriptor we couldn't find
     * @param maxResults    cap on number of suggestions
     */
    List<MemberSignature> suggestMethodAlternatives(String owner, String name,
                                                    String descriptor, int maxResults);

    /**
     * @return the MC version this index describes (e.g., {@code "26.1"}),
     *         or {@code "unknown"} if the source didn't specify
     */
    String targetMcVersion();

    /**
     * Lightweight record for a method or field signature used in suggestions.
     * Not meant to carry metadata — just the trio needed to identify a member
     * in the gap report output.
     */
    record MemberSignature(String owner, String name, String descriptor) {
        public String prettyPrint() {
            return owner + "#" + name + descriptor;
        }
    }
}
