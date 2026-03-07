/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.18.1 to run on 1.18.2.
 * Handles PlacedFeature registration API changes.
 */
public class Fabric_1_18_1_to_1_18_2 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.18.1 to 1.18.2"; }
    @Override public String getSourceVersion() { return "1.18.1"; }
    @Override public String getTargetVersion() { return "1.18.2"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerMethodRedirect(
            "net/minecraft/world/gen/feature/PlacedFeatures", "register",
            "(Ljava/lang/String;Lnet/minecraft/world/gen/feature/ConfiguredFeature;[Lnet/minecraft/world/gen/placementmodifier/PlacementModifier;)Lnet/minecraft/util/registry/RegistryEntry;",
            "com/retromod/shim/fabric/embedded/PlacedFeatureShim", "register",
            "(Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
