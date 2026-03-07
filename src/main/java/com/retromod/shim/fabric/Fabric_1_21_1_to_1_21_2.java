/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2024/10/14/1212.html
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21.1 to run on 1.21.2.
 * 
 * Major breaking changes addressed:
 * - Identifier constructor became protected (use Identifier.of)
 * - TypedActionResult -> ActionResult in many places
 * - FabricElytraItem removed (use glider component)
 * - FabricBlockEntityType.Builder removed
 * - HudRenderCallback now passes RenderTickCounter
 * - Data pack paths changed to singular nouns
 * - FabricDimensions removed
 * - Recipe system reworked
 */
public class Fabric_1_21_1_to_1_21_2 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.1 to 1.21.2";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.1";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.2";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        
        // ============================================================
        // IDENTIFIER CHANGES
        // new Identifier(...) -> Identifier.of(...)
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/IdentifierShim", "of", 
            "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/util/Identifier;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/IdentifierShim", "of",
            "(Ljava/lang/String;)Lnet/minecraft/util/Identifier;"
        );
        
        // ============================================================
        // ACTION RESULT CHANGES
        // TypedActionResult<ItemStack> -> ActionResult in UseItemCallback
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/item/v1/FabricItem", "getAttributeModifiers",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;",
            "com/retromod/shim/fabric/embedded/FabricItemShim", "getAttributeModifiers",
            "(Ljava/lang/Object;Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;)Lcom/google/common/collect/Multimap;"
        );
        
        // ============================================================
        // FABRIC DIMENSIONS REMOVED
        // FabricDimensions.teleport -> Entity.teleportTo
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/dimension/v1/FabricDimensions", "teleport",
            "(Lnet/minecraft/entity/Entity;Lnet/minecraft/server/world/ServerWorld;Lnet/fabricmc/fabric/api/dimension/v1/TeleportTarget;)Lnet/minecraft/entity/Entity;",
            "com/retromod/shim/fabric/embedded/FabricDimensionsShim", "teleport",
            "(Lnet/minecraft/entity/Entity;Lnet/minecraft/server/world/ServerWorld;Ljava/lang/Object;)Lnet/minecraft/entity/Entity;"
        );
        
        // ============================================================
        // BLOCK ENTITY TYPE BUILDER
        // FabricBlockEntityType.Builder removed
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/object/builder/v1/block/entity/FabricBlockEntityType$Builder",
            "com/retromod/shim/fabric/embedded/FabricBlockEntityTypeBuilderShim"
        );
        
        // ============================================================
        // RENDERER REGISTRIES REMOVED
        // fabric-renderer-registries-v1 module removed
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendereregistry/v1/BlockEntityRendererRegistry",
            "com/retromod/shim/fabric/embedded/BlockEntityRendererRegistryShim"
        );
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendereregistry/v1/EntityRendererRegistry",
            "com/retromod/shim/fabric/embedded/EntityRendererRegistryShim"
        );
        
        // ============================================================
        // RESOURCE CONDITION CHANGES
        // test method signature changed
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/resource/conditions/v1/ResourceCondition", "test",
            "(Lnet/minecraft/registry/DynamicRegistryManager;)Z",
            "com/retromod/shim/fabric/embedded/ResourceConditionShim", "test",
            "(Ljava/lang/Object;Lnet/minecraft/registry/DynamicRegistryManager;)Z"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.IdentifierShim",
            "com.retromod.shim.fabric.embedded.FabricItemShim",
            "com.retromod.shim.fabric.embedded.FabricDimensionsShim",
            "com.retromod.shim.fabric.embedded.FabricBlockEntityTypeBuilderShim",
            "com.retromod.shim.fabric.embedded.BlockEntityRendererRegistryShim",
            "com.retromod.shim.fabric.embedded.EntityRendererRegistryShim",
            "com.retromod.shim.fabric.embedded.ResourceConditionShim"
        };
    }
}
