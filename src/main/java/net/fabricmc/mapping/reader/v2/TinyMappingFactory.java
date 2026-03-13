/*
 * RetroMod Polyfill - Stub for removed Fabric Mapping API
 * Copyright (c) 2026 Bownlux
 */
package net.fabricmc.mapping.reader.v2;

import java.io.BufferedReader;

/**
 * Stub replacement for the removed TinyMappingFactory.
 * Load methods are no-ops since the mapping reader was removed.
 */
public final class TinyMappingFactory {

    private TinyMappingFactory() {}

    public static void load(BufferedReader reader, TinyVisitor visitor) {
        // No-op: mapping reader API removed
    }

    public static void loadWithDetection(BufferedReader reader, TinyVisitor visitor) {
        // No-op: mapping reader API removed
    }
}
