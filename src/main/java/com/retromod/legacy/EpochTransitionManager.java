/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.legacy;

import java.util.*;

/**
 * Manages all epoch transitions.
 */
public class EpochTransitionManager {
    
    private final Map<Integer, EpochTransition> transitions = new HashMap<>();
    
    public EpochTransitionManager() {
        // Register all epoch transitions
        register(new Epoch1To2Transition());  // Legacy 1.8-1.12 → Flattening 1.13
        register(new Epoch2To3Transition());  // Flattening 1.13 → Modern 1.14-1.16
        register(new Epoch3To4Transition());  // Modern 1.14-1.16 → Caves 1.17-1.18
        register(new Epoch4To5Transition());  // Caves 1.17-1.18 → Data 1.19-1.20
        register(new Epoch5To6Transition());  // Data 1.19-1.20 → Modern 1.20.5+
    }
    
    public void register(EpochTransition transition) {
        int key = transition.sourceEpoch() * 10 + transition.targetEpoch();
        transitions.put(key, transition);
    }
    
    public EpochTransition getTransition(int sourceEpoch, int targetEpoch) {
        int key = sourceEpoch * 10 + targetEpoch;
        return transitions.get(key);
    }
}
