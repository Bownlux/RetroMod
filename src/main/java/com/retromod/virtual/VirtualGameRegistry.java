/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

import java.util.*;

/**
 * Virtual replacement for GameRegistry.
 * This was the main way to register blocks/items in 1.8-1.12.
 */
public class VirtualGameRegistry {
    
    private static final Map<String, Object> BLOCKS = new LinkedHashMap<>();
    private static final Map<String, Object> ITEMS = new LinkedHashMap<>();
    private static final Map<String, Object> TILE_ENTITIES = new LinkedHashMap<>();
    private static final Map<String, Object> ENTITIES = new LinkedHashMap<>();
    
    // Pending registrations queue - bridged to modern registry at runtime
    private static final List<PendingRegistration> PENDING = new ArrayList<>();
    
    public static void registerBlock(Object block, String name) {
        BLOCKS.put(name, block);
        PENDING.add(new PendingRegistration("block", name, block));
        bridgeToModernRegistry("block", name, block);
    }
    
    public static void registerBlock(Object block, Object itemBlock, String name) {
        BLOCKS.put(name, block);
        ITEMS.put(name, itemBlock);
        PENDING.add(new PendingRegistration("block", name, block));
        PENDING.add(new PendingRegistration("item", name, itemBlock));
        bridgeToModernRegistry("block", name, block);
        bridgeToModernRegistry("item", name, itemBlock);
    }
    
    public static void registerItem(Object item, String name) {
        ITEMS.put(name, item);
        PENDING.add(new PendingRegistration("item", name, item));
        bridgeToModernRegistry("item", name, item);
    }
    
    public static void registerTileEntity(Class<?> tileEntityClass, String name) {
        TILE_ENTITIES.put(name, tileEntityClass);
        PENDING.add(new PendingRegistration("block_entity_type", name, tileEntityClass));
        bridgeToModernRegistry("block_entity_type", name, tileEntityClass);
    }
    
    public static void registerEntity(Class<?> entityClass, String name, int id,
            Object mod, int trackingRange, int updateFrequency, boolean sendsVelocityUpdates) {
        ENTITIES.put(name, entityClass);
        PENDING.add(new PendingRegistration("entity_type", name, entityClass));
        bridgeToModernRegistry("entity_type", name, entityClass);
    }
    
    public static void registerWorldGenerator(Object generator, int weight) {
        System.out.println("RetroMod: World generator - modern MC uses data-driven world gen");
    }
    
    public static void addRecipe(Object output, Object... params) {
        System.out.println("RetroMod: Recipe registration - modern MC uses data-driven recipes");
    }
    
    public static void addSmelting(Object input, Object output, float xp) {
        System.out.println("RetroMod: Smelting recipe - modern MC uses data-driven recipes");
    }
    
    public static void addShapedRecipe(Object output, Object... params) {
        System.out.println("RetroMod: Shaped recipe - modern MC uses data-driven recipes");
    }
    
    public static void addShapelessRecipe(Object output, Object... params) {
        System.out.println("RetroMod: Shapeless recipe - modern MC uses data-driven recipes");
    }
    
    private static void bridgeToModernRegistry(String type, String name, Object object) {
        // Will be connected at runtime via reflection
        System.out.println("RetroMod: Queued " + type + " registration: " + name);
    }
    
    public static Object findBlock(String modId, String name) {
        return BLOCKS.get(modId + ":" + name);
    }
    
    public static Object findItem(String modId, String name) {
        return ITEMS.get(modId + ":" + name);
    }
    
    public static List<PendingRegistration> getPendingRegistrations() {
        return Collections.unmodifiableList(PENDING);
    }
    
    public record PendingRegistration(String type, String name, Object object) {}
}
