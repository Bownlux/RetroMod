/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for Forge's ForgeRegistries that bridges to NeoForge registries.
 */
package com.retromod.shim.forge.embedded;

import java.lang.reflect.*;
import java.util.*;

/**
 * Shim for net.minecraftforge.registries.ForgeRegistries
 * 
 * Bridges old Forge registry access patterns to NeoForge.
 * 
 * Old usage: ForgeRegistries.ITEMS.getValue(location)
 * New usage: BuiltInRegistries.ITEM.get(location)
 */
public final class ForgeRegistriesShim {
    
    // Registry instances (lazily initialized)
    private static Object ITEMS;
    private static Object BLOCKS;
    private static Object ENTITY_TYPES;
    private static Object BLOCK_ENTITY_TYPES;
    private static Object MENU_TYPES;
    private static Object SOUND_EVENTS;
    private static Object PARTICLE_TYPES;
    private static Object FLUIDS;
    private static Object POTIONS;
    private static Object ENCHANTMENTS;
    private static Object MOB_EFFECTS;
    private static Object RECIPE_TYPES;
    private static Object RECIPE_SERIALIZERS;
    private static Object PAINTING_VARIANTS;
    private static Object CREATIVE_MODE_TABS;
    
    private static boolean initialized = false;
    
    private ForgeRegistriesShim() {}
    
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            // Try NeoForge registries first
            Class<?> neoRegistries = Class.forName(
                "net.neoforged.neoforge.registries.NeoForgeRegistries"
            );
            
