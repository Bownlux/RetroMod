/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * Shim for Forge's Network system that bridges to NeoForge.
 * 
 * The networking API changed significantly:
 * - Forge used SimpleChannel with packet registration
 * - NeoForge uses PayloadChannel with codec-based registration
 */
package com.retromod.shim.forge.embedded;

import java.lang.reflect.*;
import java.util.*;
import java.util.function.*;

/**
 * Shim for net.minecraftforge.network.NetworkRegistry and SimpleChannel
 * 
 * Bridges old Forge networking patterns to NeoForge's new system.
 */
public final class NetworkShim {
    
    private static Class<?> payloadRegistrarClass;
    private static Class<?> simpleChannelClass;
    private static boolean isNeoForge = false;
    private static boolean initialized = false;
    
    // Track registered channels
    private static final Map<String, Object> registeredChannels = new HashMap<>();
    
    private NetworkShim() {}
    
    private static synchronized void initialize() {
        if (initialized) return;
        initialized = true;
        
        try {
            // Try NeoForge networking
            payloadRegistrarClass = Class.forName(
                "net.neoforged.neoforge.network.registration.PayloadRegistrar"
            );
            isNeoForge = true;
            
        } catch (ClassNotFoundException e) {
            try {
                // Fall back to Forge SimpleChannel
                simpleChannelClass = Class.forName(
                    "net.minecraftforge.network.simple.SimpleChannel"
                );
            } catch (ClassNotFoundException e2) {
                System.err.println("RetroMod: No networking system found");
            }
        }
    }
    
    /**
     * Create a new network channel.
     * 
     * Old Forge pattern:
     *   NetworkRegistry.newSimpleChannel(
     *     new ResourceLocation(MODID, "main"),
     *     () -> PROTOCOL_VERSION,
     *     PROTOCOL_VERSION::equals,
     *     PROTOCOL_VERSION::equals
     *   );
     */
    public static Object newSimpleChannel(Object resourceLocation, 
            Supplier<String> networkProtocolVersion,
            Predicate<String> clientAcceptedVersions,
            Predicate<String> serverAcceptedVersions) {
        
        initialize();
        
        if (isNeoForge) {
            // NeoForge uses a different registration pattern
            // We create a wrapper that tracks packet registrations
            return new SimpleChannelWrapper(resourceLocation, networkProtocolVersion.get());
        }
        
        // Try old Forge
        if (simpleChannelClass != null) {
            try {
                Class<?> registryClass = Class.forName(
                    "net.minecraftforge.network.NetworkRegistry"
                );
                
                Method newChannel = registryClass.getMethod("newSimpleChannel",
                    resourceLocation.getClass(),
                    Supplier.class,
                    Predicate.class,
                    Predicate.class
                );
                
                return newChannel.invoke(null, resourceLocation, 
                    networkProtocolVersion, clientAcceptedVersions, serverAcceptedVersions);
                    
            } catch (Exception e) {
                System.err.println("RetroMod: Could not create SimpleChannel: " + e);
            }
        }
        
        return new SimpleChannelWrapper(resourceLocation, "1");
    }
    
    /**
     * Wrapper class that mimics SimpleChannel for NeoForge.
     * Collects packet registrations and replays them during mod setup.
     */
    public static class SimpleChannelWrapper {
        private final Object resourceLocation;
        private final String protocolVersion;
        private final List<PacketRegistration<?>> registrations = new ArrayList<>();
        private int nextId = 0;
        
        public SimpleChannelWrapper(Object resourceLocation, String protocolVersion) {
            this.resourceLocation = resourceLocation;
            this.protocolVersion = protocolVersion;
            registeredChannels.put(resourceLocation.toString(), this);
        }
        
        /**
         * Register a packet.
         * 
         * Old pattern:
         *   channel.registerMessage(id, MyPacket.class, MyPacket::encode, 
         *       MyPacket::decode, MyPacket::handle);
         */
        public <T> void registerMessage(int id, Class<T> messageType,
                BiConsumer<T, Object> encoder,
                Function<Object, T> decoder,
                BiConsumer<T, Object> messageConsumer) {
            
            registrations.add(new PacketRegistration<>(
                id, messageType, encoder, decoder, messageConsumer
            ));
        }
        
        /**
         * Register a packet with automatic ID assignment.
         */
        public <T> MessageBuilder<T> messageBuilder(Class<T> type, int id) {
            return new MessageBuilder<>(this, type, id);
        }
        
        /**
         * Send a packet to the server.
         */
        public void sendToServer(Object message) {
            // NeoForge: PacketDistributor.sendToServer(payload)
            try {
                Class<?> distributorClass = Class.forName(
                    "net.neoforged.neoforge.network.PacketDistributor"
                );
                
                Method sendToServer = distributorClass.getMethod("sendToServer", Object.class);
                
                // Wrap the message as a payload
                Object payload = wrapAsPayload(message);
                sendToServer.invoke(null, payload);
                
            } catch (Exception e) {
                System.err.println("RetroMod: Could not send to server: " + e);
            }
        }
        
