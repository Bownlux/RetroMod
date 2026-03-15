/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.dimension.v1;

/**
 * Stub for FabricDimensions, removed in Minecraft 1.21.2.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 * Complements the embedded shim at com.retromod.shim.fabric.embedded.
 */
public class FabricDimensions {

    private FabricDimensions() {
    }

    /**
     * Stub for the teleport method. Returns the entity unchanged.
     *
     * @param entity          the entity to teleport
     * @param serverWorld     the target world
     * @param teleportTarget  the teleport target position/rotation
     * @return the entity passed in
     */
    @SuppressWarnings("unchecked")
    public static <E> E teleport(E entity, Object serverWorld, Object teleportTarget) {
        return entity;
    }
}
