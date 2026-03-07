/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual NeoForge changes documented at:
 * https://neoforged.net/news/21.9release/
 * https://neoforged.net/news/21.9-transfer-rework/
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for NeoForge mods built for 1.21.8 to run on 1.21.9+.
 * 
 * Major breaking changes addressed:
 * - MASSIVE Transfer API rework (IItemHandler, IFluidHandler, IEnergyStorage)
 * - FML refactored (class loading, state access)
 * - Key mapping categories now records
 * - Level rendering events migrated to extraction/render separation
 * - RenderHighlightEvent removed
 */
public class NeoForge_1_21_8_to_1_21_9 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "NeoForge 1.21.8 to 1.21.9";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.8";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.9";
    }
    
    @Override
    public String getModLoaderType() {
        return "neoforge";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        
        // ============================================================
        // TRANSFER API REWORK - HUGE CHANGE
        // IItemHandler -> ResourceHandler<ItemResource>
        // IFluidHandler -> ResourceHandler<FluidResource>  
        // IEnergyStorage -> EnergyHandler
        // ============================================================
        
        // The old interfaces are deprecated but not removed in 21.9
        // Wrappers are provided: IItemHandler.of(handler)
        
        // For mods using IItemHandler directly, provide compatibility
        transformer.registerMethodRedirect(
            "net/neoforged/neoforge/items/IItemHandler", "getSlots", "()I",
            "com/retromod/shim/neoforge/embedded/IItemHandlerShim", "getSlots",
            "(Ljava/lang/Object;)I"
        );
        
        transformer.registerMethodRedirect(
            "net/neoforged/neoforge/items/IItemHandler", "getStackInSlot",
            "(I)Lnet/minecraft/world/item/ItemStack;",
            "com/retromod/shim/neoforge/embedded/IItemHandlerShim", "getStackInSlot",
            "(Ljava/lang/Object;I)Lnet/minecraft/world/item/ItemStack;"
        );
        
        transformer.registerMethodRedirect(
            "net/neoforged/neoforge/items/IItemHandler", "insertItem",
            "(ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;",
            "com/retromod/shim/neoforge/embedded/IItemHandlerShim", "insertItem",
            "(Ljava/lang/Object;ILnet/minecraft/world/item/ItemStack;Z)Lnet/minecraft/world/item/ItemStack;"
        );
        
        transformer.registerMethodRedirect(
            "net/neoforged/neoforge/items/IItemHandler", "extractItem",
            "(IIZ)Lnet/minecraft/world/item/ItemStack;",
            "com/retromod/shim/neoforge/embedded/IItemHandlerShim", "extractItem",
            "(Ljava/lang/Object;IIZ)Lnet/minecraft/world/item/ItemStack;"
        );
        
        // ItemStackHandler -> ItemAccessItemHandler
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/items/ItemStackHandler",
            "com/retromod/shim/neoforge/embedded/ItemStackHandlerShim"
        );
        
        // ComponentItemHandler -> ItemAccessItemHandler
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/items/ComponentItemHandler",
            "com/retromod/shim/neoforge/embedded/ComponentItemHandlerShim"
        );
        
        // ============================================================
        // FML STATE ACCESS
        // FMLLoader methods changed
        // ============================================================
        
        // FML state now accessed via FMLLoader.getCurrent()
        transformer.registerMethodRedirect(
            "net/neoforged/fml/loading/FMLLoader", "getLoadingModList",
            "()Lnet/neoforged/fml/loading/LoadingModList;",
            "com/retromod/shim/neoforge/embedded/FMLLoaderShim", "getLoadingModList",
            "()Ljava/lang/Object;"
        );
        
        // ============================================================
        // KEY MAPPING CHANGES
        // String category -> KeyMapping.Category record
        // ============================================================
        
        // Old: new KeyMapping(name, type, key, categoryString)
        // New: new KeyMapping(name, type, key, categoryRecord)
        
        // The old constructor is removed - must create Category first
        transformer.registerMethodRedirect(
            "net/minecraft/client/KeyMapping", "<init>",
            "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            "com/retromod/shim/neoforge/embedded/KeyMappingShim", "create",
            "(Ljava/lang/String;Ljava/lang/Object;ILjava/lang/String;)Lnet/minecraft/client/KeyMapping;"
        );
        
        // ============================================================
        // RENDER HIGHLIGHT EVENT REMOVED
        // -> ExtractBlockOutlineRenderStateEvent
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/client/event/RenderHighlightEvent",
            "com/retromod/shim/neoforge/embedded/RenderHighlightEventShim"
        );
        
        transformer.registerClassRedirect(
            "net/neoforged/neoforge/client/event/RenderHighlightEvent$Block",
            "com/retromod/shim/neoforge/embedded/RenderHighlightEventShim$Block"
        );
        
        // ============================================================
        // ENTITY.getWorld() -> ENTITY.getEntityWorld()
        // Same change as Fabric - Minecraft core change
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "getWorld", "()Lnet/minecraft/world/level/Level;",
            "net/minecraft/world/entity/Entity", "getEntityWorld", "()Lnet/minecraft/world/level/Level;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/LivingEntity", "getWorld", "()Lnet/minecraft/world/level/Level;",
            "net/minecraft/world/entity/LivingEntity", "getEntityWorld", "()Lnet/minecraft/world/level/Level;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/player/Player", "getWorld", "()Lnet/minecraft/world/level/Level;",
            "net/minecraft/world/entity/player/Player", "getEntityWorld", "()Lnet/minecraft/world/level/Level;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.neoforge.embedded.IItemHandlerShim",
            "com.retromod.shim.neoforge.embedded.ItemStackHandlerShim",
            "com.retromod.shim.neoforge.embedded.ComponentItemHandlerShim",
            "com.retromod.shim.neoforge.embedded.FMLLoaderShim",
            "com.retromod.shim.neoforge.embedded.KeyMappingShim",
            "com.retromod.shim.neoforge.embedded.RenderHighlightEventShim"
        };
    }
}
