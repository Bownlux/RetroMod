/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Forge Registry System API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge Registry System API compatibility shim.
 * 
 * Handles changes in Forge's registry system.
 * Critical for mod block/item/entity registration.
 * 
 * API changes:
 * - DeferredRegister changes
 * - RegistryObject -> DeferredHolder
 * - Registry key/location changes
 */
public class ForgeRegistryApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge Registry System API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.20.1";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // DEFERRED REGISTER CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "net/neoforged/neoforge/registries/DeferredRegister"
        );
        
        // RegistryObject -> DeferredHolder
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/RegistryObject",
            "net/neoforged/neoforge/registries/DeferredHolder"
        );
        
        // DeferredRegister.create methods
        transformer.registerMethodRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "create",
            "(Lnet/minecraftforge/registries/IForgeRegistry;Ljava/lang/String;)Lnet/minecraftforge/registries/DeferredRegister;",
            "com/retromod/shim/api/forge/embedded/RegistryShim",
            "createDeferred",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "create",
            "(Lnet/minecraft/core/Registry;Ljava/lang/String;)Lnet/minecraftforge/registries/DeferredRegister;",
            "net/neoforged/neoforge/registries/DeferredRegister",
            "create",
            "(Lnet/minecraft/core/Registry;Ljava/lang/String;)Lnet/neoforged/neoforge/registries/DeferredRegister;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "create",
            "(Lnet/minecraft/resources/ResourceKey;Ljava/lang/String;)Lnet/minecraftforge/registries/DeferredRegister;",
            "net/neoforged/neoforge/registries/DeferredRegister",
            "create",
            "(Lnet/minecraft/resources/ResourceKey;Ljava/lang/String;)Lnet/neoforged/neoforge/registries/DeferredRegister;"
        );
        
        // ============================================================
        // FORGE REGISTRY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/IForgeRegistry",
            "net/neoforged/neoforge/registries/IForgeRegistry"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/IForgeRegistryEntry",
            "java/lang/Object" // Removed in NeoForge
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "net/neoforged/neoforge/registries/NeoForgeRegistries"
        );
        
        // ForgeRegistries fields
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "ITEMS",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraft/core/registries/BuiltInRegistries",
            "ITEM",
            "Lnet/minecraft/core/Registry;"
        );
        
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "BLOCKS",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraft/core/registries/BuiltInRegistries",
            "BLOCK",
            "Lnet/minecraft/core/Registry;"
        );
        
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "ENTITY_TYPES",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraft/core/registries/BuiltInRegistries",
            "ENTITY_TYPE",
            "Lnet/minecraft/core/Registry;"
        );
        
        transformer.registerFieldRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "BLOCK_ENTITY_TYPES",
            "Lnet/minecraftforge/registries/IForgeRegistry;",
            "net/minecraft/core/registries/BuiltInRegistries",
            "BLOCK_ENTITY_TYPE",
            "Lnet/minecraft/core/Registry;"
        );
        
        // ============================================================
        // REGISTRY KEYS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/ForgeRegistries$Keys",
            "net/neoforged/neoforge/registries/NeoForgeRegistries$Keys"
        );
        
        // ============================================================
        // REGISTRY BUILDER CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/RegistryBuilder",
            "net/neoforged/neoforge/registries/RegistryBuilder"
        );
        
        // ============================================================
        // GAME REGISTRY (LEGACY)
        // ============================================================
        
        // Very old GameRegistry class
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/registry/GameRegistry",
            "com/retromod/shim/api/forge/embedded/GameRegistryShim"
        );
        
        // ============================================================
        // OBJECT HOLDER (LEGACY)
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/ObjectHolder",
            "java/lang/Deprecated" // No longer used
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.RegistryShim",
            "com.retromod.shim.api.forge.embedded.GameRegistryShim"
        };
    }
}
