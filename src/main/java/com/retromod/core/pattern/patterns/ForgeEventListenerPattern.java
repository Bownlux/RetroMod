/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects classes that host Forge-style event listeners — methods annotated
 * with {@code @SubscribeEvent} (either the old Forge FQN or the new NeoForge
 * FQN).
 *
 * <h3>Match confidence</h3>
 * <p>High — an annotation match is unambiguous. Returns 1.0.</p>
 *
 * <h3>What it reports</h3>
 * <ul>
 *   <li>{@code handlerCount} — how many annotated methods on the class</li>
 *   <li>{@code annotationFqn} — which form of {@code @SubscribeEvent} was used
 *       (Forge vs NeoForge), so mod authors know if their code has already
 *       been migrated</li>
 *   <li>{@code eventTypes} — comma-separated list of the event types handled,
 *       extracted from each handler method's first parameter</li>
 * </ul>
 *
 * <h3>Why it matters</h3>
 * <p>When RetroMod translates Forge → NeoForge the annotation class itself gets
 * redirected via {@code ForgeEventApiShim}. Detection here lets us verify
 * <b>after</b> transformation that every event listener still has a valid
 * {@code @SubscribeEvent} annotation pointing at a reachable class — a missed
 * rename would produce a silently-unregistered handler (mod loads but never
 * handles any events, very confusing to debug).</p>
 */
public final class ForgeEventListenerPattern implements ClassPattern {

    /** Annotation descriptors we recognize. Both descriptors are valid at once
     * because a mod may have been partially migrated, or mix Forge and NeoForge
     * conventions. */
    private static final String FORGE_SUBSCRIBE_DESC =
            "Lnet/minecraftforge/eventbus/api/SubscribeEvent;";
    private static final String NEOFORGE_SUBSCRIBE_DESC =
            "Lnet/neoforged/bus/api/SubscribeEvent;";

    @Override
    public String name() { return "ForgeEventListener"; }

    @Override
    public String description() {
        return "Classes with @SubscribeEvent-annotated handler methods";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        // Honour scope filter — never match MC or JDK classes.
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.methods == null || cls.methods.isEmpty()) return null;

        List<String> handlerNames = new ArrayList<>();
        List<String> eventTypes = new ArrayList<>();
        String annotationFqn = null;

        for (MethodNode method : cls.methods) {
            if (method.visibleAnnotations == null) continue;

            boolean matched = false;
            for (AnnotationNode ann : method.visibleAnnotations) {
                if (FORGE_SUBSCRIBE_DESC.equals(ann.desc)
                    || NEOFORGE_SUBSCRIBE_DESC.equals(ann.desc)) {
                    matched = true;
                    // Record the annotation FQN on the FIRST match; if a class
                    // mixes Forge and NeoForge annotations, we note it.
                    String thisFqn = ann.desc.substring(1, ann.desc.length() - 1);
                    annotationFqn = annotationFqn == null
                            ? thisFqn
                            : (annotationFqn.equals(thisFqn) ? annotationFqn : "mixed");
                    break;
                }
            }
            if (matched) {
                handlerNames.add(method.name);
                // Event type = first parameter type of the handler method.
                String paramType = extractFirstParamInternalName(method.desc);
                if (paramType != null) eventTypes.add(paramType);
            }
        }

        if (handlerNames.isEmpty()) return null;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("handlerCount", handlerNames.size());
        metadata.put("annotationFqn", annotationFqn);
        metadata.put("eventTypes", String.join(",", eventTypes));
        return new PatternMatch(name(), cls.name, 1.0, metadata);
    }

    /**
     * Extract the internal name of the first parameter type from a method
     * descriptor. Returns null if the method has no parameters or its first
     * parameter is primitive.
     *
     * <p>Simplistic parser — doesn't handle nested arrays/generics because
     * event handlers conventionally take a single event object.</p>
     */
    private static String extractFirstParamInternalName(String desc) {
        if (desc == null || desc.length() < 3) return null;
        int open = desc.indexOf('(');
        if (open < 0) return null;
        int cursor = open + 1;
        if (cursor >= desc.length() || desc.charAt(cursor) == ')') return null;

        // Skip leading array markers
        while (cursor < desc.length() && desc.charAt(cursor) == '[') cursor++;
        if (cursor >= desc.length()) return null;

        if (desc.charAt(cursor) != 'L') {
            // Primitive first parameter — event handlers never do this
            return null;
        }
        int semi = desc.indexOf(';', cursor);
        if (semi < 0) return null;
        return desc.substring(cursor + 1, semi);
    }
}
