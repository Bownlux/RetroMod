/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package baubles.api;

public enum BaubleType {
    AMULET, RING, BELT, TRINKET, HEAD, BODY, CHARM;

    public boolean hasSlot(int slot) { return true; }
    public int[] getValidSlots() { return new int[]{ordinal()}; }
}
