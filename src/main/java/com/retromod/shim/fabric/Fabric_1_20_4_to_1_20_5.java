/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20.4 to run on 1.20.5.
 * Handles the massive component system migration replacing NBT on ItemStacks,
 * FoodComponent API changes, attribute system updates, and Item.Settings rework.
 */
public class Fabric_1_20_4_to_1_20_5 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.4 to 1.20.5"; }
    @Override public String getSourceVersion() { return "1.20.4"; }
    @Override public String getTargetVersion() { return "1.20.5"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ItemStack NBT methods removed - use components
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "getNbt",
            "()Lnet/minecraft/nbt/NbtCompound;",
            "com/retromod/shim/fabric/embedded/ComponentBridgeShim", "getNbt",
            "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NbtCompound;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "getOrCreateNbt",
            "()Lnet/minecraft/nbt/NbtCompound;",
            "com/retromod/shim/fabric/embedded/ComponentBridgeShim", "getOrCreateNbt",
            "(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/nbt/NbtCompound;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "setNbt",
            "(Lnet/minecraft/nbt/NbtCompound;)V",
            "com/retromod/shim/fabric/embedded/ComponentBridgeShim", "setNbt",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/nbt/NbtCompound;)V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemStack", "hasNbt",
            "()Z",
            "com/retromod/shim/fabric/embedded/ComponentBridgeShim", "hasNbt",
            "(Lnet/minecraft/item/ItemStack;)Z"
        );
        // FoodComponent changes
        transformer.registerMethodRedirect(
            "net/minecraft/item/FoodComponent$Builder", "hunger",
            "(I)Lnet/minecraft/item/FoodComponent$Builder;",
            "com/retromod/shim/fabric/embedded/FoodComponentShim", "nutrition",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/item/FoodComponent$Builder", "saturationModifier",
            "(F)Lnet/minecraft/item/FoodComponent$Builder;",
            "com/retromod/shim/fabric/embedded/FoodComponentShim", "saturationModifier",
            "(Ljava/lang/Object;F)Ljava/lang/Object;"
        );
        // Attribute system changes
        transformer.registerMethodRedirect(
            "net/minecraft/entity/attribute/EntityAttributeModifier", "<init>",
            "(Ljava/util/UUID;Ljava/lang/String;DLnet/minecraft/entity/attribute/EntityAttributeModifier$Operation;)V",
            "com/retromod/shim/fabric/embedded/AttributeShim", "createModifier",
            "(Ljava/util/UUID;Ljava/lang/String;DLjava/lang/Object;)Ljava/lang/Object;"
        );
        // Item.Settings changes
        transformer.registerMethodRedirect(
            "net/minecraft/item/Item$Settings", "food",
            "(Lnet/minecraft/item/FoodComponent;)Lnet/minecraft/item/Item$Settings;",
            "com/retromod/shim/fabric/embedded/FoodComponentShim", "food",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
