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

import java.lang.reflect.Method;

/**
 * Shim for net.minecraft.util.Identifier
 * 
 * Old usage: new Identifier("namespace", "path")
 * New usage: Identifier.of("namespace", "path")
 * 
 * This shim bridges the gap by providing static methods that
 * delegate to the new API.
 */
public final class IdentifierShim {
    
    private static Method ofMethod;
    private static Method parseMethod;
    private static Class<?> identifierClass;
    
    static {
        try {
            // Try to find the Identifier class (Fabric uses this name)
            try {
                identifierClass = Class.forName("net.minecraft.util.Identifier");
            } catch (ClassNotFoundException e) {
                // NeoForge/Forge might still use ResourceLocation
                identifierClass = Class.forName("net.minecraft.resources.ResourceLocation");
            }
            
            // Find the 'of' method (2 argument version)
            try {
                ofMethod = identifierClass.getMethod("of", String.class, String.class);
            } catch (NoSuchMethodException e) {
                // Try 'fromNamespaceAndPath' (NeoForge naming)
                ofMethod = identifierClass.getMethod("fromNamespaceAndPath", String.class, String.class);
            }
            
            // Find the parse method (1 argument version)
            try {
                parseMethod = identifierClass.getMethod("of", String.class);
            } catch (NoSuchMethodException e) {
                parseMethod = identifierClass.getMethod("parse", String.class);
            }
            
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
