/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed NeoForge APIs.
 * Covers Transfer API (ItemStackHandler, ComponentItemHandler),
 * rendering events (RenderHighlightEvent), and annotation changes.
 */
public class NeoForgeCorePolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "NeoForge Core Removed APIs";
    }

    @Override
    public String getCategory() {
        return "neoforge";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/neoforged/neoforge/items/ItemStackHandler",
            "net/neoforged/neoforge/items/ComponentItemHandler",
            "net/neoforged/neoforge/client/event/RenderHighlightEvent",
            "net/neoforged/neoforge/client/event/RenderHighlightEvent$Block",
            "javax/annotation/Nullable",
            "javax/annotation/Nonnull"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "net.neoforged.neoforge.items.ItemStackHandler",
            "net.neoforged.neoforge.items.ComponentItemHandler",
            "net.neoforged.neoforge.client.event.RenderHighlightEvent",
            "javax.annotation.Nullable",
            "javax.annotation.Nonnull"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
