/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for FabricBlockEntityType.Builder that was removed in 1.21.2.
 * Block entity types are no longer constructed using builders.
 */
package com.retromod.shim.fabric.embedded;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/**
 * Shim for net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityType.Builder
 * 
 * In 1.21.2, FabricBlockEntityType.Builder was removed.
 * Use FabricBlockEntityTypeBuilder instead (which was un-deprecated).
 * 
 * This shim bridges the removed Builder class to the current API.
 */
public class FabricBlockEntityTypeBuilderShim<T> {
    
    private static Class<?> builderClass;
    private static Method createMethod;
    private static Method addBlockMethod;
    private static Method addBlocksMethod;
    private static Method buildMethod;
    private static boolean initialized = false;
    
    private Object realBuilder;
    private final Object blockEntityFactory;
    private final List<Object> blocks = new ArrayList<>();
    
    private FabricBlockEntityTypeBuilderShim(Object factory) {
        this.blockEntityFactory = factory;
        initializeRealBuilder();
    }
    
    private static synchronized void initializeApi() {
        if (initialized) return;
        initialized = true;
        
        try {
            // Try to find FabricBlockEntityTypeBuilder (the non-deprecated replacement)
            builderClass = Class.forName(
                "net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder"
            );
            
            // Find create method
            for (Method m : builderClass.getMethods()) {
                if (m.getName().equals("create") && Modifier.isStatic(m.getModifiers())) {
                    createMethod = m;
                    break;
                }
            }
            
            // Find addBlock/addBlocks methods
            for (Method m : builderClass.getMethods()) {
                if (m.getName().equals("addBlock") && m.getParameterCount() == 1) {
                    addBlockMethod = m;
                } else if (m.getName().equals("addBlocks") && m.getParameterCount() == 1) {
                    addBlocksMethod = m;
                } else if (m.getName().equals("build") && m.getParameterCount() == 0) {
                    buildMethod = m;
                }
            }
            
        } catch (ClassNotFoundException e) {
            // FabricBlockEntityTypeBuilder not found - very old or very new version
            System.err.println("RetroMod: FabricBlockEntityTypeBuilder not found");
        }
    }
    
    private void initializeRealBuilder() {
        initializeApi();
        
        if (createMethod != null) {
            try {
                realBuilder = createMethod.invoke(null, blockEntityFactory);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create FabricBlockEntityTypeBuilder", e);
            }
        }
    }
    
    /**
     * Create a new builder.
     * 
     * Old API: FabricBlockEntityType.Builder.create(factory, blocks...)
     * New API: FabricBlockEntityTypeBuilder.create(factory).addBlocks(blocks...)
     */
    public static <T> FabricBlockEntityTypeBuilderShim<T> create(Object factory, Object... blocks) {
        FabricBlockEntityTypeBuilderShim<T> builder = new FabricBlockEntityTypeBuilderShim<>(factory);
        
        if (blocks != null && blocks.length > 0) {
            for (Object block : blocks) {
                builder.addBlock(block);
            }
        }
        
        return builder;
    }
    
    /**
     * Add a block to the builder.
     */
    public FabricBlockEntityTypeBuilderShim<T> addBlock(Object block) {
        blocks.add(block);
        
        if (realBuilder != null && addBlockMethod != null) {
            try {
                realBuilder = addBlockMethod.invoke(realBuilder, block);
            } catch (Exception e) {
                System.err.println("RetroMod: Failed to add block to builder: " + e);
            }
        }
        
        return this;
    }
    
    /**
     * Add multiple blocks to the builder.
     */
    public FabricBlockEntityTypeBuilderShim<T> addBlocks(Object... blocks) {
        for (Object block : blocks) {
            addBlock(block);
        }
        return this;
    }
    
    /**
     * Build the BlockEntityType.
     */
    public Object build() {
        if (realBuilder != null && buildMethod != null) {
            try {
                return buildMethod.invoke(realBuilder);
            } catch (Exception e) {
                throw new RuntimeException("Failed to build BlockEntityType", e);
            }
        }
        
        // Fallback: try to create directly using vanilla API
        return createBlockEntityTypeFallback();
    }
    
    /**
     * Build with a registry key (older API signature).
     */
    public Object build(Object id) {
        // The id parameter was used for registration but isn't needed for building
        return build();
    }
    
    /**
     * Fallback creation using vanilla Minecraft API.
     */
    private Object createBlockEntityTypeFallback() {
        try {
            // Try BlockEntityType.Builder.of(factory, blocks).build()
            Class<?> vanillaBuilderClass = Class.forName(
                "net.minecraft.world.level.block.entity.BlockEntityType$Builder"
            );
            
            // Find of() method
            Method ofMethod = null;
            for (Method m : vanillaBuilderClass.getMethods()) {
                if (m.getName().equals("of") && Modifier.isStatic(m.getModifiers())) {
                    ofMethod = m;
                    break;
                }
            }
            
            if (ofMethod != null) {
                Object[] blocksArray = blocks.toArray();
                Object builder = ofMethod.invoke(null, blockEntityFactory, blocksArray);
                
                // Call build()
                Method build = vanillaBuilderClass.getMethod("build", (Class<?>[]) null);
                return build.invoke(builder);
            }
            
        } catch (Exception e) {
            throw new RuntimeException("RetroMod: Failed to create BlockEntityType via fallback", e);
        }
        
        throw new RuntimeException("RetroMod: No BlockEntityType creation method available");
    }
}
