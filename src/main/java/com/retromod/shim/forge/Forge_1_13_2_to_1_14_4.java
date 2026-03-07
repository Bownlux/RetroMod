/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Village & Pillage update. More class renames as Mojang continued the cleanup
 * started in the Flattening.
 */
public class Forge_1_13_2_to_1_14_4 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.13.2 to 1.14.4"; }
    @Override public String getSourceVersion() { return "1.13.2"; }
    @Override public String getTargetVersion() { return "1.14.4"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Renderer renames
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/entity/RenderLivingBase",
            "net/minecraft/client/renderer/entity/LivingRenderer"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/entity/RenderPlayer",
            "net/minecraft/client/renderer/entity/PlayerRenderer"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/entity/Render",
            "net/minecraft/client/renderer/entity/EntityRenderer"
        );
        // Village restructuring
        transformer.registerClassRedirect(
            "net/minecraft/entity/passive/EntityVillager",
            "net/minecraft/entity/merchant/villager/VillagerEntity"
        );
        // Block constructor changes
        transformer.registerMethodRedirect(
            "net/minecraft/block/Block", "<init>",
            "(Lnet/minecraft/block/Block$Properties;)V",
            "net/minecraft/block/Block", "<init>",
            "(Lnet/minecraft/block/AbstractBlock$Properties;)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
