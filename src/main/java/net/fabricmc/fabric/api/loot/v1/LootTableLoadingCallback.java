/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.loot.v1;

/**
 * Stub for LootTableLoadingCallback, removed from Fabric API.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 */
@FunctionalInterface
public interface LootTableLoadingCallback {

    /**
     * Placeholder EVENT field. Set to null since the event system is not available.
     */
    Object EVENT = null;

    /**
     * Called when a loot table is loading.
     *
     * @param resourceManager the resource manager
     * @param lootManager     the loot manager
     * @param id              the loot table identifier
     * @param tableBuilder    the loot table builder
     * @param setter          the loot table setter
     */
    void onLootTableLoading(Object resourceManager, Object lootManager, Object id, Object tableBuilder, Object setter);
}
