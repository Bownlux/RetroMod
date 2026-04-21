/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * JEI (Just Enough Items) API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * JEI API compatibility shim.
 * 
 * JEI is the most popular recipe viewer for Forge/NeoForge.
 * API changes across versions:
 * - v7.x -> v8.x: Major API restructure
 * - v8.x -> v9.x: Plugin registration changes
 * - v9.x -> v10.x: Ingredient type changes
 * - v10.x -> v11.x: Recipe manager changes
 * - v11.x -> v15.x+: Further plugin changes, NeoForge support
 */
public class JeiApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "JEI API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "7.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "15.0.0";
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
        
        // Old: mezz.jei.api
        // Packages stayed mostly the same but some classes moved
        
        // ============================================================
        // PLUGIN INTERFACE CHANGES
        // ============================================================
        
        // Old: IModPlugin methods
        // v7: registerItemSubtypes, registerRecipes, registerRecipeTransferHandlers
        // v9+: consolidated into different methods
        
        transformer.registerMethodRedirect(
            "mezz/jei/api/IModPlugin",
            "registerItemSubtypes",
            "(Lmezz/jei/api/registration/ISubtypeRegistration;)V",
            "mezz/jei/api/IModPlugin",
            "registerItemSubtypes",
            "(Lmezz/jei/api/registration/ISubtypeRegistration;)V"
        );
        
        // ============================================================
        // INGREDIENT TYPE CHANGES
        // ============================================================
        
        // Old: IIngredientType<T> generics handling changed
        transformer.registerMethodRedirect(
            "mezz/jei/api/ingredients/VanillaTypes",
            "ITEM",
            "Lmezz/jei/api/ingredients/IIngredientType;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getItemType",
            "()Lmezz/jei/api/ingredients/IIngredientType;"
        );
        
        transformer.registerMethodRedirect(
            "mezz/jei/api/ingredients/VanillaTypes",
            "FLUID",
            "Lmezz/jei/api/ingredients/IIngredientType;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getFluidType",
            "()Lmezz/jei/api/ingredients/IIngredientType;"
        );
        
        // ============================================================
        // RECIPE CATEGORY CHANGES
        // ============================================================
        
        // Old: IRecipeCategory.getUid() -> getRecipeType()
        transformer.registerMethodRedirect(
            "mezz/jei/api/recipe/category/IRecipeCategory",
            "getUid",
            "()Lnet/minecraft/resources/ResourceLocation;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getCategoryUid",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"
        );
        
        // Old: IRecipeCategory.getRecipeClass()
        transformer.registerMethodRedirect(
            "mezz/jei/api/recipe/category/IRecipeCategory",
            "getRecipeClass",
            "()Ljava/lang/Class;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getRecipeClass",
            "(Ljava/lang/Object;)Ljava/lang/Class;"
        );
        
        // ============================================================
        // RECIPE REGISTRATION CHANGES
        // ============================================================
        
        // Old: IRecipeRegistration.addRecipes(list, uid)
        // New: IRecipeRegistration.addRecipes(recipeType, list)
        transformer.registerMethodRedirect(
            "mezz/jei/api/registration/IRecipeRegistration",
            "addRecipes",
            "(Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "addRecipesLegacy",
            "(Ljava/lang/Object;Ljava/util/Collection;Lnet/minecraft/resources/ResourceLocation;)V"
        );
        
        // ============================================================
        // GUI HANDLER CHANGES
        // ============================================================
        
        // Old: IGuiHandler -> IGuiContainerHandler (renamed)
        transformer.registerClassRedirect(
            "mezz/jei/api/gui/IGuiHandler",
            "mezz/jei/api/gui/handlers/IGuiContainerHandler"
        );
        
        // ============================================================
        // DRAWING CHANGES
        // ============================================================
        
        // Old: IRecipeCategory.draw(recipe, matrixStack, ...)
        // New: IRecipeCategory.draw(recipe, recipeSlotsView, guiGraphics, ...)
        transformer.registerMethodRedirect(
            "mezz/jei/api/recipe/category/IRecipeCategory",
            "draw",
            "(Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;DD)V",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "drawLegacy",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;DD)V"
        );
        
        // PoseStack -> GuiGraphics changes
        transformer.registerClassRedirect(
            "com/mojang/blaze3d/vertex/PoseStack",
            "net/minecraft/client/gui/GuiGraphics"
        );
        
        // ============================================================
        // RECIPE SLOT CHANGES
        // ============================================================
        
        // Old: IRecipeLayout.getItemStacks()
        // New: Different slot system
        transformer.registerMethodRedirect(
            "mezz/jei/api/gui/IRecipeLayout",
            "getItemStacks",
            "()Lmezz/jei/api/gui/ingredient/IGuiItemStackGroup;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getItemStacks",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // FOCUS CHANGES
        // ============================================================
        
        // Old: IFocus<T> interface changes
        transformer.registerMethodRedirect(
            "mezz/jei/api/recipe/IFocus",
            "getValue",
            "()Ljava/lang/Object;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getFocusValue",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        transformer.registerMethodRedirect(
            "mezz/jei/api/recipe/IFocus",
            "getMode",
            "()Lmezz/jei/api/recipe/IFocus$Mode;",
            "com/retromod/shim/api/forge/embedded/JeiShim",
            "getFocusMode",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.JeiShim"
        };
    }
}
