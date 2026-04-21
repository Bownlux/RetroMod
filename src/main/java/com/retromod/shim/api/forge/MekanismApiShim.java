/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Mekanism API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Mekanism API compatibility shim.
 * 
 * Mekanism is one of the largest tech mods with an extensive API
 * for addons and integrations.
 * 
 * API changes:
 * - Gas system changes across versions
 * - Chemical handling changes
 * - TileEntity -> BlockEntity renames
 * - Recipe system changes
 */
public class MekanismApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Mekanism API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "10.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "10.5.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // TILE ENTITY -> BLOCK ENTITY RENAMES
        // ============================================================
        
        transformer.registerClassRedirect(
            "mekanism/common/tile/TileEntityMekanism",
            "mekanism/common/tile/MekanismBlockEntity"
        );
        
        transformer.registerClassRedirect(
            "mekanism/common/tile/base/TileEntityMekanism",
            "mekanism/common/tile/base/MekanismBlockEntity"
        );
        
        // ============================================================
        // GAS/CHEMICAL SYSTEM CHANGES
        // ============================================================
        
        // Gas API
        transformer.registerClassRedirect(
            "mekanism/api/gas/Gas",
            "mekanism/api/chemical/gas/Gas"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/gas/GasStack",
            "mekanism/api/chemical/gas/GasStack"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/gas/IGasHandler",
            "mekanism/api/chemical/gas/IGasHandler"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/gas/GasTank",
            "mekanism/api/chemical/gas/GasTank"
        );
        
        // Infusion API
        transformer.registerClassRedirect(
            "mekanism/api/infuse/InfuseType",
            "mekanism/api/chemical/infuse/InfuseType"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/infuse/InfusionStack",
            "mekanism/api/chemical/infuse/InfusionStack"
        );
        
        // Pigment API
        transformer.registerClassRedirect(
            "mekanism/api/pigment/Pigment",
            "mekanism/api/chemical/pigment/Pigment"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/pigment/PigmentStack",
            "mekanism/api/chemical/pigment/PigmentStack"
        );
        
        // Slurry API
        transformer.registerClassRedirect(
            "mekanism/api/slurry/Slurry",
            "mekanism/api/chemical/slurry/Slurry"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/slurry/SlurryStack",
            "mekanism/api/chemical/slurry/SlurryStack"
        );
        
        // ============================================================
        // CHEMICAL BASE CLASSES
        // ============================================================
        
        transformer.registerClassRedirect(
            "mekanism/api/chemical/Chemical",
            "mekanism/api/chemical/Chemical"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/chemical/ChemicalStack",
            "mekanism/api/chemical/ChemicalStack"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/chemical/IChemicalHandler",
            "mekanism/api/chemical/IChemicalHandler"
        );
        
        // ============================================================
        // RECIPE SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "mekanism/api/recipes/MekanismRecipe",
            "mekanism/api/recipes/MekanismRecipe"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/recipes/inputs/ItemStackIngredient",
            "mekanism/api/recipes/ingredients/ItemStackIngredient"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/recipes/inputs/FluidStackIngredient",
            "mekanism/api/recipes/ingredients/FluidStackIngredient"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/recipes/inputs/GasStackIngredient",
            "mekanism/api/recipes/ingredients/chemical/GasStackIngredient"
        );
        
        // ============================================================
        // ENERGY SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "mekanism/api/energy/IEnergyContainer",
            "mekanism/api/energy/IEnergyContainer"
        );
        
        transformer.registerClassRedirect(
            "mekanism/api/energy/IStrictEnergyHandler",
            "mekanism/api/energy/IStrictEnergyHandler"
        );
        
        // FloatingLong (Mekanism's energy unit)
        transformer.registerClassRedirect(
            "mekanism/api/math/FloatingLong",
            "mekanism/api/math/FloatingLong"
        );
        
        // ============================================================
        // TRANSMITTER SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "mekanism/common/tile/transmitter/TileEntityTransmitter",
            "mekanism/common/tile/transmitter/TransmitterBlockEntity"
        );
        
        // ============================================================
        // MULTIBLOCK CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "mekanism/common/tile/prefab/TileEntityMultiblock",
            "mekanism/common/tile/prefab/MultiblockBlockEntity"
        );
        
        // ============================================================
        // INTEGRATION/CAPABILITY CHANGES
        // ============================================================
        
        // Capabilities
        transformer.registerClassRedirect(
            "mekanism/common/capabilities/Capabilities",
            "mekanism/common/capabilities/MekanismCapabilities"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.MekanismShim"
        };
    }
}
