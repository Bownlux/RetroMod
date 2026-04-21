/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

public class Forge_1_21_9_to_1_21_10 implements VersionShim {
    @Override public String getShimName() { return "Forge 1.21.9 to 1.21.10"; }
    @Override public String getSourceVersion() { return "1.21.9"; }
    @Override public String getTargetVersion() { return "1.21.10"; }
    @Override public String getModLoaderType() { return "forge"; }
    @Override public void registerRedirects(RetroModTransformer transformer) {}
    @Override public String[] getShimClasses() { return new String[0]; }
}
