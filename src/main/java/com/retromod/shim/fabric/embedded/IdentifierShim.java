/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for Identifier (formerly ResourceLocation) constructor changes.
 * 
 * In Minecraft 1.21, the Identifier constructor became protected.
 * This shim provides static factory methods to replace constructor calls.
 */
package com.retromod.shim.fabric.embedded;

import com.retromod.util.McReflect;

import java.lang.reflect.Method;

/**
 * Shim for net.minecraft.util.Identifier (yarn) / net.minecraft.resources.ResourceLocation (mojang).
 *
 * Old usage: new Identifier("namespace", "path")
 * New usage: Identifier.of("namespace", "path")
 *
 * This shim bridges the gap by providing static methods that
 * delegate to the new API. Uses McReflect for cross-loader/namespace resolution.
 */
public final class IdentifierShim {

    private static Method ofMethod;
    private static Method parseMethod;
    private static Class<?> identifierClass;

    static {
        try {
            // Use McReflect to find the Identifier class across all loaders/namespaces
            // yarn: net.minecraft.util.Identifier
            // mojang: net.minecraft.resources.ResourceLocation
            // intermediary: resolved via MappingResolver
            identifierClass = McReflect.findClass(
                "net.minecraft.util.Identifier",           // yarn
                "net.minecraft.resources.ResourceLocation" // mojang
            );

            if (identifierClass == null) {
                throw new ClassNotFoundException(
                    "Could not find Identifier/ResourceLocation class on any namespace");
            }

            // Find the 'of' method (2 argument version)
            ofMethod = McReflect.findMethod(identifierClass,
                new Class[]{String.class, String.class},
                "of", "fromNamespaceAndPath");

            if (ofMethod == null) {
                throw new NoSuchMethodException(
                    "Could not find Identifier.of or ResourceLocation.fromNamespaceAndPath");
            }

            // Find the parse method (1 argument version)
            parseMethod = McReflect.findMethod(identifierClass,
                new Class[]{String.class},
                "of", "parse");

        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to initialize IdentifierShim", e);
        }
    }
    
    private IdentifierShim() {
        // Utility class
    }
    
    /**
     * Create an Identifier from namespace and path.
     * Replaces: new Identifier(namespace, path)
     */
    public static Object of(String namespace, String path) {
        try {
            return ofMethod.invoke(null, namespace, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Identifier: " + namespace + ":" + path, e);
        }
    }
    
    /**
     * Parse an Identifier from a string.
     * Replaces: new Identifier("namespace:path")
     */
    public static Object of(String id) {
        try {
            return parseMethod.invoke(null, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Identifier: " + id, e);
        }
    }
    
    /**
     * Create an Identifier for the minecraft namespace.
     * Replaces: new Identifier("minecraft", path)
     */
    public static Object ofVanilla(String path) {
        return of("minecraft", path);
    }
    
    /**
     * Try to parse an Identifier, returning null on failure.
     * Replaces: Identifier.tryParse()
     */
    public static Object tryParse(String id) {
        try {
            Method tryParseMethod = identifierClass.getMethod("tryParse", String.class);
            return tryParseMethod.invoke(null, id);
        } catch (NoSuchMethodException e) {
            // Fallback: try parsing and catch exceptions
            try {
                return of(id);
            } catch (Exception ex) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Check if a string is a valid Identifier.
     */
    public static boolean isValid(String id) {
        return tryParse(id) != null;
    }
}
