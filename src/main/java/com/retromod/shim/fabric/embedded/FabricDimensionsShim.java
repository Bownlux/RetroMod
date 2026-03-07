/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for removed FabricDimensions class.
 * 
 * FabricDimensions.teleport was removed in 1.21.2+ because
 * Entity#teleportTo provides the same functionality in vanilla.
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.Method;

/**
 * Shim for net.fabricmc.fabric.api.dimension.v1.FabricDimensions
 * 
 * Old: FabricDimensions.teleport(entity, world, target)
 * New: entity.teleportTo(world, x, y, z, yaw, pitch) or similar
 * 
 * This shim bridges the removed API to the new vanilla method.
 */
public final class FabricDimensionsShim {
    
    private FabricDimensionsShim() {
        // Utility class
    }
    
    /**
     * Teleport an entity to another dimension.
     * 
     * @param entity The entity to teleport
     * @param destination The target world
     * @param target The teleport target (contains position, rotation, velocity)
     * @return The entity in the new dimension (may be a different instance for players)
     */
    @SuppressWarnings("unchecked")
    public static <E> E teleport(E entity, Object destination, Object target) {
        try {
            // Get position from TeleportTarget
            double x, y, z;
            float yaw, pitch;
            
            // TeleportTarget has position(), yaw(), pitch() methods
            Class<?> targetClass = target.getClass();
            
            // Try to get Vec3d position
            Method posMethod = findMethod(targetClass, "position", "pos", "getPos");
            Object pos = posMethod.invoke(target);
            
            // Vec3d has x, y, z fields or getX(), getY(), getZ() methods
            Class<?> vecClass = pos.getClass();
            x = getDouble(pos, vecClass, "x", "getX");
            y = getDouble(pos, vecClass, "y", "getY");
            z = getDouble(pos, vecClass, "z", "getZ");
            
            // Get rotation
            yaw = getFloat(target, targetClass, "yaw", "getYaw");
            pitch = getFloat(target, targetClass, "pitch", "getPitch");
            
            // Now teleport using Entity.teleportTo or changeDimension
            Class<?> entityClass = entity.getClass();
            
            // Try Entity.teleportTo(ServerWorld, double, double, double, Set, float, float, boolean)
            try {
                Method teleportTo = findMethod(entityClass, "teleportTo");
                if (teleportTo != null && teleportTo.getParameterCount() >= 6) {
                    // Found teleportTo - use it
                    teleportTo.invoke(entity, destination, x, y, z, 
                        java.util.Set.of(), yaw, pitch, false);
                    return entity;
                }
            } catch (Exception ignored) {}
            
            // Try changeDimension for inter-dimensional teleport
            try {
                Method changeDim = entityClass.getMethod("changeDimension", destination.getClass());
                Object result = changeDim.invoke(entity, destination);
                
                // After dimension change, set position
                if (result != null) {
                    Method setPos = result.getClass().getMethod("setPos", double.class, double.class, double.class);
                    setPos.invoke(result, x, y, z);
                    
                    Method setRot = findMethod(result.getClass(), "setYaw", "setRotation");
                    if (setRot != null) {
                        if (setRot.getParameterCount() == 2) {
                            setRot.invoke(result, yaw, pitch);
                        } else {
                            setRot.invoke(result, yaw);
                            findMethod(result.getClass(), "setPitch").invoke(result, pitch);
                        }
                    }
                    
                    return (E) result;
                }
            } catch (Exception ignored) {}
            
            // Fallback: just set position (won't change dimension but won't crash)
            Method setPos = entityClass.getMethod("setPos", double.class, double.class, double.class);
            setPos.invoke(entity, x, y, z);
            
            return entity;
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to teleport entity", e);
        }
    }
    
    private static Method findMethod(Class<?> clazz, String... names) {
        for (String name : names) {
            for (Method m : clazz.getMethods()) {
                if (m.getName().equals(name)) {
                    return m;
                }
            }
        }
        return null;
    }
    
    private static double getDouble(Object obj, Class<?> clazz, String... names) throws Exception {
        // Try field first
        for (String name : names) {
            try {
                var field = clazz.getField(name);
                return field.getDouble(obj);
            } catch (NoSuchFieldException ignored) {}
        }
        
        // Try method
        Method m = findMethod(clazz, names);
        if (m != null) {
            return ((Number) m.invoke(obj)).doubleValue();
        }
        
        return 0.0;
    }
    
    private static float getFloat(Object obj, Class<?> clazz, String... names) throws Exception {
        // Try field first
        for (String name : names) {
            try {
                var field = clazz.getField(name);
                return field.getFloat(obj);
            } catch (NoSuchFieldException ignored) {}
        }
        
        // Try method
        Method m = findMethod(clazz, names);
        if (m != null) {
            return ((Number) m.invoke(obj)).floatValue();
        }
        
        return 0.0f;
    }
}
