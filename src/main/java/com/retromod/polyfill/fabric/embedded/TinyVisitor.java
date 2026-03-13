/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.fabricmc.mapping.reader.v2.TinyVisitor
 * which was removed from Fabric Loader in newer versions.
 *
 * This interface exists solely to prevent ClassNotFoundException
 * when old mods reference it. Methods throw UnsupportedOperationException
 * since the TinyV2 mapping reader API is no longer functional.
 */
package com.retromod.polyfill.fabric.embedded;

/**
 * Stub replacement for the removed net.fabricmc.mapping.reader.v2.TinyVisitor.
 * Mods that import this class will find this stub instead of crashing.
 */
public interface TinyVisitor {

    default void start(TinyMetadata metadata) {
        // No-op stub
    }

    default void pushClass(MappingGetter name) {
        // No-op stub
    }

    default void pushField(MappingGetter name, String descriptor) {
        // No-op stub
    }

    default void pushMethod(MappingGetter name, String descriptor) {
        // No-op stub
    }

    default void pushParameter(MappingGetter name, int localVariableIndex) {
        // No-op stub
    }

    default void pushLocalVariable(MappingGetter name, int localVariableIndex,
            int localVariableStartOffset, int localVariableTableIndex) {
        // No-op stub
    }

    default void pushComment(String comment) {
        // No-op stub
    }

    default void pop(int count) {
        // No-op stub
    }

    /**
     * Stub for the TinyMetadata interface used by TinyVisitor.
     */
    interface TinyMetadata {
        default int getMajorVersion() { return 2; }
        default int getMinorVersion() { return 0; }
        default java.util.List<String> getNamespaces() { return java.util.List.of(); }
        default java.util.Map<String, String> getProperties() { return java.util.Map.of(); }
    }

    /**
     * Stub for the MappingGetter interface used by TinyVisitor.
     */
    interface MappingGetter {
        default String get(int namespace) { return ""; }
        default String getRawName(int namespace) { return ""; }
        default String[] getAllNames() { return new String[0]; }
    }
}
