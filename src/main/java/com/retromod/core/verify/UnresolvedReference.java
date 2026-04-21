/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import java.util.List;
import java.util.Objects;

/**
 * A single MC-API reference that didn't resolve against the target MC version.
 * Produced by {@link ReferenceVerifier} and aggregated into a
 * {@link VerificationReport}.
 *
 * <h3>Field meanings</h3>
 * <ul>
 *   <li>{@link Kind} — what kind of miss this is. {@code MISSING_CLASS} is the
 *       most severe because it implies every member reference against that
 *       class is also broken. {@code MISSING_METHOD} / {@code MISSING_FIELD}
 *       mean the owner class exists but the specific member didn't — a classic
 *       signature change or rename. {@code BAD_SIGNATURE} is reserved for
 *       future use (v1 emits it only if a method name exists but no descriptor
 *       variant matches; v1 just flags it as MISSING_METHOD with suggestions
 *       for now).</li>
 *   <li>{@code owner/name/descriptor} — the reference itself, in JVM internal
 *       form. {@code name}/{@code descriptor} are empty strings for
 *       {@code MISSING_CLASS} since only the owner class is the thing that's
 *       missing in that case.</li>
 *   <li>{@code sourceClass/sourceMethod/sourceLine} — where in the mod the
 *       reference came from. {@code sourceLine} is {@code -1} if the class
 *       was compiled without debug info.</li>
 *   <li>{@code suggestions} — up to a handful of candidate replacements that
 *       DO exist in the target MC index. May be empty if no similar names
 *       were found.</li>
 * </ul>
 *
 * <h3>Equality and sorting</h3>
 * <p>Records compare by all fields, so two references that differ only in
 * {@code sourceLine} are considered distinct. The cross-mod gap report
 * aggregates on {@link #identityKey()} (owner+name+descriptor+kind) instead —
 * see {@link VerificationReport}.</p>
 */
public record UnresolvedReference(
        Kind kind,
        String owner,
        String name,
        String descriptor,
        String sourceClass,
        String sourceMethod,
        int sourceLine,
        List<String> suggestions
) {

    /** Kind of resolution miss. */
    public enum Kind {
        /** The owner class itself doesn't exist in the target MC. */
        MISSING_CLASS,
        /** The owner class exists but this method was not found. */
        MISSING_METHOD,
        /** The owner class exists but this field was not found. */
        MISSING_FIELD,
        /** A method of this name exists but none with this descriptor. */
        BAD_SIGNATURE
    }

    /** Canonical constructor with defensive copies + null normalization. */
    public UnresolvedReference {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(owner, "owner");
        name = name == null ? "" : name;
        descriptor = descriptor == null ? "" : descriptor;
        sourceClass = sourceClass == null ? "<unknown>" : sourceClass;
        sourceMethod = sourceMethod == null ? "<unknown>" : sourceMethod;
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    /**
     * Identity key used for cross-mod aggregation — strips source location so
     * the same reference from two different mods collapses into one row in the
     * cross-mod gap report.
     */
    public String identityKey() {
        return kind.name() + "|" + owner + "|" + name + "|" + descriptor;
    }

    /**
     * Human-readable one-liner for this reference, used in the per-mod gap
     * report. Does not include source location or suggestions — callers
     * format those separately.
     */
    public String prettyPrint() {
        return switch (kind) {
            case MISSING_CLASS -> owner;
            case MISSING_METHOD, BAD_SIGNATURE -> owner + "#" + name + descriptor;
            case MISSING_FIELD -> owner + "#" + name + " : " + descriptor;
        };
    }
}
