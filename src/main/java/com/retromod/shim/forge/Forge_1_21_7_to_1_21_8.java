/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

public class Forge_1_21_7_to_1_21_8 implements VersionShim {
    @Override public String getShimName() { return "Forge 1.21.7 to 1.21.8"; }
    @Override public String getSourceVersion() { return "1.21.7"; }
    @Override public String getTargetVersion() { return "1.21.8"; }
    @Override public String getModLoaderType() { return "forge"; }
    @Override public void registerRedirects(RetroModTransformer transformer) {}
    @Override public String[] getShimClasses() { return new String[0]; }
}
