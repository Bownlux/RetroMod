/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2025/09/23/1219.html
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21.8 to run on 1.21.9+.
 * 
 * Major breaking changes addressed:
 * - Entity#getWorld renamed to Entity#getEntityWorld (CRITICAL)
 * - Resource Loader API major rework
 * - World Render Events removed (temporarily)
 * - Key mapping changes
 * - Block entity rendering now uses OrderedRenderCommandQueue
 */
public class Fabric_1_21_8_to_1_21_9 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.8 to 1.21.9";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.8";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.9";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        
        // ============================================================
        // ENTITY.getWorld() -> ENTITY.getEntityWorld()
        // This is the most impactful change - affects almost all mods
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/Entity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        // Also for subclasses that might override
        transformer.registerMethodRedirect(
            "net/minecraft/entity/LivingEntity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/LivingEntity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/entity/player/PlayerEntity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/player/PlayerEntity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/server/network/ServerPlayerEntity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/server/network/ServerPlayerEntity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraft/client/network/ClientPlayerEntity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/client/network/ClientPlayerEntity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        // ============================================================
        // RESOURCE LOADER API REWORK
        // ResourceManagerHelper -> ResourceLoader
        // ============================================================
        
        // Old: ResourceManagerHelper.get(type).registerReloadListener(listener)
        // New: ResourceLoader.get(type).registerReloader(id, listener)
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/resource/ResourceManagerHelper",
            "com/retromod/shim/fabric/embedded/ResourceManagerHelperShim"
        );
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/resource/ResourceManagerHelper", "get",
            "(Lnet/minecraft/resource/ResourceType;)Lnet/fabricmc/fabric/api/resource/ResourceManagerHelper;",
            "com/retromod/shim/fabric/embedded/ResourceManagerHelperShim", "get",
            "(Lnet/minecraft/resource/ResourceType;)Lcom/retromod/shim/fabric/embedded/ResourceManagerHelperShim;"
        );
        
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/resource/ResourceManagerHelper", "registerReloadListener",
            "(Lnet/fabricmc/fabric/api/resource/IdentifiableResourceReloadListener;)V",
            "com/retromod/shim/fabric/embedded/ResourceManagerHelperShim", "registerReloadListener",
            "(Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // WORLD RENDER EVENTS REMOVED
        // No replacement yet in 1.21.9 - use mixins
        // ============================================================
        
        // These events don't exist in 1.21.9, provide no-op shims
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents",
            "com/retromod/shim/fabric/embedded/WorldRenderEventsShim"
        );
        
        // ============================================================
        // KEY MAPPING CHANGES
        // KeyBinding.Category is now a record
        // ============================================================
        
        // Old: KeyBinding with String category
        // New: KeyBinding.Category record
        
        // The old constructor still works, this is more of a recommendation
        // than a breaking change, but we provide migration helpers
        
        // ============================================================
        // BLOCK ENTITY RENDERING
        // Now uses OrderedRenderCommandQueue
        // ============================================================
        
        // BlockEntityRenderer methods changed signatures
        // Most mods won't break but may need updates for optimal performance
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.ResourceManagerHelperShim",
            "com.retromod.shim.fabric.embedded.WorldRenderEventsShim",
            "com.retromod.shim.fabric.embedded.EntityWorldShim"
        };
    }
}
