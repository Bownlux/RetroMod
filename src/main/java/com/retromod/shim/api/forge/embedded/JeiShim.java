package com.retromod.shim.api.forge.embedded;
import java.lang.reflect.Method;
import java.util.Collection;
public class JeiShim {
    public static Object getItemType() {
        try { return Class.forName("mezz.jei.api.ingredients.VanillaTypes").getField("ITEM_STACK").get(null);
        } catch (Exception e) {
            try { return Class.forName("mezz.jei.api.constants.VanillaTypes").getField("ITEM_STACK").get(null);
            } catch (Exception e2) { return null; }
        }
    }
    public static Object getFluidType() {
        try { return Class.forName("mezz.jei.api.ingredients.VanillaTypes").getField("FLUID_STACK").get(null);
        } catch (Exception e) {
            try { return Class.forName("mezz.jei.api.neoforge.NeoForgeTypes").getField("FLUID_STACK").get(null);
            } catch (Exception e2) { return null; }
        }
    }
    public static Object getCategoryUid(Object category) {
        try {
            Method getRecipeType = category.getClass().getMethod("getRecipeType");
            Object recipeType = getRecipeType.invoke(category);
            return recipeType.getClass().getMethod("getUid").invoke(recipeType);
        } catch (Exception e) {
            try { return category.getClass().getMethod("getUid").invoke(category);
            } catch (Exception e2) { return null; }
        }
    }
    public static Class<?> getRecipeClass(Object category) {
        try {
            Object recipeType = category.getClass().getMethod("getRecipeType").invoke(category);
            return (Class<?>) recipeType.getClass().getMethod("getRecipeClass").invoke(recipeType);
        } catch (Exception e) { return Object.class; }
    }
    public static void addRecipesLegacy(Object registration, Collection<?> recipes, Object uid) {
        try {
            for (Method m : registration.getClass().getMethods()) {
                if (m.getName().equals("addRecipes") && m.getParameterCount() == 2) {
                    m.invoke(registration, createRecipeType(uid), recipes);
                    return;
                }
            }
        } catch (Exception e) { }
    }
    private static Object createRecipeType(Object uid) {
        try {
            Class<?> recipeType = Class.forName("mezz.jei.api.recipe.RecipeType");
            Method create = recipeType.getMethod("create", Class.forName("net.minecraft.resources.ResourceLocation"), Class.class);
            return create.invoke(null, uid, Object.class);
        } catch (Exception e) { return null; }
    }
    public static void drawLegacy(Object category, Object recipe, Object poseStack, double mouseX, double mouseY) {
        try {
            for (Method m : category.getClass().getMethods()) {
                if (m.getName().equals("draw") && m.getParameterCount() >= 4) {
                    m.invoke(category, recipe, null, poseStack, mouseX, mouseY);
                    return;
                }
            }
        } catch (Exception e) { }
    }
    public static Object getItemStacks(Object layout) {
        try { return layout.getClass().getMethod("getItemStacks").invoke(layout);
        } catch (Exception e) {
            try { return layout.getClass().getMethod("getRecipeSlotsView").invoke(layout);
            } catch (Exception e2) { return null; }
        }
    }
    public static Object getFocusValue(Object focus) {
        try {
            Object typed = focus.getClass().getMethod("getTypedValue").invoke(focus);
            return typed.getClass().getMethod("getIngredient").invoke(typed);
        } catch (Exception e) {
            try { return focus.getClass().getMethod("getValue").invoke(focus);
            } catch (Exception e2) { return null; }
        }
    }
    public static Object getFocusMode(Object focus) {
        try { return focus.getClass().getMethod("getRole").invoke(focus);
        } catch (Exception e) {
            try { return focus.getClass().getMethod("getMode").invoke(focus);
            } catch (Exception e2) { return null; }
        }
    }
}
