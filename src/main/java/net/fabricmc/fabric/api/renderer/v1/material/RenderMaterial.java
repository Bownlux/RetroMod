/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.renderer.v1.material;

/**
 * Stub for RenderMaterial, removed in Minecraft 1.21.6.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 */
public interface RenderMaterial {

    default int spriteDepth() {
        return 1;
    }

    default Object blendMode() {
        return null;
    }

    default boolean disableColorIndex() {
        return false;
    }

    default boolean emissive() {
        return false;
    }

    default boolean disableDiffuse() {
        return false;
    }

    default boolean disableAo() {
        return false;
    }
}
