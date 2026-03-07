/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for HUD Render API changes in Fabric 1.21.6+.
 * 
 * The HUD API was completely rewritten to use HudElementRegistry.
 * This shim bridges the old HudRenderCallback to the new API.
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

/**
 * Shim for net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback
 * 
 * Old API: HudRenderCallback.EVENT.register((matrices, tickDelta) -> {...})
 * New API: HudElementRegistry.addLast(id, (context, tickCounter) -> {...})
 * 
 * This shim:
 * 1. Captures old-style registrations
 * 2. Wraps them for the new API
 * 3. Handles the signature change (tickDelta float -> RenderTickCounter)
 */
public class HudRenderCallbackShim {
    
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final Map<Object, Object> registeredCallbacks = new ConcurrentHashMap<>();
    
    // The EVENT field that legacy code accesses
    public static final HudRenderCallbackShim EVENT = new HudRenderCallbackShim();
    
    private static Object hudElementRegistry;
    private static Method addLastMethod;
    private static Method addFirstMethod;
    private static boolean initialized = false;
    
    private HudRenderCallbackShim() {
        // Singleton
    }
    
    private static void ensureInitialized() {
        if (initialized) return;
        
        try {
            // Try to find HudElementRegistry (new API)
            Class<?> registryClass = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.HudElementRegistry"
            );
            
            // Find addLast method
            for (Method m : registryClass.getMethods()) {
                if (m.getName().equals("addLast") && m.getParameterCount() == 2) {
                    addLastMethod = m;
                }
                if (m.getName().equals("addFirst") && m.getParameterCount() == 2) {
                    addFirstMethod = m;
                }
            }
            
            initialized = true;
            
        } catch (ClassNotFoundException e) {
            // HudElementRegistry doesn't exist - we're on old API
            // Try to use the old callback system directly
            try {
                Class<?> oldCallbackClass = Class.forName(
                    "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback"
                );
                // Old API exists - we don't need to shim
                initialized = true;
            } catch (ClassNotFoundException ex) {
                // Neither API exists - we're on a version without HUD API
                initialized = true;
            }
        }
    }
    
    /**
     * Register a HUD render callback (old API style).
     * 
     * @param callback BiConsumer<MatrixStack, Float> style callback
     */
    public void register(Object callback) {
        ensureInitialized();
        
        try {
            if (addLastMethod != null) {
                // New API - wrap the callback
                Object wrappedCallback = wrapOldCallback(callback);
                Object id = createIdentifier("retromod", "compat_hud_" + COUNTER.incrementAndGet());
                
                addLastMethod.invoke(null, id, wrappedCallback);
                registeredCallbacks.put(callback, wrappedCallback);
                
            } else {
                // Try old API directly
                tryOldApi(callback);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to register HUD callback", e);
        }
    }
    
    /**
     * Wrap an old-style callback for the new API.
     * 
     * Old: (MatrixStack matrices, float tickDelta) -> void
     * New: (DrawContext context, RenderTickCounter tickCounter) -> void
     */
    private Object wrapOldCallback(Object oldCallback) throws Exception {
        // Find the new callback interface
        Class<?> newCallbackInterface = Class.forName(
            "net.fabricmc.fabric.api.client.rendering.v1.HudElementCallback"
        );
        
        // Create a proxy that wraps the old callback
        return Proxy.newProxyInstance(
            newCallbackInterface.getClassLoader(),
            new Class<?>[] { newCallbackInterface },
            (proxy, method, args) -> {
                if (method.getName().equals("render") && args != null && args.length == 2) {
                    Object drawContext = args[0];
                    Object tickCounter = args[1];
                    
                    // Extract MatrixStack from DrawContext
                    Object matrices = extractMatrices(drawContext);
                    
                    // Extract tickDelta from RenderTickCounter
                    float tickDelta = extractTickDelta(tickCounter);
                    
                    // Call the old callback
                    invokeOldCallback(oldCallback, matrices, tickDelta);
                }
                return null;
            }
        );
    }
    
    /**
     * Extract MatrixStack from DrawContext.
     */
    private Object extractMatrices(Object drawContext) {
        try {
            // DrawContext has getMatrices() method
            Method getMatrices = drawContext.getClass().getMethod("getMatrices");
            return getMatrices.invoke(drawContext);
        } catch (Exception e) {
            // Fallback: create new MatrixStack
            try {
                Class<?> matrixStackClass = Class.forName("net.minecraft.client.util.math.MatrixStack");
                return matrixStackClass.getConstructor().newInstance();
            } catch (Exception ex) {
                return null;
            }
        }
    }
    
    /**
     * Extract tickDelta from RenderTickCounter.
     */
    private float extractTickDelta(Object tickCounter) {
        try {
            // RenderTickCounter has getTickDelta(boolean) method
            Method getTickDelta = tickCounter.getClass().getMethod("getTickDelta", boolean.class);
            return (float) getTickDelta.invoke(tickCounter, true);
        } catch (NoSuchMethodException e) {
            // Try without parameter
            try {
                Method getTickDelta = tickCounter.getClass().getMethod("getTickDelta");
                return (float) getTickDelta.invoke(tickCounter);
            } catch (Exception ex) {
                return 0.0f;
            }
        } catch (Exception e) {
            return 0.0f;
        }
    }
    
    /**
     * Invoke the old callback with MatrixStack and tickDelta.
     */
    private void invokeOldCallback(Object callback, Object matrices, float tickDelta) {
        try {
            // Old callback is a BiConsumer or similar functional interface
            // It might be HudRenderCallback which has onHudRender(MatrixStack, float)
            
            // Try functional interface approach
            if (callback instanceof BiConsumer) {
                ((BiConsumer<Object, Float>) callback).accept(matrices, tickDelta);
                return;
            }
            
            // Try to find render/onHudRender method
            for (Method m : callback.getClass().getMethods()) {
                if ((m.getName().equals("onHudRender") || m.getName().equals("render")) &&
                    m.getParameterCount() == 2) {
                    
                    m.invoke(callback, matrices, tickDelta);
                    return;
                }
            }
            
            // Try accept method (for lambdas)
            Method accept = callback.getClass().getMethod("accept", Object.class, Object.class);
            accept.invoke(callback, matrices, tickDelta);
            
        } catch (Exception e) {
            // Log but don't crash
            System.err.println("RetroMod: Failed to invoke HUD callback: " + e.getMessage());
        }
    }
    
    /**
     * Try to use the old API directly.
     */
    private void tryOldApi(Object callback) {
        try {
            Class<?> oldCallbackClass = Class.forName(
                "net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback"
            );
            
            Field eventField = oldCallbackClass.getField("EVENT");
            Object event = eventField.get(null);
            
            Method registerMethod = event.getClass().getMethod("register", Object.class);
            registerMethod.invoke(event, callback);
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: No compatible HUD API found", e);
        }
    }
    
    /**
     * Create an Identifier for registration.
     */
    private Object createIdentifier(String namespace, String path) {
        return IdentifierShim.of(namespace, path);
    }
    
    /**
     * Check if the new HUD API is available.
     */
    public static boolean isNewApiAvailable() {
        ensureInitialized();
        return addLastMethod != null;
    }
    
    /**
     * Unregister a callback (if supported).
     */
    public boolean unregister(Object callback) {
        // New API doesn't support unregistration by callback
        // Just remove from our tracking
        return registeredCallbacks.remove(callback) != null;
    }
}
