/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.fabricmc.mapping.reader.v2.TinyMappingFactory
 * which was the entry point for loading TinyV2 mappings.
 */
package com.retromod.polyfill.fabric.embedded;

import java.io.BufferedReader;

/**
 * Stub replacement for the removed TinyMappingFactory.
 * The load methods return no-op visitors since the actual
 * mapping reader infrastructure no longer exists.
 */
public final class TinyMappingFactory {

    private TinyMappingFactory() {}

    /**
     * Stub: pretends to load mappings but does nothing.
     */
    public static void load(BufferedReader reader, TinyVisitor visitor) {
        // No-op: mapping reader API removed in newer Fabric Loader
    }

    /**
     * Stub: pretends to load mappings but does nothing.
     */
    public static void loadWithDetection(BufferedReader reader, TinyVisitor visitor) {
        // No-op: mapping reader API removed in newer Fabric Loader
    }
}
