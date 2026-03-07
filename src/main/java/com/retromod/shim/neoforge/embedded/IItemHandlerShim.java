/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for IItemHandler interface that was reworked in NeoForge 21.9.
 * 
 * The old IItemHandler interface was deprecated and replaced with
 * ResourceHandler<ItemResource>. This shim provides compatibility
 * by wrapping the new API with the old interface.
 */
package com.retromod.shim.neoforge.embedded;

import java.lang.reflect.Method;

/**
 * Shim for net.neoforged.neoforge.items.IItemHandler
 * 
 * The Transfer API rework in NeoForge 21.9 replaced:
 * - IItemHandler -> ResourceHandler<ItemResource>
 * - IFluidHandler -> ResourceHandler<FluidResource>
 * - IEnergyStorage -> EnergyHandler
 * 
 * NeoForge provides IItemHandler.of(handler) to wrap new handlers,
 * but this shim helps when old code is calling IItemHandler methods
 * on objects that might be the new type.
 */
public final class IItemHandlerShim {
    
    private IItemHandlerShim() {
        // Utility class
    }
    
    /**
     * Get the number of slots in an item handler.
     * Works with both old IItemHandler and new ResourceHandler.
     */
    public static int getSlots(Object handler) {
        try {
            // Try old API first
            Method getSlotsMethod = findMethod(handler.getClass(), "getSlots");
            if (getSlotsMethod != null) {
                return (int) getSlotsMethod.invoke(handler);
            }
            
            // Try new API: ResourceHandler has getSlotCount() or similar
            Method slotCountMethod = findMethod(handler.getClass(), "getSlotCount", "slotCount", "size");
            if (slotCountMethod != null) {
                return (int) slotCountMethod.invoke(handler);
            }
            
            throw new RuntimeException("Cannot determine slot count for handler type: " + handler.getClass());
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to get slot count", e);
        }
    }
    
    /**
     * Get the ItemStack in a slot.
     * Works with both old and new APIs.
     */
    public static Object getStackInSlot(Object handler, int slot) {
        try {
            // Try old API
            Method getStackMethod = findMethod(handler.getClass(), "getStackInSlot");
            if (getStackMethod != null) {
                return getStackMethod.invoke(handler, slot);
            }
            
            // Try new API: getResource(slot) returns ItemResource, need to convert
            Method getResourceMethod = findMethod(handler.getClass(), "getResource", "getStack");
            if (getResourceMethod != null) {
                Object resource = getResourceMethod.invoke(handler, slot);
                return resourceToStack(resource, handler, slot);
            }
            
            throw new RuntimeException("Cannot get stack for handler type: " + handler.getClass());
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to get stack in slot " + slot, e);
        }
    }
    
