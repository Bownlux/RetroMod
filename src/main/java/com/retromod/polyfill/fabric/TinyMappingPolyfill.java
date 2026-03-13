/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for the removed net.fabricmc.mapping.reader.v2 package.
 *
 * This API was part of Fabric Loader's TinyV2 mapping reader
 * and was removed in newer Fabric Loader versions. Mods like
 * Not Enough Crashes used TinyVisitor for stack trace deobfuscation.
 *
 * The polyfill provides stub interfaces so the classes can be found,
 * preventing ClassNotFoundException at startup. The actual mapping
 * reading functionality is not replicated (stubs are no-ops).
 */
public class TinyMappingPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Fabric TinyMapping Reader API";
    }

    @Override
    public String getCategory() {
        return "fabric_api";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/fabricmc/mapping/reader/v2/TinyVisitor",
            "net/fabricmc/mapping/reader/v2/TinyV2Visitor",
            "net/fabricmc/mapping/reader/v2/TinyMappingFactory",
            "net/fabricmc/mapping/reader/v2/TinyMetadataConsumer"
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            // Classes at original package path (no redirect needed - direct classpath presence)
            "net.fabricmc.mapping.reader.v2.TinyVisitor",
            "net.fabricmc.mapping.reader.v2.TinyV2Visitor",
            "net.fabricmc.mapping.reader.v2.TinyMappingFactory",
            "net.fabricmc.mapping.reader.v2.TinyMetadata",
            "net.fabricmc.mapping.reader.v2.MappingGetter"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // These polyfill classes are placed at the ORIGINAL package path
        // (net.fabricmc.mapping.reader.v2.*) so they are found directly
        // by the classloader without needing bytecode redirects.
        // This is necessary because mixin code uses these classes via
        // direct references that bypass RetroMod's transformer.

        // Register as embedded shims for logging/tracking
        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
