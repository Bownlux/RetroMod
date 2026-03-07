/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.18 to 1.18.1 shim - Minor bugfix release.
 * No significant API breaks requiring bytecode-level redirects;
 * this was a minor stabilization patch.
 */
public class Forge_1_18_to_1_18_1 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.18 to 1.18.1"; }
    @Override public String getSourceVersion() { return "1.18"; }
    @Override public String getTargetVersion() { return "1.18.1"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Minor bugfix release with no significant API breaks requiring redirects
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
