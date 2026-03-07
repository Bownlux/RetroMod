/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * FABRIC LEGACY EPOCH TRANSITIONS
 * 
 * Supports transforming Fabric mods from 1.14+ to run on 1.21.x
 */
package com.retromod.legacy;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.util.*;

/**
 * Fabric 1.14 → 1.15 transition
 * - Minor changes, mostly stable
 */
class FabricEpoch_1_14_to_1_15 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.14 → 1.15"; }
    @Override public int sourceEpoch() { return 100; }
    @Override public int targetEpoch() { return 101; }
    
    public FabricEpoch_1_14_to_1_15() {
        // Bee-related additions, mostly additive
        // No major breaking changes
    }
}

/**
 * Fabric 1.15 → 1.16 transition
 * - Nether update
 * - Dimension changes
 */
class FabricEpoch_1_15_to_1_16 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.15 → 1.16"; }
    @Override public int sourceEpoch() { return 101; }
    @Override public int targetEpoch() { return 102; }
    
    public FabricEpoch_1_15_to_1_16() {
        // Dimension API changes
        addClass("net/minecraft/world/dimension/DimensionType", 
                 "net/minecraft/world/dimension/DimensionType");
        
        // Biome changes for Nether
        addClass("net/minecraft/world/biome/Biomes",
                 "net/minecraft/world/biome/BiomeKeys");
    }
}

/**
 * Fabric 1.16 → 1.17 transition
 * - Java 16 required
 * - World height changes begin
 */
class FabricEpoch_1_16_to_1_17 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.16 → 1.17 (Java 16+)"; }
    @Override public int sourceEpoch() { return 102; }
    @Override public int targetEpoch() { return 103; }
    
    public FabricEpoch_1_16_to_1_17() {
        // Copper, Amethyst, etc. - mostly additive
        // World height preparation
        
        addShim("com.retromod.legacy.shim.fabric.WorldHeightShim");
    }
}

/**
 * Fabric 1.17 → 1.18 transition
 * - World height expanded (-64 to 320)
 * - New world generation
 */
class FabricEpoch_1_17_to_1_18 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.17 → 1.18 (World Height)"; }
    @Override public int sourceEpoch() { return 103; }
    @Override public int targetEpoch() { return 104; }
    
    public FabricEpoch_1_17_to_1_18() {
        // Major world gen overhaul
        addClass("net/minecraft/world/gen/chunk/ChunkGenerator",
                 "net/minecraft/world/gen/chunk/ChunkGenerator");
        
        // Noise settings changes
        addClass("net/minecraft/world/gen/NoiseConfig",
                 "net/minecraft/world/gen/noise/NoiseConfig");
        
        addShim("com.retromod.legacy.shim.fabric.ChunkHeightShim");
        addShim("com.retromod.legacy.shim.fabric.WorldGenShim");
    }
}

/**
 * Fabric 1.18 → 1.19 transition
 * - Warden, Deep Dark
 * - New particle/sound systems
 */
class FabricEpoch_1_18_to_1_19 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.18 → 1.19"; }
    @Override public int sourceEpoch() { return 104; }
    @Override public int targetEpoch() { return 105; }
    
    public FabricEpoch_1_18_to_1_19() {
        // Chat signing introduced (later removed requirements)
        // Sculk mechanics
    }
}

/**
 * Fabric 1.19 → 1.20 transition
 * - Armor trims, Cherry Grove
 * - More data-driven features
 */
class FabricEpoch_1_19_to_1_20 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.19 → 1.20"; }
    @Override public int sourceEpoch() { return 105; }
    @Override public int targetEpoch() { return 106; }
    
    public FabricEpoch_1_19_to_1_20() {
        // Display entities, interaction entities
        // More registry changes
    }
}

/**
 * Fabric 1.20 → 1.20.5 transition  
 * - Java 21 required
 * - Components replace NBT for items
 */
