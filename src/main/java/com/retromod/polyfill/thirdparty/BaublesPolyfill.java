/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.thirdparty;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the removed Baubles API (1.7.10-1.12.2).
 * Baubles was replaced by Curios in newer versions.
 */
public class BaublesPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Baubles API";
    }

    @Override
    public String getCategory() {
        return "thirdparty";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "baubles/api/IBauble",
            "baubles/api/BaubleType",
            "baubles/api/BaublesApi",
            "baubles/api/cap/IBaublesItemHandler",
            "baubles/api/cap/BaublesCapabilities",
            "baubles/api/render/IRenderBauble"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "baubles.api.IBauble",
            "baubles.api.BaubleType",
            "baubles.api.BaublesApi",
            "baubles.api.cap.IBaublesItemHandler",
            "baubles.api.cap.BaublesCapabilities",
            "baubles.api.render.IRenderBauble"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
