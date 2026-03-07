/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Cardinal Components API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Cardinal Components API compatibility shim.
 * 
 * Cardinal Components provides a capability-like system for Fabric mods.
 * Similar to Forge Capabilities but designed for Fabric.
 * 
 * API changes:
 * - v2.x -> v3.x: Component registration changes
 * - v3.x -> v4.x: Sync system changes
 * - v4.x -> v5.x: Entity components changes
 * - v5.x -> v6.x+: Further API refinements
 */
public class CardinalComponentsApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Cardinal Components API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "2.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "6.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE CHANGES
        // ============================================================
        
        // Old: nerdhub.cardinal.components
        // New: dev.onyxstudios.cca (then org.ladysnake.cca)
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/ComponentType",
            "org/ladysnake/cca/api/v3/component/ComponentKey"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/component/ComponentKey",
            "org/ladysnake/cca/api/v3/component/ComponentKey"
        );
        
        // ============================================================
        // COMPONENT INTERFACE CHANGES
        // ============================================================
        
        // Old: Component interface
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/Component",
            "org/ladysnake/cca/api/v3/component/Component"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/component/Component",
            "org/ladysnake/cca/api/v3/component/Component"
        );
        
        // Syncable component
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/component/SyncedComponent",
            "org/ladysnake/cca/api/v3/component/sync/AutoSyncedComponent"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/component/sync/AutoSyncedComponent",
            "org/ladysnake/cca/api/v3/component/sync/AutoSyncedComponent"
        );
        
        // ============================================================
        // COMPONENT REGISTRY CHANGES
        // ============================================================
        
        // Old: ComponentRegistry.INSTANCE
        // New: ComponentRegistryV3.INSTANCE
        transformer.registerFieldRedirect(
            "nerdhub/cardinal/components/api/ComponentRegistry",
            "INSTANCE",
            "Lnerdhub/cardinal/components/api/ComponentRegistry;",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "getRegistry",
            "()Ljava/lang/Object;"
        );
        
        transformer.registerFieldRedirect(
            "dev/onyxstudios/cca/api/v3/component/ComponentRegistry",
            "INSTANCE",
            "Ldev/onyxstudios/cca/api/v3/component/ComponentRegistry;",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "getRegistry",
            "()Ljava/lang/Object;"
        );
        
        // ============================================================
        // COMPONENT ACCESS CHANGES
        // ============================================================
        
        // Old: ComponentType.get(provider)
        // New: ComponentKey.get(provider)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/ComponentType",
            "get",
            "(Ljava/lang/Object;)Lnerdhub/cardinal/components/api/Component;",
            "org/ladysnake/cca/api/v3/component/ComponentKey",
            "get",
            "(Ljava/lang/Object;)Lorg/ladysnake/cca/api/v3/component/Component;"
        );
        
        // Old: ComponentType.maybeGet(provider)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/ComponentType",
            "maybeGet",
            "(Ljava/lang/Object;)Ljava/util/Optional;",
            "org/ladysnake/cca/api/v3/component/ComponentKey",
            "maybeGet",
            "(Ljava/lang/Object;)Ljava/util/Optional;"
        );
        
        // ============================================================
        // ENTITY COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/EntityComponents",
            "org/ladysnake/cca/api/v3/entity/EntityComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/entity/EntityComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/entity/EntityComponentFactoryRegistry"
        );
        
        // ============================================================
        // WORLD COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/WorldComponents",
            "org/ladysnake/cca/api/v3/world/WorldComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/world/WorldComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/world/WorldComponentFactoryRegistry"
        );
        
        // ============================================================
        // CHUNK COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/ChunkComponents",
            "org/ladysnake/cca/api/v3/chunk/ChunkComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/chunk/ChunkComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/chunk/ChunkComponentFactoryRegistry"
        );
        
        // ============================================================
        // ITEM COMPONENTS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "nerdhub/cardinal/components/api/util/ItemComponents",
            "org/ladysnake/cca/api/v3/item/ItemComponentFactoryRegistry"
        );
        
        transformer.registerClassRedirect(
            "dev/onyxstudios/cca/api/v3/item/ItemComponentFactoryRegistry",
            "org/ladysnake/cca/api/v3/item/ItemComponentFactoryRegistry"
        );
        
        // ============================================================
        // NBT SERIALIZATION CHANGES
        // ============================================================
        
        // Old: Component.fromTag(tag)
        // New: Component.readFromNbt(tag, registryLookup)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/Component",
            "fromTag",
            "(Lnet/minecraft/nbt/CompoundTag;)V",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "readFromNbt",
            "(Ljava/lang/Object;Lnet/minecraft/nbt/CompoundTag;)V"
        );
        
        // Old: Component.toTag(tag)
        // New: Component.writeToNbt(tag, registryLookup)
        transformer.registerMethodRedirect(
            "nerdhub/cardinal/components/api/Component",
            "toTag",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "com/retromod/shim/api/fabric/embedded/CardinalShim",
            "writeToNbt",
            "(Ljava/lang/Object;Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.CardinalShim"
        };
    }
}
