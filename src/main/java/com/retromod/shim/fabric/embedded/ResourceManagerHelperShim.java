/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for Resource Loader API rework in 1.21.9.
 * 
 * Old API: ResourceManagerHelper.get(type).registerReloadListener(listener)
 * New API: ResourceLoader.get(type).registerReloader(id, listener)
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shim for net.fabricmc.fabric.api.resource.ResourceManagerHelper
 * 
 * This class bridges the old ResourceManagerHelper API to the new
 * ResourceLoader API introduced in Fabric API for 1.21.9.
 */
public class ResourceManagerHelperShim {
    
    private static final Map<Object, ResourceManagerHelperShim> INSTANCES = new ConcurrentHashMap<>();
    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    
    private final Object resourceType;
    private Object realLoader;
    
    private ResourceManagerHelperShim(Object resourceType) {
        this.resourceType = resourceType;
        initRealLoader();
    }
    
    private void initRealLoader() {
        try {
            // Try to get ResourceLoader (new API)
            Class<?> loaderClass = Class.forName("net.fabricmc.fabric.api.resource.ResourceLoader");
            Method getMethod = loaderClass.getMethod("get", resourceType.getClass());
            realLoader = getMethod.invoke(null, resourceType);
        } catch (ClassNotFoundException e) {
            // ResourceLoader doesn't exist - we're on old API
            // In this case, defer to old API (shouldn't happen in 1.21.9+)
            try {
                Class<?> helperClass = Class.forName("net.fabricmc.fabric.api.resource.ResourceManagerHelper");
                Method getMethod = helperClass.getMethod("get", resourceType.getClass());
                realLoader = getMethod.invoke(null, resourceType);
            } catch (Exception ex) {
                throw new RuntimeException("RetroMod: Could not find resource loader API", ex);
            }
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to get resource loader", e);
        }
    }
    
    /**
     * Get the ResourceManagerHelper/ResourceLoader for a resource type.
     */
    public static ResourceManagerHelperShim get(Object resourceType) {
        return INSTANCES.computeIfAbsent(resourceType, ResourceManagerHelperShim::new);
    }
    
    /**
     * Register a reload listener (old API).
     * Bridges to registerReloader(id, listener) on new API.
     */
    public void registerReloadListener(Object listener) {
        try {
            // Get the listener's ID if it implements IdentifiableResourceReloadListener
            Object id = getListenerId(listener);
            
            if (id == null) {
                // Generate a unique ID for listeners without one
                id = createIdentifier("retromod", "compat_listener_" + COUNTER.incrementAndGet());
            }
            
            // Try new API: registerReloader(Identifier, ResourceReloader)
            try {
                Method registerMethod = realLoader.getClass().getMethod(
                    "registerReloader", 
                    id.getClass(), 
                    Class.forName("net.minecraft.resource.ResourceReloader")
                );
                registerMethod.invoke(realLoader, id, listener);
                return;
            } catch (NoSuchMethodException e) {
                // Not new API
            }
            
            // Try old API: registerReloadListener(IdentifiableResourceReloadListener)
            try {
                Method registerMethod = findMethodByName(realLoader.getClass(), "registerReloadListener");
                if (registerMethod != null) {
                    registerMethod.invoke(realLoader, listener);
                    return;
                }
            } catch (Exception ignored) {}
            
            throw new RuntimeException("Could not register reload listener - no compatible API found");
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to register reload listener", e);
        }
    }
    
    /**
     * Get the ID from an IdentifiableResourceReloadListener.
     */
    private Object getListenerId(Object listener) {
        try {
            // Check if it implements IdentifiableResourceReloadListener
            Class<?> identifiableClass = Class.forName(
                "net.fabricmc.fabric.api.resource.IdentifiableResourceReloadListener"
            );
            
            if (identifiableClass.isInstance(listener)) {
                Method getIdMethod = identifiableClass.getMethod("getFabricId");
                return getIdMethod.invoke(listener);
            }
        } catch (ClassNotFoundException e) {
            // Interface doesn't exist in this version
        } catch (Exception e) {
            // Listener doesn't have an ID
        }
        
        return null;
    }
    
    /**
     * Create an Identifier using the appropriate method for the current version.
     */
    private Object createIdentifier(String namespace, String path) {
        try {
            return IdentifierShim.of(namespace, path);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create identifier: " + namespace + ":" + path, e);
        }
    }
    
    private Method findMethodByName(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        return null;
    }
    
    /**
     * Add reloader ordering (new API only).
     * No-op on old API.
     */
    public void addReloaderOrdering(Object first, Object second) {
        try {
            Method orderMethod = realLoader.getClass().getMethod(
                "addReloaderOrdering",
                first.getClass(),
                second.getClass()
            );
            orderMethod.invoke(realLoader, first, second);
        } catch (NoSuchMethodException e) {
            // Old API doesn't support ordering - ignore
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to add reloader ordering", e);
        }
    }
}
