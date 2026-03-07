/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.19.2 to 1.19.3 shim - Registry and Creative Tab rework.
 * The registry system was restructured from Registry to BuiltInRegistries.
 * Creative mode tabs were completely overhauled with a new builder pattern.
 * Forge registry events were also restructured, and GUI rendering moved
 * from PoseStack to GuiGraphics.
 */
public class Forge_1_19_2_to_1_19_3 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.19.2 to 1.19.3"; }
    @Override public String getSourceVersion() { return "1.19.2"; }
    @Override public String getTargetVersion() { return "1.19.3"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerClassRedirect(
            "net/minecraft/core/Registry",
            "net/minecraft/core/registries/BuiltInRegistries"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/CreativeModeTab", "builder",
            "(Lnet/minecraft/world/item/CreativeModeTab$Row;I)Lnet/minecraft/world/item/CreativeModeTab$Builder;",
            "com/retromod/shim/forge/embedded/CreativeTabShim", "builder",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );
        // Forge registry event changes
        transformer.registerClassRedirect(
            "net/minecraftforge/event/RegistryEvent",
            "net/minecraftforge/registries/RegisterEvent"
        );
        transformer.registerMethodRedirect(
            "net/minecraftforge/client/event/RenderGuiOverlayEvent", "getMatrixStack",
            "()Lcom/mojang/blaze3d/vertex/PoseStack;",
            "com/retromod/shim/forge/embedded/RenderShim", "getGuiGraphics",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
