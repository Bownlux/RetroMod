/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import com.retromod.gui.RetroModGui;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Forge entry point for RetroMod.
 * Works on BOTH clients and dedicated servers!
 * 
 * CLIENT:
 *   - GUI file picker for adding mods
 *   - Visual performance warnings
 *   - "Add Mods" floating button
 * 
 * SERVER:
 *   - Console-based warnings
 *   - Automatic transformation
 *   - No GUI (headless)
 * 
 * USER EXPERIENCE (CLIENT):
 * 
 * First Launch:
 *   1. RetroMod shows a welcome dialog
 *   2. Opens file picker (Finder on Mac, Explorer on Windows)
 *   3. User selects mod JARs they want to use
 *   4. RetroMod transforms them and puts in mods folder
 *   5. User restarts game
 *   6. Mods work!
 * 
 * Subsequent Launches:
 *   - Small "Add Mods" button in corner
 *   - Click to add more mods anytime
 * 
 * USER EXPERIENCE (SERVER):
 * 
 *   - Just drop mods in mods folder
 *   - RetroMod auto-transforms on startup
 *   - Warnings logged to console
 */
public class RetroModForge {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod");
    
    public RetroModForge() {
        LOGGER.info("RetroMod initializing on Forge...");
        
        // Detect environment
        boolean isServer = EnvironmentDetector.isDedicatedServer();
        LOGGER.info("Environment: {}", isServer ? "Dedicated Server" : "Client");
        
        // Initialize the transformer
        RetroModTransformer transformer = RetroModTransformer.getInstance();
        
        // Load Forge-specific shims
        loadForgeShims(transformer);
        
        // Initialize hybrid AOT/JIT engine
        initializeHybridEngine();
        
        // CLIENT: Show GUI for first-time setup or add mods button
        // SERVER: Skip GUI, just log
        if (!isServer && EnvironmentDetector.canShowGui()) {
            initializeClientGui();
        } else {
            LOGGER.info("Server mode - GUI disabled");
            LOGGER.info("Add mods to the mods folder and restart");
        }
        
        // Scan for mods that can be runtime-transformed (minor versions)
        scanForRuntimeTransformableMods();
        
        LOGGER.info("RetroMod initialized!");
        
        if (isServer) {
            LOGGER.info("=======================================================");
            LOGGER.info("  RetroMod: Server Mode Active");
            LOGGER.info("=======================================================");
            LOGGER.info("  • Bytecode transformation: ENABLED");
            LOGGER.info("  • AOT compilation: ENABLED");
            LOGGER.info("  • GUI features: DISABLED (headless)");
            LOGGER.info("=======================================================");
        }
    }
    
    /**
     * Initialize hybrid AOT/JIT engine.
     */
    private void initializeHybridEngine() {
        try {
            Path gameDir = Paths.get(".").toAbsolutePath().normalize();
            Path modsFolder = gameDir.resolve("mods");
            
            HybridTransformationEngine hybrid = HybridTransformationEngine.getInstance();
            hybrid.initialize(modsFolder, "1.21.11");
            
            LOGGER.info("Hybrid AOT/JIT engine initialized");
        } catch (Exception e) {
            LOGGER.warn("Could not initialize hybrid engine: {}", e.getMessage());
        }
    }
    
    /**
     * Initialize GUI components (client only).
     */
    private void initializeClientGui() {
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();
        
        try {
            RetroModGui gui = new RetroModGui(gameDir);
            
            if (gui.isFirstRun()) {
                // First time - show welcome and file picker
                LOGGER.info("First run detected - showing setup dialog");
                gui.showFirstRunDialog();
            } else {
                // Show floating "Add Mods" button
                gui.showAddModsButton();
            }
        } catch (Exception e) {
            LOGGER.warn("Could not initialize GUI: {}", e.getMessage());
            LOGGER.info("Use CLI instead: java -jar retromod-cli.jar aot <mod.jar>");
        }
    }
    
    private void loadForgeShims(RetroModTransformer transformer) {
        try {
            java.util.ServiceLoader<VersionShim> loader = 
                java.util.ServiceLoader.load(VersionShim.class);
            
            int count = 0;
            for (VersionShim shim : loader) {
                String loaderType = shim.getModLoaderType();
                if ("forge".equals(loaderType) || "common".equals(loaderType)) {
                    shim.registerRedirects(transformer);
                    count++;
                }
            }
            
            LOGGER.info("Loaded {} Forge version shims", count);
        } catch (Exception e) {
            LOGGER.error("Failed to load Forge shims", e);
        }
    }
    
    private void scanForRuntimeTransformableMods() {
        try {
            Path modsFolder = Paths.get("mods");
            if (!Files.exists(modsFolder)) return;
            
            ModVersionDetector detector = new ModVersionDetector();
            java.io.File[] modFiles = modsFolder.toFile().listFiles(
                (dir, name) -> name.endsWith(".jar") && !name.contains("-retromod")
            );
            
            if (modFiles == null) return;
            
            for (java.io.File modFile : modFiles) {
                try {
                    var info = detector.detectVersion(modFile.toPath());
                    if (info != null && info.needsTransformation("1.21.11")) {
                        String sourceVersion = info.targetMcVersion();
                        
                        // Only runtime-transform minor version diffs
                        if (sourceVersion != null && sourceVersion.startsWith("1.21")) {
                            LOGGER.info("Runtime transforming: {} ({} -> 1.21.11)", 
                                modFile.getName(), sourceVersion);
                            
                            for (String pkg : info.modPackages()) {
                                RetroModTransformer.getInstance().addTransformablePackage(pkg);
                            }
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not analyze: {}", modFile.getName());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error scanning mods", e);
        }
    }
}
