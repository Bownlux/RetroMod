/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Forge Capabilities System Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge Capabilities system compatibility shim.
 * 
 * The Capability system is one of the most significant API differences
 * between Forge and NeoForge. NeoForge completely rewrote capabilities.
 * 
 * Forge (1.20.x and earlier):
 * - LazyOptional<T> for capability storage
 * - ICapabilityProvider interface
 * - @CapabilityInject annotation
 * - CapabilityManager.INSTANCE.register()
 * 
 * NeoForge (1.21+):
 * - Direct Optional<T> or nullable returns
 * - New BlockCapability, EntityCapability, ItemCapability
 * - Different registration system
 * - Data attachments instead of capabilities for some uses
 */
public class ForgeCapabilitiesShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge Capabilities Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.20.1";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.11";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // LAZY OPTIONAL CHANGES
        // ============================================================
        
        // Forge: LazyOptional<T>
        // NeoForge: Regular Optional<T> or direct access
        transformer.registerClassRedirect(
            "net/minecraftforge/common/util/LazyOptional",
            "com/retromod/shim/api/forge/embedded/LazyOptionalShim"
        );
        
        // LazyOptional.of() -> wrapper
        transformer.registerMethodRedirect(
            "net/minecraftforge/common/util/LazyOptional",
            "of",
            "(Lnet/minecraftforge/common/util/NonNullSupplier;)Lnet/minecraftforge/common/util/LazyOptional;",
            "com/retromod/shim/api/forge/embedded/LazyOptionalShim",
            "of",
            "(Ljava/util/function/Supplier;)Lcom/retromod/shim/api/forge/embedded/LazyOptionalShim;"
        );
        
        // LazyOptional.empty() -> Optional.empty() wrapper
        transformer.registerMethodRedirect(
            "net/minecraftforge/common/util/LazyOptional",
            "empty",
            "()Lnet/minecraftforge/common/util/LazyOptional;",
            "com/retromod/shim/api/forge/embedded/LazyOptionalShim",
            "empty",
            "()Lcom/retromod/shim/api/forge/embedded/LazyOptionalShim;"
        );
        
        // ============================================================
        // CAPABILITY PROVIDER CHANGES
        // ============================================================
        
        // Old: ICapabilityProvider
        // New: BlockEntity, Entity, ItemStack have different capability APIs
        transformer.registerClassRedirect(
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "com/retromod/shim/api/forge/embedded/CapabilityProviderShim"
        );
        
        // Old: getCapability(Capability<T> cap, Direction side)
        transformer.registerMethodRedirect(
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "getCapability",
            "(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)Lnet/minecraftforge/common/util/LazyOptional;",
            "com/retromod/shim/api/forge/embedded/CapabilityProviderShim",
            "getCapability",
            "(Ljava/lang/Object;Ljava/lang/Object;Lnet/minecraft/core/Direction;)Lcom/retromod/shim/api/forge/embedded/LazyOptionalShim;"
        );
        
        // ============================================================
        // CAPABILITY REGISTRATION CHANGES
        // ============================================================
        
        // Old: @CapabilityInject annotation
        // This is handled at class transformation level
        
        // Old: CapabilityManager.INSTANCE.register(class, storage, factory)
        transformer.registerMethodRedirect(
            "net/minecraftforge/common/capabilities/CapabilityManager",
            "register",
            "(Ljava/lang/Class;Lnet/minecraftforge/common/capabilities/Capability$IStorage;Ljava/util/concurrent/Callable;)V",
            "com/retromod/shim/api/forge/embedded/CapabilityManagerShim",
            "register",
            "(Ljava/lang/Class;Ljava/lang/Object;Ljava/util/concurrent/Callable;)V"
        );
        
        // ============================================================
        // FORGE CAPABILITY CLASSES -> NEOFORGE
        // ============================================================
        
        // ForgeCapabilities.ITEM_HANDLER
        transformer.registerFieldRedirect(
            "net/minecraftforge/common/capabilities/ForgeCapabilities",
            "ITEM_HANDLER",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/ForgeCapabilitiesShim",
            "getItemHandler",
            "()Ljava/lang/Object;"
        );
        
        // ForgeCapabilities.FLUID_HANDLER
        transformer.registerFieldRedirect(
            "net/minecraftforge/common/capabilities/ForgeCapabilities",
            "FLUID_HANDLER",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/ForgeCapabilitiesShim",
            "getFluidHandler",
            "()Ljava/lang/Object;"
        );
        
        // ForgeCapabilities.ENERGY
        transformer.registerFieldRedirect(
            "net/minecraftforge/common/capabilities/ForgeCapabilities",
            "ENERGY",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/ForgeCapabilitiesShim",
            "getEnergy",
            "()Ljava/lang/Object;"
        );
        
        // ============================================================
        // ITEM HANDLER CHANGES
        // ============================================================
        
        // IItemHandler stays mostly the same but access changes
        transformer.registerClassRedirect(
            "net/minecraftforge/items/IItemHandler",
            "net/neoforged/neoforge/items/IItemHandler"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/items/ItemStackHandler",
            "net/neoforged/neoforge/items/ItemStackHandler"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/items/SlotItemHandler",
            "net/neoforged/neoforge/items/SlotItemHandler"
        );
        
        // ============================================================
        // FLUID HANDLER CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fluids/capability/IFluidHandler",
            "net/neoforged/neoforge/fluids/capability/IFluidHandler"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fluids/FluidStack",
            "net/neoforged/neoforge/fluids/FluidStack"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fluids/capability/templates/FluidTank",
            "net/neoforged/neoforge/fluids/capability/templates/FluidTank"
        );
        
        // ============================================================
        // ENERGY STORAGE CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/energy/IEnergyStorage",
            "net/neoforged/neoforge/energy/IEnergyStorage"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/energy/EnergyStorage",
            "net/neoforged/neoforge/energy/EnergyStorage"
        );
        
        // ============================================================
        // ATTACH CAPABILITY EVENT CHANGES
        // ============================================================
        
        // Old: AttachCapabilitiesEvent<T>
        // New: RegisterCapabilitiesEvent or data attachments
        transformer.registerClassRedirect(
            "net/minecraftforge/event/AttachCapabilitiesEvent",
            "com/retromod/shim/api/forge/embedded/AttachCapabilitiesEventShim"
        );
        
        // Old: event.addCapability(id, provider)
        transformer.registerMethodRedirect(
            "net/minecraftforge/event/AttachCapabilitiesEvent",
            "addCapability",
            "(Lnet/minecraft/resources/ResourceLocation;Lnet/minecraftforge/common/capabilities/ICapabilityProvider;)V",
            "com/retromod/shim/api/forge/embedded/AttachCapabilitiesEventShim",
            "addCapability",
            "(Ljava/lang/Object;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.LazyOptionalShim",
            "com.retromod.shim.api.forge.embedded.CapabilityProviderShim",
            "com.retromod.shim.api.forge.embedded.CapabilityManagerShim",
            "com.retromod.shim.api.forge.embedded.ForgeCapabilitiesShim",
            "com.retromod.shim.api.forge.embedded.AttachCapabilitiesEventShim"
        };
    }
}
