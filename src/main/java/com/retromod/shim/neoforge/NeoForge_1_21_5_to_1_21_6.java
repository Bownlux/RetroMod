/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim for NeoForge 1.21.5 to 1.21.6.
 */
public class NeoForge_1_21_5_to_1_21_6 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.5 to 1.21.6"; }
    @Override public String getSourceVersion() { return "1.21.5"; }
    @Override public String getTargetVersion() { return "1.21.6"; }
    @Override public String getModLoaderType() { return "neoforge"; }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Block model lighting pipeline changes
        // HUD API changes parallel to Fabric
    }
    
    @Override public String[] getShimClasses() { return new String[0]; }
}
