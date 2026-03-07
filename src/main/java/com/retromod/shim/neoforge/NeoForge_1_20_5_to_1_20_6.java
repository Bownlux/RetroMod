/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.20.5 to 1.20.6 shim - Minor stabilization release.
 * No significant API breaks requiring bytecode-level redirects;
 * the component system introduced in 1.20.5 was stabilized.
 */
public class NeoForge_1_20_5_to_1_20_6 implements VersionShim {

    @Override public String getShimName() { return "NeoForge 1.20.5 to 1.20.6"; }
    @Override public String getSourceVersion() { return "1.20.5"; }
    @Override public String getTargetVersion() { return "1.20.6"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Minor stabilization release with no significant API breaks requiring redirects
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
