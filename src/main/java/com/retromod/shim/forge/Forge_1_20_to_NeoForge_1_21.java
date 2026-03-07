/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Transformation shim for Forge 1.20.x → NeoForge 1.21.x migration.
 */
public class Forge_1_20_to_NeoForge_1_21 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge 1.20 to NeoForge 1.21";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.20";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Core package renames
        transformer.registerClassRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "net/neoforged/neoforge/common/NeoForge"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Mod",
            "net/neoforged/fml/common/Mod"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/SubscribeEvent",
            "net/neoforged/bus/api/SubscribeEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/IEventBus",
            "net/neoforged/bus/api/IEventBus"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/eventbus/api/Event",
            "net/neoforged/bus/api/Event"
        );
        
        // Registry classes
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/ForgeRegistries",
            "net/neoforged/neoforge/registries/NeoForgeRegistries"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/DeferredRegister",
            "net/neoforged/neoforge/registries/DeferredRegister"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/RegistryObject",
            "net/neoforged/neoforge/registries/DeferredHolder"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/registries/IForgeRegistry",
            "net/minecraft/core/Registry"
        );
        
        // Capability system
        transformer.registerClassRedirect(
            "net/minecraftforge/common/capabilities/Capability",
            "net/neoforged/neoforge/capabilities/BlockCapability"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/capabilities/CapabilityManager",
            "net/neoforged/neoforge/capabilities/Capabilities"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/common/util/LazyOptional",
            "java/util/Optional"
        );
        
        // Config system
        transformer.registerClassRedirect(
            "net/minecraftforge/common/ForgeConfigSpec",
            "net/neoforged/neoforge/common/ModConfigSpec"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/ModLoadingContext",
            "net/neoforged/fml/ModContainer"
        );
        
        // Network
        transformer.registerClassRedirect(
            "net/minecraftforge/network/NetworkRegistry",
            "net/neoforged/neoforge/network/registration/PayloadRegistrar"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/network/simple/SimpleChannel",
            "net/neoforged/neoforge/network/handling/IPayloadHandler"
        );
        
        // Client events
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/RenderGuiOverlayEvent",
            "net/neoforged/neoforge/client/event/RenderGuiLayerEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/client/event/EntityRenderersEvent",
            "net/neoforged/neoforge/client/event/EntityRenderersEvent"
        );
        
        // Data generation
        transformer.registerClassRedirect(
            "net/minecraftforge/data/event/GatherDataEvent",
            "net/neoforged/neoforge/data/event/GatherDataEvent"
        );
        
        // Field redirects for EVENT_BUS
        transformer.registerFieldRedirect(
            "net/minecraftforge/common/MinecraftForge",
            "EVENT_BUS",
            "net/neoforged/neoforge/common/NeoForge",
            "EVENT_BUS"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.forge.embedded.ForgeRegistriesShim",
            "com.retromod.shim.forge.embedded.CapabilityShim",
            "com.retromod.shim.forge.embedded.NetworkShim"
        };
    }
}
