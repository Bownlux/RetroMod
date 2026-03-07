/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * CoFH/Thermal/Redstone Flux API -> Forge Energy API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * CoFH Core / Thermal Foundation / Redstone Flux API compatibility shim.
 *
 * The CoFH (Cult of the Full Hub) team created the original RF (Redstone Flux)
 * energy API used from Forge 1.7.10 through 1.12.2. This was THE standard
 * energy system that nearly all tech mods used (Thermal Expansion, EnderIO,
 * Mekanism, Actually Additions, etc.).
 *
 * In Forge 1.12+, the Forge Energy (FE) system was added as a built-in
 * capability based on the RF API. By 1.13+, the old CoFH RF API was
 * removed and all mods migrated to Forge Energy (IEnergyStorage).
 *
 * Key mappings:
 * - cofh.api.energy.IEnergyHandler -> net.minecraftforge.energy.IEnergyStorage
 * - cofh.api.energy.IEnergyReceiver -> IEnergyStorage (receive only)
 * - cofh.api.energy.IEnergyProvider -> IEnergyStorage (extract only)
 * - cofh.redstoneflux.api.IEnergyReceiver -> IEnergyStorage
 * - cofh.redstoneflux.api.IEnergyProvider -> IEnergyStorage
 * - cofh.api.energy.EnergyStorage -> net.minecraftforge.energy.EnergyStorage
 * - cofh.api.energy.IEnergyConnection -> IEnergyStorage (canConnect via non-zero capacity)
 */
