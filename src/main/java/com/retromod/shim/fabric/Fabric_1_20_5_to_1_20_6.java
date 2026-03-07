/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20.5 to run on 1.20.6.
 * Minor stabilization release with no significant API changes.
 */
public class Fabric_1_20_5_to_1_20_6 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.5 to 1.20.6"; }
    @Override public String getSourceVersion() { return "1.20.5"; }
    @Override public String getTargetVersion() { return "1.20.6"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Minor bugfix release - minimal API changes
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
