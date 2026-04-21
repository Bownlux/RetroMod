/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.21 to 1.21.1 shim - Bugfix release.
 * No significant API breaks requiring bytecode-level redirects.
 */
public class NeoForge_1_21_to_1_21_1 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21 to 1.21.1"; }
    @Override public String getSourceVersion() { return "1.21"; }
    @Override public String getTargetVersion() { return "1.21.1"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Bugfix release — no API breaks requiring redirects
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
