/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * LibGui API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * LibGui API compatibility shim.
 * 
 * LibGui (by Cotton) is a popular GUI library for Fabric mods.
 * Provides widgets, panels, and screen handling.
 * 
 * API changes:
 * - Widget system changes
 * - Screen handling changes
 * - Networking integration changes
 */
public class LibGuiApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "LibGui API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "4.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "10.0.0";
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
        
        // io.github.cottonmc.cotton.gui -> io.github.cottonmc.cotton.gui
        // Package stayed same but internal changes
        
        // ============================================================
        // SCREEN CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/client/CottonInventoryScreen",
            "io/github/cottonmc/cotton/gui/client/CottonInventoryScreen"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/client/LightweightGuiDescription",
            "io/github/cottonmc/cotton/gui/client/LightweightGuiDescription"
        );
        
        // ============================================================
        // GUI DESCRIPTION CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/SyncedGuiDescription",
            "io/github/cottonmc/cotton/gui/SyncedGuiDescription"
        );
        
        // Old: CottonCraftingController
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/CottonCraftingController",
            "io/github/cottonmc/cotton/gui/SyncedGuiDescription"
        );
        
        // ============================================================
        // WIDGET CHANGES
        // ============================================================
        
        // WWidget base class
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WWidget",
            "io/github/cottonmc/cotton/gui/widget/WWidget"
        );
        
        // WPanel classes
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WPanel",
            "io/github/cottonmc/cotton/gui/widget/WPanel"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WPlainPanel",
            "io/github/cottonmc/cotton/gui/widget/WPlainPanel"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WGridPanel",
            "io/github/cottonmc/cotton/gui/widget/WGridPanel"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WCardPanel",
            "io/github/cottonmc/cotton/gui/widget/WCardPanel"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WTabPanel",
            "io/github/cottonmc/cotton/gui/widget/WTabPanel"
        );
        
        // Interactive widgets
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WButton",
            "io/github/cottonmc/cotton/gui/widget/WButton"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WLabel",
            "io/github/cottonmc/cotton/gui/widget/WLabel"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WTextField",
            "io/github/cottonmc/cotton/gui/widget/WTextField"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WToggleButton",
            "io/github/cottonmc/cotton/gui/widget/WToggleButton"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WSlider",
            "io/github/cottonmc/cotton/gui/widget/WSlider"
        );
        
        // ============================================================
        // SLOT WIDGETS
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WItemSlot",
            "io/github/cottonmc/cotton/gui/widget/WItemSlot"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WPlayerInvPanel",
            "io/github/cottonmc/cotton/gui/widget/WPlayerInvPanel"
        );
        
        // ============================================================
        // BAR WIDGETS
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WBar",
            "io/github/cottonmc/cotton/gui/widget/WBar"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/WBar$Direction",
            "io/github/cottonmc/cotton/gui/widget/WBar$Direction"
        );
        
        // ============================================================
        // ICON/SPRITE CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/icon/Icon",
            "io/github/cottonmc/cotton/gui/widget/icon/Icon"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/icon/ItemIcon",
            "io/github/cottonmc/cotton/gui/widget/icon/ItemIcon"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/icon/TextureIcon",
            "io/github/cottonmc/cotton/gui/widget/icon/TextureIcon"
        );
        
        // ============================================================
        // DATA/PROPERTY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/data/Insets",
            "io/github/cottonmc/cotton/gui/widget/data/Insets"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/data/HorizontalAlignment",
            "io/github/cottonmc/cotton/gui/widget/data/HorizontalAlignment"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/widget/data/VerticalAlignment",
            "io/github/cottonmc/cotton/gui/widget/data/VerticalAlignment"
        );
        
        // ============================================================
        // VALIDATION CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/ValidatedSlot",
            "io/github/cottonmc/cotton/gui/ValidatedSlot"
        );
        
        // ============================================================
        // NETWORKING CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/networking/NetworkSide",
            "io/github/cottonmc/cotton/gui/networking/NetworkSide"
        );
        
        transformer.registerClassRedirect(
            "io/github/cottonmc/cotton/gui/networking/ScreenNetworking",
            "io/github/cottonmc/cotton/gui/networking/ScreenNetworking"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
