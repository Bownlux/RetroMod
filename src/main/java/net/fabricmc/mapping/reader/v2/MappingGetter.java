/*
 * RetroMod Polyfill - Stub for removed Fabric Mapping API
 * Copyright (c) 2026 Bownlux
 */
package net.fabricmc.mapping.reader.v2;

/**
 * Stub replacement for the removed MappingGetter interface.
 */
public interface MappingGetter {
    default String get(int namespace) { return ""; }
    default String getRawName(int namespace) { return ""; }
    default String[] getAllNames() { return new String[0]; }
}
