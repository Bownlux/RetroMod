/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.16.5 to run on 1.17.
 * Handles Java 16 transition, tag system restructuring, and ore generation changes.
 */
public class Fabric_1_16_5_to_1_17 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.16.5 to 1.17"; }
    @Override public String getSourceVersion() { return "1.16.5"; }
    @Override public String getTargetVersion() { return "1.17"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Tag system restructured
        transformer.registerClassRedirect(
            "net/minecraft/tag/ServerTagManagerHolder",
            "com/retromod/shim/fabric/embedded/TagShim"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/BlockTags",
            "net/minecraft/registry/tag/BlockTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/ItemTags",
            "net/minecraft/registry/tag/ItemTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/EntityTypeTags",
            "net/minecraft/registry/tag/EntityTypeTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/FluidTags",
            "net/minecraft/registry/tag/FluidTags"
        );
        // Ore feature config changed
        transformer.registerMethodRedirect(
            "net/minecraft/world/gen/feature/OreFeatureConfig", "<init>",
            "(Lnet/minecraft/structure/rule/RuleTest;Lnet/minecraft/block/BlockState;I)V",
            "com/retromod/shim/fabric/embedded/OreFeatureShim", "create",
            "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
