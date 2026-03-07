/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Architectury API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Architectury API compatibility shim.
 * 
 * Architectury is a popular API for creating cross-loader mods
 * (supporting both Fabric and Forge/NeoForge from one codebase).
 * 
 * API changes:
 * - v1.x -> v2.x: Package restructure
 * - v2.x -> v3.x: Registration API changes
 * - v3.x -> v4.x: Event system overhaul
 * - v4.x -> v6.x: Platform abstraction changes
 * - v6.x -> v9.x+: NeoForge support, further changes
 */
public class ArchitecturyApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Architectury API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "9.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "common";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE CHANGES
        // ============================================================
        
        // Old: me.shedaniel.architectury
        // New: dev.architectury (package rename in v6+)
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/Registries",
            "dev/architectury/registry/registries/Registries"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/DeferredRegister",
            "dev/architectury/registry/registries/DeferredRegister"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/RegistrySupplier",
            "dev/architectury/registry/registries/RegistrySupplier"
        );
        
        // ============================================================
        // PLATFORM CLASS CHANGES
        // ============================================================
        
        // Old: Platform class location
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/platform/Platform",
            "dev/architectury/platform/Platform"
        );
        
        // Old: ExpectPlatform annotation
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/annotations/ExpectPlatform",
            "dev/architectury/injectables/annotations/ExpectPlatform"
        );
        
        // ============================================================
        // REGISTRATION API CHANGES
        // ============================================================
        
        // Old: Registries.get(modId)
        // New: DeferredRegister.create(modId, registry)
        transformer.registerMethodRedirect(
            "me/shedaniel/architectury/registry/Registries",
            "get",
            "(Ljava/lang/String;)Lme/shedaniel/architectury/registry/Registries;",
            "com/retromod/shim/api/common/embedded/ArchitecturyShim",
            "getRegistries",
            "(Ljava/lang/String;)Ljava/lang/Object;"
        );
        
        // Old: registries.get(registry)
        transformer.registerMethodRedirect(
            "me/shedaniel/architectury/registry/Registries",
            "get",
            "(Lnet/minecraft/core/Registry;)Lme/shedaniel/architectury/registry/DeferredRegister;",
            "com/retromod/shim/api/common/embedded/ArchitecturyShim",
            "getDeferredRegister",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // EVENT SYSTEM CHANGES
        // ============================================================
        
        // Old event classes
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/event/events/common/LifecycleEvent",
            "dev/architectury/event/events/common/LifecycleEvent"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/event/events/common/PlayerEvent",
            "dev/architectury/event/events/common/PlayerEvent"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/event/events/common/TickEvent",
            "dev/architectury/event/events/common/TickEvent"
        );
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/event/events/client/ClientLifecycleEvent",
            "dev/architectury/event/events/client/ClientLifecycleEvent"
        );
        
        // Event registration
        transformer.registerMethodRedirect(
            "me/shedaniel/architectury/event/Event",
            "register",
            "(Ljava/lang/Object;)V",
            "dev/architectury/event/Event",
            "register",
            "(Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // NETWORKING CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/networking/NetworkManager",
            "dev/architectury/networking/NetworkManager"
        );
        
        // Old: NetworkManager.registerReceiver(side, id, receiver)
        transformer.registerMethodRedirect(
            "me/shedaniel/architectury/networking/NetworkManager",
            "registerReceiver",
            "(Lme/shedaniel/architectury/networking/NetworkManager$Side;Lnet/minecraft/resources/ResourceLocation;Lme/shedaniel/architectury/networking/NetworkManager$NetworkReceiver;)V",
            "com/retromod/shim/api/common/embedded/ArchitecturyShim",
            "registerReceiver",
            "(Ljava/lang/Object;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // MENU REGISTRY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/MenuRegistry",
            "dev/architectury/registry/menu/MenuRegistry"
        );
        
        // Old: MenuRegistry.register(id, factory)
        transformer.registerMethodRedirect(
            "me/shedaniel/architectury/registry/MenuRegistry",
            "register",
            "(Lnet/minecraft/resources/ResourceLocation;Lme/shedaniel/architectury/registry/MenuRegistry$SimpleMenuTypeFactory;)Lnet/minecraft/world/inventory/MenuType;",
            "com/retromod/shim/api/common/embedded/ArchitecturyShim",
            "registerMenu",
            "(Lnet/minecraft/resources/ResourceLocation;Ljava/lang/Object;)Lnet/minecraft/world/inventory/MenuType;"
        );
        
        // ============================================================
        // BLOCK ENTITY REGISTRY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/BlockEntityRegistry",
            "dev/architectury/registry/registries/DeferredRegister"
        );
        
        // ============================================================
        // FUEL REGISTRY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/fuel/FuelRegistry",
            "dev/architectury/registry/fuel/FuelRegistry"
        );
        
        // ============================================================
        // GAME RULES CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/GameRulesRegistry",
            "dev/architectury/registry/level/GameRules"
        );
        
        // ============================================================
        // KEY MAPPINGS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/KeyMappingRegistry",
            "dev/architectury/registry/client/keymappings/KeyMappingRegistry"
        );
        
        // ============================================================
        // RENDER TYPE REGISTRY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/shedaniel/architectury/registry/RenderTypeRegistry",
            "dev/architectury/registry/client/rendering/RenderTypeRegistry"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.ArchitecturyShim"
        };
    }
}
