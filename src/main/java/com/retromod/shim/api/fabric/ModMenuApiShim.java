/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Mod Menu API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Mod Menu API compatibility shim.
 * 
 * Mod Menu is the de-facto standard for config screen integration on Fabric.
 * API changes between major versions:
 * - v1.x -> v2.x: ModMenuApi interface changes
 * - v2.x -> v3.x: ConfigScreenFactory signature changes
 * - v3.x -> v4.x: Badge system changes
 * - v4.x -> v7.x: Further ConfigScreen changes
 */
public class ModMenuApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Mod Menu API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "7.0.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // MOD MENU API v1 -> v2+ CHANGES
        // ============================================================
        
        // Old: ModMenuApi.getModConfigScreenFactory()
        // New: ModMenuApi.getConfigScreenFactory()
        transformer.registerMethodRedirect(
            "com/terraformersmc/modmenu/api/ModMenuApi",
            "getModConfigScreenFactory",
            "()Ljava/util/function/Function;",
            "com/terraformersmc/modmenu/api/ModMenuApi",
            "getConfigScreenFactory",
            "()Lcom/terraformersmc/modmenu/api/ConfigScreenFactory;"
        );
        
        // Old ConfigScreenFactory interface location
        transformer.registerClassRedirect(
            "io/github/prospector/modmenu/api/ConfigScreenFactory",
            "com/terraformersmc/modmenu/api/ConfigScreenFactory"
        );
        
        // Old ModMenuApi interface location (pre-rename)
        transformer.registerClassRedirect(
            "io/github/prospector/modmenu/api/ModMenuApi",
            "com/terraformersmc/modmenu/api/ModMenuApi"
        );
        
        // ============================================================
        // BADGE SYSTEM CHANGES
        // ============================================================
        
        // Old badge methods
        transformer.registerMethodRedirect(
            "com/terraformersmc/modmenu/api/ModMenuApi",
            "getModBadge",
            "()Ljava/util/Optional;",
            "com/retromod/shim/api/fabric/embedded/ModMenuShim",
            "getModBadge",
            "()Ljava/util/Optional;"
        );
        
        // ============================================================
        // CONFIG SCREEN FACTORY CHANGES
        // ============================================================
        
        // Old: ConfigScreenFactory<Screen> (generic)
        // New: ConfigScreenFactory (non-generic in some versions)
        transformer.registerMethodRedirect(
            "com/terraformersmc/modmenu/api/ConfigScreenFactory",
            "create",
            "(Lnet/minecraft/client/gui/screen/Screen;)Lnet/minecraft/client/gui/screen/Screen;",
            "com/retromod/shim/api/fabric/embedded/ModMenuShim",
            "createConfigScreen",
            "(Ljava/lang/Object;Lnet/minecraft/client/gui/screen/Screen;)Lnet/minecraft/client/gui/screen/Screen;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.ModMenuShim"
        };
    }
}
