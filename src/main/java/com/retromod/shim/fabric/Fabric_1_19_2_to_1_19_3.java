/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.19.2 to run on 1.19.3.
 * Handles the major registry package move, creative tab API rework, screen rendering
 * changes with DrawContext introduction, and feature flag additions.
 */
public class Fabric_1_19_2_to_1_19_3 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19.2 to 1.19.3"; }
    @Override public String getSourceVersion() { return "1.19.2"; }
    @Override public String getTargetVersion() { return "1.19.3"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Registry package moved
        transformer.registerClassRedirect(
            "net/minecraft/util/registry/Registry",
            "net/minecraft/registry/Registries"
        );
        transformer.registerClassRedirect(
            "net/minecraft/util/registry/BuiltinRegistries",
            "net/minecraft/registry/RegistryKeys"
        );
        transformer.registerClassRedirect(
            "net/minecraft/util/registry/RegistryEntry",
            "net/minecraft/registry/entry/RegistryEntry"
        );
        transformer.registerClassRedirect(
            "net/minecraft/util/registry/RegistryKey",
            "net/minecraft/registry/RegistryKey"
        );
        // Creative tab API rework
        transformer.registerMethodRedirect(
            "net/minecraft/item/ItemGroup", "builder",
            "(Lnet/minecraft/item/ItemGroup$Row;I)Lnet/minecraft/item/ItemGroup$Builder;",
            "com/retromod/shim/fabric/embedded/ItemGroupShim", "builder",
            "(Ljava/lang/Object;I)Ljava/lang/Object;"
        );
        // Screen rendering changes - DrawContext introduced
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screen/Screen", "renderBackground",
            "(Lnet/minecraft/client/util/math/MatrixStack;)V",
            "com/retromod/shim/fabric/embedded/ScreenShim", "renderBackground",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        );
        // Feature flags
        transformer.registerClassRedirect(
            "net/minecraft/resource/featuretoggle/FeatureFlags",
            "net/minecraft/world/flag/FeatureFlags"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
