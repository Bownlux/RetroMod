/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.21.8 to 1.21.9 compatibility shim.
 * Handles Entity.getWorld() -> Entity.getEntityWorld() rename.
 */
public class Forge_1_21_8_to_1_21_9 implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Forge 1.21.8 to 1.21.9";
    }
    
    @Override
    public String getSourceVersion() {
        return "1.21.8";
    }
    
    @Override
    public String getTargetVersion() {
        return "1.21.9";
    }
    
    @Override
    public String getModLoaderType() {
        return "forge";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Entity.getWorld() renamed to getEntityWorld() in 1.21.9
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity",
            "getWorld",
            "()Lnet/minecraft/world/level/Level;",
            "net/minecraft/world/entity/Entity",
            "getEntityWorld",
            "()Lnet/minecraft/world/level/Level;"
        );
        
        // Also handle the level() method variant
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity",
            "level",
            "()Lnet/minecraft/world/level/Level;",
            "net/minecraft/world/entity/Entity",
            "getEntityWorld",
            "()Lnet/minecraft/world/level/Level;"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.forge.embedded.EntityShim"
        };
    }
}
