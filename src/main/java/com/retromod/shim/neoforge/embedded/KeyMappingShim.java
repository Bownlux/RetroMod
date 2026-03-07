/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for KeyMapping changes in NeoForge 1.21.9.
 * Category changed from String to KeyMapping.Category record.
 */
package com.retromod.shim.neoforge.embedded;

import java.lang.reflect.*;
import java.util.*;

/**
 * Shim for net.minecraft.client.KeyMapping
 * 
 * Old constructor: KeyMapping(String name, InputType type, int key, String category)
 * New constructor: KeyMapping(String name, InputType type, int key, KeyMapping.Category category)
 * 
 * This shim bridges the old String-based categories to the new record-based system.
 */
public final class KeyMappingShim {
    
    private static final Map<String, Object> categoryCache = new HashMap<>();
    private static Class<?> categoryClass;
    private static Class<?> keyMappingClass;
    private static Constructor<?> newConstructor;
    private static Constructor<?> categoryConstructor;
    private static boolean initialized = false;
    
    private KeyMappingShim() {}
    
    /**
     * Create a KeyMapping using the old String category API.
     */
    public static Object create(String translationKey, Object inputType, int keyCode, String categoryString) {
        initialize();
        
        try {
            // Convert string category to Category record
            Object category = getOrCreateCategory(categoryString);
            
            // Create KeyMapping with new constructor
            return newConstructor.newInstance(translationKey, inputType, keyCode, category);
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to create KeyMapping", e);
        }
    }
    
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            keyMappingClass = Class.forName("net.minecraft.client.KeyMapping");
            
            // Find the Category inner class/record
            for (Class<?> inner : keyMappingClass.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Category")) {
                    categoryClass = inner;
                    break;
                }
            }
            
            if (categoryClass == null) {
                throw new ClassNotFoundException("KeyMapping.Category");
            }
            
            // Find constructors
            // Category constructor takes Identifier/ResourceLocation
            for (Constructor<?> c : categoryClass.getConstructors()) {
                if (c.getParameterCount() == 1) {
                    categoryConstructor = c;
                    break;
                }
            }
            
            // KeyMapping constructor with Category
            for (Constructor<?> c : keyMappingClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 4 && params[3] == categoryClass) {
                    newConstructor = c;
                    break;
                }
            }
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to initialize KeyMappingShim", e);
        }
    }
    
    /**
     * Get or create a Category from a string.
     * Caches categories to avoid duplicate registration errors.
     */
    private static Object getOrCreateCategory(String categoryString) throws Exception {
        // Check cache
        if (categoryCache.containsKey(categoryString)) {
            return categoryCache.get(categoryString);
        }
        
        // Parse the category string
        // Old format was like "key.categories.modid" or just "modid.keybinds"
        String namespace;
        String path;
        
        if (categoryString.contains(":")) {
            String[] parts = categoryString.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        } else if (categoryString.startsWith("key.categories.")) {
            String rest = categoryString.substring("key.categories.".length());
            namespace = rest.contains(".") ? rest.substring(0, rest.indexOf('.')) : "minecraft";
            path = rest.contains(".") ? rest.substring(rest.indexOf('.') + 1) : rest;
        } else {
            // Use as-is, probably mod's own format
            namespace = "retromod_compat";
            path = categoryString.replace('.', '_').replace(' ', '_').toLowerCase();
        }
        
        // Create Identifier
        Object identifier = com.retromod.shim.fabric.embedded.IdentifierShim.of(namespace, path);
        
        // Create Category
        Object category = categoryConstructor.newInstance(identifier);
        
        // Cache it
        categoryCache.put(categoryString, category);
        
        // Register it if needed
        registerCategory(category);
        
        return category;
    }
    
    /**
     * Register a category with the game's category registry.
     */
    private static void registerCategory(Object category) {
        try {
            // Find RegisterKeyMappingsEvent and register if available
            // This is NeoForge-specific
            
            // For now, categories are auto-registered when used in KeyMapping constructor
            // in newer versions, so we just create and cache them
            
        } catch (Exception e) {
            // Category registration is optional - the mapping will still work
            System.err.println("RetroMod: Could not register key category: " + e);
        }
    }
    
    /**
     * Get all registered categories (for debugging).
     */
    public static Set<String> getRegisteredCategories() {
        return Collections.unmodifiableSet(categoryCache.keySet());
    }
}
