/* RetroMod Polyfill - Stub for removed API. Copyright (c) 2026 Bownlux */
package net.fabricmc.fabric.api.object.builder.v1.block.entity;

/**
 * Stub for FabricBlockEntityTypeBuilder, removed in Minecraft 1.21.2.
 * Prevents ClassNotFoundException for mods compiled against older Fabric API.
 * Complements the embedded shim at com.retromod.shim.fabric.embedded.FabricBlockEntityTypeBuilderShim.
 */
public class FabricBlockEntityTypeBuilder {

    private FabricBlockEntityTypeBuilder() {
    }

    public static FabricBlockEntityTypeBuilder create() {
        return new FabricBlockEntityTypeBuilder();
    }

    public FabricBlockEntityTypeBuilder addBlock(Object block) {
        return this;
    }

    public FabricBlockEntityTypeBuilder addBlocks(Object... blocks) {
        return this;
    }

    public Object build() {
        return null;
    }
}
