/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

/** Virtual Initialization event. */
public class VirtualInitEvent {
    public void registerServerCommand(Object command) {
        System.out.println("RetroMod: Server command registration deferred");
    }
}