        /**
         * Send a packet to a specific player.
         */
        public void send(Object target, Object message) {
            try {
                Class<?> distributorClass = Class.forName(
                    "net.neoforged.neoforge.network.PacketDistributor"
                );
                
                // Determine target type and get appropriate method
                Object payload = wrapAsPayload(message);
                
                // PacketDistributor.sendToPlayer(player, payload)
                Method sendToPlayer = distributorClass.getMethod("sendToPlayer", 
                    Class.forName("net.minecraft.server.level.ServerPlayer"),
                    Object.class
                );
                
                sendToPlayer.invoke(null, target, payload);
                
            } catch (Exception e) {
                System.err.println("RetroMod: Could not send packet: " + e);
            }
        }
        
        /**
         * Wrap a Forge-style message as a NeoForge payload.
         */
        private Object wrapAsPayload(Object message) {
            // Create a wrapper payload that holds the original message
            return new PayloadWrapper(resourceLocation, message, this);
        }
        
        public Object getResourceLocation() {
            return resourceLocation;
        }
        
        public List<PacketRegistration<?>> getRegistrations() {
            return registrations;
        }
    }
    
    /**
     * Message builder for fluent registration.
     */
    public static class MessageBuilder<T> {
        private final SimpleChannelWrapper channel;
        private final Class<T> type;
        private final int id;
        private BiConsumer<T, Object> encoder;
        private Function<Object, T> decoder;
        private BiConsumer<T, Object> consumer;
        
        public MessageBuilder(SimpleChannelWrapper channel, Class<T> type, int id) {
            this.channel = channel;
            this.type = type;
            this.id = id;
        }
        
        public MessageBuilder<T> encoder(BiConsumer<T, Object> encoder) {
            this.encoder = encoder;
            return this;
        }
        
        public MessageBuilder<T> decoder(Function<Object, T> decoder) {
            this.decoder = decoder;
            return this;
        }
        
        public MessageBuilder<T> consumer(BiConsumer<T, Object> consumer) {
            this.consumer = consumer;
            return this;
        }
        
        public void add() {
            channel.registerMessage(id, type, encoder, decoder, consumer);
        }
    }
    
    /**
     * Record for storing packet registration info.
     */
    public record PacketRegistration<T>(
        int id,
        Class<T> messageType,
        BiConsumer<T, Object> encoder,
        Function<Object, T> decoder,
        BiConsumer<T, Object> consumer
    ) {}
    
    /**
     * Wrapper that holds a Forge-style message as a NeoForge payload.
     */
    public static class PayloadWrapper {
        private final Object channelId;
        private final Object message;
        private final SimpleChannelWrapper channel;
        
        public PayloadWrapper(Object channelId, Object message, SimpleChannelWrapper channel) {
            this.channelId = channelId;
            this.message = message;
            this.channel = channel;
        }
        
        public Object getChannelId() { return channelId; }
        public Object getMessage() { return message; }
        public SimpleChannelWrapper getChannel() { return channel; }
        
        /**
         * Get the payload type ID (for NeoForge registration).
         */
        public Object type() {
            return channelId;
        }
    }
    
    /**
     * PacketDistributor compatibility.
     * Provides target specifications for packet sending.
     */
    public static class PacketDistributor {
        
        public static Object PLAYER(Object player) {
            return new PacketTarget("PLAYER", player);
        }
        
        public static Object ALL() {
            return new PacketTarget("ALL", null);
        }
        
        public static Object SERVER() {
            return new PacketTarget("SERVER", null);
        }
        
        public static Object TRACKING_ENTITY(Object entity) {
            return new PacketTarget("TRACKING_ENTITY", entity);
        }
        
        public static Object TRACKING_CHUNK(Object chunk) {
            return new PacketTarget("TRACKING_CHUNK", chunk);
        }
        
        public static Object NEAR(Object pos, double distance, Object dimension) {
            return new PacketTarget("NEAR", new Object[]{pos, distance, dimension});
        }
        
        public record PacketTarget(String type, Object data) {}
    }
    
    /**
     * Get all registered channels (for debugging).
     */
    public static Map<String, Object> getRegisteredChannels() {
        return Collections.unmodifiableMap(registeredChannels);
    }
    
    /**
     * Replay registrations to NeoForge's PayloadRegistrar.
     * Called during mod initialization.
     */
    public static void replayRegistrations(Object registrar) {
        for (Object channel : registeredChannels.values()) {
            if (channel instanceof SimpleChannelWrapper wrapper) {
                for (PacketRegistration<?> reg : wrapper.getRegistrations()) {
                    registerWithNeoForge(registrar, wrapper, reg);
                }
            }
        }
    }
    
    private static void registerWithNeoForge(Object registrar, 
            SimpleChannelWrapper channel, PacketRegistration<?> reg) {
        
        // NeoForge uses codec-based registration
        // This is a simplified bridge - full implementation would create proper codecs
        try {
            // registrar.playToServer(type, codec, handler)
            // or registrar.playToClient(type, codec, handler)
            
            System.out.println("RetroMod: Would register packet " + reg.messageType().getSimpleName() 
                + " on channel " + channel.getResourceLocation());
                
        } catch (Exception e) {
            System.err.println("RetroMod: Could not replay registration: " + e);
        }
    }
}
