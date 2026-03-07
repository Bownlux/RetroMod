/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * EPOCH 4 → 5: Caves & Cliffs (1.17-1.18) to Data-Driven (1.19-1.20)
 */
package com.retromod.legacy;

public class Epoch4To5Transition extends BaseEpochTransition {
    
    @Override public String name() { return "Caves & Cliffs 1.17-1.18 → Data-Driven 1.19-1.20"; }
    @Override public int sourceEpoch() { return 4; }
    @Override public int targetEpoch() { return 5; }
    
    public Epoch4To5Transition() {
        // Text API changes
        addClass("net/minecraft/text/LiteralText", "net/minecraft/text/Text");
        addClass("net/minecraft/text/TranslatableText", "net/minecraft/text/Text");
        
        // Screen handler changes
        addClass("net/minecraft/screen/NamedScreenHandlerFactory",
                 "net/minecraft/screen/ScreenHandlerFactory");
        
        // Registry changes
        addClass("net/minecraft/util/registry/BuiltinRegistries",
                 "net/minecraft/registry/BuiltinRegistries");
        
        addShim("com.retromod.virtual.DataDrivenShim");
    }
}
