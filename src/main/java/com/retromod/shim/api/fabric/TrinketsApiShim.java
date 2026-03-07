/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Trinkets API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Trinkets API compatibility shim.
 * 
 * Trinkets provides an accessory/equipment slots system for Fabric mods.
 * Similar to Curios on Forge.
 * 
 * API changes:
 * - v2.x -> v3.x: TrinketComponent changes
 * - v3.x -> v3.5: SlotReference changes
 * - v3.5+: Entity attribute handling changes
 */
public class TrinketsApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Trinkets API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "2.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "3.7.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // TRINKET INTERFACE CHANGES
        // ============================================================
        
        // Old: Trinket.tick(stack, slot, entity)
        // New: Trinket.tick(stack, slotReference, entity)
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/Trinket",
            "tick",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotType;Lnet/minecraft/entity/LivingEntity;)V",
            "dev/emi/trinkets/api/Trinket",
            "tick",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotReference;Lnet/minecraft/entity/LivingEntity;)V"
        );
        
        // Old: Trinket.onEquip(stack, slot, entity)
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/Trinket",
            "onEquip",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotType;Lnet/minecraft/entity/LivingEntity;)V",
            "dev/emi/trinkets/api/Trinket",
            "onEquip",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotReference;Lnet/minecraft/entity/LivingEntity;)V"
        );
        
        // Old: Trinket.onUnequip(stack, slot, entity)
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/Trinket",
            "onUnequip",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotType;Lnet/minecraft/entity/LivingEntity;)V",
            "dev/emi/trinkets/api/Trinket",
            "onUnequip",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotReference;Lnet/minecraft/entity/LivingEntity;)V"
        );
        
        // ============================================================
        // TRINKET COMPONENT CHANGES
        // ============================================================
        
        // Old: TrinketsApi.getTrinketComponent(entity).ifPresent(...)
        // API changed in how components are retrieved
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/TrinketsApi",
            "getTrinketComponent",
            "(Lnet/minecraft/entity/LivingEntity;)Ljava/util/Optional;",
            "com/retromod/shim/api/fabric/embedded/TrinketsShim",
            "getTrinketComponent",
            "(Lnet/minecraft/entity/LivingEntity;)Ljava/util/Optional;"
        );
        
        // ============================================================
        // SLOT TYPE CHANGES
        // ============================================================
        
        // Old SlotType class
        transformer.registerClassRedirect(
            "dev/emi/trinkets/api/SlotType",
            "dev/emi/trinkets/api/SlotReference"
        );
        
        // Old: getSlots method
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/TrinketComponent",
            "getSlots",
            "()Ljava/util/Map;",
            "dev/emi/trinkets/api/TrinketComponent",
            "getInventory",
            "()Ldev/emi/trinkets/api/TrinketInventory;"
        );
        
        // ============================================================
        // REGISTRATION CHANGES
        // ============================================================
        
        // Old: TrinketsApi.registerTrinket(item, trinket)
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/TrinketsApi",
            "registerTrinket",
            "(Lnet/minecraft/item/Item;Ldev/emi/trinkets/api/Trinket;)V",
            "com/retromod/shim/api/fabric/embedded/TrinketsShim",
            "registerTrinket",
            "(Lnet/minecraft/item/Item;Ldev/emi/trinkets/api/Trinket;)V"
        );
        
        // ============================================================
        // ATTRIBUTE MODIFIER CHANGES
        // ============================================================
        
        // Old: Trinket.getModifiers(stack, slot, entity, uuid)
        // New: Trinket.getModifiers(stack, slotReference, entity, uuid) or getAttributeModifiers
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/Trinket",
            "getModifiers",
            "(Lnet/minecraft/item/ItemStack;Ldev/emi/trinkets/api/SlotType;Lnet/minecraft/entity/LivingEntity;Ljava/util/UUID;)Lcom/google/common/collect/Multimap;",
            "com/retromod/shim/api/fabric/embedded/TrinketsShim",
            "getModifiers",
            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Ljava/lang/Object;Lnet/minecraft/entity/LivingEntity;Ljava/util/UUID;)Lcom/google/common/collect/Multimap;"
        );
        
        // ============================================================
        // SLOT GROUPS CHANGES
        // ============================================================
        
        // Old: SlotGroups.getSlotGroup(name)
        transformer.registerMethodRedirect(
            "dev/emi/trinkets/api/SlotGroups",
            "getSlotGroup",
            "(Ljava/lang/String;)Ldev/emi/trinkets/api/SlotGroup;",
            "com/retromod/shim/api/fabric/embedded/TrinketsShim",
            "getSlotGroup",
            "(Ljava/lang/String;)Ljava/lang/Object;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.TrinketsShim"
        };
    }
}
