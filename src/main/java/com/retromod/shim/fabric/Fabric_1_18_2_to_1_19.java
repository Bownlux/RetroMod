/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.18.2 to run on 1.19.
 * Handles the major Text API overhaul, chat message changes, and GameEvent updates.
 */
public class Fabric_1_18_2_to_1_19 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.18.2 to 1.19"; }
    @Override public String getSourceVersion() { return "1.18.2"; }
    @Override public String getTargetVersion() { return "1.19"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Text API overhaul - the biggest change
        transformer.registerClassRedirect(
            "net/minecraft/text/LiteralText",
            "net/minecraft/text/Text"
        );
        transformer.registerClassRedirect(
            "net/minecraft/text/TranslatableText",
            "net/minecraft/text/Text"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/text/LiteralText", "<init>",
            "(Ljava/lang/String;)V",
            "net/minecraft/text/Text", "literal",
            "(Ljava/lang/String;)Lnet/minecraft/text/Text;"
        );
        transformer.registerMethodRedirect(
            "net/minecraft/text/TranslatableText", "<init>",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "net/minecraft/text/Text", "translatable",
            "(Ljava/lang/String;[Ljava/lang/Object;)Lnet/minecraft/text/Text;"
        );
        // Chat message changes
        transformer.registerMethodRedirect(
            "net/minecraft/server/network/ServerPlayerEntity", "sendMessage",
            "(Lnet/minecraft/text/Text;Z)V",
            "com/retromod/shim/fabric/embedded/ChatShim", "sendMessage",
            "(Ljava/lang/Object;Ljava/lang/Object;Z)V"
        );
        // GameEvent changes
        transformer.registerClassRedirect(
            "net/minecraft/world/event/GameEvent$Callback",
            "net/minecraft/world/event/GameEvent$Emitter"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
