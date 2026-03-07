/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Nether Update. Forge updated its registry system and some rendering methods changed.
 * Java 8 is the last version used.
 */
public class Forge_1_15_2_to_1_16_5 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.15.2 to 1.16.5"; }
    @Override public String getSourceVersion() { return "1.15.2"; }
    @Override public String getTargetVersion() { return "1.16.5"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Math vector package restructure
        transformer.registerClassRedirect(
            "net/minecraft/util/math/Vec3d",
            "net/minecraft/util/math/vector/Vector3d"
        );
        transformer.registerClassRedirect(
            "net/minecraft/util/math/Matrix4f",
            "net/minecraft/util/math/vector/Matrix4f"
        );
        transformer.registerClassRedirect(
            "net/minecraft/util/math/Quaternion",
            "net/minecraft/util/math/vector/Quaternion"
        );
        transformer.registerClassRedirect(
            "net/minecraft/util/math/Vector3f",
            "net/minecraft/util/math/vector/Vector3f"
        );
        // World/dimension changes for Nether rework
        transformer.registerMethodRedirect(
            "net/minecraft/world/dimension/DimensionType", "isUltrawarm",
            "()Z",
            "com/retromod/shim/forge/embedded/DimensionShim", "isUltrawarm",
            "(Ljava/lang/Object;)Z"
        );
        // Codec-based worldgen started
        transformer.registerMethodRedirect(
            "net/minecraft/world/gen/feature/Feature", "withConfiguration",
            "(Lnet/minecraft/world/gen/feature/IFeatureConfig;)Lnet/minecraft/world/gen/feature/ConfiguredFeature;",
            "com/retromod/shim/forge/embedded/FeatureShim", "withConfiguration",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
