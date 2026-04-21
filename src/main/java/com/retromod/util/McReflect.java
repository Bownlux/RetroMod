/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cross-loader Minecraft class and member resolution utility.
 *
 * Minecraft classes have different names depending on the mod loader:
 *   - Yarn (Fabric dev):    net.minecraft.client.gui.screen.TitleScreen
 *   - Intermediary (Fabric prod): net.minecraft.class_442
 *   - Mojang (NeoForge/Forge):    net.minecraft.client.gui.screens.TitleScreen
 *
 * This utility tries multiple name variants and uses Fabric's MappingResolver
 * (when available) to translate yarn names to intermediary for production Fabric.
 *
 * Non-Minecraft classes (e.g. net.fabricmc.*, net.neoforged.*) are NOT obfuscated
 * and can be resolved with plain Class.forName().
 */
public final class McReflect {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Reflect");

    // Cache resolved classes to avoid repeated lookups
    private static final Map<String, Class<?>> CLASS_CACHE = new ConcurrentHashMap<>();

    // Fabric MappingResolver (lazily initialized, null if not on Fabric)
    private static volatile Object mappingResolver;
    private static volatile Method mapClassNameMethod;
    private static volatile boolean resolverInitialized = false;

    private McReflect() {}

    // =====================================================================
    // Class Resolution
    // =====================================================================

    /**
     * Find a Minecraft class by trying multiple name variants.
     *
     * Checks in order:
     *   1. Each provided name via Class.forName (handles dev + Mojang names)
     *   2. Fabric MappingResolver yarn->intermediary for the first name
     *
     * @param names one or more possible class names (yarn first, then mojang, etc.)
     * @return the resolved Class, or null if not found
     */
    public static Class<?> findClass(String... names) {
        if (names == null || names.length == 0) return null;

        // Check cache (keyed on first name)
        String cacheKey = names[0];
        Class<?> cached = CLASS_CACHE.get(cacheKey);
        if (cached != null) return cached;

        // Try each name directly
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            try {
                Class<?> c = Class.forName(name);
                CLASS_CACHE.put(cacheKey, c);
                return c;
            } catch (ClassNotFoundException ignored) {
                // Try next name
            }
        }

        // Try Fabric MappingResolver: map first name (yarn) -> intermediary
        Class<?> mapped = findClassViaMappingResolver(names[0]);
        if (mapped != null) {
            CLASS_CACHE.put(cacheKey, mapped);
            return mapped;
        }

