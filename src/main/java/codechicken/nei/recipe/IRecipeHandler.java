/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package codechicken.nei.recipe;

import java.util.List;
import java.util.Collections;

public interface IRecipeHandler {
    default String getRecipeName() { return ""; }
    default int numRecipes() { return 0; }
    default void drawBackground(int recipe) {}
    default void drawForeground(int recipe) {}
    default List<?> getIngredientStacks(int recipe) { return Collections.emptyList(); }
    default List<?> getOtherStacks(int recipe) { return Collections.emptyList(); }
    default String getGuiTexture() { return ""; }
}
