/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.client.rendereregistry.v1;

import java.util.function.Function;

/**
 * Stub for BlockEntityRendererRegistry, removed in Minecraft 1.21.2.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 * Note: the typo "rendereregistry" is intentional - it matches the real Fabric API package name.
 */
public class BlockEntityRendererRegistry {

    private BlockEntityRendererRegistry() {
    }

    public static <T> void register(Object blockEntityType, Function<Object, ?> rendererFactory) {
        // no-op
    }
}
