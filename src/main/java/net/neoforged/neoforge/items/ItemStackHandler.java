/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.neoforged.neoforge.items;

public class ItemStackHandler {
    protected int size;

    public ItemStackHandler() { this(1); }

    public ItemStackHandler(int size) { this.size = size; }

    public int getSlots() { return size; }

    public Object getStackInSlot(int slot) { return null; }

    public Object insertItem(int slot, Object stack, boolean simulate) { return stack; }

    public Object extractItem(int slot, int amount, boolean simulate) { return null; }

    public int getSlotLimit(int slot) { return 64; }

    public boolean isItemValid(int slot, Object stack) { return true; }

    protected void onContentsChanged(int slot) {}
}