    /**
     * Insert an ItemStack into a slot.
     */
    public static Object insertItem(Object handler, int slot, Object stack, boolean simulate) {
        try {
            // Try old API
            Method insertMethod = handler.getClass().getMethod(
                "insertItem", int.class, stack.getClass(), boolean.class
            );
            return insertMethod.invoke(handler, slot, stack, simulate);
            
        } catch (NoSuchMethodException e) {
            // Try new API with transaction
            try {
                return insertItemNewApi(handler, slot, stack, simulate);
            } catch (Exception ex) {
                throw new RuntimeException("RetroMod: Failed to insert item", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to insert item", e);
        }
    }
    
    /**
     * Extract an ItemStack from a slot.
     */
    public static Object extractItem(Object handler, int slot, int amount, boolean simulate) {
        try {
            // Try old API
            Method extractMethod = handler.getClass().getMethod(
                "extractItem", int.class, int.class, boolean.class
            );
            return extractMethod.invoke(handler, slot, amount, simulate);
            
        } catch (NoSuchMethodException e) {
            // Try new API with transaction
            try {
                return extractItemNewApi(handler, slot, amount, simulate);
            } catch (Exception ex) {
                throw new RuntimeException("RetroMod: Failed to extract item", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to extract item", e);
        }
    }
    
    /**
     * Get the slot limit for a slot.
     */
    public static int getSlotLimit(Object handler, int slot) {
        try {
            Method limitMethod = findMethod(handler.getClass(), "getSlotLimit", "getMaxStackSize");
            if (limitMethod != null) {
                if (limitMethod.getParameterCount() == 1) {
                    return (int) limitMethod.invoke(handler, slot);
                } else {
                    return (int) limitMethod.invoke(handler);
                }
            }
            
            // Default to 64 (standard stack size)
            return 64;
            
        } catch (Exception e) {
            return 64;
        }
    }
    
    /**
     * Check if an ItemStack is valid for a slot.
     */
    public static boolean isItemValid(Object handler, int slot, Object stack) {
        try {
            Method validMethod = findMethod(handler.getClass(), "isItemValid");
            if (validMethod != null) {
                return (boolean) validMethod.invoke(handler, slot, stack);
            }
            
            // Default to true
            return true;
            
        } catch (Exception e) {
            return true;
        }
    }
    
    // --- New API helpers ---
    
    private static Object resourceToStack(Object resource, Object handler, int slot) throws Exception {
        if (resource == null) {
            return getEmptyStack();
        }
        
        // If it's already an ItemStack, return it
        if (resource.getClass().getSimpleName().contains("ItemStack")) {
            return resource;
        }
        
        // Try to convert ItemResource to ItemStack
        // ItemResource has item() and components()
        Method itemMethod = findMethod(resource.getClass(), "item", "getItem");
        if (itemMethod != null) {
            Object item = itemMethod.invoke(resource);
            
            // Get count from handler
            Method countMethod = findMethod(handler.getClass(), "getAmount", "getCount");
            int count = 1;
            if (countMethod != null) {
                if (countMethod.getParameterCount() == 1) {
                    count = ((Number) countMethod.invoke(handler, slot)).intValue();
                } else {
                    count = ((Number) countMethod.invoke(handler)).intValue();
                }
            }
            
            // Create ItemStack
            return createStack(item, count);
        }
        
        return getEmptyStack();
    }
    
    private static Object insertItemNewApi(Object handler, int slot, Object stack, boolean simulate) 
            throws Exception {
        // New API uses transactions
        // For simplicity, we try to find a compatible insert method
        
        Method[] methods = handler.getClass().getMethods();
        for (Method m : methods) {
            if (m.getName().contains("insert") || m.getName().contains("Insert")) {
                // Found potential insert method
                Class<?>[] params = m.getParameterTypes();
                if (params.length >= 2) {
                    // Try to invoke
                    try {
                        if (params.length == 2) {
                            return m.invoke(handler, stack, simulate ? 0 : 1);
                        } else if (params.length == 3) {
                            return m.invoke(handler, slot, stack, simulate);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        
        // Couldn't find method, return original stack (nothing inserted)
        return stack;
    }
    
    private static Object extractItemNewApi(Object handler, int slot, int amount, boolean simulate) 
            throws Exception {
        // Similar to insert, find compatible extract method
        Method[] methods = handler.getClass().getMethods();
        for (Method m : methods) {
            if (m.getName().contains("extract") || m.getName().contains("Extract")) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length >= 2) {
                    try {
                        if (params.length == 2) {
                            return m.invoke(handler, amount, simulate);
                        } else if (params.length == 3) {
                            return m.invoke(handler, slot, amount, simulate);
                        }
                    } catch (Exception ignored) {}
                }
            }
        }
        
        return getEmptyStack();
    }
    
    private static Object getEmptyStack() throws Exception {
        Class<?> stackClass = Class.forName("net.minecraft.world.item.ItemStack");
        return stackClass.getField("EMPTY").get(null);
    }
    
    private static Object createStack(Object item, int count) throws Exception {
        Class<?> stackClass = Class.forName("net.minecraft.world.item.ItemStack");
        return stackClass.getConstructor(item.getClass(), int.class).newInstance(item, count);
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
}
