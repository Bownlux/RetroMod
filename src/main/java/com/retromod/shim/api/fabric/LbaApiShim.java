/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * LibBlockAttributes (LBA) API -> Fabric Transfer API v2 Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * LibBlockAttributes (LBA) API compatibility shim.
 *
 * LibBlockAttributes by AlexIIL was an early Fabric library (1.14 - 1.18)
 * for item and fluid transfer between blocks. It predated Fabric's official
 * Transfer API and was widely used by early Fabric tech mods.
 *
 * LBA was deprecated once the official Fabric Transfer API v2 was added
 * in Fabric API 0.35+ (Minecraft 1.17+). Mods like Modern Industrialization,
 * AE2 Fabric, and others migrated from LBA to the Transfer API.
 *
 * Key mappings:
 * - alexiil.mc.lib.attributes.item.ItemAttributes -> fabric-transfer-api-v1 ItemStorage
 * - alexiil.mc.lib.attributes.fluid.FluidAttributes -> fabric-transfer-api-v1 FluidStorage
 * - FixedItemInv / FixedFluidInv -> Storage<ItemVariant> / Storage<FluidVariant>
 * - ItemInvUtil.move() -> StorageUtil.move()
 * - FluidAmount -> long (droplets, 81000 per bucket)
 */
public class LbaApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "LibBlockAttributes -> Fabric Transfer API Compatibility";
    }

    @Override
    public String getSourceVersion() {
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        return "fabric";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // ITEM ATTRIBUTE LOOKUP -> FABRIC ITEM STORAGE LOOKUP
        // ============================================================

        // Old: alexiil.mc.lib.attributes.item.ItemAttributes
        // LBA used a custom attribute system for looking up item inventories on blocks
        // New: net.fabricmc.fabric.api.transfer.v1.item.ItemStorage (Fabric Transfer API)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemAttributesCompat"
        );

        // Old: ItemAttributes.INSERTABLE - attribute for inserting items into a block
        // New: ItemStorage.SIDED (BlockApiLookup for Storage<ItemVariant>)
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "INSERTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "ITEM_INSERTABLE",
            "Ljava/lang/Object;"
        );

        // Old: ItemAttributes.EXTRACTABLE - attribute for extracting items from a block
        // New: ItemStorage.SIDED
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "EXTRACTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "ITEM_EXTRACTABLE",
            "Ljava/lang/Object;"
        );

        // Old: ItemAttributes.GROUPED_INV - grouped inventory attribute
        // New: ItemStorage.SIDED
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/item/ItemAttributes",
            "GROUPED_INV",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "ITEM_GROUPED",
            "Ljava/lang/Object;"
        );

        // ============================================================
        // ITEM INVENTORY INTERFACES
        // ============================================================

        // Old: alexiil.mc.lib.attributes.item.FixedItemInv - fixed-size item inventory
        // LBA's equivalent of Vanilla's Inventory but with better API
        // New: net.fabricmc.fabric.api.transfer.v1.item.InventoryStorage wrapping Storage<ItemVariant>
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FixedItemInvCompat"
        );

        // Old: FixedItemInv.getSlotCount() - number of slots
        // New: mapped through InventoryStorage
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "getSlotCount",
            "()I",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getSlotCount",
            "(Ljava/lang/Object;)I"
        );

        // Old: FixedItemInv.getInvStack(slot) - get stack in slot
        // New: mapped through InventoryStorage slot list
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "getInvStack",
            "(I)Lnet/minecraft/item/ItemStack;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getInvStack",
            "(Ljava/lang/Object;I)Lnet/minecraft/item/ItemStack;"
        );

        // Old: FixedItemInv.setInvStack(slot, stack, simulation) - set stack
        // New: mapped through transaction system in Transfer API
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/FixedItemInv",
            "setInvStack",
            "(ILnet/minecraft/item/ItemStack;Lalexiil/mc/lib/attributes/Simulation;)Z",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "setInvStack",
            "(Ljava/lang/Object;ILnet/minecraft/item/ItemStack;Ljava/lang/Object;)Z"
        );

        // Old: alexiil.mc.lib.attributes.item.ItemInsertable - insert items interface
        // New: Storage<ItemVariant>.insert(resource, maxAmount, transaction)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemInsertable",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemInsertableCompat"
        );

        // Old: alexiil.mc.lib.attributes.item.ItemExtractable - extract items interface
        // New: Storage<ItemVariant>.extract(resource, maxAmount, transaction)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemExtractable",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemExtractableCompat"
        );

        // ============================================================
        // ITEM TRANSFER UTILITIES
        // ============================================================

        // Old: alexiil.mc.lib.attributes.item.ItemInvUtil - utility for moving items
        // New: net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/item/ItemInvUtil",
            "com/retromod/shim/api/fabric/embedded/LbaShim$ItemInvUtilCompat"
        );

        // Old: ItemInvUtil.move(from, to, filter, maxAmount) - transfer items
        // New: StorageUtil.move(from, to, predicate, maxAmount, transaction)
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/item/ItemInvUtil",
            "move",
            "(Lalexiil/mc/lib/attributes/item/ItemExtractable;Lalexiil/mc/lib/attributes/item/ItemInsertable;Lalexiil/mc/lib/attributes/item/filter/ItemFilter;I)I",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "moveItems",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;I)I"
        );

        // ============================================================
        // FLUID ATTRIBUTE LOOKUP -> FABRIC FLUID STORAGE LOOKUP
        // ============================================================

        // Old: alexiil.mc.lib.attributes.fluid.FluidAttributes
        // LBA's fluid attribute lookup system
        // New: net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/FluidAttributes",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FluidAttributesCompat"
        );

        // Old: FluidAttributes.INSERTABLE - attribute for inserting fluid
        // New: FluidStorage.SIDED
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/FluidAttributes",
            "INSERTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_INSERTABLE",
            "Ljava/lang/Object;"
        );

        // Old: FluidAttributes.EXTRACTABLE
        // New: FluidStorage.SIDED
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/FluidAttributes",
            "EXTRACTABLE",
            "Lalexiil/mc/lib/attributes/Attribute;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_EXTRACTABLE",
            "Ljava/lang/Object;"
        );

        // ============================================================
        // FLUID INVENTORY INTERFACES
        // ============================================================

        // Old: alexiil.mc.lib.attributes.fluid.FixedFluidInv - fixed-size fluid tanks
        // New: Storage<FluidVariant>
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/FixedFluidInv",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FixedFluidInvCompat"
        );

        // Old: FixedFluidInv.getTankCount()
        // New: mapped through Storage slot iteration
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/FixedFluidInv",
            "getTankCount",
            "()I",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getTankCount",
            "(Ljava/lang/Object;)I"
        );

        // Old: FixedFluidInv.getInvFluid(tank) - get FluidVolume in tank
        // New: iterate Storage<FluidVariant> slots
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/FixedFluidInv",
            "getInvFluid",
            "(I)Lalexiil/mc/lib/attributes/fluid/volume/FluidVolume;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getInvFluid",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );

        // ============================================================
        // FLUID AMOUNT -> LONG (DROPLETS)
        // ============================================================

        // Old: alexiil.mc.lib.attributes.fluid.amount.FluidAmount
        // LBA used a Fraction-based fluid amount (numerator/denominator)
        // New: Fabric Transfer API uses long (droplets, 81000 per bucket)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/amount/FluidAmount",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FluidAmountCompat"
        );

        // Old: FluidAmount.BUCKET - constant for one bucket
        // New: FluidConstants.BUCKET (81000 droplets)
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/amount/FluidAmount",
            "BUCKET",
            "Lalexiil/mc/lib/attributes/fluid/amount/FluidAmount;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_BUCKET",
            "Ljava/lang/Object;"
        );

        // Old: FluidAmount.BOTTLE - constant for one bottle (1/3 bucket)
        // New: FluidConstants.BOTTLE (27000 droplets)
        transformer.registerFieldRedirect(
            "alexiil/mc/lib/attributes/fluid/amount/FluidAmount",
            "BOTTLE",
            "Lalexiil/mc/lib/attributes/fluid/amount/FluidAmount;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "FLUID_BOTTLE",
            "Ljava/lang/Object;"
        );

        // ============================================================
        // FLUID VOLUME -> FLUID VARIANT
        // ============================================================

        // Old: alexiil.mc.lib.attributes.fluid.volume.FluidVolume
        // Combined fluid type + amount in one object
        // New: FluidVariant + long amount (separate in Transfer API)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/fluid/volume/FluidVolume",
            "com/retromod/shim/api/fabric/embedded/LbaShim$FluidVolumeCompat"
        );

        // Old: FluidVolume.getFluidKey() - get the fluid type
        // New: FluidVariant (the variant IS the key)
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/volume/FluidVolume",
            "getFluidKey",
            "()Lalexiil/mc/lib/attributes/fluid/volume/FluidKey;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getFluidKey",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Old: FluidVolume.getAmount_F() - get amount as FluidAmount
        // New: amount as long droplets
        transformer.registerMethodRedirect(
            "alexiil/mc/lib/attributes/fluid/volume/FluidVolume",
            "getAmount_F",
            "()Lalexiil/mc/lib/attributes/fluid/amount/FluidAmount;",
            "com/retromod/shim/api/fabric/embedded/LbaShim",
            "getFluidAmount",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // ============================================================
        // SIMULATION ENUM -> TRANSACTION
        // ============================================================

        // Old: alexiil.mc.lib.attributes.Simulation enum (ACTION, SIMULATE)
        // LBA used an enum to indicate real vs simulated operations
        // New: Fabric Transfer API uses Transaction (open/commit/abort)
        transformer.registerClassRedirect(
            "alexiil/mc/lib/attributes/Simulation",
            "com/retromod/shim/api/fabric/embedded/LbaShim$SimulationCompat"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.LbaShim"
        };
    }
}
