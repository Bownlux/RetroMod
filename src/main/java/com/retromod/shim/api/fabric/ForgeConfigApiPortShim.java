/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Forge Config API Port Compatibility Shim (for Fabric)
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge Config API Port compatibility shim.
 * 
 * This is the Fabric port of Forge's config system.
 * Many mods use it for cross-loader config compatibility.
 */
public class ForgeConfigApiPortShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge Config API Port Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "8.0.0";
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
        
        // Old package: net.minecraftforge.common
        // Fabric port: fuzs.forgeconfigapiport
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec",
            "fuzs/forgeconfigapiport/api/config/v2/ForgeConfigSpec"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$Builder",
            "fuzs/forgeconfigapiport/api/config/v2/ForgeConfigSpec$Builder"
        );
        
        // ============================================================
        // CONFIG REGISTRATION CHANGES
        // ============================================================
        
        // ModLoadingContext changes
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "fuzs/forgeconfigapiport/api/config/v2/ModConfigEvents"
        );
        
        // Config type enum
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/config/ModConfig$Type",
            "fuzs/forgeconfigapiport/api/config/v2/ModConfig$Type"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
