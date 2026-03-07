/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20 to run on 1.20.1.
 * Minor bugfix release with no significant API changes.
 */
public class Fabric_1_20_to_1_20_1 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20 to 1.20.1"; }
    @Override public String getSourceVersion() { return "1.20"; }
    @Override public String getTargetVersion() { return "1.20.1"; }
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
