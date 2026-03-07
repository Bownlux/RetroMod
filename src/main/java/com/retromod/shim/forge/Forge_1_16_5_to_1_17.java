/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.16.5 to 1.17 shim - Tag system rework.
 * The tag system was restructured, removing the getAllTags() pattern
 * in favor of a new tag registry approach. Forge registry events
 * remained stable across this transition.
 */
public class Forge_1_16_5_to_1_17 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.16.5 to 1.17"; }
    @Override public String getSourceVersion() { return "1.16.5"; }
    @Override public String getTargetVersion() { return "1.17"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerMethodRedirect(
            "net/minecraft/tags/BlockTags", "getAllTags",
            "()Lnet/minecraft/tags/TagCollection;",
            "com/retromod/shim/forge/embedded/TagShim", "getBlockTags",
            "()Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/tags/ItemTags", "getAllTags",
            "()Lnet/minecraft/tags/TagCollection;",
            "com/retromod/shim/forge/embedded/TagShim", "getItemTags",
            "()Ljava/lang/Object;"
        );
        // Forge event handlers stable across this transition
        transformer.registerMethodRedirect(
            "net/minecraftforge/event/RegistryEvent$Register", "getRegistry",
            "()Lnet/minecraftforge/registries/IForgeRegistry;",
            "com/retromod/shim/forge/embedded/RegistryShim", "getRegistry",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
