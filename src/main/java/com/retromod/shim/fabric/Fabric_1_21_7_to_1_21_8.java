/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim for Fabric 1.21.7 to 1.21.8.
 */
public class Fabric_1_21_7_to_1_21_8 implements VersionShim {
    @Override public String getShimName() { return "Fabric 1.21.7 to 1.21.8"; }
    @Override public String getSourceVersion() { return "1.21.7"; }
    @Override public String getTargetVersion() { return "1.21.8"; }
    @Override public String getModLoaderType() { return "fabric"; }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // 1.21.7 to 1.21.8 - minor stabilization before major 1.21.9 changes
    }
    
    @Override public String[] getShimClasses() { return new String[0]; }
}
