/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * Shim for Block Settings changes.
 * 
 * Major change in 1.20: Material system was completely removed.
 * Old: FabricBlockSettings.of(Material.STONE)
 * New: AbstractBlock.Settings.create() with individual property setters
 * 
 * This shim maps old Material-based calls to appropriate property configurations.
 */
public class BlockSettingsShim {
    
    // Map of material names to their typical properties
    private static final Map<String, MaterialProperties> MATERIAL_DEFAULTS = new HashMap<>();
    
    static {
        // Define default properties for common materials
        MATERIAL_DEFAULTS.put("STONE", new MaterialProperties(true, false, false, 1.5f, 6.0f));
        MATERIAL_DEFAULTS.put("WOOD", new MaterialProperties(true, true, false, 2.0f, 3.0f));
        MATERIAL_DEFAULTS.put("METAL", new MaterialProperties(true, false, false, 5.0f, 6.0f));
        MATERIAL_DEFAULTS.put("GLASS", new MaterialProperties(false, false, false, 0.3f, 0.3f));
        MATERIAL_DEFAULTS.put("WOOL", new MaterialProperties(true, true, false, 0.8f, 0.8f));
        MATERIAL_DEFAULTS.put("PLANT", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("DIRT", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("SAND", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("WATER", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("LAVA", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("ICE", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("SNOW", new MaterialProperties(true, false, false, 0.2f, 0.2f));
        MATERIAL_DEFAULTS.put("CLAY", new MaterialProperties(true, false, false, 0.6f, 0.6f));
        MATERIAL_DEFAULTS.put("GOURD", new MaterialProperties(true, false, false, 1.0f, 1.0f));
        MATERIAL_DEFAULTS.put("PORTAL", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("CAKE", new MaterialProperties(true, false, false, 0.5f, 0.5f));
        MATERIAL_DEFAULTS.put("COBWEB", new MaterialProperties(false, false, false, 4.0f, 4.0f));
        MATERIAL_DEFAULTS.put("PISTON", new MaterialProperties(true, false, false, 1.5f, 1.5f));
        MATERIAL_DEFAULTS.put("DECORATION", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("AIR", new MaterialProperties(false, false, true, 0.0f, 0.0f));
        MATERIAL_DEFAULTS.put("LEAVES", new MaterialProperties(true, true, false, 0.2f, 0.2f));
        MATERIAL_DEFAULTS.put("SPONGE", new MaterialProperties(true, false, false, 0.6f, 0.6f));
        MATERIAL_DEFAULTS.put("SHULKER_BOX", new MaterialProperties(true, false, false, 2.0f, 2.0f));
        MATERIAL_DEFAULTS.put("NETHER_WOOD", new MaterialProperties(true, false, false, 2.0f, 3.0f));
    }
    
    /**
     * Convert old FabricBlockSettings.of(material) to new AbstractBlock.Settings.create()
     */
    public static Object of(Object material) {
        try {
            // Get the new Settings.create() method
            Class<?> settingsClass = Class.forName("net.minecraft.block.AbstractBlock$Settings");
            Method createMethod = settingsClass.getMethod("create");
            Object settings = createMethod.invoke(null);
            
            // Try to determine material type and apply properties
            if (material != null) {
                String materialName = getMaterialName(material);
                MaterialProperties props = MATERIAL_DEFAULTS.getOrDefault(materialName, 
                    new MaterialProperties(true, false, false, 1.0f, 1.0f));
                
                // Apply strength
                Method strengthMethod = settingsClass.getMethod("strength", float.class, float.class);
                settings = strengthMethod.invoke(settings, props.hardness, props.resistance);
                
                // Apply solid/non-solid
                if (!props.solid) {
                    try {
                        Method noCollisionMethod = settingsClass.getMethod("noCollision");
                        settings = noCollisionMethod.invoke(settings);
                    } catch (NoSuchMethodException e) {
                        // Method might not exist in this version
                    }
                }
                
                // Apply burnable
                if (props.burnable) {
                    try {
                        Method burnableMethod = settingsClass.getMethod("burnable");
                        settings = burnableMethod.invoke(settings);
                    } catch (NoSuchMethodException e) {
                        // Older versions might not have this
                    }
                }
                
                // Apply replaceable
                if (props.replaceable) {
                    try {
                        Method replaceableMethod = settingsClass.getMethod("replaceable");
                        settings = replaceableMethod.invoke(settings);
                    } catch (NoSuchMethodException e) {
                        // Method might not exist
                    }
                }
            }
            
            return settings;
            
        } catch (Exception e) {
            // Fallback: try to create basic settings
            try {
                Class<?> settingsClass = Class.forName("net.minecraft.block.AbstractBlock$Settings");
                return settingsClass.getMethod("create").invoke(null);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot create block settings", e2);
            }
        }
    }
    
    /**
     * Get material name from material object via reflection.
     */
    private static String getMaterialName(Object material) {
        try {
            // Try to get the name field or toString
            String str = material.toString().toUpperCase();
            // Extract material name from various formats
            for (String key : MATERIAL_DEFAULTS.keySet()) {
                if (str.contains(key)) {
                    return key;
                }
            }
            return "STONE"; // Default fallback
        } catch (Exception e) {
            return "STONE";
        }
    }
    
    /**
     * Simple class to hold material properties.
     */
    private static class MaterialProperties {
        final boolean solid;
        final boolean burnable;
        final boolean replaceable;
        final float hardness;
        final float resistance;
        
        MaterialProperties(boolean solid, boolean burnable, boolean replaceable, 
                          float hardness, float resistance) {
            this.solid = solid;
            this.burnable = burnable;
            this.replaceable = replaceable;
            this.hardness = hardness;
            this.resistance = resistance;
        }
    }
}
