/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One pattern-detection hit: "pattern X matched class Y with confidence Z."
 *
 * <h3>What lives here</h3>
 * <ul>
 *   <li>{@code patternName} — the identifier of the {@link ClassPattern} that
 *       fired. Matches {@link ClassPattern#name()}.</li>
 *   <li>{@code className} — JVM internal name of the mod class that matched.</li>
 *   <li>{@code confidence} — 0.0 to 1.0. 1.0 means "100% certain" (e.g., the
 *       class has an unambiguous signal like an annotation); lower values mean
 *       the match is based on heuristic signals (method-name patterns,
 *       structural shape).</li>
 *   <li>{@code metadata} — pattern-specific extra info, captured as an ordered
 *       key-value map so the gap report can print it without knowing the pattern.
 *       Examples: {@code handlerCount=3}, {@code superclass=net/minecraft/world/level/block/entity/BlockEntity}.</li>
 * </ul>
 *
 * <h3>Why a generic map for metadata?</h3>
 * <p>Each pattern extracts different info — a forge-event-listener pattern cares
 * about handler method signatures, a registry-holder pattern cares about the
 * list of registered items. Typed metadata classes would mean a hierarchy of
 * {@code PatternMatch} subclasses. For the initial feature the expressiveness
 * of a map is worth the loss of compile-time safety; metadata is only ever
 * rendered, never introspected by name.</p>
 */
public record PatternMatch(
        String patternName,
        String className,
        double confidence,
        Map<String, Object> metadata
) {

    public PatternMatch {
        Objects.requireNonNull(patternName, "patternName");
        Objects.requireNonNull(className, "className");
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("confidence must be in [0,1], got " + confidence);
        }
        metadata = metadata == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }

    /**
     * Short one-line description for gap-report output, e.g.
     * {@code "com/example/Foo (0.90) — handlerCount=3, annotation=SubscribeEvent"}.
     */
    public String prettyPrint() {
        StringBuilder sb = new StringBuilder();
        sb.append(className).append(" (").append(String.format("%.2f", confidence)).append(")");
        if (!metadata.isEmpty()) {
            sb.append(" — ");
            boolean first = true;
            for (Map.Entry<String, Object> e : metadata.entrySet()) {
                if (!first) sb.append(", ");
                sb.append(e.getKey()).append('=').append(e.getValue());
                first = false;
            }
        }
        return sb.toString();
    }
}
