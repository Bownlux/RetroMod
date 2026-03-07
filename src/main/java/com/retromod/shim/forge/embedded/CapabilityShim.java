/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for Forge's Capability system that bridges to NeoForge.
 * 
 * The capability system changed significantly between Forge and NeoForge:
 * - Forge used LazyOptional<T> and ICapabilityProvider
 * - NeoForge uses BlockCapability, EntityCapability, ItemCapability
 */
package com.retromod.shim.forge.embedded;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Shim for net.minecraftforge.common.capabilities.Capability
 * 
 * Bridges old Forge capability patterns to NeoForge's new capability system.
 */
public final class CapabilityShim {
    
    private static Class<?> blockCapabilityClass;
    private static Class<?> entityCapabilityClass;
    private static Class<?> itemCapabilityClass;
    private static Class<?> lazyOptionalClass;
    
    private static boolean initialized = false;
    
    private CapabilityShim() {}
    
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            // Try NeoForge capability classes
            blockCapabilityClass = Class.forName(
                "net.neoforged.neoforge.capabilities.BlockCapability"
            );
            entityCapabilityClass = Class.forName(
                "net.neoforged.neoforge.capabilities.EntityCapability"
            );
            itemCapabilityClass = Class.forName(
                "net.neoforged.neoforge.capabilities.ItemCapability"
            );
            
        } catch (ClassNotFoundException e) {
            try {
                // Fall back to old Forge
                lazyOptionalClass = Class.forName(
                    "net.minecraftforge.common.util.LazyOptional"
                );
            } catch (ClassNotFoundException e2) {
                System.err.println("RetroMod: No capability system found");
            }
        }
    }
    
    /**
     * Create a LazyOptional-like wrapper for the new capability system.
     * 
     * Old pattern:
     *   LazyOptional<IItemHandler> lazy = LazyOptional.of(() -> handler);
     *   
     * This shim creates a compatible wrapper.
     */
    public static <T> Object lazyOptionalOf(Supplier<T> supplier) {
        initialize();
        
        if (lazyOptionalClass != null) {
            try {
                Method ofMethod = lazyOptionalClass.getMethod("of", Supplier.class);
                return ofMethod.invoke(null, supplier);
            } catch (Exception e) {
                // Fall through to wrapper
            }
        }
        
        // Create a LazyOptional-like wrapper
        return new LazyOptionalWrapper<>(supplier);
    }
    
    /**
     * Create an empty LazyOptional.
     */
    public static Object lazyOptionalEmpty() {
        initialize();
        
        if (lazyOptionalClass != null) {
            try {
                Method emptyMethod = lazyOptionalClass.getMethod("empty");
                return emptyMethod.invoke(null);
            } catch (Exception e) {
                // Fall through to wrapper
            }
        }
        
        return new LazyOptionalWrapper<>(null);
    }
    
    /**
     * Get a capability from a block.
     * 
     * Old pattern:
     *   blockEntity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
     *   
     * New pattern:
     *   level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side)
     */
    public static Object getBlockCapability(Object level, Object capability, 
            Object pos, Object state, Object blockEntity, Object side) {
        
        initialize();
        
        if (blockCapabilityClass != null) {
            try {
                // NeoForge: level.getCapability(cap, pos, state, blockEntity, side)
                Method getCapMethod = level.getClass().getMethod("getCapability",
                    blockCapabilityClass, 
                    pos.getClass(),
                    Class.forName("net.minecraft.world.level.block.state.BlockState"),
                    Class.forName("net.minecraft.world.level.block.entity.BlockEntity"),
                    Class.forName("net.minecraft.core.Direction")
                );
                
                return getCapMethod.invoke(level, capability, pos, state, blockEntity, side);
                
            } catch (Exception e) {
                // Try simpler signature
                try {
                    Method getCapMethod = level.getClass().getMethod("getCapability",
                        blockCapabilityClass, pos.getClass(), Object.class);
                    return getCapMethod.invoke(level, capability, pos, side);
                } catch (Exception e2) {
                    System.err.println("RetroMod: Could not get block capability: " + e2);
                }
            }
        }
        
        // Fall back to old-style getCapability on the block entity
        if (blockEntity != null) {
            return getCapabilityOldStyle(blockEntity, capability, side);
        }
        
        return lazyOptionalEmpty();
    }
    
    /**
     * Get a capability from an entity.
     * 
     * Old pattern:
     *   entity.getCapability(ForgeCapabilities.ITEM_HANDLER, side)
     */
    public static Object getEntityCapability(Object entity, Object capability, Object side) {
        initialize();
        
        if (entityCapabilityClass != null) {
            try {
                // NeoForge: entity.getCapability(cap, side)
                Method getCapMethod = entity.getClass().getMethod("getCapability",
                    entityCapabilityClass, 
                    Class.forName("net.minecraft.core.Direction")
                );
                
                return getCapMethod.invoke(entity, capability, side);
                
            } catch (Exception e) {
                // Try without direction
                try {
                    Method getCapMethod = entity.getClass().getMethod("getCapability",
                        entityCapabilityClass);
                    return getCapMethod.invoke(entity, capability);
                } catch (Exception e2) {
                    // Fall through to old style
                }
            }
        }
        
        return getCapabilityOldStyle(entity, capability, side);
    }
    
    /**
     * Get a capability from an item stack.
     * 
     * Old pattern:
     *   stack.getCapability(ForgeCapabilities.ITEM_HANDLER)
     */
    public static Object getItemCapability(Object stack, Object capability) {
        initialize();
        
        if (itemCapabilityClass != null) {
            try {
                Method getCapMethod = stack.getClass().getMethod("getCapability",
                    itemCapabilityClass);
                return getCapMethod.invoke(stack, capability);
            } catch (Exception e) {
                // Fall through
            }
        }
        
        return getCapabilityOldStyle(stack, capability, null);
    }
    
    /**
     * Old-style getCapability call for Forge.
     */
    private static Object getCapabilityOldStyle(Object target, Object capability, Object side) {
        try {
            if (side != null) {
                Method getCapMethod = target.getClass().getMethod("getCapability",
                    capability.getClass(), 
                    Class.forName("net.minecraft.core.Direction"));
                return getCapMethod.invoke(target, capability, side);
            } else {
                Method getCapMethod = target.getClass().getMethod("getCapability",
                    capability.getClass());
                return getCapMethod.invoke(target, capability);
            }
        } catch (Exception e) {
            return lazyOptionalEmpty();
        }
    }
    
    /**
     * Invalidate a LazyOptional (called on block entity removal, etc.)
     */
    public static void invalidate(Object lazyOptional) {
        if (lazyOptional == null) return;
        
        try {
            Method invalidateMethod = lazyOptional.getClass().getMethod("invalidate");
            invalidateMethod.invoke(lazyOptional);
        } catch (Exception e) {
            // Wrapper doesn't need invalidation
        }
    }
    
    /**
     * Wrapper class that mimics LazyOptional behavior for the new capability system.
     */
    public static class LazyOptionalWrapper<T> {
        private final Supplier<T> supplier;
        private T cached;
        private boolean resolved = false;
        private boolean valid = true;
        
        public LazyOptionalWrapper(Supplier<T> supplier) {
            this.supplier = supplier;
        }
        
        public boolean isPresent() {
            resolve();
            return valid && cached != null;
        }
        
        public T orElse(T other) {
            resolve();
            return (valid && cached != null) ? cached : other;
        }
        
        public T orElseThrow() {
            resolve();
            if (!valid || cached == null) {
                throw new NoSuchElementException("LazyOptional is empty or invalid");
            }
            return cached;
        }
        
        public void ifPresent(java.util.function.Consumer<T> consumer) {
            resolve();
            if (valid && cached != null) {
                consumer.accept(cached);
            }
        }
        
        public <U> LazyOptionalWrapper<U> lazyMap(java.util.function.Function<T, U> mapper) {
            if (!isPresent()) {
                return new LazyOptionalWrapper<>(null);
            }
            return new LazyOptionalWrapper<>(() -> mapper.apply(orElseThrow()));
        }
        
        public void invalidate() {
            valid = false;
            cached = null;
        }
        
        private void resolve() {
            if (!resolved && supplier != null) {
                try {
                    cached = supplier.get();
                } catch (Exception e) {
                    // Supplier failed
                }
                resolved = true;
            }
        }
    }
    
    /**
     * Well-known capability tokens.
     * These bridge ForgeCapabilities.X to NeoForge Capabilities.X
     */
    public static class Tokens {
        public static Object ITEM_HANDLER;
        public static Object FLUID_HANDLER;
        public static Object ENERGY;
        
        static {
            try {
                Class<?> caps = Class.forName(
                    "net.neoforged.neoforge.capabilities.Capabilities$ItemHandler"
                );
                ITEM_HANDLER = caps.getField("BLOCK").get(null);
            } catch (Exception e) {
                // Will try Forge style
            }
            
            try {
                Class<?> caps = Class.forName(
                    "net.neoforged.neoforge.capabilities.Capabilities$FluidHandler"
                );
                FLUID_HANDLER = caps.getField("BLOCK").get(null);
            } catch (Exception e) {
                // Will try Forge style
            }
            
            try {
                Class<?> caps = Class.forName(
                    "net.neoforged.neoforge.capabilities.Capabilities$EnergyStorage"
                );
                ENERGY = caps.getField("BLOCK").get(null);
            } catch (Exception e) {
                // Will try Forge style
            }
        }
    }
}
