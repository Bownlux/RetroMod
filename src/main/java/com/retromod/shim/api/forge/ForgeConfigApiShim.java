/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Forge Config API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge Config API compatibility shim.
 * 
 * Handles changes in Forge's built-in configuration system.
 * Changes between Forge and NeoForge versions.
 * 
 * API changes:
 * - ForgeConfigSpec changes
 * - Config loading event changes
 * - Path handling changes
 */
public class ForgeConfigApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge Config API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.19.0";
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
        // FORGE -> NEOFORGE CONFIG CHANGES
        // ============================================================
        
        // ForgeConfigSpec -> NeoForgeConfigSpec
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec",
            "net/neoforged/neoforge/common/ModConfigSpec"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$Builder",
            "net/neoforged/neoforge/common/ModConfigSpec$Builder"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$ConfigValue",
            "net/neoforged/neoforge/common/ModConfigSpec$ConfigValue"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$BooleanValue",
            "net/neoforged/neoforge/common/ModConfigSpec$BooleanValue"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$IntValue",
            "net/neoforged/neoforge/common/ModConfigSpec$IntValue"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$DoubleValue",
            "net/neoforged/neoforge/common/ModConfigSpec$DoubleValue"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$LongValue",
            "net/neoforged/neoforge/common/ModConfigSpec$LongValue"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec$EnumValue",
            "net/neoforged/neoforge/common/ModConfigSpec$EnumValue"
        );
        
        // ============================================================
        // MOD CONFIG CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/config/ModConfig",
            "net/neoforged/fml/config/ModConfig"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/config/ModConfig$Type",
            "net/neoforged/fml/config/ModConfig$Type"
        );
        
        // ============================================================
        // CONFIG LOADING EVENT CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/event/config/ModConfigEvent",
            "net/neoforged/fml/event/config/ModConfigEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/event/config/ModConfigEvent$Loading",
            "net/neoforged/fml/event/config/ModConfigEvent$Loading"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/event/config/ModConfigEvent$Reloading",
            "net/neoforged/fml/event/config/ModConfigEvent$Reloading"
        );
        
        // ============================================================
        // MOD LOADING CONTEXT CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "net/neoforged/fml/ModLoadingContext"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "registerConfig",
            "(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/common/ForgeConfigSpec;)V",
            "net/neoforged/fml/ModLoadingContext",
            "registerConfig",
            "(Lnet/neoforged/fml/config/ModConfig$Type;Lnet/neoforged/neoforge/common/ModConfigSpec;)V"
        );
        
        transformer.registerMethodRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "registerConfig",
            "(Lnet/minecraftforge/fml/config/ModConfig$Type;Lnet/minecraftforge/common/ForgeConfigSpec;Ljava/lang/String;)V",
            "net/neoforged/fml/ModLoadingContext",
            "registerConfig",
            "(Lnet/neoforged/fml/config/ModConfig$Type;Lnet/neoforged/neoforge/common/ModConfigSpec;Ljava/lang/String;)V"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
