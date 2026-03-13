/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed Forge core APIs.
 * Covers SidedProxy, RegistryObject, MinecraftForge event bus,
 * capability system (LazyOptional, ICapabilityProvider).
 */
public class ForgeCorePolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Forge Core Removed APIs";
    }

    @Override
    public String getCategory() {
        return "forge";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraftforge/fml/common/SidedProxy",
            "net/minecraftforge/registries/RegistryObject",
            "net/minecraftforge/common/MinecraftForge",
            "net/minecraftforge/common/capabilities/ICapabilityProvider",
            "net/minecraftforge/common/util/LazyOptional"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "net.minecraftforge.fml.common.SidedProxy",
            "net.minecraftforge.registries.RegistryObject",
            "net.minecraftforge.common.MinecraftForge",
            "net.minecraftforge.common.capabilities.ICapabilityProvider",
            "net.minecraftforge.common.util.LazyOptional"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
