/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed Fabric API classes.
 *
 * The Fabric API has deprecated and removed several builder and
 * registry classes across versions. This provider registers stub
 * implementations so older mods referencing these classes do not
 * crash with ClassNotFoundException at startup.
 *
 * Covered removals:
 * - FabricItemGroupBuilder (replaced by ItemGroup.Builder in newer Fabric API)
 * - FabricBlockEntityTypeBuilder (moved/renamed in newer Fabric API)
 * - BlockEntityRendererRegistry / EntityRendererRegistry (registration API changes)
 * - FabricDimensions (dimension API overhaul)
 * - MaterialFinder / RenderMaterial (rendering API changes)
 * - LootTableLoadingCallback (loot table API rework)
 * - FabricItemSettings (replaced by Item.Settings in newer Fabric API)
 */
public class FabricApiPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Fabric API Removed Classes";
    }

    @Override
    public String getCategory() {
        return "fabric_api";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/fabricmc/fabric/api/itemgroup/v1/FabricItemGroupBuilder",
            "net/fabricmc/fabric/api/object/builder/v1/block/entity/FabricBlockEntityTypeBuilder",
            "net/fabricmc/fabric/api/client/rendering/v1/BlockEntityRendererRegistry",
            "net/fabricmc/fabric/api/client/rendering/v1/EntityRendererRegistry",
            "net/fabricmc/fabric/api/dimension/v1/FabricDimensions",
            "net/fabricmc/fabric/api/renderer/v1/material/MaterialFinder",
            "net/fabricmc/fabric/api/renderer/v1/material/RenderMaterial",
            "net/fabricmc/fabric/api/loot/v2/LootTableLoadingCallback",
            "net/fabricmc/fabric/api/item/v1/FabricItemSettings"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroupBuilder",
            "net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder",
            "net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry",
            "net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry",
            "net.fabricmc.fabric.api.dimension.v1.FabricDimensions",
            "net.fabricmc.fabric.api.renderer.v1.material.MaterialFinder",
            "net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial",
            "net.fabricmc.fabric.api.loot.v2.LootTableLoadingCallback",
            "net.fabricmc.fabric.api.item.v1.FabricItemSettings"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // Register all polyfill classes as embedded shims for tracking.
        // The stub classes are placed at the original package paths so
        // they are found directly by the classloader.
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