public class ThermalApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "CoFH/Thermal RF -> Forge Energy API Compatibility";
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
        return "forge";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // CoFH ENERGY API (cofh.api.energy) - Original 1.7.10/1.10.2 API
        // ============================================================

        // Old: cofh.api.energy.IEnergyHandler - combined send/receive interface
        // Was the main interface tile entities implemented for RF support
        // New: net.minecraftforge.energy.IEnergyStorage (Forge built-in)
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyHandler",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: IEnergyHandler.receiveEnergy(facing, maxReceive, simulate)
        // The facing parameter was an EnumFacing (now Direction), used for sided energy
        // New: IEnergyStorage.receiveEnergy(maxReceive, simulate) - no facing param
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "receiveEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "receiveEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        // Old: IEnergyHandler.extractEnergy(facing, maxExtract, simulate)
        // New: IEnergyStorage.extractEnergy(maxExtract, simulate) - no facing param
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "extractEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "extractEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        // Old: IEnergyHandler.getEnergyStored(facing)
        // New: IEnergyStorage.getEnergyStored() - no facing param
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "getEnergyStored",
            "(Lnet/minecraft/util/EnumFacing;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "getEnergyStored",
            "(Ljava/lang/Object;Ljava/lang/Object;)I"
        );

        // Old: IEnergyHandler.getMaxEnergyStored(facing)
        // New: IEnergyStorage.getMaxEnergyStored() - no facing param
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyHandler",
            "getMaxEnergyStored",
            "(Lnet/minecraft/util/EnumFacing;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "getMaxEnergyStored",
            "(Ljava/lang/Object;Ljava/lang/Object;)I"
        );

        // ============================================================
        // CoFH ENERGY RECEIVER / PROVIDER (cofh.api.energy)
        // ============================================================

        // Old: cofh.api.energy.IEnergyReceiver - receive-only interface
        // New: IEnergyStorage (canReceive() returns true, canExtract() returns false)
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyReceiver",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: cofh.api.energy.IEnergyProvider - extract-only interface
        // New: IEnergyStorage (canExtract() returns true, canReceive() returns false)
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyProvider",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: cofh.api.energy.IEnergyConnection - base interface for energy connectivity
        // Had canConnectEnergy(facing) method
        // New: IEnergyStorage (if capability is present, connection is possible)
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyConnection",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: IEnergyConnection.canConnectEnergy(facing)
        // New: Forge Energy uses capability presence instead
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyConnection",
            "canConnectEnergy",
            "(Lnet/minecraft/util/EnumFacing;)Z",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "canConnectEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;)Z"
        );

        // ============================================================
        // CoFH ENERGY STORAGE IMPLEMENTATION (cofh.api.energy)
        // ============================================================

        // Old: cofh.api.energy.EnergyStorage - reference implementation
        // New: net.minecraftforge.energy.EnergyStorage
        transformer.registerClassRedirect(
            "cofh/api/energy/EnergyStorage",
            "net/minecraftforge/energy/EnergyStorage"
        );

        // Old: new EnergyStorage(capacity)
        // New: new EnergyStorage(capacity) - constructor is compatible
        transformer.registerMethodRedirect(
            "cofh/api/energy/EnergyStorage",
            "<init>",
            "(I)V",
            "net/minecraftforge/energy/EnergyStorage",
            "<init>",
            "(I)V"
        );

        // Old: new EnergyStorage(capacity, maxTransfer)
        // New: new EnergyStorage(capacity, maxTransfer)
        transformer.registerMethodRedirect(
            "cofh/api/energy/EnergyStorage",
            "<init>",
            "(II)V",
            "net/minecraftforge/energy/EnergyStorage",
            "<init>",
            "(II)V"
        );

        // Old: new EnergyStorage(capacity, maxReceive, maxExtract)
        // New: new EnergyStorage(capacity, maxReceive, maxExtract)
        transformer.registerMethodRedirect(
            "cofh/api/energy/EnergyStorage",
            "<init>",
            "(III)V",
            "net/minecraftforge/energy/EnergyStorage",
            "<init>",
            "(III)V"
        );

        // ============================================================
        // REDSTONE FLUX API (cofh.redstoneflux.api) - 1.10/1.12 variant
        // This was a standalone library separate from cofh.api.energy
        // ============================================================

        // Old: cofh.redstoneflux.api.IEnergyReceiver
        // New: net.minecraftforge.energy.IEnergyStorage
        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyReceiver",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: cofh.redstoneflux.api.IEnergyProvider
        // New: net.minecraftforge.energy.IEnergyStorage
        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyProvider",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: cofh.redstoneflux.api.IEnergyStorage (RF's own storage interface)
        // New: net.minecraftforge.energy.IEnergyStorage (Forge Energy)
        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyStorage",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: cofh.redstoneflux.api.IEnergyConnection
        // New: net.minecraftforge.energy.IEnergyStorage
        transformer.registerClassRedirect(
            "cofh/redstoneflux/api/IEnergyConnection",
            "net/minecraftforge/energy/IEnergyStorage"
        );

        // Old: IEnergyReceiver.receiveEnergy(facing, maxReceive, simulate) (RF variant)
        // New: IEnergyStorage.receiveEnergy(maxReceive, simulate) - no facing
        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyReceiver",
            "receiveEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "receiveEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        // Old: IEnergyProvider.extractEnergy(facing, maxExtract, simulate)
        // New: IEnergyStorage.extractEnergy(maxExtract, simulate) - no facing
        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyProvider",
            "extractEnergy",
            "(Lnet/minecraft/util/EnumFacing;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "extractEnergy",
            "(Ljava/lang/Object;Ljava/lang/Object;IZ)I"
        );

        // Old: IEnergyStorage.getEnergyStored() (RF variant - no facing)
        // New: IEnergyStorage.getEnergyStored() - directly compatible
        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyStorage",
            "getEnergyStored",
            "()I",
            "net/minecraftforge/energy/IEnergyStorage",
            "getEnergyStored",
            "()I"
        );

        // Old: IEnergyStorage.getMaxEnergyStored() (RF variant)
        // New: IEnergyStorage.getMaxEnergyStored() - directly compatible
        transformer.registerMethodRedirect(
            "cofh/redstoneflux/api/IEnergyStorage",
            "getMaxEnergyStored",
            "()I",
            "net/minecraftforge/energy/IEnergyStorage",
            "getMaxEnergyStored",
            "()I"
        );

        // ============================================================
        // CoFH CAPABILITY REGISTRATION
        // ============================================================

        // Old: cofh.api.energy.IEnergyContainerItem - energy items
        // This was used by Thermal items for RF storage in ItemStacks
        // New: Forge uses IEnergyStorage capability on ItemStacks
        transformer.registerClassRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "com/retromod/shim/api/forge/embedded/ThermalShim$EnergyContainerItemCompat"
        );

        // Old: IEnergyContainerItem.receiveEnergy(stack, maxReceive, simulate)
        // New: Get IEnergyStorage capability from stack and call receiveEnergy
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "receiveEnergy",
            "(Lnet/minecraft/item/ItemStack;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemReceiveEnergy",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;IZ)I"
        );

        // Old: IEnergyContainerItem.extractEnergy(stack, maxExtract, simulate)
        // New: Get IEnergyStorage capability from stack and call extractEnergy
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "extractEnergy",
            "(Lnet/minecraft/item/ItemStack;IZ)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemExtractEnergy",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;IZ)I"
        );

        // Old: IEnergyContainerItem.getEnergyStored(stack)
        // New: Get IEnergyStorage capability from stack and call getEnergyStored
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "getEnergyStored",
            "(Lnet/minecraft/item/ItemStack;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemGetEnergyStored",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)I"
        );

        // Old: IEnergyContainerItem.getMaxEnergyStored(stack)
        // New: Get IEnergyStorage capability from stack and call getMaxEnergyStored
        transformer.registerMethodRedirect(
            "cofh/api/energy/IEnergyContainerItem",
            "getMaxEnergyStored",
            "(Lnet/minecraft/item/ItemStack;)I",
            "com/retromod/shim/api/forge/embedded/ThermalShim",
            "itemGetMaxEnergyStored",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)I"
        );

        // ============================================================
        // EnumFacing -> Direction MIGRATION
        // (Required since RF API used old EnumFacing parameter names)
        // ============================================================

        // Old: net.minecraft.util.EnumFacing (pre-1.13)
        // New: net.minecraft.core.Direction (1.17+ mapped) / net.minecraft.util.Direction (1.13-1.16)
        transformer.registerClassRedirect(
            "net/minecraft/util/EnumFacing",
            "net/minecraft/core/Direction"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.ThermalShim"
        };
    }
}
