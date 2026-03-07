/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

public class NeoForge_1_21_4_to_1_21_5 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.4 to 1.21.5"; }
    @Override public String getSourceVersion() { return "1.21.4"; }
    @Override public String getTargetVersion() { return "1.21.5"; }
    @Override public String getModLoaderType() { return "neoforge"; }
    @Override public void registerRedirects(RetroModTransformer transformer) {}
    @Override public String[] getShimClasses() { return new String[0]; }
}
