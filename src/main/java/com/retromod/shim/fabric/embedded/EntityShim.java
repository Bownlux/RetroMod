/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * ENTITY SHIM
 * 
 * Provides compatibility for Entity API changes between versions.
 * Uses reflection to work without Minecraft compile-time dependency.
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.*;

/**
 * Shim for Entity class changes across Minecraft versions.
 * 
 * Key changes handled:
 * - 1.21.9+: Entity.getWorld() renamed to Entity.getEntityWorld()
 * - Various method signature changes across versions
 */
public class EntityShim {
    
    private static Method getWorldMethod = null;
    private static Method getEntityWorldMethod = null;
    private static Method getBlockPosMethod = null;
    private static boolean methodsInitialized = false;
    
    /**
     * Gets the block position of an entity.
     * Works across versions by trying both getBlockPos() and getPosition().
     */
    public static Object getBlockPos(Object entity) {
        if (entity == null) return null;
        
        try {
            initMethods(entity.getClass());
            
            if (getBlockPosMethod != null) {
                return getBlockPosMethod.invoke(entity);
            }
            
            // Try alternative method names
            for (String methodName : new String[]{"getBlockPos", "getPosition", "blockPosition"}) {
                try {
                    Method m = entity.getClass().getMethod(methodName);
                    return m.invoke(entity);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            System.err.println("RetroMod: EntityShim.getBlockPos error: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets the world from an entity.
     * Handles the 1.21.9+ rename from getWorld() to getEntityWorld().
     */
    public static Object getWorld(Object entity) {
        if (entity == null) return null;
        
        try {
            initMethods(entity.getClass());
            
            // Try getEntityWorld first (1.21.9+)
            if (getEntityWorldMethod != null) {
                return getEntityWorldMethod.invoke(entity);
            }
            
            // Fallback to getWorld (pre-1.21.9)
            if (getWorldMethod != null) {
                return getWorldMethod.invoke(entity);
            }
            
            // Try other method names
            for (String methodName : new String[]{"getEntityWorld", "getWorld", "level", "getLevel"}) {
                try {
                    Method m = entity.getClass().getMethod(methodName);
                    return m.invoke(entity);
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Exception e) {
            System.err.println("RetroMod: EntityShim.getWorld error: " + e.getMessage());
        }
        return null;
    }
    
    /**
     * Gets the entity's X position.
     */
    public static double getX(Object entity) {
        return getDoubleField(entity, "getX", "getPosX", "posX");
    }
    
    /**
     * Gets the entity's Y position.
     */
    public static double getY(Object entity) {
        return getDoubleField(entity, "getY", "getPosY", "posY");
    }
    
    /**
     * Gets the entity's Z position.
     */
    public static double getZ(Object entity) {
        return getDoubleField(entity, "getZ", "getPosZ", "posZ");
    }
    
    /**
     * Gets the entity's yaw rotation.
     */
    public static float getYaw(Object entity) {
        return getFloatField(entity, "getYaw", "getRotationYaw", "rotationYaw");
    }
    
    /**
     * Gets the entity's pitch rotation.
     */
    public static float getPitch(Object entity) {
        return getFloatField(entity, "getPitch", "getRotationPitch", "rotationPitch");
    }
    
    /**
     * Checks if entity is on ground.
     */
    public static boolean isOnGround(Object entity) {
        return getBooleanField(entity, "isOnGround", "onGround");
    }
    
    /**
     * Gets the entity's UUID.
     */
    public static Object getUUID(Object entity) {
        return invokeMethod(entity, "getUuid", "getUniqueID", "getUUID");
    }
    
    /**
     * Gets entity's display name.
     */
    public static Object getDisplayName(Object entity) {
        return invokeMethod(entity, "getDisplayName", "getName", "getCustomName");
    }
    
    private static void initMethods(Class<?> entityClass) {
        if (methodsInitialized) return;
        
        try {
            // Try to find getEntityWorld (1.21.9+)
            try {
                getEntityWorldMethod = entityClass.getMethod("getEntityWorld");
            } catch (NoSuchMethodException ignored) {}
            
            // Try to find getWorld (pre-1.21.9)
            try {
                getWorldMethod = entityClass.getMethod("getWorld");
            } catch (NoSuchMethodException ignored) {}
            
            // Try to find getBlockPos
            try {
                getBlockPosMethod = entityClass.getMethod("getBlockPos");
            } catch (NoSuchMethodException ignored) {}
            
            methodsInitialized = true;
        } catch (Exception e) {
            System.err.println("RetroMod: EntityShim init error: " + e.getMessage());
        }
    }
    
    private static double getDoubleField(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                return (double) m.invoke(obj);
            } catch (Exception ignored) {}
            
            // Try as field
            try {
                Field f = obj.getClass().getField(name);
                return f.getDouble(obj);
            } catch (Exception ignored) {}
        }
        return 0.0;
    }
    
    private static float getFloatField(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                return (float) m.invoke(obj);
            } catch (Exception ignored) {}
            
            try {
                Field f = obj.getClass().getField(name);
                return f.getFloat(obj);
            } catch (Exception ignored) {}
        }
        return 0.0f;
    }
    
    private static boolean getBooleanField(Object obj, String... names) {
        for (String name : names) {
            try {
                Method m = obj.getClass().getMethod(name);
                return (boolean) m.invoke(obj);
            } catch (Exception ignored) {}
            
            try {
                Field f = obj.getClass().getField(name);
                return f.getBoolean(obj);
            } catch (Exception ignored) {}
        }
        return false;
    }
    
    private static Object invokeMethod(Object obj, String... methodNames) {
        for (String name : methodNames) {
            try {
                Method m = obj.getClass().getMethod(name);
                return m.invoke(obj);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
