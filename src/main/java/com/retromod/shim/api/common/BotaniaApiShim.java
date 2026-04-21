/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Botania API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Botania API compatibility shim.
 * 
 * Botania is a popular magic/tech mod with addon support.
 * Works on Fabric and Forge/NeoForge.
 * 
 * API changes:
 * - Mana system interface changes
 * - Flower/functional changes
 * - Lexica Botania integration changes
 */
public class BotaniaApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Botania API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.18.0";
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
        // PACKAGE CHANGES
        // ============================================================
        
        // Main API package - vazkii.botania.api
        transformer.registerClassRedirect(
            "vazkii/botania/api/BotaniaAPI",
            "vazkii/botania/api/BotaniaAPI"
        );
        
        // ============================================================
        // MANA SYSTEM CHANGES
        // ============================================================
        
        // IManaReceiver/IManaProvider
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/IManaReceiver",
            "vazkii/botania/api/mana/ManaReceiver"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/IManaPool",
            "vazkii/botania/api/mana/ManaPool"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/IManaCollector",
            "vazkii/botania/api/mana/ManaCollector"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/IManaSpreader",
            "vazkii/botania/api/mana/ManaSpreader"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/IManaItem",
            "vazkii/botania/api/mana/ManaItem"
        );
        
        // ManaItemHandler
        transformer.registerMethodRedirect(
            "vazkii/botania/api/mana/ManaItemHandler",
            "requestMana",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/player/PlayerEntity;IZ)I",
            "vazkii/botania/api/mana/ManaItemHandler",
            "requestMana",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/player/Player;IZ)I"
        );
        
        // ============================================================
        // FLOWER SYSTEM CHANGES
        // ============================================================
        
        // Functional flowers
        transformer.registerClassRedirect(
            "vazkii/botania/api/subtile/TileEntityFunctionalFlower",
            "vazkii/botania/api/block_entity/FunctionalFlowerBlockEntity"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/subtile/TileEntityGeneratingFlower",
            "vazkii/botania/api/block_entity/GeneratingFlowerBlockEntity"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/subtile/TileEntitySpecialFlower",
            "vazkii/botania/api/block_entity/SpecialFlowerBlockEntity"
        );
        
        // ============================================================
        // CORPOREA SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/corporea/ICorporeaNode",
            "vazkii/botania/api/corporea/CorporeaNode"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/corporea/ICorporeaSpark",
            "vazkii/botania/api/corporea/CorporeaSpark"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/corporea/ICorporeaRequestMatcher",
            "vazkii/botania/api/corporea/CorporeaRequestMatcher"
        );
        
        // ============================================================
        // BREW SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/brew/Brew",
            "vazkii/botania/api/brew/Brew"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/brew/IBrewContainer",
            "vazkii/botania/api/brew/BrewContainer"
        );
        
        // ============================================================
        // RUNE SYSTEM CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/recipe/IRuneAltarRecipe",
            "vazkii/botania/api/recipe/RuneAltarRecipe"
        );
        
        // ============================================================
        // ELVEN TRADE CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/recipe/IElvenTradeRecipe",
            "vazkii/botania/api/recipe/ElvenTradeRecipe"
        );
        
        // ============================================================
        // BLOCK ENTITY CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/subtile/TileEntityBindableSpecialFlower",
            "vazkii/botania/api/block_entity/BindableSpecialFlowerBlockEntity"
        );
        
        // ============================================================
        // WAND INTERFACE CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/wand/IWandable",
            "vazkii/botania/api/block/Wandable"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/wand/IWandBindable",
            "vazkii/botania/api/block/WandBindable"
        );
        
        // ============================================================
        // LENS INTERFACE CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/ILens",
            "vazkii/botania/api/mana/Lens"
        );
        
        transformer.registerClassRedirect(
            "vazkii/botania/api/mana/ICompositableLens",
            "vazkii/botania/api/mana/CompositableLens"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.BotaniaShim"
        };
    }
}
