/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * Create Mod API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Create mod API compatibility shim.
 * 
 * Create is one of the most popular tech/automation mods.
 * Many addons depend on its API for kinetic systems, processing, etc.
 * 
 * Works on both Fabric and Forge/NeoForge.
 * 
 * API changes:
 * - 0.3.x -> 0.4.x: Package restructure
 * - 0.4.x -> 0.5.x: Kinetic API changes
 * - 0.5.x -> 0.6.x: 1.20+ adaptations
 */
public class CreateModApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Create Mod API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "0.3.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "0.6.0";
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
        
        // Old package locations
        transformer.registerClassRedirect(
            "com/simibubi/create/content/contraptions/base/KineticTileEntity",
            "com/simibubi/create/content/kinetics/base/KineticBlockEntity"
        );
        
        transformer.registerClassRedirect(
            "com/simibubi/create/content/contraptions/components/mixer/MixingRecipe",
            "com/simibubi/create/content/kinetics/mixer/MixingRecipe"
        );
        
        // TileEntity -> BlockEntity rename
        transformer.registerClassRedirect(
            "com/simibubi/create/foundation/tileEntity/SmartTileEntity",
            "com/simibubi/create/foundation/blockEntity/SmartBlockEntity"
        );
        
        transformer.registerClassRedirect(
            "com/simibubi/create/foundation/tileEntity/TileEntityBehaviour",
            "com/simibubi/create/foundation/blockEntity/behaviour/BlockEntityBehaviour"
        );
        
        // ============================================================
        // KINETIC API CHANGES
        // ============================================================
        
        // Old: KineticTileEntity.getSpeed()
        transformer.registerMethodRedirect(
            "com/simibubi/create/content/contraptions/base/KineticTileEntity",
            "getSpeed",
            "()F",
            "com/simibubi/create/content/kinetics/base/KineticBlockEntity",
            "getSpeed",
            "()F"
        );
        
        // Stress calculations
        transformer.registerMethodRedirect(
            "com/simibubi/create/content/contraptions/base/KineticTileEntity",
            "calculateStressApplied",
            "()F",
            "com/simibubi/create/content/kinetics/base/KineticBlockEntity",
            "calculateStressApplied",
            "()F"
        );
        
        // ============================================================
        // RECIPE CHANGES
        // ============================================================
        
        // ProcessingRecipe changes
        transformer.registerClassRedirect(
            "com/simibubi/create/content/contraptions/processing/ProcessingRecipe",
            "com/simibubi/create/content/processing/recipe/ProcessingRecipe"
        );
        
        transformer.registerClassRedirect(
            "com/simibubi/create/content/contraptions/processing/ProcessingRecipeBuilder",
            "com/simibubi/create/content/processing/recipe/ProcessingRecipeBuilder"
        );
        
        // ============================================================
        // FLUID HANDLING CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "com/simibubi/create/foundation/fluid/SmartFluidTank",
            "com/simibubi/create/foundation/fluid/SmartFluidTank"
        );
        
        // ============================================================
        // PONDER (TUTORIAL) SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "com/simibubi/create/foundation/ponder/PonderRegistry",
            "com/simibubi/create/infrastructure/ponder/PonderIndex"
        );
        
        // ============================================================
        // SCHEMATIC CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "com/simibubi/create/content/schematics/SchematicWorld",
            "com/simibubi/create/content/schematics/SchematicWorld"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
