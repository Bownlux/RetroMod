/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

import java.util.*;

/** Virtual PreInitialization event. */
public class VirtualPreInitEvent {
    private final Map<String, Object> modMetadata = new HashMap<>();
    
    public Object getModMetadata() { return modMetadata; }
    
    public void registerServerCommand(Object command) {
        System.out.println("RetroMod: Server command registration deferred");
    }
}
