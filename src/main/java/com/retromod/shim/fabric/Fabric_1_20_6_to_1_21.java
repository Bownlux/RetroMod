/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20.6 to run on 1.21.
 * Handles enchantment system becoming data-driven (registry-based) and
 * EnchantmentHelper method signature changes.
 */
public class Fabric_1_20_6_to_1_21 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.6 to 1.21"; }
    @Override public String getSourceVersion() { return "1.20.6"; }
    @Override public String getTargetVersion() { return "1.21"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Enchantments became data-driven (registry-based)
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/Enchantment", "getRarity",
            "()Lnet/minecraft/enchantment/Enchantment$Rarity;",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getRarity",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/Enchantment", "getMaxLevel",
            "()I",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getMaxLevel",
            "(Ljava/lang/Object;)I"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/Enchantment", "getMinLevel",
            "()I",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getMinLevel",
            "(Ljava/lang/Object;)I"
        );
        // EnchantmentHelper method changes
        transformer.registerMethodRedirect(
            "net/minecraft/enchantment/EnchantmentHelper", "getLevel",
            "(Lnet/minecraft/enchantment/Enchantment;Lnet/minecraft/item/ItemStack;)I",
            "com/retromod/shim/fabric/embedded/EnchantmentShim", "getLevel",
            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;)I"
        );
        // Trial chambers / Breeze
        transformer.registerClassRedirect(
            "net/minecraft/entity/mob/BreezeEntity",
            "net/minecraft/entity/mob/BreezeEntity"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
