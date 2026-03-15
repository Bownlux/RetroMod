/*
 * RetroMod Polyfill - Stub for removed Fabric Mapping API
 * Copyright (c) 2026 Bownlux
 */
package net.fabricmc.mapping.reader.v2;

import java.util.List;
import java.util.Map;

/**
 * Stub replacement for the removed TinyMetadata interface.
 */
public interface TinyMetadata {
    default int getMajorVersion() { return 2; }
    default int getMinorVersion() { return 0; }
    default List<String> getNamespaces() { return List.of(); }
    default Map<String, String> getProperties() { return Map.of(); }
}
