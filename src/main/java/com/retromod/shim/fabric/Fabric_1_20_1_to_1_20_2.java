/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.20.1 to run on 1.20.2.
 * Handles networking protocol refactor and configuration phase addition.
 */
public class Fabric_1_20_1_to_1_20_2 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.20.1 to 1.20.2"; }
    @Override public String getSourceVersion() { return "1.20.1"; }
    @Override public String getTargetVersion() { return "1.20.2"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Networking protocol refactored
        transformer.registerMethodRedirect(
            "net/minecraft/network/PacketByteBuf", "readIdentifier",
            "()Lnet/minecraft/util/Identifier;",
            "com/retromod/shim/fabric/embedded/NetworkShim", "readIdentifier",
            "(Ljava/lang/Object;)Lnet/minecraft/util/Identifier;"
        );
        // Configuration phase added
        transformer.registerMethodRedirect(
            "net/minecraft/network/ClientConnection", "send",
            "(Lnet/minecraft/network/Packet;)V",
            "com/retromod/shim/fabric/embedded/NetworkShim", "send",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
