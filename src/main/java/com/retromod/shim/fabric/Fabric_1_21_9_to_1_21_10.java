/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.21.9 to run on 1.21.10.
 * 
 * This documents all known API changes between these versions and provides
 * redirects to maintain compatibility.
 */
public class Fabric_1_21_9_to_1_21_10 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Fabric 1.21.9 to 1.21.10";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.9";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.10";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // MINECRAFT API CHANGES
        // ============================================================
        
        // Example: Entity method renames
        // In 1.21.10, getWorld() was changed to getEntityWorld() 
        // (hypothetical example - check actual mappings)
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", 
            "getWorld", 
            "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/Entity", 
            "getEntityWorld", 
            "()Lnet/minecraft/world/World;"
        );
        
        // Example: BlockState method change
        transformer.registerMethodRedirect(
            "net/minecraft/block/BlockState",
            "getBlock",
            "()Lnet/minecraft/block/Block;",
            "net/minecraft/block/BlockState",
            "getBlockType",
            "()Lnet/minecraft/block/Block;"
        );
        
        // ============================================================
        // FABRIC API CHANGES
        // ============================================================
        
        // Example: ServerLifecycleEvents rename
        // transformer.registerClassRedirect(
        //     "net/fabricmc/fabric/api/event/lifecycle/v1/ServerLifecycleEvents",
        //     "net/fabricmc/fabric/api/event/lifecycle/v2/ServerLifecycleEvents"
        // );
        
        // Example: Removed utility method - redirect to shim
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/util/NbtType",
            "getType",
            "(B)I",
            "com/retromod/shim/fabric/embedded/NbtTypeShim",
            "getType",
            "(B)I"
        );
        
        // ============================================================
        // YARN MAPPING CHANGES
        // These handle when intermediary names get remapped differently
        // ============================================================
        
        // Example: Method intermediary name changed
        // transformer.registerMethodRedirect(
        //     "net/minecraft/class_1234",
        //     "method_5678",
        //     "()V",
        //     "net/minecraft/class_1234",
        //     "method_5679",
        //     "()V"
        // );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.fabric.embedded.NbtTypeShim",
            "com.retromod.shim.fabric.embedded.EntityShim"
        };
    }
}
