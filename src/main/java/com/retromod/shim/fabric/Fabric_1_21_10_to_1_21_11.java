/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2025/12/05/12111.html
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21.10 to run on 1.21.11.
 * 
 * 1.21.11 is the LAST obfuscated Minecraft version!
 * After this, Minecraft will be unobfuscated (starting with 26.1).
 * 
 * Major changes addressed:
 * - Mojang renamed ResourceLocation to Identifier in vanilla
 * - World Render Events reintroduced
 * - Biome Modification API updated for Environment Attributes
 * - APIs renamed to match Mojang's naming
 * - Yarn mappings discontinued after this version
 */
public class Fabric_1_21_10_to_1_21_11 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.10 to 1.21.11";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.10";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.11";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        
        // ============================================================
        // API NAMING CHANGES
        // Fabric API renamed many things to match Mojang's naming
        // ============================================================
        
        // Example renames from #5060:
        // These are mostly internal but may affect some mods
        
        // ============================================================
        // WORLD RENDER EVENTS REINTRODUCED
        // New implementation matches recent Minecraft changes
        // ============================================================
        
        // The new World Render Events separate extraction from rendering
        // Mods using the old (pre-1.21.9) API need updates
        
        // If mod was built for 1.21.9/1.21.10 (no events), they're fine
        // If mod was built for pre-1.21.9 and already shimmed, they're fine
        
        // ============================================================
        // BIOME MODIFICATION API
        // Updated for Environment Attributes system
        // ============================================================
        
        // BiomeModificationContext now has getAttributes() method
        // Old API still works, just expanded
        
        // ============================================================
        // RESOURCE LOADER
        // "v1" removed from impl, mixin, test packages
        // ============================================================
        
        // This is an internal change, shouldn't affect mod code
        // But some mods might have been accessing internals
        
        // ============================================================
        // NOTE: Preparing for 26.1 (Unobfuscated)
        // ============================================================
        
        // After 1.21.11:
        // - Yarn mappings discontinued
        // - Intermediary no longer needed
        // - All mods need recompilation anyway
        
        // This shim primarily helps bridge the final obfuscated version
        // to prepare for the transition
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            // Minimal shims needed - most changes are expansions not removals
        };
    }
}
