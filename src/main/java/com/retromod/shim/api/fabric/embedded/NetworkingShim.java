/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Method;

/**
 * Shim for Fabric Networking API changes.
 * 
 * The networking API underwent major changes in 1.20.5:
 * - Old: send(player, Identifier, PacketByteBuf)
 * - New: send(player, CustomPayload)
 * 
 * This shim wraps legacy calls to work with the new payload system.
 */
public class NetworkingShim {
    
    private static final Object LOCK = new Object();
    private static boolean initialized = false;
    private static Method newSendMethod;
    private static Method newClientSendMethod;
    private static Class<?> payloadClass;
    
    /**
     * Legacy server send method - wraps buf in a payload.
     */
    public static void sendLegacy(Object player, Object identifier, Object packetByteBuf) {
        try {
            ensureInitialized();
            
            // Create a legacy wrapper payload
            Object payload = createLegacyPayload(identifier, packetByteBuf);
            
            // Call new send method
            Class<?> serverNetworking = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
            Method send = serverNetworking.getMethod("send", 
                Class.forName("net.minecraft.server.network.ServerPlayerEntity"),
                payloadClass);
            send.invoke(null, player, payload);
            
        } catch (Exception e) {
            // Fallback: try to find legacy method that still exists
            tryLegacyFallback(player, identifier, packetByteBuf, true);
        }
    }
    
    /**
     * Legacy client send method.
     */
    public static void clientSendLegacy(Object identifier, Object packetByteBuf) {
        try {
            ensureInitialized();
            
            Object payload = createLegacyPayload(identifier, packetByteBuf);
            
            Class<?> clientNetworking = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
            Method send = clientNetworking.getMethod("send", payloadClass);
            send.invoke(null, payload);
            
        } catch (Exception e) {
            tryLegacyFallback(null, identifier, packetByteBuf, false);
        }
    }
    
    /**
     * Creates a PacketByteBuf (replaces PacketByteBufs.create()).
     */
    public static Object createBuf() {
        try {
            // Try new way first
            Class<?> packetByteBufClass = Class.forName("net.minecraft.network.PacketByteBuf");
            Class<?> unpooledClass = Class.forName("io.netty.buffer.Unpooled");
            Method buffer = unpooledClass.getMethod("buffer");
            Object byteBuf = buffer.invoke(null);
            return packetByteBufClass.getConstructor(Class.forName("io.netty.buffer.ByteBuf"))
                .newInstance(byteBuf);
        } catch (Exception e) {
            // Try legacy PacketByteBufs.create()
            try {
                Class<?> packetByteBufs = Class.forName("net.fabricmc.fabric.api.networking.v1.PacketByteBufs");
                Method create = packetByteBufs.getMethod("create");
                return create.invoke(null);
            } catch (Exception e2) {
                throw new RuntimeException("Cannot create PacketByteBuf", e2);
            }
        }
    }
    
    private static void ensureInitialized() throws Exception {
        if (!initialized) {
            synchronized (LOCK) {
                if (!initialized) {
                    // Try to find CustomPayload class
                    try {
                        payloadClass = Class.forName("net.fabricmc.fabric.api.networking.v1.FabricPacket");
                    } catch (ClassNotFoundException e) {
                        try {
                            payloadClass = Class.forName("net.minecraft.network.packet.CustomPayload");
                        } catch (ClassNotFoundException e2) {
                            payloadClass = Object.class;
                        }
                    }
                    initialized = true;
                }
            }
        }
    }
    
    private static Object createLegacyPayload(Object identifier, Object packetByteBuf) {
        try {
            // Create a wrapper that implements CustomPayload
            Class<?> wrapperClass = Class.forName("com.retromod.shim.api.fabric.embedded.LegacyPayloadWrapper");
            return wrapperClass.getConstructor(Object.class, Object.class)
                .newInstance(identifier, packetByteBuf);
        } catch (Exception e) {
            // Return a simple record-like object
            return new Object[] { identifier, packetByteBuf };
        }
    }
    
    private static void tryLegacyFallback(Object player, Object identifier, Object buf, boolean isServer) {
        try {
            if (isServer) {
                Class<?> serverNetworking = Class.forName("net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking");
                for (Method m : serverNetworking.getMethods()) {
                    if (m.getName().equals("send") && m.getParameterCount() == 3) {
                        m.invoke(null, player, identifier, buf);
                        return;
                    }
                }
            } else {
                Class<?> clientNetworking = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking");
                for (Method m : clientNetworking.getMethods()) {
                    if (m.getName().equals("send") && m.getParameterCount() == 2) {
                        m.invoke(null, identifier, buf);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Networking shim failed", e);
        }
    }
}
