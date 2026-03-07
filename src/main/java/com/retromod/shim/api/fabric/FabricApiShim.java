/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Fabric API Compatibility Shims
 * Handles changes in Fabric API between versions 1.14 - 1.21.11
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Comprehensive Fabric API compatibility shim.
 * 
 * Fabric API is modular - these are the most commonly used modules:
 * - fabric-api-base
 * - fabric-networking-api-v1
 * - fabric-resource-loader-v0
 * - fabric-item-group-api-v1
 * - fabric-registry-sync-v0
 * - fabric-rendering-v1
 * - fabric-events-interaction-v0
 * - fabric-block-api-v1
 * - fabric-item-api-v1
 * - fabric-transfer-api-v1
 * - fabric-loot-api-v2
 */
public class FabricApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "0.50.0"; // Fabric API for ~1.18
    }
    
    @Override
    public String getTargetVersion() {
        return "0.100.0"; // Fabric API for 1.21.x
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // NETWORKING API CHANGES (Major overhaul in 1.20.5+)
        // ============================================================
        
        // Old: ServerPlayNetworking.send(player, id, buf)
        // New: ServerPlayNetworking.send(player, payload)
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking",
            "send",
            "(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/network/PacketByteBuf;)V",
            "com/retromod/shim/api/fabric/embedded/NetworkingShim",
            "sendLegacy",
            "(Lnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Lnet/minecraft/network/PacketByteBuf;)V"
        );
        
        // Old: ClientPlayNetworking.send(id, buf)
        // New: ClientPlayNetworking.send(payload)
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/ClientPlayNetworking",
            "send",
            "(Lnet/minecraft/util/Identifier;Lnet/minecraft/network/PacketByteBuf;)V",
            "com/retromod/shim/api/fabric/embedded/NetworkingShim",
            "clientSendLegacy",
            "(Lnet/minecraft/util/Identifier;Lnet/minecraft/network/PacketByteBuf;)V"
        );
        
        // PacketByteBufs.create() -> new PacketByteBuf(Unpooled.buffer())
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/PacketByteBufs",
            "create",
            "()Lnet/minecraft/network/PacketByteBuf;",
            "com/retromod/shim/api/fabric/embedded/NetworkingShim",
            "createBuf",
            "()Lnet/minecraft/network/PacketByteBuf;"
        );
        
        // ============================================================
        // ITEM GROUP API CHANGES (1.19.3+ overhaul)
        // ============================================================
        
        // Old: ItemGroupEvents.modifyEntriesEvent(group).register(...)
        // New: Different signature in 1.20+
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents",
            "modifyEntriesEvent",
            "(Lnet/minecraft/item/ItemGroup;)Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/ItemGroupShim",
            "modifyEntriesEvent",
            "(Lnet/minecraft/item/ItemGroup;)Lnet/fabricmc/fabric/api/event/Event;"
        );
        
        // FabricItemGroupBuilder removed in 1.19.3
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/itemgroup/FabricItemGroupBuilder",
            "com/retromod/shim/api/fabric/embedded/FabricItemGroupBuilderShim"
        );
        
        // ============================================================
        // BLOCK/ITEM SETTINGS CHANGES (1.20+)
        // ============================================================
        
        // FabricBlockSettings.of(material) -> AbstractBlock.Settings.create()
        // Material system removed in 1.20
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings",
            "of",
            "(Lnet/minecraft/block/Material;)Lnet/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings;",
            "com/retromod/shim/api/fabric/embedded/BlockSettingsShim",
            "of",
            "(Ljava/lang/Object;)Lnet/minecraft/block/AbstractBlock$Settings;"
        );
        
        // FabricBlockSettings.copyOf(block) still works but signature changed
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings",
            "copy",
            "(Lnet/minecraft/block/AbstractBlock;)Lnet/fabricmc/fabric/api/object/builder/v1/block/FabricBlockSettings;",
            "net/minecraft/block/AbstractBlock$Settings",
            "copy",
            "(Lnet/minecraft/block/AbstractBlock;)Lnet/minecraft/block/AbstractBlock$Settings;"
        );
        
        // FabricItemSettings -> Item.Settings (FabricItemSettings removed)
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/item/v1/FabricItemSettings",
            "net/minecraft/item/Item$Settings"
        );
        
        // ============================================================
        // REGISTRY CHANGES (1.19.3+ Registries overhaul)
        // ============================================================
        
        // Registry.register -> Registries system
        transformer.registerMethodRedirect(
            "net/minecraft/util/registry/Registry",
            "register",
            "(Lnet/minecraft/util/registry/Registry;Lnet/minecraft/util/Identifier;Ljava/lang/Object;)Ljava/lang/Object;",
            "net/minecraft/registry/Registry",
            "register",
            "(Lnet/minecraft/registry/Registry;Lnet/minecraft/util/Identifier;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // Old Registry class -> new Registries class
        transformer.registerClassRedirect(
            "net/minecraft/util/registry/Registry",
            "net/minecraft/registry/Registries"
        );
        
        // BuiltinRegistries -> BuiltInRegistries
        transformer.registerClassRedirect(
            "net/minecraft/util/registry/BuiltinRegistries",
            "net/minecraft/registry/BuiltInRegistries"
        );
        
        // ============================================================
        // RENDERING API CHANGES
        // ============================================================
        
        // BlockRenderLayerMap changes
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/blockrenderlayer/v1/BlockRenderLayerMap",
            "putBlock",
            "(Lnet/minecraft/block/Block;Lnet/minecraft/client/render/RenderLayer;)V",
            "com/retromod/shim/api/fabric/embedded/RenderLayerShim",
            "putBlock",
            "(Lnet/minecraft/block/Block;Lnet/minecraft/client/render/RenderLayer;)V"
        );
        
        // ColorProviderRegistry changes
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/ColorProviderRegistry",
            "BLOCK",
            "Lnet/fabricmc/fabric/api/client/rendering/v1/ColorProviderRegistry;",
            "com/retromod/shim/api/fabric/embedded/ColorProviderShim",
            "getBlockRegistry",
            "()Ljava/lang/Object;"
        );
        
        // ============================================================
        // RESOURCE LOADER CHANGES
        // ============================================================
        
        // ResourceManagerHelper changes
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/resource/ResourceManagerHelper",
            "get",
            "(Lnet/minecraft/resource/ResourceType;)Lnet/fabricmc/fabric/api/resource/ResourceManagerHelper;",
            "com/retromod/shim/api/fabric/embedded/ResourceShim",
            "getHelper",
            "(Lnet/minecraft/resource/ResourceType;)Lnet/fabricmc/fabric/api/resource/ResourceManagerHelper;"
        );
        
        // ============================================================
        // TRANSFER API CHANGES (1.20+)
        // ============================================================
        
        // Storage API changes
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/transfer/v1/item/ItemStorage",
            "SIDED",
            "Lnet/fabricmc/fabric/api/lookup/v1/block/BlockApiLookup;",
            "com/retromod/shim/api/fabric/embedded/TransferApiShim",
            "getItemStorageSided",
            "()Lnet/fabricmc/fabric/api/lookup/v1/block/BlockApiLookup;"
        );
        
        // ============================================================
        // LOOT API CHANGES (v1 -> v2)
        // ============================================================
        
        // LootTableLoadingCallback removed, use LootTableEvents
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/loot/v1/LootTableLoadingCallback",
            "com/retromod/shim/api/fabric/embedded/LootTableShim"
        );
        
        // ============================================================
        // EVENT INTERACTION CHANGES
        // ============================================================
        
        // UseBlockCallback, UseItemCallback, AttackBlockCallback changes
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/event/player/UseBlockCallback",
            "EVENT",
            "Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/PlayerEventsShim",
            "getUseBlockEvent",
            "()Lnet/fabricmc/fabric/api/event/Event;"
        );
        
        // ============================================================
        // BIOME MODIFICATION API (1.19+)
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/biome/v1/BiomeModifications",
            "addFeature",
            "(Ljava/util/function/Predicate;Lnet/minecraft/world/gen/GenerationStep$Feature;Lnet/minecraft/util/registry/RegistryKey;)V",
            "com/retromod/shim/api/fabric/embedded/BiomeShim",
            "addFeature",
            "(Ljava/util/function/Predicate;Lnet/minecraft/world/gen/GenerationStep$Feature;Lnet/minecraft/registry/RegistryKey;)V"
        );
        
        // ============================================================
        // SCREEN API CHANGES
        // ============================================================
        
        // ScreenEvents changes
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents",
            "beforeRender",
            "(Lnet/minecraft/client/gui/screen/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/ScreenEventsShim",
            "beforeRender",
            "(Lnet/minecraft/client/gui/screen/Screen;)Lnet/fabricmc/fabric/api/event/Event;"
        );
        
        // ============================================================
        // DIMENSION API CHANGES
        // ============================================================
        
        // FabricDimensions.teleport signature changed
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/dimension/v1/FabricDimensions",
            "teleport",
            "(Lnet/minecraft/entity/Entity;Lnet/minecraft/server/world/ServerWorld;Lnet/fabricmc/fabric/api/dimension/v1/FabricDimensions$TeleportTarget;)Lnet/minecraft/entity/Entity;",
            "com/retromod/shim/api/fabric/embedded/DimensionShim",
            "teleport",
            "(Lnet/minecraft/entity/Entity;Lnet/minecraft/server/world/ServerWorld;Ljava/lang/Object;)Lnet/minecraft/entity/Entity;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.NetworkingShim",
            "com.retromod.shim.api.fabric.embedded.ItemGroupShim",
            "com.retromod.shim.api.fabric.embedded.FabricItemGroupBuilderShim",
            "com.retromod.shim.api.fabric.embedded.BlockSettingsShim",
            "com.retromod.shim.api.fabric.embedded.RenderLayerShim",
            "com.retromod.shim.api.fabric.embedded.ColorProviderShim",
            "com.retromod.shim.api.fabric.embedded.ResourceShim",
            "com.retromod.shim.api.fabric.embedded.TransferApiShim",
            "com.retromod.shim.api.fabric.embedded.LootTableShim",
            "com.retromod.shim.api.fabric.embedded.PlayerEventsShim",
            "com.retromod.shim.api.fabric.embedded.BiomeShim",
            "com.retromod.shim.api.fabric.embedded.ScreenEventsShim",
            "com.retromod.shim.api.fabric.embedded.DimensionShim"
        };
    }
}
