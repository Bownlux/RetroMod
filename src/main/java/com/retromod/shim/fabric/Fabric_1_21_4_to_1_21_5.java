/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim for Fabric 1.21.4 to 1.21.5.
 */
public class Fabric_1_21_4_to_1_21_5 implements VersionShim {
    @Override public String getShimName() { return "Fabric 1.21.4 to 1.21.5"; }
    @Override public String getSourceVersion() { return "1.21.4"; }
    @Override public String getTargetVersion() { return "1.21.5"; }
    @Override public String getModLoaderType() { return "fabric"; }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // 1.21.4 to 1.21.5 - preparation for HUD changes in 1.21.6
    }
    
    @Override public String[] getShimClasses() { return new String[0]; }
}
