/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.retromod.gui.RetromodGui;
import com.retromod.gui.TitleScreenButtonInjector;
import com.retromod.util.McReflect;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Client-specific initialization for Retromod.
 * 
 * This handles:
 * - GUI file picker for adding mods
 * - Visual performance warnings
 * - "Add Mods" floating button
 * 
 * Server-side code should NOT import this class.
 */
@Environment(EnvType.CLIENT)
public class RetromodClient implements ClientModInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-Client");
    
    @Override
    public void onInitializeClient() {
        LOGGER.info("Retromod client-side initialization...");
        
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

        // Register Retromod presence channel for server cosmetic integration
        registerRetromodPresenceChannel();

        LOGGER.info("Retromod client initialization complete!");
    }
    
    /**
     * Initialize GUI components.
     *
     * Registers a title screen button injector that adds a "Retromod" button
     * to the Minecraft title screen. The injector auto-detects the loader
     * (Fabric/NeoForge/Forge) and uses the appropriate event system.
     *
     * On Fabric, this requires Fabric API to be installed for ScreenEvents.
     * If Fabric API is not available, falls back to logging a message.
     *
     * See: TitleScreenButtonInjector, RetromodScreen
     */
    private void initializeGui(Path gameDir) {
        try {
            TitleScreenButtonInjector.register();
            LOGGER.info("Retromod GUI available via title screen button");
        } catch (Exception e) {
            LOGGER.warn("Could not register title screen button: {}", e.getMessage());
            LOGGER.info("Use the CLI instead: retromod <command>");
        }
    }
    
    /**
     * Register crash handler with Minecraft client for world saving.
     * Uses McReflect to resolve the correct class name across all loaders.
     */
    private void registerClientCrashHandler() {
        try {
            SafeCrashHandler crashHandler = SafeCrashHandler.getInstance();

            // Resolve MC client class (handles yarn, mojang, and intermediary names)
            Class<?> mcClass = McReflect.findClass(
                "net.minecraft.client.MinecraftClient",  // yarn
                "net.minecraft.client.Minecraft"         // mojang
            );

            if (mcClass != null) {
                Method getInstance = McReflect.findMethod(mcClass, "getInstance");
                if (getInstance != null) {
                    Object mcInstance = getInstance.invoke(null);
                    crashHandler.registerClient(mcInstance);
                    LOGGER.debug("Registered crash handler with Minecraft client");
                }
            } else {
                LOGGER.debug("Could not find Minecraft client class for crash handler");
            }
        } catch (Exception e) {
            LOGGER.debug("Crash handler not available: {}", e.getMessage());
        }
    }

    /**
     * Register a plugin messaging channel to signal Retromod presence to servers.
     * When joining a server (e.g., RevivalSMP), sends a presence packet so the
     * server can unlock cosmetics for Retromod users.
     *
     * Uses reflection to avoid compile-time dependency on Fabric API networking classes
     * (which are not available in the Maven build, only in Gradle/Fabric Loom).
     */
    private void registerRetromodPresenceChannel() {
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

            LOGGER.info("Retromod presence channel registered");
        } catch (Exception e) {
            LOGGER.debug("Could not register presence channel (Fabric API may not be available): {}", e.getMessage());
        }
    }

    /**
     * Send the Retromod presence packet to the server via reflection.
     */
    private void sendPresencePacket(Object packetSender) {
        try {
            // Create PacketByteBuf: PacketByteBufs.create()
            Class<?> packetByteBufsClass = Class.forName("net.fabricmc.fabric.api.networking.v1.PacketByteBufs");
            Object buf = packetByteBufsClass.getMethod("create").invoke(null);

            // Write data: buf.writeString("retromod"), buf.writeString("1.0.0-beta.7")
            Method writeString = buf.getClass().getMethod("writeString", String.class);
            writeString.invoke(buf, "retromod");
            writeString.invoke(buf, "1.0.0-beta.7");

            // Create Identifier: Identifier.of("retromod", "presence")
            // yarn: net.minecraft.util.Identifier, mojang: net.minecraft.resources.ResourceLocation
            Class<?> identifierClass = McReflect.findClass(
                "net.minecraft.util.Identifier",              // yarn
                "net.minecraft.resources.ResourceLocation"    // mojang
            );
            if (identifierClass == null) {
                LOGGER.debug("Could not find Identifier/ResourceLocation class");
                return;
            }
            Object identifier;
            // MC 1.21+ uses Identifier.of() / ResourceLocation.fromNamespaceAndPath()
            Method idFactory = McReflect.findMethod(identifierClass,
                new Class[]{String.class, String.class},
                "of", "fromNamespaceAndPath");
            if (idFactory != null) {
                identifier = idFactory.invoke(null, "retromod", "presence");
            } else {
                // Older versions use constructor
                identifier = identifierClass.getConstructor(String.class, String.class)
                    .newInstance("retromod", "presence");
            }

            // Send: sender.sendPacket(identifier, buf)
            Method sendPacket = packetSender.getClass().getMethod("sendPacket",
                identifierClass, buf.getClass().getSuperclass());
            sendPacket.invoke(packetSender, identifier, buf);

            LOGGER.debug("Sent Retromod presence signal to server");
        } catch (Exception e) {
            LOGGER.debug("Could not send Retromod presence signal: {}", e.getMessage());
        }
    }
}
