/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * EPOCH 3 → 4: Modern Foundation (1.14-1.16) to Caves & Cliffs (1.17-1.18)
 */
package com.retromod.legacy;

public class Epoch3To4Transition extends BaseEpochTransition {
    
    @Override public String name() { return "Modern 1.14-1.16 → Caves & Cliffs 1.17-1.18"; }
    @Override public int sourceEpoch() { return 3; }
    @Override public int targetEpoch() { return 4; }
    
    public Epoch3To4Transition() {
        // Package restructuring
        addClass("net/minecraft/util/registry/Registry", 
                 "net/minecraft/registry/Registry");
        addClass("net/minecraft/util/registry/DefaultedRegistry",
                 "net/minecraft/registry/DefaultedRegistry");
        addClass("net/minecraft/util/registry/RegistryKey",
                 "net/minecraft/registry/RegistryKey");
        
        // Chunk format changes
        addClass("net/minecraft/world/chunk/ChunkSection",
                 "net/minecraft/world/chunk/ChunkSection");
        
        // Biome source changes
        addClass("net/minecraft/world/biome/provider/BiomeProvider",
                 "net/minecraft/world/biome/source/BiomeSource");
        
        addShim("com.retromod.virtual.WorldHeightShim");
        addShim("com.retromod.virtual.ChunkFormatShim");
    }
}
