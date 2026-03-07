/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for World Render Events that were removed in Fabric 1.21.9.
 * 
 * The world render events were removed temporarily while Fabric
 * adapted to Minecraft's rendering pipeline changes. This shim
 * provides no-op implementations to prevent crashes.
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * Shim for net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents
 * 
 * In 1.21.9, these events were removed. They were reintroduced in 1.21.10
 * with a different design (separation of extraction and rendering).
 * 
 * This shim:
 * 1. Captures event registrations
 * 2. Forwards to new API if available
 * 3. No-ops if no API available (prevents crashes)
 */
public class WorldRenderEventsShim {
    
    // Event instances that mimic the old API
    public static final Event<BeforeBlockOutline> BEFORE_BLOCK_OUTLINE = new Event<>();
    public static final Event<AfterBlockOutline> AFTER_BLOCK_OUTLINE = new Event<>();
    public static final Event<Start> START = new Event<>();
    public static final Event<End> END = new Event<>();
    public static final Event<Last> LAST = new Event<>();
    public static final Event<BeforeEntities> BEFORE_ENTITIES = new Event<>();
    public static final Event<AfterEntities> AFTER_ENTITIES = new Event<>();
    public static final Event<BeforeDebugRender> BEFORE_DEBUG_RENDER = new Event<>();
    
    // Check if new API is available
    private static Boolean newApiAvailable = null;
    
    private static boolean checkNewApi() {
        if (newApiAvailable == null) {
            try {
                Class.forName("net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents");
                newApiAvailable = true;
            } catch (ClassNotFoundException e) {
                newApiAvailable = false;
            }
        }
        return newApiAvailable;
    }
    
    /**
     * Generic event holder that can forward to real events if available.
     */
    public static class Event<T> {
        private final List<T> listeners = new ArrayList<>();
        private Object realEvent = null;
        
        public void register(T listener) {
            listeners.add(listener);
            
            if (checkNewApi()) {
                // Try to forward to real event
                forwardRegistration(listener);
            }
            // Otherwise, registrations are stored but never called
        }
        
        private void forwardRegistration(T listener) {
            // Try to find and forward to the real event
            // This is complex because the new API has different signatures
            // For now, we just store the listener
        }
        
        /**
         * Invoke all listeners (called by our integration if we inject it).
         */
        public void invoke(Object... args) {
            for (T listener : listeners) {
                try {
                    invokeListener(listener, args);
                } catch (Exception e) {
                    System.err.println("RetroMod: Error invoking world render listener: " + e.getMessage());
                }
            }
        }
        
        private void invokeListener(T listener, Object[] args) throws Exception {
            // Find the functional method
            for (Method m : listener.getClass().getMethods()) {
                if (!m.isDefault() && !Modifier.isStatic(m.getModifiers()) &&
                    m.getDeclaringClass() != Object.class) {
                    
                    if (m.getParameterCount() == args.length) {
                        m.invoke(listener, args);
                        return;
                    }
                }
            }
        }
    }
    
    // Functional interfaces matching old API
    
    @FunctionalInterface
    public interface BeforeBlockOutline {
        boolean beforeBlockOutline(Object context, Object hitResult);
    }
    
    @FunctionalInterface
    public interface AfterBlockOutline {
        void afterBlockOutline(Object context, Object hitResult);
    }
    
    @FunctionalInterface
    public interface Start {
        void onStart(Object context);
    }
    
    @FunctionalInterface
    public interface End {
        void onEnd(Object context);
    }
    
    @FunctionalInterface
    public interface Last {
        void onLast(Object context);
    }
    
    @FunctionalInterface
    public interface BeforeEntities {
        void beforeEntities(Object context);
    }
    
    @FunctionalInterface
    public interface AfterEntities {
        void afterEntities(Object context);
    }
    
    @FunctionalInterface
    public interface BeforeDebugRender {
        void beforeDebugRender(Object context);
    }
}
