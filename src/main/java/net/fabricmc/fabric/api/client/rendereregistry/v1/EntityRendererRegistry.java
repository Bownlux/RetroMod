/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.client.rendereregistry.v1;

/**
 * Stub for EntityRendererRegistry, removed in Minecraft 1.21.2.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 * Note: the typo "rendereregistry" is intentional - it matches the real Fabric API package name.
 */
public class EntityRendererRegistry {

    /**
     * Functional interface matching the original EntityRendererFactory signature.
     */
    @FunctionalInterface
    public interface EntityRendererFactory {
        Object create(Object context);
    }

    private EntityRendererRegistry() {
    }

    public static void register(Object entityType, EntityRendererFactory factory) {
        // no-op
    }
}