        LOGGER.debug("Could not resolve MC class from names: {}", String.join(", ", names));
        return null;
    }

    /**
     * Find a Minecraft class, throwing if not found.
     */
    public static Class<?> requireClass(String... names) {
        Class<?> c = findClass(names);
        if (c == null) {
            throw new RuntimeException("Required MC class not found: " + String.join(", ", names));
        }
        return c;
    }

    // =====================================================================
    // Method Resolution
    // =====================================================================

    /**
     * Find a method by trying multiple name variants.
     *
     * @param clazz      the class to search
     * @param paramTypes  the parameter types (use null for no-arg methods)
     * @param names       one or more possible method names
     * @return the Method, or null if not found
     */
    public static Method findMethod(Class<?> clazz, Class<?>[] paramTypes, String... names) {
        if (clazz == null) return null;

        for (String name : names) {
            if (name == null) continue;
            try {
                Method m;
                if (paramTypes != null) {
                    m = clazz.getMethod(name, paramTypes);
                } else {
                    m = clazz.getMethod(name);
                }
                return m;
            } catch (NoSuchMethodException ignored) {
                // Try declared methods too
                try {
                    Method m;
                    if (paramTypes != null) {
                        m = clazz.getDeclaredMethod(name, paramTypes);
                    } else {
                        m = clazz.getDeclaredMethod(name);
                    }
                    m.setAccessible(true);
                    return m;
                } catch (NoSuchMethodException ignored2) {
                    // Try next name
                }
            }
        }

        // Last resort: search by name only (first match, ignoring parameter types)
        for (String name : names) {
            if (name == null) continue;
            for (Method m : clazz.getDeclaredMethods()) {
                if (m.getName().equals(name)) {
                    m.setAccessible(true);
                    return m;
                }
            }
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
        }

        return null;
    }

    /**
     * Find a no-arg method by trying multiple name variants.
     */
    public static Method findMethod(Class<?> clazz, String... names) {
        return findMethod(clazz, null, names);
    }

    // =====================================================================
    // Field Resolution
    // =====================================================================

    /**
     * Find a field by trying multiple name variants.
     *
     * @param clazz the class to search
     * @param names one or more possible field names
     * @return the Field, or null if not found
     */
    public static Field findField(Class<?> clazz, String... names) {
        if (clazz == null) return null;

        for (String name : names) {
            if (name == null) continue;
            try {
                return clazz.getField(name);
            } catch (NoSuchFieldException ignored) {
                try {
                    Field f = clazz.getDeclaredField(name);
                    f.setAccessible(true);
                    return f;
                } catch (NoSuchFieldException ignored2) {
                    // Try next name
                }
            }
        }

        return null;
    }

    /**
     * Get an int field value, trying multiple field names.
     *
     * @param obj          the object instance
     * @param clazz        the class to look up the field on
     * @param defaultValue fallback if field not found
     * @param names        one or more possible field names
     * @return the field value, or defaultValue if not found
     */
    public static int getIntField(Object obj, Class<?> clazz, int defaultValue, String... names) {
        Field f = findField(clazz, names);
        if (f != null) {
            try {
                return f.getInt(obj);
            } catch (Exception e) {
                LOGGER.debug("Could not read int field: {}", e.getMessage());
            }
        }
        return defaultValue;
    }

    /**
     * Get an Object field value, trying multiple field names.
     */
    public static Object getField(Object obj, Class<?> clazz, String... names) {
        Field f = findField(clazz, names);
        if (f != null) {
            try {
                return f.get(obj);
            } catch (Exception e) {
                LOGGER.debug("Could not read field: {}", e.getMessage());
            }
        }
        return null;
    }

    // =====================================================================
    // Loader Detection
    // =====================================================================

    /**
     * Check if a class exists on the classpath.
     */
    public static boolean classExists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    /**
     * Check if we're running on Fabric.
     */
    public static boolean isFabric() {
        return classExists("net.fabricmc.loader.api.FabricLoader");
    }

    /**
     * Check if we're running on NeoForge.
     */
    public static boolean isNeoForge() {
        return classExists("net.neoforged.neoforge.common.NeoForge");
    }

    /**
     * Check if we're running on Forge (legacy).
     */
    public static boolean isForge() {
        return classExists("net.minecraftforge.common.MinecraftForge");
    }

    // =====================================================================
    // Fabric MappingResolver
    // =====================================================================

    /**
     * Try to resolve a yarn class name to the runtime name using Fabric's MappingResolver.
     * Returns null if not on Fabric or if mapping fails.
     */
    private static Class<?> findClassViaMappingResolver(String yarnName) {
        initResolver();
        if (mapClassNameMethod == null || mappingResolver == null) return null;

        try {
            String mapped = (String) mapClassNameMethod.invoke(mappingResolver, "named", yarnName);
            if (mapped != null && !mapped.equals(yarnName)) {
                return Class.forName(mapped);
            }
        } catch (Exception e) {
            LOGGER.debug("MappingResolver lookup failed for {}: {}", yarnName, e.getMessage());
        }
        return null;
    }

    /**
     * Initialize the Fabric MappingResolver (once, thread-safe).
     */
    private static void initResolver() {
        if (resolverInitialized) return;
        synchronized (McReflect.class) {
            if (resolverInitialized) return;
            resolverInitialized = true;

            try {
                Class<?> loaderClass = Class.forName("net.fabricmc.loader.api.FabricLoader");
                Object loader = loaderClass.getMethod("getInstance").invoke(null);
                mappingResolver = loaderClass.getMethod("getMappingResolver").invoke(loader);

                // Find the mapClassName method on the resolver
                for (Method m : mappingResolver.getClass().getMethods()) {
                    if ("mapClassName".equals(m.getName()) && m.getParameterCount() == 2) {
                        mapClassNameMethod = m;
                        break;
                    }
                }

                if (mapClassNameMethod != null) {
                    LOGGER.debug("Fabric MappingResolver initialized");
                }
            } catch (Exception e) {
                LOGGER.debug("Fabric MappingResolver not available (not on Fabric): {}", e.getMessage());
            }
        }
    }
}
