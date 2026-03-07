/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

import java.util.*;

/**
 * Virtual replacement for ModelLoader (client-side).
 * Model registration is now automatic in modern MC.
 */
public class VirtualModelLoader {
    
    private static final List<ModelRegistration> CUSTOM_MODELS = new ArrayList<>();
    private static final List<Object> MESH_DEFINITIONS = new ArrayList<>();
    
    public static void setCustomModelResourceLocation(Object item, int metadata, 
            Object resourceLocation) {
        CUSTOM_MODELS.add(new ModelRegistration(item, metadata, resourceLocation));
        System.out.println("RetroMod: Custom model registration queued - modern MC uses automatic model loading");
    }
    
    public static void setCustomMeshDefinition(Object item, Object meshDefinition) {
        MESH_DEFINITIONS.add(meshDefinition);
        System.out.println("RetroMod: Custom mesh definition queued");
    }
    
    public static void registerItemVariants(Object item, Object... variants) {
        System.out.println("RetroMod: Item variant registration - handled by model JSON");
    }
    
    public static void setBucketModelDefinition(Object item) {
        System.out.println("RetroMod: Bucket model - handled by modern fluid rendering");
    }
    
    public static List<ModelRegistration> getCustomModels() {
        return Collections.unmodifiableList(CUSTOM_MODELS);
    }
    
    public record ModelRegistration(Object item, int metadata, Object resourceLocation) {}
}

/**
 * Virtual ModelResourceLocation.
 */
class VirtualModelResourceLocation {
    public final String namespace;
    public final String path;
    public final String variant;
    
    public VirtualModelResourceLocation(String location, String variant) {
        if (location.contains(":")) {
            String[] parts = location.split(":", 2);
            this.namespace = parts[0];
            this.path = parts[1];
        } else {
            this.namespace = "minecraft";
            this.path = location;
        }
        this.variant = variant;
    }
    
    public VirtualModelResourceLocation(String namespace, String path, String variant) {
        this.namespace = namespace;
        this.path = path;
        this.variant = variant;
    }
    
    @Override
    public String toString() {
        return namespace + ":" + path + "#" + variant;
    }
}
