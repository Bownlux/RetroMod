/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.vanilla;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed vanilla Minecraft API classes.
 *
 * Several core Minecraft classes were removed or refactored across
 * major versions. This provider registers stub implementations so
 * older mods referencing these classes do not crash with
 * ClassNotFoundException at startup.
 *
 * Covered removals:
 * - LiteralText / TranslatableText (replaced by Text.literal / Text.translatable)
 * - Material / MaterialColor (block material system removed in 1.19.3+)
 * - StructureFeature (replaced by Structure in 1.19+)
 */
public class MinecraftVanillaPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Minecraft Vanilla Removed APIs";
    }

    @Override
    public String getCategory() {
        return "minecraft_vanilla";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/text/LiteralText",
            "net/minecraft/text/TranslatableText",
            "net/minecraft/block/Material",
            "net/minecraft/block/MaterialColor",
            "net/minecraft/world/gen/feature/StructureFeature"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "net.minecraft.text.LiteralText",
            "net.minecraft.text.TranslatableText",
            "net.minecraft.block.Material",
            "net.minecraft.block.MaterialColor",
            "net.minecraft.world.gen.feature.StructureFeature"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // Classes exist at original paths - just register for tracking
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
