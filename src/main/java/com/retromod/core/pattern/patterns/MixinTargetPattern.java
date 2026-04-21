/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detects <a href="https://github.com/SpongePowered/Mixin">Mixin</a> classes
 * and, where possible, verifies their target classes still exist in the
 * target MC version.
 *
 * <h3>Why Mixins deserve their own pattern</h3>
 * <p>Mixins are the single largest source of silent mod breakage across MC
 * versions. A typical Mixin class looks like:</p>
 * <pre>
 * &#64;Mixin(Player.class)
 * public abstract class PlayerMixin {
 *     &#64;Inject(method = "tick", at = &#64;At("HEAD"))
 *     private void onTick(CallbackInfo ci) { ... }
 * }
 * </pre>
 * <p>If MC renames {@code Player} or the {@code tick} method, nothing at
 * compile time flags the mismatch — Mixins resolve targets <i>at runtime</i>
 * when the Mixin framework applies them. The result is "mod loads but does
 * nothing" bugs that are nearly impossible to diagnose without reading
 * obscure log lines.</p>
 *
 * <p>This pattern detects every {@code @Mixin}-annotated class and extracts
 * its declared targets. The gap report can then show which mixins reference
 * classes still present in the target MC vs. which are now pointing at
 * renamed or removed types.</p>
 *
 * <h3>Match behaviour</h3>
 * <p>Confidence 1.0 when the class has a {@code @Mixin} annotation — the
 * annotation is an unambiguous declaration by the mod author.</p>
 *
 * <h3>Metadata captured</h3>
 * <ul>
 *   <li>{@code targetClasses} — comma-separated list of mixin targets
 *       extracted from the {@code @Mixin} annotation's {@code value} array</li>
 *   <li>{@code targetsResolved} — how many of those targets actually exist
 *       in the target MC index</li>
 *   <li>{@code targetsMissing} — how many targets are NOT found in MC —
 *       the actionable number for mod authors to investigate</li>
 * </ul>
 *
 * <h3>Known limitations</h3>
 * <p>Only the {@code value} array of {@code @Mixin} is read. The
 * {@code targets} string-array form ({@code @Mixin(targets = {"net.minecraft.X"})})
 * is less common but out of v1 scope — would require string-form class-name
 * resolution logic we don't have factored out. Added in a follow-up if the
 * gap report shows we need it.</p>
 */
public final class MixinTargetPattern implements ClassPattern {

    /** Mixin annotation descriptor — the SpongePowered FQN is the de-facto standard. */
    private static final String MIXIN_ANNOTATION_DESC =
            "Lorg/spongepowered/asm/mixin/Mixin;";

    @Override
    public String name() { return "MixinTarget"; }

    @Override
    public String description() {
        return "Mixin classes and whether their targets still exist in MC";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.visibleAnnotations == null) return null;

        AnnotationNode mixinAnnotation = null;
        for (AnnotationNode ann : cls.visibleAnnotations) {
            if (MIXIN_ANNOTATION_DESC.equals(ann.desc)) {
                mixinAnnotation = ann;
                break;
            }
        }
        if (mixinAnnotation == null) return null;

        List<String> targetClassNames = extractMixinValueArray(mixinAnnotation);
        if (targetClassNames.isEmpty()) {
            // @Mixin with no value= is almost always a "targets=" form we don't
            // handle in v1. Record the match without detailed target info.
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("note", "targets-not-extractable");
            return new PatternMatch(name(), cls.name, 1.0, metadata);
        }

        int resolved = 0;
        int missing = 0;
        List<String> missingTargets = new ArrayList<>();
        if (ctx.mcIndex().isAvailable()) {
            for (String target : targetClassNames) {
                if (ctx.mcIndex().hasClass(target)) {
                    resolved++;
                } else {
                    missing++;
                    missingTargets.add(target);
                }
            }
        }

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("targetClasses", String.join(",", targetClassNames));
        if (ctx.mcIndex().isAvailable()) {
            metadata.put("targetsResolved", resolved);
            metadata.put("targetsMissing", missing);
            if (missing > 0) {
                metadata.put("missingTargets", String.join(",", missingTargets));
            }
        }
        return new PatternMatch(name(), cls.name, 1.0, metadata);
    }

    /**
     * Extract the JVM internal names of classes from a {@code @Mixin(value = {X.class, Y.class})}
     * annotation. The {@code value} attribute serializes as a list where each
     * element is an ASM {@link Type} object whose internal name we want.
     *
     * <p>Returns empty list if no {@code value} attribute present or if its
     * shape doesn't match what we expect. Graceful by design — a malformed
     * annotation shouldn't crash the matcher.</p>
     */
    private static List<String> extractMixinValueArray(AnnotationNode ann) {
        if (ann.values == null) return List.of();
        // Annotation values are stored as a flat list of alternating (name, value) pairs.
        // We scan for the "value" key and grab its list companion.
        for (int i = 0; i + 1 < ann.values.size(); i += 2) {
            Object key = ann.values.get(i);
            Object val = ann.values.get(i + 1);
            if ("value".equals(key) && val instanceof List<?> list) {
                List<String> out = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof Type t) {
                        // Internal name for Type.OBJECT is "net/minecraft/Foo".
                        // We don't worry about array element types — @Mixin values
                        // don't take arrays.
                        if (t.getSort() == Type.OBJECT) {
                            out.add(t.getInternalName());
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }
}
