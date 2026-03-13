/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.thirdparty;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the removed WAILA (What Am I Looking At) API.
 * WAILA was replaced by Jade, which maintains similar names
 * but in a different package (snownee.jade.api).
 */
public class WailaPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "WAILA (What Am I Looking At) API";
    }

    @Override
    public String getCategory() {
        return "thirdparty";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "mcp/mobius/waila/api/IWailaPlugin",
            "mcp/mobius/waila/api/IWailaRegistrar",
            "mcp/mobius/waila/api/IWailaDataProvider",
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "mcp/mobius/waila/api/IWailaConfigHandler"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "mcp.mobius.waila.api.IWailaPlugin",
            "mcp.mobius.waila.api.IWailaRegistrar",
            "mcp.mobius.waila.api.IWailaDataProvider",
            "mcp.mobius.waila.api.IWailaDataAccessor",
            "mcp.mobius.waila.api.IWailaConfigHandler"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
