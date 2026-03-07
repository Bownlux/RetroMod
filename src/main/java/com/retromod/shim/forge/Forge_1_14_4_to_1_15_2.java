/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Buzzy Bees update. Major rendering pipeline overhaul in Forge.
 */
public class Forge_1_14_4_to_1_15_2 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.14.4 to 1.15.2"; }
    @Override public String getSourceVersion() { return "1.14.4"; }
    @Override public String getTargetVersion() { return "1.15.2"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Rendering changes
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "pushMatrix",
            "()V",
            "com/mojang/blaze3d/systems/RenderSystem", "pushMatrix",
            "()V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "popMatrix",
            "()V",
            "com/mojang/blaze3d/systems/RenderSystem", "popMatrix",
            "()V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "color4f",
            "(FFFF)V",
            "com/mojang/blaze3d/systems/RenderSystem", "color4f",
            "(FFFF)V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "enableBlend",
            "()V",
            "com/mojang/blaze3d/systems/RenderSystem", "enableBlend",
            "()V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "disableBlend",
            "()V",
            "com/mojang/blaze3d/systems/RenderSystem", "disableBlend",
            "()V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "enableAlphaTest",
            "()V",
            "com/retromod/shim/forge/embedded/RenderShim", "enableAlphaTest",
            "()V"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/client/renderer/GlStateManager", "disableAlphaTest",
            "()V",
            "com/retromod/shim/forge/embedded/RenderShim", "disableAlphaTest",
            "()V"
        );
        // MatrixStack introduced
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/Matrix4f",
            "net/minecraft/util/math/vector/Matrix4f"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
