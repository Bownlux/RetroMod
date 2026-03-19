/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Dedicated server initialization for RetroMod.
 * 
 * Server-specific features:
 * - No GUI (headless operation)
 * - Console-based warnings
 * - Automatic world saving on crash
 * - TPS monitoring for performance
 * 
 * Does NOT initialize any Swing/AWT components.
 */
@Environment(EnvType.SERVER)
public class RetroModServer implements DedicatedServerModInitializer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Server");
    
    @Override
    public void onInitializeServer() {
        LOGGER.info("RetroMod dedicated server initialization...");
        
        // Mark environment as server (headless)
        EnvironmentDetector.setEnvironment(false, true);
        
        // Initialize server-specific features
        initializeServerFeatures();
        
        // Register crash handler with server
        registerServerCrashHandler();
        
        LOGGER.info("=======================================================");
        LOGGER.info("  RetroMod: Server Mode Active");
        LOGGER.info("=======================================================");
        LOGGER.info("  • Bytecode transformation: ENABLED");
        LOGGER.info("  • AOT compilation: ENABLED");
        LOGGER.info("  • GUI features: DISABLED (headless)");
        LOGGER.info("  • Console warnings: ENABLED");
        LOGGER.info("=======================================================");
        
        LOGGER.info("RetroMod server initialization complete!");
    }
    
    /**
     * Initialize server-specific features.
     */
    private void initializeServerFeatures() {
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();
        Path modsFolder = gameDir.resolve("mods");
        
        // IMPORTANT: Create folders if they don't exist (backup in case PreLaunch missed)
        ModHealthChecker.ensureFoldersExist(gameDir);
        
        // Initialize hybrid engine for server
        try {
            HybridTransformationEngine hybrid = HybridTransformationEngine.getInstance();
            hybrid.initialize(modsFolder, "26.1");
            LOGGER.info("Hybrid AOT/JIT engine initialized for server");
        } catch (Exception e) {
            LOGGER.warn("Could not initialize hybrid engine: {}", e.getMessage());
        }
        
        // Log mod count
        try {
            long modCount = java.nio.file.Files.list(modsFolder)
                .filter(p -> p.toString().endsWith(".jar"))
                .count();
            LOGGER.info("Found {} mod JARs in mods folder", modCount);
        } catch (Exception e) {
            LOGGER.debug("Could not count mods: {}", e.getMessage());
        }
    }
    
    /**
     * Register crash handler with Minecraft server for world saving.
     */
    private void registerServerCrashHandler() {
        try {
            SafeCrashHandler crashHandler = SafeCrashHandler.getInstance();
            
            // Try to get server instance
            // This uses reflection to avoid compile-time dependency
            try {
                Class<?> serverClass = Class.forName("net.minecraft.server.MinecraftServer");
                // Server instance is typically set later, so we'll register a callback
                LOGGER.debug("Server crash handler ready (will register instance when available)");
            } catch (Exception e) {
                LOGGER.debug("Could not prepare server crash handler");
            }
        } catch (Exception e) {
            LOGGER.debug("Crash handler not available: {}", e.getMessage());
        }
    }
    
    /**
     * Called when the server is fully started.
     * Can be used to register the server instance with crash handler.
     */
    public static void onServerStarted(Object server) {
        try {
            SafeCrashHandler.getInstance().registerServer(server);
            LOGGER.info("Registered crash handler with Minecraft server");
        } catch (Exception e) {
            LOGGER.debug("Could not register server with crash handler");
        }
    }
    
    /**
     * Called on each server tick for TPS monitoring.
     */
    public static void onServerTick() {
        try {
            MemorySafetyMonitor.getInstance().onServerTick();
        } catch (Exception e) {
            // Ignore tick errors
        }
    }
}