class FabricEpoch_1_20_to_1_20_5 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.20 → 1.20.5 (Java 21, Components)"; }
    @Override public int sourceEpoch() { return 106; }
    @Override public int targetEpoch() { return 107; }
    
    public FabricEpoch_1_20_to_1_20_5() {
        // ItemStack NBT → Components
        // This is a MAJOR change
        
        // Identifier constructor changes
        addMethod("net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;)V",
                  "net/minecraft/util/Identifier", "of", "(Ljava/lang/String;)Lnet/minecraft/util/Identifier;");
        addMethod("net/minecraft/util/Identifier", "<init>", "(Ljava/lang/String;Ljava/lang/String;)V",
                  "net/minecraft/util/Identifier", "of", "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/util/Identifier;");
        
        addShim("com.retromod.legacy.shim.fabric.ComponentBridgeShim");
        addShim("com.retromod.shim.fabric.embedded.IdentifierShim");
    }
}

/**
 * Fabric 1.20.5 → 1.21 transition
 * - Trial chambers, Breeze, Mace
 */
class FabricEpoch_1_20_5_to_1_21 extends BaseEpochTransition {
    @Override public String name() { return "Fabric 1.20.5 → 1.21"; }
    @Override public int sourceEpoch() { return 107; }
    @Override public int targetEpoch() { return 108; }
    
    public FabricEpoch_1_20_5_to_1_21() {
        // Mostly additive - new content
        // Component system refinements
    }
}

/**
 * Manages all Fabric legacy epoch transitions.
 */
public class FabricEpochManager {
    
    private final Map<Integer, EpochTransition> transitions = new HashMap<>();
    
    public FabricEpochManager() {
        register(new FabricEpoch_1_14_to_1_15());
        register(new FabricEpoch_1_15_to_1_16());
        register(new FabricEpoch_1_16_to_1_17());
        register(new FabricEpoch_1_17_to_1_18());
        register(new FabricEpoch_1_18_to_1_19());
        register(new FabricEpoch_1_19_to_1_20());
        register(new FabricEpoch_1_20_to_1_20_5());
        register(new FabricEpoch_1_20_5_to_1_21());
    }
    
    private void register(EpochTransition transition) {
        int key = transition.sourceEpoch() * 1000 + transition.targetEpoch();
        transitions.put(key, transition);
    }
    
    public EpochTransition getTransition(int sourceEpoch, int targetEpoch) {
        int key = sourceEpoch * 1000 + targetEpoch;
        return transitions.get(key);
    }
    
    /**
     * Get all transitions needed to go from source to target version.
     */
    public List<EpochTransition> getTransitionChain(String sourceVersion, String targetVersion) {
        List<EpochTransition> chain = new ArrayList<>();
        
        int sourceEpoch = versionToEpoch(sourceVersion);
        int targetEpoch = versionToEpoch(targetVersion);
        
        for (int e = sourceEpoch; e < targetEpoch; e++) {
            EpochTransition t = getTransition(e, e + 1);
            if (t != null) {
                chain.add(t);
            }
        }
        
        return chain;
    }
    
    /**
     * Convert Fabric Minecraft version to epoch number.
     */
    public static int versionToEpoch(String version) {
        if (version.startsWith("1.14")) return 100;
        if (version.startsWith("1.15")) return 101;
        if (version.startsWith("1.16")) return 102;
        if (version.startsWith("1.17")) return 103;
        if (version.startsWith("1.18")) return 104;
        if (version.startsWith("1.19")) return 105;
        if (version.equals("1.20") || version.startsWith("1.20.1") || 
            version.startsWith("1.20.2") || version.startsWith("1.20.3") ||
            version.startsWith("1.20.4")) return 106;
        if (version.startsWith("1.20.5") || version.startsWith("1.20.6")) return 107;
        if (version.startsWith("1.21")) return 108;
        return 108; // Default to latest
    }
}
