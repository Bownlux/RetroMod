/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Forge Event System API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge Event System API compatibility shim.
 * 
 * Handles changes in Forge's event system between Forge and NeoForge.
 * This is critical for mod compatibility as most mods use events.
 * 
 * API changes:
 * - Event class package changes
 * - Event bus registration changes
 * - Event result/outcome changes
 */
public class ForgeEventApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge Event System API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.20.1";
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
        // EVENT BUS CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/IEventBus",
            "net/neoforged/bus/api/IEventBus"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/Event",
            "net/neoforged/bus/api/Event"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/SubscribeEvent",
            "net/neoforged/bus/api/SubscribeEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/EventPriority",
            "net/neoforged/bus/api/EventPriority"
        );
        
        // Event.Result -> EventResult
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/Event$Result",
            "net/neoforged/bus/api/EventResult"
        );
        
        // ============================================================
        // COMMON EVENTS PACKAGE CHANGES
        // ============================================================
        
        // Forge common events -> NeoForge
        transformer.registerClassRedirect(
            "net/minecraftforge/event/TickEvent",
            "net/neoforged/neoforge/event/tick/TickEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/TickEvent$ServerTickEvent",
            "net/neoforged/neoforge/event/tick/ServerTickEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/TickEvent$ClientTickEvent",
            "net/neoforged/neoforge/event/tick/ClientTickEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/TickEvent$LevelTickEvent",
            "net/neoforged/neoforge/event/tick/LevelTickEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/TickEvent$PlayerTickEvent",
            "net/neoforged/neoforge/event/tick/PlayerTickEvent"
        );
        
        // ============================================================
        // ENTITY EVENTS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/EntityEvent",
            "net/neoforged/neoforge/event/entity/EntityEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/EntityJoinWorldEvent",
            "net/neoforged/neoforge/event/entity/EntityJoinLevelEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/EntityJoinLevelEvent",
            "net/neoforged/neoforge/event/entity/EntityJoinLevelEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/living/LivingEvent",
            "net/neoforged/neoforge/event/entity/living/LivingEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/living/LivingDeathEvent",
            "net/neoforged/neoforge/event/entity/living/LivingDeathEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/living/LivingHurtEvent",
            "net/neoforged/neoforge/event/entity/living/LivingDamageEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/living/LivingDamageEvent",
            "net/neoforged/neoforge/event/entity/living/LivingDamageEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/living/LivingDropsEvent",
            "net/neoforged/neoforge/event/entity/living/LivingDropsEvent"
        );
        
        // ============================================================
        // PLAYER EVENTS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/player/PlayerEvent",
            "net/neoforged/neoforge/event/entity/player/PlayerEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/player/PlayerInteractEvent",
            "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock",
            "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickBlock"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickItem",
            "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$RightClickItem"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/player/PlayerInteractEvent$LeftClickBlock",
            "net/neoforged/neoforge/event/entity/player/PlayerInteractEvent$LeftClickBlock"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/entity/player/ItemTooltipEvent",
            "net/neoforged/neoforge/event/entity/player/ItemTooltipEvent"
        );
        
        // ============================================================
        // WORLD/LEVEL EVENTS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/world/WorldEvent",
            "net/neoforged/neoforge/event/level/LevelEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/level/LevelEvent",
            "net/neoforged/neoforge/event/level/LevelEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/world/BlockEvent",
            "net/neoforged/neoforge/event/level/BlockEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/level/BlockEvent",
            "net/neoforged/neoforge/event/level/BlockEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/world/ChunkEvent",
            "net/neoforged/neoforge/event/level/ChunkEvent"
        );
        
        // ============================================================
        // CLIENT EVENTS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderGuiOverlayEvent",
            "net/neoforged/neoforge/client/event/RenderGuiLayerEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderLevelStageEvent",
            "net/neoforged/neoforge/client/event/RenderLevelStageEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/InputEvent",
            "net/neoforged/neoforge/client/event/InputEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/InputEvent$Key",
            "net/neoforged/neoforge/client/event/InputEvent$Key"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/ScreenEvent",
            "net/neoforged/neoforge/client/event/ScreenEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderPlayerEvent",
            "net/neoforged/neoforge/client/event/RenderPlayerEvent"
        );
        
        // ============================================================
        // REGISTRATION EVENTS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/event/RegistryEvent",
            "net/neoforged/neoforge/registries/RegisterEvent"
        );
        
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/RegisterEvent",
            "net/neoforged/neoforge/registries/RegisterEvent"
        );
        
        // ============================================================
        // FORGE BUS -> NEOFORGE BUS
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "net/neoforged/neoforge/common/NeoForge"
        );
        
        transformer.registerFieldRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "EVENT_BUS",
            "Lnet/minecraftforge/eventbus/api/IEventBus;",
            "net/neoforged/neoforge/common/NeoForge",
            "EVENT_BUS",
            "Lnet/neoforged/bus/api/IEventBus;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {};
    }
}
