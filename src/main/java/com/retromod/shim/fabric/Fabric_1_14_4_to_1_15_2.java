/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Fabric shim for 1.14.4 to 1.15.2. Rendering pipeline changes and some API additions.
 */
public class Fabric_1_14_4_to_1_15_2 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.14.4 to 1.15.2"; }
    @Override public String getSourceVersion() { return "1.14.4"; }
    @Override public String getTargetVersion() { return "1.15.2"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Rendering changes
        transformer.registerMethodRedirect(
            "net/minecraft/client/render/item/ItemRenderer", "renderGuiItemIcon",
            "(Lnet/minecraft/item/ItemStack;II)V",
            "com/retromod/shim/fabric/embedded/ItemRendererShim", "renderGuiItemIcon",
            "(Ljava/lang/Object;Ljava/lang/Object;II)V"
        );
        // Screen rendering method changes
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screen/Screen", "renderTooltip",
            "(Ljava/util/List;II)V",
            "com/retromod/shim/fabric/embedded/ScreenShim", "renderTooltip",
            "(Ljava/lang/Object;Ljava/util/List;II)V"
        );
        // Bee entity added (no breaking changes, just additions)
        // RenderLayer changes
        transformer.registerMethodRedirect(
            "net/minecraft/client/render/RenderLayer", "getEntityCutout",
            "(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;",
            "net/minecraft/client/render/RenderLayer", "getEntityCutoutNoCull",
            "(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
