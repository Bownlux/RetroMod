/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.item.v1;

/**
 * Stub for FabricItemSettings, removed from Fabric API.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 * Extends Object instead of Item.Settings since the Minecraft class may not be available.
 */
public class FabricItemSettings {

    public FabricItemSettings() {
    }

    public FabricItemSettings group(Object itemGroup) {
        return this;
    }

    public FabricItemSettings maxCount(int maxCount) {
        return this;
    }

    public FabricItemSettings maxDamage(int maxDamage) {
        return this;
    }

    public FabricItemSettings recipeRemainder(Object recipeRemainder) {
        return this;
    }

    public FabricItemSettings rarity(Object rarity) {
        return this;
    }

    public FabricItemSettings fireproof() {
        return this;
    }

    public FabricItemSettings food(Object foodComponent) {
        return this;
    }

    public FabricItemSettings customDamage(Object handler) {
        return this;
    }

    public FabricItemSettings equipmentSlot(Object equipmentSlotProvider) {
        return this;
    }
}
