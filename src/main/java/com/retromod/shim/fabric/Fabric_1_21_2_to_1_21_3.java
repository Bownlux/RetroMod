/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim for Fabric 1.21.2 to 1.21.3.
 * Minor version with minimal breaking changes.
 */
public class Fabric_1_21_2_to_1_21_3 implements VersionShim {
    @Override public String getShimName() { return "Fabric 1.21.2 to 1.21.3"; }
    @Override public String getSourceVersion() { return "1.21.2"; }
    @Override public String getTargetVersion() { return "1.21.3"; }
    @Override public String getModLoaderType() { return "fabric"; }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // 1.21.2 to 1.21.3 had minimal breaking changes
        // Mostly internal improvements and recipe system stabilization
    }
    
    @Override public String[] getShimClasses() { return new String[0]; }
}
