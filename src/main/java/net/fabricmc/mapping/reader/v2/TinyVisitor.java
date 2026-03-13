/*
 * RetroMod Polyfill - Stub for removed Fabric Mapping API
 * Copyright (c) 2026 Bownlux
 *
 * This class exists at the ORIGINAL package path so that mods
 * (including mixin code) can find it without bytecode rewriting.
 * The Fabric mapping reader v2 API was removed from Fabric Loader.
 */
package net.fabricmc.mapping.reader.v2;

/**
 * Stub replacement for the removed TinyVisitor interface.
 * All methods are default no-ops.
 */
public interface TinyVisitor {

    default void start(TinyMetadata metadata) {}

    default void pushClass(MappingGetter name) {}

    default void pushField(MappingGetter name, String descriptor) {}

    default void pushMethod(MappingGetter name, String descriptor) {}

    default void pushParameter(MappingGetter name, int localVariableIndex) {}

    default void pushLocalVariable(MappingGetter name, int localVariableIndex,
            int localVariableStartOffset, int localVariableTableIndex) {}

    default void pushComment(String comment) {}

    default void pop(int count) {}
}
