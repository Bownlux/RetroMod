/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.18.1 to 1.18.2 shim - Minor feature registration changes.
 * PlacedFeature placement API was adjusted, changing the method
 * signature for the place() method.
 */
public class Forge_1_18_1_to_1_18_2 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.18.1 to 1.18.2"; }
    @Override public String getSourceVersion() { return "1.18.1"; }
    @Override public String getTargetVersion() { return "1.18.2"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // PlacedFeature changes
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/levelgen/placement/PlacedFeature", "place",
            "(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Ljava/util/Random;Lnet/minecraft/core/BlockPos;)Z",
            "com/retromod/shim/forge/embedded/PlacedFeatureShim", "place",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Z"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