            // If NeoForge exists, we're on NeoForge
            initNeoForgeRegistries(neoRegistries);
            
        } catch (ClassNotFoundException e) {
            try {
                // Fall back to vanilla BuiltInRegistries
                Class<?> builtIn = Class.forName(
                    "net.minecraft.core.registries.BuiltInRegistries"
                );
                initVanillaRegistries(builtIn);
                
            } catch (ClassNotFoundException e2) {
                System.err.println("RetroMod: Could not find registry classes");
            }
        }
    }
    
    private static void initNeoForgeRegistries(Class<?> neoRegistries) throws ClassNotFoundException {
        // NeoForge uses BuiltInRegistries for most vanilla registries
        Class<?> builtIn = Class.forName("net.minecraft.core.registries.BuiltInRegistries");
        
        try {
            ITEMS = builtIn.getField("ITEM").get(null);
            BLOCKS = builtIn.getField("BLOCK").get(null);
            ENTITY_TYPES = builtIn.getField("ENTITY_TYPE").get(null);
            BLOCK_ENTITY_TYPES = builtIn.getField("BLOCK_ENTITY_TYPE").get(null);
            MENU_TYPES = builtIn.getField("MENU").get(null);
            SOUND_EVENTS = builtIn.getField("SOUND_EVENT").get(null);
            PARTICLE_TYPES = builtIn.getField("PARTICLE_TYPE").get(null);
            FLUIDS = builtIn.getField("FLUID").get(null);
            POTIONS = builtIn.getField("POTION").get(null);
            ENCHANTMENTS = builtIn.getField("ENCHANTMENT").get(null);
            MOB_EFFECTS = builtIn.getField("MOB_EFFECT").get(null);
            RECIPE_TYPES = builtIn.getField("RECIPE_TYPE").get(null);
            RECIPE_SERIALIZERS = builtIn.getField("RECIPE_SERIALIZER").get(null);
            PAINTING_VARIANTS = builtIn.getField("PAINTING_VARIANT").get(null);
            CREATIVE_MODE_TABS = builtIn.getField("CREATIVE_MODE_TAB").get(null);
        } catch (Exception e) {
            System.err.println("RetroMod: Failed to initialize registries: " + e);
        }
    }
    
    private static void initVanillaRegistries(Class<?> builtIn) {
        try {
            ITEMS = builtIn.getField("ITEM").get(null);
            BLOCKS = builtIn.getField("BLOCK").get(null);
            ENTITY_TYPES = builtIn.getField("ENTITY_TYPE").get(null);
            BLOCK_ENTITY_TYPES = builtIn.getField("BLOCK_ENTITY_TYPE").get(null);
            MENU_TYPES = builtIn.getField("MENU").get(null);
            SOUND_EVENTS = builtIn.getField("SOUND_EVENT").get(null);
            PARTICLE_TYPES = builtIn.getField("PARTICLE_TYPE").get(null);
            FLUIDS = builtIn.getField("FLUID").get(null);
        } catch (Exception e) {
            System.err.println("RetroMod: Failed to initialize vanilla registries: " + e);
        }
    }
    
    // Accessor methods that match old ForgeRegistries API
    
    public static Object getItems() {
        initialize();
        return ITEMS;
    }
    
    public static Object getBlocks() {
        initialize();
        return BLOCKS;
    }
    
    public static Object getEntityTypes() {
        initialize();
        return ENTITY_TYPES;
    }
    
    public static Object getBlockEntityTypes() {
        initialize();
        return BLOCK_ENTITY_TYPES;
    }
    
    public static Object getMenuTypes() {
        initialize();
        return MENU_TYPES;
    }
    
    public static Object getSoundEvents() {
        initialize();
        return SOUND_EVENTS;
    }
    
    public static Object getParticleTypes() {
        initialize();
        return PARTICLE_TYPES;
    }
    
    public static Object getFluids() {
        initialize();
        return FLUIDS;
    }
    
    public static Object getPotions() {
        initialize();
        return POTIONS;
    }
    
    public static Object getEnchantments() {
        initialize();
        return ENCHANTMENTS;
    }
    
    public static Object getMobEffects() {
        initialize();
        return MOB_EFFECTS;
    }
    
    /**
     * Get a value from a registry by location.
     * Bridges ForgeRegistries.X.getValue(location) to Registry.get(location)
     */
    public static Object getValue(Object registry, Object location) {
        if (registry == null || location == null) return null;
        
        try {
            // Try get() method (vanilla Registry)
            Method getMethod = registry.getClass().getMethod("get", location.getClass());
            return getMethod.invoke(registry, location);
        } catch (NoSuchMethodException e) {
            try {
                // Try getValue() method (old Forge)
                Method getValueMethod = registry.getClass().getMethod("getValue", location.getClass());
                return getValueMethod.invoke(registry, location);
            } catch (Exception e2) {
                System.err.println("RetroMod: Could not get registry value: " + e2);
            }
        } catch (Exception e) {
            System.err.println("RetroMod: Registry lookup failed: " + e);
        }
        
        return null;
    }
    
    /**
     * Get the key (ResourceLocation) for a registry value.
     * Bridges ForgeRegistries.X.getKey(value) to Registry.getKey(value)
     */
    public static Object getKey(Object registry, Object value) {
        if (registry == null || value == null) return null;
        
        try {
            Method getKeyMethod = registry.getClass().getMethod("getKey", Object.class);
            return getKeyMethod.invoke(registry, value);
        } catch (Exception e) {
            System.err.println("RetroMod: Could not get registry key: " + e);
        }
        
        return null;
    }
    
    /**
     * Check if a registry contains a key.
     */
    public static boolean containsKey(Object registry, Object location) {
        if (registry == null || location == null) return false;
        
        try {
            Method containsKeyMethod = registry.getClass().getMethod("containsKey", location.getClass());
            return (boolean) containsKeyMethod.invoke(registry, location);
        } catch (Exception e) {
            // Fallback: try to get and check null
            return getValue(registry, location) != null;
        }
    }
    
    /**
     * Get all keys in a registry.
     */
    @SuppressWarnings("unchecked")
    public static Set<Object> getKeys(Object registry) {
        if (registry == null) return Collections.emptySet();
        
        try {
            Method keySetMethod = registry.getClass().getMethod("keySet");
            return (Set<Object>) keySetMethod.invoke(registry);
        } catch (Exception e) {
            System.err.println("RetroMod: Could not get registry keys: " + e);
            return Collections.emptySet();
        }
    }
    
    /**
     * Get all values in a registry.
     */
    @SuppressWarnings("unchecked")
    public static Collection<Object> getValues(Object registry) {
        if (registry == null) return Collections.emptyList();
        
        try {
            // Try stream() or forEach approach
            Method iteratorMethod = registry.getClass().getMethod("iterator");
            Iterator<Object> iter = (Iterator<Object>) iteratorMethod.invoke(registry);
            
            List<Object> values = new ArrayList<>();
            while (iter.hasNext()) {
                values.add(iter.next());
            }
            return values;
            
        } catch (Exception e) {
            System.err.println("RetroMod: Could not get registry values: " + e);
            return Collections.emptyList();
        }
    }
}
