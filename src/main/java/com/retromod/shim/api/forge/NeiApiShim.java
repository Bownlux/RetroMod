/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Not Enough Items (NEI) API -> JEI API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Not Enough Items (NEI) API compatibility shim.
 *
 * NEI by ChickenBones (CodeChickenLib) was the dominant recipe viewer
 * for Forge 1.4.7 through 1.12.2. It was never updated past 1.12.2
 * and was fully replaced by JEI (Just Enough Items) by mezz.
 *
 * This shim redirects NEI API calls to their JEI equivalents so that
 * addon mods written for NEI can register recipes through JEI instead.
 *
 * Key mappings:
 * - codechicken.nei.api.API -> mezz.jei.api plugin system
 * - IRecipeHandler -> JEI IRecipeCategory
 * - TemplateRecipeHandler -> JEI recipe category with layout
 * - NEIGuiConfig -> JEI config screen
 * - ItemList/ItemFilter -> JEI ingredient filter
 */
public class NeiApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "NEI -> JEI API Compatibility";
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
        // CORE API CLASS REDIRECT
        // ============================================================

        // Old: codechicken.nei.api.API - main registration entry point
        // NEI used a static API class to register recipe handlers, item info, etc.
        // New: JEI uses IModPlugin implementations discovered via @JeiPlugin annotation
        transformer.registerClassRedirect(
            "codechicken/nei/api/API",
            "com/retromod/shim/api/forge/embedded/NeiShim"
        );

        // Old: API.registerRecipeHandler(handler) - register a recipe category
        // New: JEI IRecipeRegistration.addRecipes() via plugin
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "registerRecipeHandler",
            "(Lcodechicken/nei/recipe/ICraftingHandler;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "registerRecipeHandler",
            "(Ljava/lang/Object;)V"
        );

        // Old: API.registerUsageHandler(handler) - register usage lookup
        // New: JEI handles usages automatically via recipe categories
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "registerUsageHandler",
            "(Lcodechicken/nei/recipe/ICraftingHandler;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "registerUsageHandler",
            "(Ljava/lang/Object;)V"
        );

        // Old: API.hideItem(stack) - hide item from NEI panel
        // New: JEI IIngredientManager.removeIngredientsAtRuntime()
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "hideItem",
            "(Lnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "hideItem",
            "(Lnet/minecraft/world/item/ItemStack;)V"
        );

        // Old: API.addItemListEntry(stack) - add item to NEI panel
        // New: JEI IIngredientManager.addIngredientsAtRuntime()
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "addItemListEntry",
            "(Lnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "addItemListEntry",
            "(Lnet/minecraft/world/item/ItemStack;)V"
        );

        // Old: API.setGuiOffset(guiClass, x, y) - adjust recipe GUI position
        // New: JEI handles GUI positioning internally
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "setGuiOffset",
            "(Ljava/lang/Class;II)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "setGuiOffset",
            "(Ljava/lang/Class;II)V"
        );

        // ============================================================
        // RECIPE HANDLER INTERFACE REDIRECTS
        // ============================================================

        // Old: codechicken.nei.recipe.IRecipeHandler - base recipe handler interface
        // New: mezz.jei.api.recipe.category.IRecipeCategory
        transformer.registerClassRedirect(
            "codechicken/nei/recipe/IRecipeHandler",
            "com/retromod/shim/api/forge/embedded/NeiShim$RecipeHandlerCompat"
        );

        // Old: codechicken.nei.recipe.ICraftingHandler - crafting-specific handler
        // New: JEI IRecipeCategory (JEI unifies all recipe types)
        transformer.registerClassRedirect(
            "codechicken/nei/recipe/ICraftingHandler",
            "com/retromod/shim/api/forge/embedded/NeiShim$RecipeHandlerCompat"
        );

        // Old: IRecipeHandler.getRecipeName() - display name for recipe tab
        // New: IRecipeCategory.getTitle()
        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/IRecipeHandler",
            "getRecipeName",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "getRecipeName",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        // Old: IRecipeHandler.numRecipes() - number of loaded recipes
        // New: mapped through JEI recipe manager
        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/IRecipeHandler",
            "numRecipes",
            "()I",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "numRecipes",
            "(Ljava/lang/Object;)I"
        );

        // ============================================================
        // TEMPLATE RECIPE HANDLER -> JEI CATEGORY
        // ============================================================

        // Old: codechicken.nei.recipe.TemplateRecipeHandler - base class most NEI addons extend
        // This was the main way mods added custom recipe displays
        // New: JEI IRecipeCategory implementation
        transformer.registerClassRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "com/retromod/shim/api/forge/embedded/NeiShim$TemplateRecipeHandlerCompat"
        );

        // Old: TemplateRecipeHandler.loadCraftingRecipes(outputId, results)
        // Used to populate recipe list when viewing an item's recipes
        // New: JEI populates via IRecipeRegistration
        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "loadCraftingRecipes",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "loadCraftingRecipes",
            "(Ljava/lang/Object;Ljava/lang/String;[Ljava/lang/Object;)V"
        );

        // Old: TemplateRecipeHandler.loadUsageRecipes(ingredient)
        // Used to populate list when viewing an item's usages
        // New: JEI handles usage lookups automatically from registered recipes
        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "loadUsageRecipes",
            "(Lnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "loadUsageRecipes",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)V"
        );

        // Old: TemplateRecipeHandler.drawBackground(guiContainerIndex)
        // New: IRecipeCategory.draw(recipe, recipeSlotsView, guiGraphics, mouseX, mouseY)
        transformer.registerMethodRedirect(
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "drawBackground",
            "(I)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "drawBackground",
            "(Ljava/lang/Object;I)V"
        );

        // ============================================================
        // NEI ITEM INFO / DESCRIPTION PAGES
        // ============================================================

        // Old: API.addDescription(stack, description...) - item info page in NEI
        // New: JEI IRecipeRegistration.addItemStackInfo(stack, components)
        transformer.registerMethodRedirect(
            "codechicken/nei/api/API",
            "addDescription",
            "(Lnet/minecraft/item/ItemStack;[Ljava/lang/String;)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "addDescription",
            "(Lnet/minecraft/world/item/ItemStack;[Ljava/lang/String;)V"
        );

        // ============================================================
        // ITEM FILTER / SEARCH
        // ============================================================

        // Old: codechicken.nei.ItemList - the panel of all items
        // New: JEI IIngredientManager
        transformer.registerClassRedirect(
            "codechicken/nei/ItemList",
            "com/retromod/shim/api/forge/embedded/NeiShim$ItemListCompat"
        );

        // Old: codechicken.nei.SearchField - the search bar
        // New: JEI IFilterTextSource / IIngredientFilter
        transformer.registerClassRedirect(
            "codechicken/nei/SearchField",
            "com/retromod/shim/api/forge/embedded/NeiShim$SearchFieldCompat"
        );

        // ============================================================
        // GUI CONTAINER OVERLAY
        // ============================================================

        // Old: codechicken.nei.api.INEIGuiAdapter - adapt container GUIs for NEI overlay
        // New: JEI IGuiContainerHandler
        transformer.registerClassRedirect(
            "codechicken/nei/api/INEIGuiAdapter",
            "com/retromod/shim/api/forge/embedded/NeiShim$GuiAdapterCompat"
        );

        // ============================================================
        // DRAWING HELPER REDIRECTS
        // ============================================================

        // Old: codechicken.lib.gui.GuiDraw - CodeChickenLib GUI drawing utilities
        // New: net.minecraft.client.gui.GuiGraphics (vanilla 1.20+)
        transformer.registerClassRedirect(
            "codechicken/lib/gui/GuiDraw",
            "com/retromod/shim/api/forge/embedded/NeiShim$GuiDrawCompat"
        );

        // Old: GuiDraw.drawTexturedModalRect(x, y, u, v, w, h)
        // New: GuiGraphics.blit(texture, x, y, u, v, w, h)
        transformer.registerMethodRedirect(
            "codechicken/lib/gui/GuiDraw",
            "drawTexturedModalRect",
            "(IIIIII)V",
            "com/retromod/shim/api/forge/embedded/NeiShim",
            "drawTexturedModalRect",
            "(IIIIII)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.NeiShim"
        };
    }
}
