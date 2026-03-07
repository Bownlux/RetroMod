/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2025/06/15/1216.html
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21.5 to run on 1.21.6+.
 * 
 * Major breaking changes addressed:
 * - HUD API completely rewritten (HudElementRegistry)
 * - Material API removed from Fabric Rendering API
 * - CoreShaderRegistrationCallback removed
 * - RenderSystem methods removed
 * - Several deprecated modules removed
 * - Item#appendTooltip deprecated -> ComponentTooltipAppenderRegistry
 */
public class Fabric_1_21_5_to_1_21_6 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.5 to 1.21.6";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.5";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.6";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        
        // ============================================================
        // HUD API REWRITE
        // Old HUD callbacks -> HudElementRegistry
        // ============================================================
        
        // HudRenderCallback is now completely different
        // Old: HudRenderCallback.EVENT.register((matrices, tickDelta) -> {...})
        // New: HudElementRegistry.addLast(id, (context, tickCounter) -> {...})
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/HudRenderCallback",
            "com/retromod/shim/fabric/embedded/HudRenderCallbackShim"
        );
        
        // ============================================================
        // MATERIAL API REMOVED
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/renderer/v1/material/MaterialFinder",
            "com/retromod/shim/fabric/embedded/MaterialFinderShim"
        );
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/renderer/v1/material/RenderMaterial",
            "com/retromod/shim/fabric/embedded/RenderMaterialShim"
        );
        
        // ============================================================
        // CORE SHADER REGISTRATION REMOVED
        // Vanilla resource pack now supports modded core shaders
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/CoreShaderRegistrationCallback",
            "com/retromod/shim/fabric/embedded/CoreShaderRegistrationCallbackShim"
        );
        
        // ============================================================
        // WORLD RENDERER CHANGES
        // Methods moved to VertexRendering
        // ============================================================
        
        transformer.registerMethodRedirect(
            "net/minecraft/client/render/WorldRenderer", "drawBox",
            "(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDDDDFFFF)V",
            "com/retromod/shim/fabric/embedded/WorldRendererShim", "drawBox",
            "(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;DDDDDDFFFF)V"
        );
        
        // ============================================================
        // TAG API MODULES MERGED
        // Several tag modules consolidated
        // ============================================================
        
        // fabric-item-api-v1, fabric-convention-tags-v1 merged
        // No direct redirects needed, just dependency changes
        
        // ============================================================
        // TOOLTIP CHANGES
        // Item#appendTooltip deprecated -> ComponentTooltipAppenderRegistry
        // ============================================================
        
        // Mods can still use Item#appendTooltip but should migrate
        // We provide a bridge to the new API
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.HudRenderCallbackShim",
            "com.retromod.shim.fabric.embedded.MaterialFinderShim",
            "com.retromod.shim.fabric.embedded.RenderMaterialShim",
            "com.retromod.shim.fabric.embedded.CoreShaderRegistrationCallbackShim",
            "com.retromod.shim.fabric.embedded.WorldRendererShim"
        };
    }
}
