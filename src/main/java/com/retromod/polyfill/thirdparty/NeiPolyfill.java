/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.thirdparty;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the removed NEI (Not Enough Items) API (1.4.7-1.12.2).
 * NEI was replaced by JEI (Just Enough Items).
 */
public class NeiPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "NEI (Not Enough Items) API";
    }

    @Override
    public String getCategory() {
        return "thirdparty";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "codechicken/nei/api/API",
            "codechicken/nei/recipe/IRecipeHandler",
            "codechicken/nei/recipe/TemplateRecipeHandler",
            "codechicken/nei/ItemList"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "codechicken.nei.api.API",
            "codechicken.nei.recipe.IRecipeHandler",
            "codechicken.nei.recipe.TemplateRecipeHandler",
            "codechicken.nei.ItemList"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
