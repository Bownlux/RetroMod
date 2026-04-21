/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Applied Energistics 2 API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Applied Energistics 2 API compatibility shim.
 * 
 * AE2 is a popular storage/automation mod with many addons.
 * Works on Fabric (via AE2 Fabric) and Forge/NeoForge.
 * 
 * API changes:
 * - Grid system changes
 * - Storage channel changes
 * - Cell system changes across versions
 */
public class Ae2ApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Applied Energistics 2 API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "12.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "15.0.0";
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
        
        // Main API package
        transformer.registerClassRedirect(
            "appeng/api/AEApi",
            "appeng/api/AEApi"
        );
        
        // ============================================================
        // STORAGE SYSTEM CHANGES
        // ============================================================
        
        // IAEStack changes
        transformer.registerClassRedirect(
            "appeng/api/storage/data/IAEStack",
            "appeng/api/stacks/AEKey"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/storage/data/IAEItemStack",
            "appeng/api/stacks/AEItemKey"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/storage/data/IAEFluidStack",
            "appeng/api/stacks/AEFluidKey"
        );
        
        // Storage channels
        transformer.registerClassRedirect(
            "appeng/api/storage/IStorageChannel",
            "appeng/api/stacks/AEKeyType"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/storage/channels/IItemStorageChannel",
            "appeng/api/stacks/AEKeyTypes"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/storage/channels/IFluidStorageChannel",
            "appeng/api/stacks/AEKeyTypes"
        );
        
        // ============================================================
        // CELL SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "appeng/api/storage/ICellInventory",
            "appeng/api/storage/cells/ICellHandler"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/storage/ICellHandler",
            "appeng/api/storage/cells/ICellHandler"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/storage/ICellWorkbenchItem",
            "appeng/api/storage/cells/ICellWorkbenchItem"
        );
        
        // ============================================================
        // GRID SYSTEM CHANGES
        // ============================================================
        
        // IGrid interface
        transformer.registerClassRedirect(
            "appeng/api/networking/IGrid",
            "appeng/api/networking/IGrid"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/networking/IGridNode",
            "appeng/api/networking/IGridNode"
        );
        
        // Grid services
        transformer.registerClassRedirect(
            "appeng/api/networking/IGridCache",
            "appeng/api/networking/IGridService"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/networking/security/ISecurityGrid",
            "appeng/api/networking/security/ISecurityService"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/networking/storage/IStorageGrid",
            "appeng/api/networking/storage/IStorageService"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/networking/crafting/ICraftingGrid",
            "appeng/api/networking/crafting/ICraftingService"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/networking/energy/IEnergyGrid",
            "appeng/api/networking/energy/IEnergyService"
        );
        
        // ============================================================
        // CRAFTING SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "appeng/api/networking/crafting/ICraftingPatternDetails",
            "appeng/api/crafting/IPatternDetails"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/networking/crafting/ICraftingJob",
            "appeng/api/networking/crafting/ICraftingPlan"
        );
        
        // ============================================================
        // PART SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "appeng/api/parts/IPart",
            "appeng/api/parts/IPart"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/parts/IPartHost",
            "appeng/api/parts/IPartHost"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/parts/IPartItem",
            "appeng/api/parts/IPartItem"
        );
        
        // ============================================================
        // TILE ENTITY -> BLOCK ENTITY
        // ============================================================
        
        transformer.registerClassRedirect(
            "appeng/tile/AEBaseTileEntity",
            "appeng/blockentity/AEBaseBlockEntity"
        );
        
        transformer.registerClassRedirect(
            "appeng/tile/networking/CableBusTileEntity",
            "appeng/blockentity/networking/CableBusBlockEntity"
        );
        
        // ============================================================
        // UTILITY CLASSES
        // ============================================================
        
        transformer.registerClassRedirect(
            "appeng/api/util/AEColor",
            "appeng/api/util/AEColor"
        );
        
        transformer.registerClassRedirect(
            "appeng/api/util/AECableType",
            "appeng/api/util/AECableType"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.Ae2Shim"
        };
    }
}
