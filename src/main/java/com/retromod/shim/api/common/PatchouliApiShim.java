/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Patchouli (Guidebook) API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Patchouli API compatibility shim.
 * 
 * Patchouli provides an in-game documentation/guidebook system.
 * Used by many mods for tutorials and documentation.
 * Works on both Fabric and Forge/NeoForge.
 * 
 * API changes:
 * - v1.x -> v2.x: Book registration changes
 * - Page component changes across versions
 * - Template system changes
 */
public class PatchouliApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Patchouli API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "common";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PATCHOULI API CHANGES
        // ============================================================
        
        // PatchouliAPI.get() -> PatchouliAPI.get()
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI",
            "instance",
            "Lvazkii/patchouli/api/PatchouliAPI$IPatchouliAPI;",
            "vazkii/patchouli/api/PatchouliAPI",
            "get",
            "()Lvazkii/patchouli/api/PatchouliAPI$IPatchouliAPI;"
        );
        
        // ============================================================
        // BOOK OPENING CHANGES
        // ============================================================
        
        // Old: openBookGUI(player, book)
        // New: openBookGUI(book) - client-side only
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "openBookGUI",
            "(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/resources/ResourceLocation;)V",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "openBookGUI",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // MULTIBLOCK CHANGES
        // ============================================================
        
        // IMultiblock registration
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "registerMultiblock",
            "(Lnet/minecraft/resources/ResourceLocation;Lvazkii/patchouli/api/IMultiblock;)Lvazkii/patchouli/api/IMultiblock;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "registerMultiblock",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // Multiblock builder changes
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "makeMultiblock",
            "([Ljava/lang/String;[Ljava/lang/Object;)Lvazkii/patchouli/api/IMultiblock;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "makeMultiblock",
            "(Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // TEMPLATE REGISTRATION
        // ============================================================
        
        // Custom page types
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "registerTemplateAsBuiltin",
            "(Lnet/minecraft/resources/ResourceLocation;Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "registerTemplate",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/Supplier;)V"
        );
        
        // ============================================================
        // BOOK REGISTRY CHANGES
        // ============================================================
        
        // Get book by ID
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "getBook",
            "(Lnet/minecraft/resources/ResourceLocation;)Lvazkii/patchouli/api/IBook;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "getBook",
            "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // STACK INGREDIENT CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/patchouli/api/IStateMatcher",
            "vazkii/patchouli/api/IStateMatcher"
        );
        
        transformer.registerMethodRedirect(
            "vazkii/patchouli/api/PatchouliAPI$IPatchouliAPI",
            "predicateMatcher",
            "(Lnet/minecraft/world/level/block/Block;Ljava/util/function/Predicate;)Lvazkii/patchouli/api/IStateMatcher;",
            "com/retromod/shim/api/common/embedded/PatchouliShim",
            "predicateMatcher",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/util/function/Predicate;)Ljava/lang/Object;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.PatchouliShim"
        };
    }
}
