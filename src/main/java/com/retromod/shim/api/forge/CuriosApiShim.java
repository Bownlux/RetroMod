/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Curios API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Curios API compatibility shim.
 * 
 * Curios provides accessory slots for Forge/NeoForge mods.
 * Equivalent to Trinkets on Fabric.
 * 
 * API changes:
 * - v1.x -> v2.x: ICurio interface changes
 * - v2.x -> v3.x: Slot context changes
 * - v3.x -> v4.x: Registration API changes
 * - v4.x -> v5.x: NeoForge adaptation, capability changes
 */
public class CuriosApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Curios API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "5.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge"; // Also applies to neoforge
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE CHANGES
        // ============================================================
        
        // Some packages moved between versions
        transformer.registerClassRedirect(
            "top/theillusivec4/curios/api/CuriosApi",
            "top/theillusivec4/curios/api/CuriosApi"
        );
        
        // ============================================================
        // ICURIO INTERFACE CHANGES
        // ============================================================
        
        // Old: ICurio.onEquip(slot, entity)
        // New: ICurio.onEquip(SlotContext, previousStack)
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio",
            "onEquip",
            "(Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)V",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "onEquip",
            "(Ljava/lang/Object;Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)V"
        );
        
        // Old: ICurio.onUnequip(slot, entity)
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio",
            "onUnequip",
            "(Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)V",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "onUnequip",
            "(Ljava/lang/Object;Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)V"
        );
        
        // Old: ICurio.curioTick(slot, entity)
        // New: ICurio.curioTick(SlotContext)
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio",
            "curioTick",
            "(Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)V",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "curioTick",
            "(Ljava/lang/Object;Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)V"
        );
        
        // ============================================================
        // SLOT CONTEXT CHANGES
        // ============================================================
        
        // Old direct slot string -> SlotContext object
        transformer.registerClassRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio$DropRule",
            "top/theillusivec4/curios/api/type/capability/ICurio$DropRule"
        );
        
        // ============================================================
        // CAPABILITY ACCESS CHANGES
        // ============================================================
        
        // Old: CuriosApi.getCuriosHelper().getCuriosHandler(entity)
        // New: CuriosApi.getCuriosInventory(entity)
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/CuriosApi",
            "getCuriosHelper",
            "()Ltop/theillusivec4/curios/api/type/util/ICuriosHelper;",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "getCuriosHelper",
            "()Ljava/lang/Object;"
        );
        
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/util/ICuriosHelper",
            "getCuriosHandler",
            "(Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraftforge/common/util/LazyOptional;",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "getCuriosHandler",
            "(Lnet/minecraft/world/entity/LivingEntity;)Ljava/util/Optional;"
        );
        
        // ============================================================
        // SLOT TYPE REGISTRATION CHANGES
        // ============================================================
        
        // Old: SlotTypePreset
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/SlotTypePreset",
            "getIdentifier",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "getSlotIdentifier",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );
        
        // Registration changes
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/CuriosApi",
            "enqueueSlotType",
            "(Ljava/lang/String;Ltop/theillusivec4/curios/api/type/ISlotType;)V",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "registerSlotType",
            "(Ljava/lang/String;Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // ATTRIBUTE MODIFIER CHANGES
        // ============================================================
        
        // Old: ICurio.getAttributeModifiers(slot)
        // New: ICurio.getAttributeModifiers(SlotContext, uuid)
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio",
            "getAttributeModifiers",
            "(Ljava/lang/String;)Lcom/google/common/collect/Multimap;",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "getAttributeModifiers",
            "(Ljava/lang/Object;Ljava/lang/String;)Lcom/google/common/collect/Multimap;"
        );
        
        // ============================================================
        // RENDER CHANGES
        // ============================================================
        
        // Old: ICurio.canRender(slot, entity)
        // New: ICurio.canRender(SlotContext)
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio",
            "canRender",
            "(Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)Z",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "canRender",
            "(Ljava/lang/Object;Ljava/lang/String;Lnet/minecraft/world/entity/LivingEntity;)Z"
        );
        
        // Old render signature
        transformer.registerMethodRedirect(
            "top/theillusivec4/curios/api/type/capability/ICurio",
            "render",
            "(Ljava/lang/String;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V",
            "com/retromod/shim/api/forge/embedded/CuriosShim",
            "render",
            "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;ILnet/minecraft/world/entity/LivingEntity;FFFFFF)V"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.CuriosShim"
        };
    }
}
