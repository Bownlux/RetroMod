/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.19.4 to 1.20 shim - Material removal and sign changes.
 * The Material class was completely removed; BlockBehaviour.Properties.of()
 * no longer takes a Material parameter. MaterialColor was replaced by
 * NoteBlockInstrument. Sign block entities also changed their API.
 */
public class Forge_1_19_4_to_1_20 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.19.4 to 1.20"; }
    @Override public String getSourceVersion() { return "1.19.4"; }
    @Override public String getTargetVersion() { return "1.20"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/state/BlockBehaviour$Properties", "of",
            "(Lnet/minecraft/world/level/material/Material;)Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;",
            "net/minecraft/world/level/block/state/BlockBehaviour$Properties", "of",
            "()Lnet/minecraft/world/level/block/state/BlockBehaviour$Properties;"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/material/Material",
            "com/retromod/shim/forge/embedded/MaterialShim"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/level/material/MaterialColor",
            "net/minecraft/world/level/block/state/properties/NoteBlockInstrument"
        );
        // Sign changes
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/SignBlockEntity", "getMessage",
            "(I)Lnet/minecraft/network/chat/Component;",
            "com/retromod/shim/forge/embedded/SignShim", "getMessage",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
