/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.20.3 to 1.20.4 shim - Minor tick changes.
 * Minor adjustments to tick scheduling internals; no significant
 * API breaks requiring bytecode-level redirects.
 */
public class NeoForge_1_20_3_to_1_20_4 implements VersionShim {

    @Override public String getShimName() { return "NeoForge 1.20.3 to 1.20.4"; }
    @Override public String getSourceVersion() { return "1.20.3"; }
    @Override public String getTargetVersion() { return "1.20.4"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Minor tick scheduling changes with no significant API breaks requiring redirects
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
