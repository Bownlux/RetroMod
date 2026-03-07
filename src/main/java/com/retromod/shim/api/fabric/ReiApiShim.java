/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * REI (Roughly Enough Items) API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * REI API compatibility shim.
 * 
 * REI is the most popular recipe viewer for Fabric. Major API changes:
 * - v3.x -> v4.x: EntryStack changes
 * - v4.x -> v5.x: Plugin system overhaul
 * - v5.x -> v6.x: Display system changes
 * - v6.x -> v9.x: Registration API changes
 * - v9.x -> v12.x+: Further plugin changes
 */
public class ReiApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "REI API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "3.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "12.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE RELOCATIONS
        // ============================================================
        
        // Very old package
        transformer.registerClassRedirect(
            "me/shedaniel/rei/api/RecipeHelper",
            "me/shedaniel/rei/api/client/registry/display/DisplayRegistry"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/rei/api/EntryRegistry",
            "me/shedaniel/rei/api/client/registry/entry/EntryRegistry"
        );
        
        // ============================================================
        // ENTRY STACK CHANGES
        // ============================================================
        
        // Old: EntryStack.create(itemStack)
        // New: EntryStacks.of(itemStack)
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/common/entry/EntryStack",
            "create",
            "(Lnet/minecraft/item/ItemStack;)Lme/shedaniel/rei/api/common/entry/EntryStack;",
            "me/shedaniel/rei/api/common/util/EntryStacks",
            "of",
            "(Lnet/minecraft/item/ItemStack;)Lme/shedaniel/rei/api/common/entry/EntryStack;"
        );
        
        // Old: EntryStack.create(fluid)
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/common/entry/EntryStack",
            "create",
            "(Lnet/minecraft/fluid/Fluid;)Lme/shedaniel/rei/api/common/entry/EntryStack;",
            "me/shedaniel/rei/api/common/util/EntryStacks",
            "of",
            "(Lnet/minecraft/fluid/Fluid;)Lme/shedaniel/rei/api/common/entry/EntryStack;"
        );
        
        // ============================================================
        // PLUGIN INTERFACE CHANGES
        // ============================================================
        
        // Old: REIPlugin (single interface)
        // New: Split into REIClientPlugin, REIServerPlugin
        transformer.registerClassRedirect(
            "me/shedaniel/rei/api/REIPlugin",
            "me/shedaniel/rei/api/client/plugins/REIClientPlugin"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/rei/api/REIPluginV0",
            "me/shedaniel/rei/api/client/plugins/REIClientPlugin"
        );
        
        // ============================================================
        // REGISTRATION METHODS CHANGES
        // ============================================================
        
        // Old: registerRecipeDisplays(RecipeHelper)
        // New: registerDisplays(DisplayRegistry)
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/client/plugins/REIClientPlugin",
            "registerRecipeDisplays",
            "(Lme/shedaniel/rei/api/RecipeHelper;)V",
            "me/shedaniel/rei/api/client/plugins/REIClientPlugin",
            "registerDisplays",
            "(Lme/shedaniel/rei/api/client/registry/display/DisplayRegistry;)V"
        );
        
        // Old: registerEntries(EntryRegistry)
        // New: registerEntries(EntryRegistry) - same name but different package
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/client/plugins/REIClientPlugin",
            "registerEntries",
            "(Lme/shedaniel/rei/api/EntryRegistry;)V",
            "me/shedaniel/rei/api/client/plugins/REIClientPlugin",
            "registerEntries",
            "(Lme/shedaniel/rei/api/client/registry/entry/EntryRegistry;)V"
        );
        
        // ============================================================
        // DISPLAY CHANGES
        // ============================================================
        
        // Old: BasicDisplay
        // New: Different constructors
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/common/display/basic/BasicDisplay",
            "<init>",
            "(Ljava/util/List;Ljava/util/List;)V",
            "com/retromod/shim/api/fabric/embedded/ReiShim",
            "createBasicDisplay",
            "(Ljava/util/List;Ljava/util/List;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // CATEGORY CHANGES
        // ============================================================
        
        // Old: DisplayCategory methods
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/client/registry/category/CategoryRegistry",
            "add",
            "(Lme/shedaniel/rei/api/client/registry/display/DisplayCategory;)V",
            "com/retromod/shim/api/fabric/embedded/ReiShim",
            "addCategory",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // WIDGET CHANGES
        // ============================================================
        
        // Old: Widgets.createRecipeBase(bounds)
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/client/gui/Widgets",
            "createRecipeBase",
            "(Lme/shedaniel/math/Rectangle;)Lme/shedaniel/rei/api/client/gui/widgets/Widget;",
            "com/retromod/shim/api/fabric/embedded/ReiShim",
            "createRecipeBase",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // Slot widget changes
        transformer.registerMethodRedirect(
            "me/shedaniel/rei/api/client/gui/Widgets",
            "createSlot",
            "(Lme/shedaniel/math/Point;)Lme/shedaniel/rei/api/client/gui/widgets/Slot;",
            "com/retromod/shim/api/fabric/embedded/ReiShim",
            "createSlot",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.ReiShim"
        };
    }
}
