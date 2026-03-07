/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20.3 to run on 1.20.4.
 * Handles minor tick system changes and profiler method updates.
 */
public class Fabric_1_20_3_to_1_20_4 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.3 to 1.20.4"; }
    @Override public String getSourceVersion() { return "1.20.3"; }
    @Override public String getTargetVersion() { return "1.20.4"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Profiler method changes
        transformer.registerMethodRedirect(
            "net/minecraft/util/profiler/Profiler", "push",
            "(Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/ProfilerShim", "push",
            "(Ljava/lang/Object;Ljava/lang/String;)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
