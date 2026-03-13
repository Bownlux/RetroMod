/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.renderer.v1.material;

/**
 * Stub for MaterialFinder, removed in Minecraft 1.21.6.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 */
public interface MaterialFinder {

    default MaterialFinder blendMode(Object blendMode) {
        return this;
    }

    default MaterialFinder disableColorIndex(boolean disable) {
        return this;
    }

    default MaterialFinder emissive(boolean emissive) {
        return this;
    }

    default MaterialFinder disableDiffuse(boolean disable) {
        return this;
    }

    default MaterialFinder disableAo(boolean disable) {
        return this;
    }

    default RenderMaterial find() {
        return null;
    }

    default MaterialFinder clear() {
        return this;
    }

    default MaterialFinder spriteDepth(int depth) {
        return this;
    }
}
