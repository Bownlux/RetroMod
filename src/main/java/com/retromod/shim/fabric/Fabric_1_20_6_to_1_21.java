/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetromodTransformer;
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
    public void registerRedirects(RetromodTransformer transformer) {
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

        // ============================================================
        // RESOURCE LOCATION CONSTRUCTOR CHANGES
        // new ResourceLocation(namespace, path) -> ResourceLocation.fromNamespaceAndPath(namespace, path)
        // The two-arg constructor was removed in 1.21; use the static factory instead.
        // ============================================================

        transformer.registerConstructorRedirect(
            "net/minecraft/resources/ResourceLocation",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/resources/ResourceLocation", "fromNamespaceAndPath",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/ResourceLocation;"
        );

        // Intermediary-keyed variant for Fabric mods on a pre-26.1 host. There the
        // intermediary→Mojang remap is OFF (it only runs for 26.1+), so the bytecode
        // still says `new class_2960(String, String)` rather than the Mojang name —
        // and that 2-arg constructor is private as of 1.21, so the call fails at
        // runtime with IllegalAccessError (bugs #36/#37, Haema/Rubinated Nether on
        // 1.21.1). class_2960 = ResourceLocation; method_60655 = fromNamespaceAndPath
        // (intermediary names are stable, and this factory was added in 1.21). On a
        // 26.1 host the remap has already turned class_2960 into Identifier before
        // this is looked up, so this entry simply never matches there.
        transformer.registerConstructorRedirect(
            "net/minecraft/class_2960",
            "(Ljava/lang/String;Ljava/lang/String;)V",
            "net/minecraft/class_2960", "method_60655",
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/class_2960;"
        );

        // ============================================================
        // ENTITY DIMENSION CHANGE
        // Entity.changeDimension(ServerLevel) -> Entity.teleportTo(ServerLevel)
        // Method renamed in 1.21 to better reflect its purpose.
        // ============================================================

        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "changeDimension",
            "(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;",
            "net/minecraft/world/entity/Entity", "teleportTo",
            "(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/world/entity/Entity;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
