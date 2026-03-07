/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21 to run on 1.21.1.
 * 1.21.1 was a minor bugfix release with minimal API changes.
 */
public class Fabric_1_21_to_1_21_1 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21 to 1.21.1";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.1";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // 1.21 to 1.21.1 had minimal breaking changes
        // Mainly bugfixes and internal changes
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
