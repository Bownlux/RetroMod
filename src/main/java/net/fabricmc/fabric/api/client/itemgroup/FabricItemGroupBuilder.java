/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.client.itemgroup;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Stub for FabricItemGroupBuilder, removed in Minecraft 1.19.3.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 */
public class FabricItemGroupBuilder {

    private FabricItemGroupBuilder() {
    }

    public static FabricItemGroupBuilder create(Object identifier) {
        return new FabricItemGroupBuilder();
    }

    public FabricItemGroupBuilder icon(Supplier<?> iconSupplier) {
        return this;
    }

    public FabricItemGroupBuilder appendItems(BiConsumer<?, ?> itemAppender) {
        return this;
    }

    public Object build() {
        return null;
    }
}
