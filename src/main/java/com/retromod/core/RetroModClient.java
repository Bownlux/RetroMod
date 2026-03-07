/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.retromod.gui.RetroModGui;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Client-specific initialization for RetroMod.
 * 
 * This handles:
 * - GUI file picker for adding mods
 * - Visual performance warnings
 * - "Add Mods" floating button
 * 
 * Server-side code should NOT import this class.
 */
@Environment(EnvType.CLIENT)
public class RetroModClient implements ClientModInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Client");
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("RetroMod client-side initialization...");
        
        // Mark environment as client
        EnvironmentDetector.setEnvironment(true, false);
        
        // Get game directory
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();
        
        // IMPORTANT: Create folders if they don't exist (backup in case PreLaunch missed)
        ModHealthChecker.ensureFoldersExist(gameDir);
        
        // Initialize client-only features
        initializeGui(gameDir);
        
        // Register crash handler with client instance
        registerClientCrashHandler();

        // Register RetroMod presence channel for server cosmetic integration
        registerRetroModPresenceChannel();

        LOGGER.info("RetroMod client initialization complete!");
    }
    
    /**
     * Initialize GUI components (file picker, floating button).
     */
    private void initializeGui(Path gameDir) {
        try {
            if (!EnvironmentDetector.isHeadless()) {
                RetroModGui gui = new RetroModGui(gameDir);
                
                if (gui.isFirstRun()) {
                    // First time - show welcome dialog
                    LOGGER.info("First run detected - showing setup dialog");
                    gui.showFirstRunDialog();
                } else {
                    // Show floating "Add Mods" button
                    gui.showAddModsButton();
                }
            } else {
                LOGGER.info("Headless mode detected - skipping GUI initialization");
            }
        } catch (Exception e) {
            LOGGER.warn("Could not initialize GUI: {}", e.getMessage());
            LOGGER.info("GUI features disabled. Use CLI instead.");
        }
    }
    
    /**
     * Register crash handler with Minecraft client for world saving.
     */
    private void registerClientCrashHandler() {
        try {
            SafeCrashHandler crashHandler = SafeCrashHandler.getInstance();
            
            // Try to get Minecraft client instance
            // This uses reflection to avoid compile-time dependency on mapped names
            try {
                Class<?> mcClass = Class.forName("net.minecraft.client.MinecraftClient");
                Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
                crashHandler.registerClient(mcInstance);
                LOGGER.debug("Registered crash handler with Minecraft client");
            } catch (Exception e) {
                // Try Forge naming
                try {
                    Class<?> mcClass = Class.forName("net.minecraft.client.Minecraft");
                    Object mcInstance = mcClass.getMethod("getInstance").invoke(null);
                    crashHandler.registerClient(mcInstance);
                    LOGGER.debug("Registered crash handler with Minecraft client (Forge)");
                } catch (Exception e2) {
                    LOGGER.debug("Could not register client with crash handler");
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Crash handler not available: {}", e.getMessage());
        }
    }

    /**
     * Register a plugin messaging channel to signal RetroMod presence to servers.
     * When joining a server (e.g., RevivalSMP), sends a presence packet so the
     * server can unlock cosmetics for RetroMod users.
     *
     * Uses reflection to avoid compile-time dependency on Fabric API networking classes
     * (which are not available in the Maven build, only in Gradle/Fabric Loom).
     */
    private void registerRetroModPresenceChannel() {
        try {
            // ClientPlayConnectionEvents.JOIN.register(...)
            Class<?> eventsClass = Class.forName("net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents");
            Object joinEvent = eventsClass.getField("JOIN").get(null);

            // The JOIN event's register method accepts a JoinCallback functional interface
            // We create a dynamic proxy to implement it
            Class<?> joinCallbackClass = Class.forName(
                "net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents$Join");

            Object proxy = java.lang.reflect.Proxy.newProxyInstance(
                joinCallbackClass.getClassLoader(),
                new Class<?>[]{joinCallbackClass},
                (proxyObj, method, args) -> {
                    if ("onPlayReady".equals(method.getName())) {
                        sendPresencePacket(args[1]); // args[1] is the PacketSender
                    }
                    return null;
                }
            );

            // Register the proxy with the event
            Method registerMethod = joinEvent.getClass().getMethod("register", Object.class);
            registerMethod.invoke(joinEvent, proxy);

            LOGGER.info("RetroMod presence channel registered");
        } catch (Exception e) {
            LOGGER.debug("Could not register presence channel (Fabric API may not be available): {}", e.getMessage());
        }
    }

    /**
     * Send the RetroMod presence packet to the server via reflection.
     */
    private void sendPresencePacket(Object packetSender) {
        try {
            // Create PacketByteBuf: PacketByteBufs.create()
            Class<?> packetByteBufsClass = Class.forName("net.fabricmc.fabric.api.networking.v1.PacketByteBufs");
            Object buf = packetByteBufsClass.getMethod("create").invoke(null);

            // Write data: buf.writeString("retromod"), buf.writeString("1.0.0-beta.1")
            Method writeString = buf.getClass().getMethod("writeString", String.class);
            writeString.invoke(buf, "retromod");
            writeString.invoke(buf, "1.0.0-beta.1");

            // Create Identifier: Identifier.of("retromod", "presence")
            Class<?> identifierClass = Class.forName("net.minecraft.util.Identifier");
            Object identifier;
            try {
                // MC 1.21+ uses Identifier.of()
                identifier = identifierClass.getMethod("of", String.class, String.class)
                    .invoke(null, "retromod", "presence");
            } catch (NoSuchMethodException e) {
                // Older versions use new Identifier()
                identifier = identifierClass.getConstructor(String.class, String.class)
                    .newInstance("retromod", "presence");
            }

            // Send: sender.sendPacket(identifier, buf)
            Method sendPacket = packetSender.getClass().getMethod("sendPacket",
                identifierClass, buf.getClass().getSuperclass());
            sendPacket.invoke(packetSender, identifier, buf);

            LOGGER.debug("Sent RetroMod presence signal to server");
        } catch (Exception e) {
            LOGGER.debug("Could not send RetroMod presence signal: {}", e.getMessage());
        }
    }
}
