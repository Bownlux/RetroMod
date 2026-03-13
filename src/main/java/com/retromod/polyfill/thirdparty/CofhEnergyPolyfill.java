/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.thirdparty;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the removed CoFH / Redstone Flux energy API.
 * The CoFH energy API was replaced by Forge Energy (IEnergyStorage).
 */
public class CofhEnergyPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "CoFH / Redstone Flux Energy API";
    }

    @Override
    public String getCategory() {
        return "thirdparty";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "cofh/api/energy/IEnergyConnection",
            "cofh/api/energy/IEnergyHandler",
            "cofh/api/energy/IEnergyReceiver",
            "cofh/api/energy/IEnergyProvider",
            "cofh/api/energy/IEnergyContainerItem"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "cofh.api.energy.IEnergyConnection",
            "cofh.api.energy.IEnergyHandler",
            "cofh.api.energy.IEnergyReceiver",
            "cofh.api.energy.IEnergyProvider",
            "cofh.api.energy.IEnergyContainerItem"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
