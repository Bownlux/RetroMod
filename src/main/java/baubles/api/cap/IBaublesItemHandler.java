/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package baubles.api.cap;

public interface IBaublesItemHandler {
    default int getSlots() { return 7; }
    default Object getStackInSlot(int slot) { return null; }
    default boolean isItemValidForSlot(int slot, Object stack, Object player) { return true; }
}
