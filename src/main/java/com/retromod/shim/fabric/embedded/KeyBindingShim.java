/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Shim for KeyBinding changes in Minecraft 1.21.9.
 * Category changed from String to KeyBinding.Category record.
 *
 * Fabric yarn names:
 *   Old: new KeyBinding(String name, InputUtil.Type type, int key, String category)
 *   New: new KeyBinding(String name, InputUtil.Type type, int key, KeyBinding.Category category)
 */
package com.retromod.shim.fabric.embedded;

import com.retromod.util.McReflect;

import java.lang.reflect.*;
import java.util.*;

/**
 * Shim for net.minecraft.client.option.KeyBinding (Fabric yarn name).
 *
 * Bridges the old String-based category constructor to the new Category record
 * introduced in Minecraft 1.21.9. Uses McReflect for cross-namespace class
 * resolution so this works in both dev (yarn names) and production (intermediary).
 */
public final class KeyBindingShim {

    private static final Map<String, Object> categoryCache = new HashMap<>();
    private static Class<?> categoryClass;
    private static Class<?> keyBindingClass;
    private static Constructor<?> newConstructor;
    private static Constructor<?> categoryConstructor;
    private static boolean initialized = false;
    private static boolean initFailed = false;

    private KeyBindingShim() {}

    /**
     * Create a KeyBinding using the old String category API.
     * Called by transformed bytecode in place of the removed constructor.
     *
     * @param translationKey the key binding translation key (e.g. "key.zoomify.zoom")
     * @param inputType      InputUtil.Type enum (KEYSYM, SCANCODE, MOUSE)
     * @param keyCode        the default key code
     * @param categoryString the old-style string category (e.g. "key.categories.misc")
     * @return a new KeyBinding instance
     */
    public static Object create(String translationKey, Object inputType, int keyCode, String categoryString) {
        initialize();

        if (initFailed) {
            // Fallback: try the old constructor directly (might work on versions < 1.21.9)
            return createFallback(translationKey, inputType, keyCode, categoryString);
        }

        try {
            // Convert string category to Category record
            Object category = getOrCreateCategory(categoryString);

            // Create KeyBinding with new constructor
            return newConstructor.newInstance(translationKey, inputType, keyCode, category);

        } catch (Exception e) {
            // If the new constructor fails, try the old one as fallback
            Object fallback = createFallback(translationKey, inputType, keyCode, categoryString);
            if (fallback != null) return fallback;
            throw new RuntimeException("RetroMod: Failed to create KeyBinding for '" + translationKey + "'", e);
        }
    }

    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;

        try {
            // Find KeyBinding class using McReflect (handles yarn, mojang, intermediary)
            keyBindingClass = McReflect.findClass(
                "net.minecraft.client.option.KeyBinding",  // yarn
                "net.minecraft.client.KeyMapping"          // mojang
            );

            if (keyBindingClass == null) {
                initFailed = true;
                return;
            }

            // Find the Category inner class/record
            for (Class<?> inner : keyBindingClass.getDeclaredClasses()) {
                if (inner.getSimpleName().equals("Category") || inner.isRecord()) {
                    // On intermediary, the simple name won't be "Category"
                    // Check if it's a record with 1 component (the Identifier)
                    if (inner.isRecord() && inner.getRecordComponents().length == 1) {
                        categoryClass = inner;
                        break;
                    }
                    if (inner.getSimpleName().equals("Category")) {
                        categoryClass = inner;
                        break;
                    }
                }
            }

            if (categoryClass == null) {
                // Category class doesn't exist — this MC version may still use String categories
                initFailed = true;
                return;
            }

            // Find Category constructor (takes 1 arg: Identifier/ResourceLocation)
            for (Constructor<?> c : categoryClass.getConstructors()) {
                if (c.getParameterCount() == 1) {
                    categoryConstructor = c;
                    break;
                }
            }
            // Also check declared constructors (records may have canonical constructor)
            if (categoryConstructor == null) {
                for (Constructor<?> c : categoryClass.getDeclaredConstructors()) {
                    if (c.getParameterCount() == 1) {
                        c.setAccessible(true);
                        categoryConstructor = c;
                        break;
                    }
                }
            }

            // Find KeyBinding constructor with Category parameter
            for (Constructor<?> c : keyBindingClass.getConstructors()) {
                Class<?>[] params = c.getParameterTypes();
                if (params.length == 4 && params[3] == categoryClass) {
                    newConstructor = c;
                    break;
                }
            }
            if (newConstructor == null) {
                for (Constructor<?> c : keyBindingClass.getDeclaredConstructors()) {
                    Class<?>[] params = c.getParameterTypes();
                    if (params.length == 4 && params[3] == categoryClass) {
                        c.setAccessible(true);
                        newConstructor = c;
                        break;
                    }
                }
            }

            if (newConstructor == null || categoryConstructor == null) {
                initFailed = true;
            }

        } catch (Exception e) {
            initFailed = true;
            System.err.println("RetroMod: KeyBindingShim init warning: " + e.getMessage());
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

        // Parse the category string into namespace:path
        String namespace;
        String path;

        if (categoryString.contains(":")) {
            // Already in namespace:path format
            String[] parts = categoryString.split(":", 2);
            namespace = parts[0];
            path = parts[1];
        } else if (categoryString.startsWith("key.categories.")) {
            // Standard MC format: key.categories.misc, key.categories.movement, etc.
            String rest = categoryString.substring("key.categories.".length());
            namespace = "minecraft";
            path = rest;
        } else {
            // Mod-specific format, use as-is
            namespace = "retromod_compat";
            path = categoryString.replace('.', '_').replace(' ', '_').toLowerCase();
        }

        // Create Identifier using the existing IdentifierShim
        Object identifier = IdentifierShim.of(namespace, path);

        // Create Category record
        Object category = categoryConstructor.newInstance(identifier);

        // Cache it
        categoryCache.put(categoryString, category);

        return category;
    }

    /**
     * Fallback: try to create a KeyBinding using the old String-based constructor.
     * Works on MC versions before 1.21.9 where the constructor still exists.
     */
    private static Object createFallback(String translationKey, Object inputType, int keyCode, String categoryString) {
        if (keyBindingClass == null) {
            keyBindingClass = McReflect.findClass(
                "net.minecraft.client.option.KeyBinding",
                "net.minecraft.client.KeyMapping"
            );
        }
        if (keyBindingClass == null) return null;

        // Try to find and call the old 4-arg constructor with String category
        for (Constructor<?> c : keyBindingClass.getConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 4 && params[3] == String.class) {
                try {
                    return c.newInstance(translationKey, inputType, keyCode, categoryString);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        for (Constructor<?> c : keyBindingClass.getDeclaredConstructors()) {
            Class<?>[] params = c.getParameterTypes();
            if (params.length == 4 && params[3] == String.class) {
                try {
                    c.setAccessible(true);
                    return c.newInstance(translationKey, inputType, keyCode, categoryString);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}
