/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * EMI (Emi Menu Integration) API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * EMI API compatibility shim.
 * 
 * EMI is a newer recipe viewer that's becoming popular on Fabric.
 * It's designed as a modern alternative to REI/JEI.
 */
public class EmiApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "EMI API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "0.1.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.1.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // STACK CREATION CHANGES
        // ============================================================
        
        // EmiStack.of() method changes
        transformer.registerMethodRedirect(
            "dev/emi/emi/api/stack/EmiStack",
            "of",
            "(Lnet/minecraft/item/ItemStack;)Ldev/emi/emi/api/stack/EmiStack;",
            "dev/emi/emi/api/stack/EmiStack",
            "of",
            "(Lnet/minecraft/item/ItemStack;)Ldev/emi/emi/api/stack/EmiStack;"
        );
        
        // ============================================================
        // PLUGIN INTERFACE CHANGES
        // ============================================================
        
        // EmiPlugin interface
        transformer.registerClassRedirect(
            "dev/emi/emi/api/EmiPlugin",
            "dev/emi/emi/api/EmiPlugin"
        );
        
        // Registration methods
        transformer.registerMethodRedirect(
            "dev/emi/emi/api/EmiRegistry",
            "addCategory",
            "(Ldev/emi/emi/api/recipe/EmiRecipeCategory;)V",
            "dev/emi/emi/api/EmiRegistry",
            "addCategory",
            "(Ldev/emi/emi/api/recipe/EmiRecipeCategory;)V"
        );
        
        // ============================================================
        // RECIPE CHANGES
        // ============================================================
        
        // EmiRecipe interface
        transformer.registerClassRedirect(
            "dev/emi/emi/api/recipe/EmiRecipe",
            "dev/emi/emi/api/recipe/EmiRecipe"
        );
        
        // BasicEmiRecipe for simple recipes
        transformer.registerClassRedirect(
            "dev/emi/emi/api/recipe/BasicEmiRecipe",
            "dev/emi/emi/api/recipe/BasicEmiRecipe"
        );
        
        // ============================================================
        // WIDGET CHANGES
        // ============================================================
        
        // Widget creation
        transformer.registerMethodRedirect(
            "dev/emi/emi/api/widget/WidgetHolder",
            "addSlot",
            "(Ldev/emi/emi/api/stack/EmiIngredient;II)Ldev/emi/emi/api/widget/SlotWidget;",
            "dev/emi/emi/api/widget/WidgetHolder",
            "addSlot",
            "(Ldev/emi/emi/api/stack/EmiIngredient;II)Ldev/emi/emi/api/widget/SlotWidget;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
