/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Detects classes that act as <b>registration holders</b> — the {@code public
 * static final DeferredRegister<X>} + {@code public static final DeferredHolder<Y>}
 * pattern that's idiomatic for Forge/NeoForge mod registries.
 *
 * <h3>Match confidence</h3>
 * <p>High (0.95) when at least one {@code DeferredRegister} field is present.
 * The type signature is distinctive enough that false positives are essentially
 * impossible — no non-registry class uses {@code DeferredRegister<T>} as a
 * public static final field type.</p>
 *
 * <h3>What it reports</h3>
 * <ul>
 *   <li>{@code deferredRegisterCount} — how many {@code DeferredRegister} fields</li>
 *   <li>{@code holderCount} — how many {@code DeferredHolder}/{@code RegistryObject}
 *       fields (each is one registered item)</li>
 *   <li>{@code registryType} — the generic parameter of the first
 *       {@code DeferredRegister} if extractable from its signature (e.g.,
 *       {@code net/minecraft/world/item/Item})</li>
 * </ul>
 *
 * <h3>Why it matters</h3>
 * <p>The registry pattern is where half of most mod's "public API" lives. Mod
 * authors referring to {@code ModItems.MY_ITEM} from other parts of their code
 * depend on this pattern resolving cleanly after transformation. Detection
 * lets the gap report tell mod authors "your registry holder class looks OK
 * / was rewritten / is missing a field," which is directly actionable.</p>
 */
public final class DeferredRegisterHolderPattern implements ClassPattern {

    /** Old Forge and new NeoForge names — we accept either. */
    private static final String OLD_DEFERRED_REGISTER =
            "Lnet/minecraftforge/registries/DeferredRegister;";
    private static final String NEW_DEFERRED_REGISTER =
            "Lnet/neoforged/neoforge/registries/DeferredRegister;";
    private static final String OLD_REGISTRY_OBJECT =
            "Lnet/minecraftforge/registries/RegistryObject;";
    private static final String NEW_DEFERRED_HOLDER =
            "Lnet/neoforged/neoforge/registries/DeferredHolder;";

    /** Mask matching public-static-final. */
    private static final int PSF_MASK =
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL;

    @Override
    public String name() { return "DeferredRegisterHolder"; }

    @Override
    public String description() {
        return "Classes holding DeferredRegister<T> fields — mod registry holders";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.fields == null || cls.fields.isEmpty()) return null;

        int deferredRegisterCount = 0;
        int holderCount = 0;
        String firstRegistryType = null;

        for (FieldNode f : cls.fields) {
            // Only PSF fields are interesting — the idiomatic pattern uses them.
            // Skip private / non-static / non-final fields.
            if ((f.access & PSF_MASK) != PSF_MASK) continue;
            if (f.desc == null) continue;

            if (f.desc.equals(OLD_DEFERRED_REGISTER) || f.desc.equals(NEW_DEFERRED_REGISTER)) {
                deferredRegisterCount++;
                if (firstRegistryType == null) {
                    firstRegistryType = extractGenericType(f.signature);
                }
            } else if (f.desc.equals(OLD_REGISTRY_OBJECT) || f.desc.equals(NEW_DEFERRED_HOLDER)) {
                holderCount++;
            }
        }

        if (deferredRegisterCount == 0 && holderCount == 0) return null;

        // A class with ONLY DeferredHolder fields (no DeferredRegister) is suspicious —
        // typically the DeferredRegister is in a different class and we're looking at
        // a type-only holder. Still worth reporting at a lower confidence.
        double confidence = deferredRegisterCount > 0 ? 0.95 : 0.7;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("deferredRegisterCount", deferredRegisterCount);
        metadata.put("holderCount", holderCount);
        if (firstRegistryType != null) metadata.put("registryType", firstRegistryType);
        return new PatternMatch(name(), cls.name, confidence, metadata);
    }

    /**
     * Pull the generic parameter out of a {@code DeferredRegister<T>} field's
     * signature attribute. Returns null if the signature is absent or in an
     * unexpected shape.
     *
     * <p>A signature like {@code Lnet/.../DeferredRegister<Lnet/minecraft/world/item/Item;>;}
     * gives us {@code net/minecraft/world/item/Item}.</p>
     */
    private static String extractGenericType(String signature) {
        if (signature == null) return null;
        int angle = signature.indexOf('<');
        if (angle < 0) return null;
        int innerL = signature.indexOf('L', angle);
        if (innerL < 0) return null;
        int innerSemi = signature.indexOf(';', innerL);
        if (innerSemi < 0) return null;
        return signature.substring(innerL + 1, innerSemi);
    }
}
