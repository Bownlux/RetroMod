/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Forge 1.18.2 to 1.19 shim - Text/Component API overhaul.
 * TextComponent and TranslatableComponent were removed in favor of
 * static factory methods on Component (Component.literal and Component.translatable).
 * Player message sending was also restructured.
 */
public class Forge_1_18_2_to_1_19 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.18.2 to 1.19"; }
    @Override public String getSourceVersion() { return "1.18.2"; }
    @Override public String getTargetVersion() { return "1.19"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerMethodRedirect(
            "net/minecraft/network/chat/TextComponent", "<init>",
            "(Ljava/lang/String;)V",
            "net/minecraft/network/chat/Component", "literal",
            "(Ljava/lang/String;)Lnet/minecraft/network/chat/MutableComponent;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/network/chat/TranslatableComponent", "<init>",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "net/minecraft/network/chat/Component", "translatable",
            "(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/network/chat/MutableComponent;"
        );
        transformer.registerClassRedirect(
            "net/minecraft/network/chat/TextComponent",
            "net/minecraft/network/chat/Component"
        );
        transformer.registerClassRedirect(
            "net/minecraft/network/chat/TranslatableComponent",
            "net/minecraft/network/chat/Component"
        );
        // Forge events still stable
        transformer.registerMethodRedirect(
            "net/minecraft/server/level/ServerPlayer", "sendMessage",
            "(Lnet/minecraft/network/chat/Component;Z)V",
            "com/retromod/shim/forge/embedded/ChatShim", "sendMessage",
            "(Ljava/lang/Object;Ljava/lang/Object;Z)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
