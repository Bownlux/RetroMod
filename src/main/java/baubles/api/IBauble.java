/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package baubles.api;

public interface IBauble {
    default BaubleType getBaubleType(Object itemStack) { return BaubleType.TRINKET; }
    default void onWornTick(Object itemStack, Object player) {}
    default void onEquipped(Object itemStack, Object player) {}
    default void onUnequipped(Object itemStack, Object player) {}
    default boolean canEquip(Object itemStack, Object player) { return true; }
    default boolean canUnequip(Object itemStack, Object player) { return true; }
}
