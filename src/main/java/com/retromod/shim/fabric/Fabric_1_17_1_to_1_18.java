/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.17.1 to run on 1.18.
 * Handles world generation overhaul and world height changes from -64 to 320.
 */
public class Fabric_1_17_1_to_1_18 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.17.1 to 1.18"; }
    @Override public String getSourceVersion() { return "1.17.1"; }
    @Override public String getTargetVersion() { return "1.18"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // World height changes
        transformer.registerMethodRedirect(
            "net/minecraft/world/World", "getBottomY", "()I",
            "com/retromod/shim/fabric/embedded/WorldHeightShim", "getBottomY",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/World", "getTopY", "()I",
            "com/retromod/shim/fabric/embedded/WorldHeightShim", "getTopY",
            "(Ljava/lang/Object;)I"
        );
        // Structure feature renamed
        transformer.registerClassRedirect(
            "net/minecraft/world/gen/feature/StructureFeature",
            "net/minecraft/world/gen/structure/Structure"
        );
        // Biome source changes
        transformer.registerClassRedirect(
            "net/minecraft/world/biome/source/VanillaLayeredBiomeSource",
            "net/minecraft/world/biome/source/MultiNoiseBiomeSource"
        );
        // Chunk status changes
        transformer.registerMethodRedirect(
            "net/minecraft/world/chunk/Chunk", "getHighestNonEmptySection",
            "()Lnet/minecraft/world/chunk/ChunkSection;",
            "com/retromod/shim/fabric/embedded/ChunkShim", "getHighestNonEmptySection",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
