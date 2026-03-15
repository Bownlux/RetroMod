/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.gui;

import com.retromod.core.ModVersionDetector;
import com.retromod.core.RetroModTransformer;
import com.retromod.core.FabricModTransformer;
import com.retromod.aot.AotCompiler;
import com.retromod.shim.ShimRegistry;
import com.retromod.embedder.ModVersionInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/**
 * Mod compatibility checker - used internally by the GUI.
 * 
 * Handles transformation for all mod loaders:
 * - Fabric: Uses FabricModTransformer (updates fabric.mod.json in JAR)
 * - Forge/NeoForge: Uses AotCompiler (bytecode transformation)
 */
public class ModCompatibilityChecker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod");
    private static final String TARGET_VERSION = "1.21.11";
    
    private final Path modsFolder;
    private final Path backupsFolder;
    private final ModVersionDetector detector;
    
    public record IncompatibleMod(
        Path jarPath,
        String modName,
        String sourceVersion,
        String loaderType,
        String reason
    ) {}
    
    public ModCompatibilityChecker(Path gameDir) {
        this.modsFolder = gameDir.resolve("mods");
        this.backupsFolder = modsFolder.resolve("retromod-backups");
        this.detector = new ModVersionDetector();
    }
    
    /**
     * Analyze a single mod JAR.
     */
    public IncompatibleMod analyzeJar(Path jarPath) {
        try {
            ModVersionInfo info = detector.detectVersion(jarPath);
            if (info != null && info.needsTransformation(TARGET_VERSION)) {
                String sourceVersion = info.targetMcVersion();
                String loader = info.modLoaderType();
                
                String reason;
                if (sourceVersion != null && !sourceVersion.startsWith("1.21")) {
                    reason = "Version: " + sourceVersion + " → " + TARGET_VERSION;
                } else if ("forge".equals(loader)) {
                    reason = "Forge → NeoForge migration needed";
                } else {
                    reason = "Version mismatch";
                }
                
                return new IncompatibleMod(
                    jarPath,
                    info.modId() != null ? info.modId() : jarPath.getFileName().toString(),
                    sourceVersion,
                    loader,
                    reason
                );
            }
        } catch (Exception e) {
            LOGGER.debug("Could not analyze {}: {}", jarPath.getFileName(), e.getMessage());
        }
        return null;
    }
    
    /**
     * Transform a mod JAR and copy to mods folder.
     * 
     * Uses the appropriate transformer based on mod loader:
     * - Fabric: FabricModTransformer (updates fabric.mod.json directly)
     * - Forge/NeoForge: AotCompiler (bytecode transformation)
     * 
     * @param sourceJar The original mod JAR (from user's selection)
     * @return Path to the transformed JAR in the mods folder
     */
    public Path transformAndInstall(Path sourceJar) throws IOException {
        LOGGER.info("Transforming: {}", sourceJar.getFileName());
        
        // Create mods folder if needed
        Files.createDirectories(modsFolder);
        Files.createDirectories(backupsFolder);
        
        // Detect mod type
        ModVersionInfo info = detector.detectVersion(sourceJar);
        String loaderType = info != null ? info.modLoaderType() : "unknown";
        
        Path transformed;
        
        if ("fabric".equals(loaderType)) {
            // Use FabricModTransformer - this updates fabric.mod.json in the JAR
            // so no fabric_loader_dependencies.json is needed!
            LOGGER.info("Using Fabric transformer (will update fabric.mod.json)");
            FabricModTransformer fabricTransformer = new FabricModTransformer(TARGET_VERSION);
            transformed = fabricTransformer.transformMod(sourceJar, modsFolder);
        } else {
            // Use AotCompiler for Forge/NeoForge
            LOGGER.info("Using AOT compiler for {}", loaderType);
            ShimRegistry shimRegistry = new ShimRegistry();
            AotCompiler compiler = new AotCompiler(shimRegistry, TARGET_VERSION);
            
            Path tempTransformed = compiler.compileModAot(sourceJar);
            
            // Copy to mods folder with -retromod suffix
            String originalName = sourceJar.getFileName().toString();
            String newName = originalName.replace(".jar", "-retromod.jar");
            transformed = modsFolder.resolve(newName);
            
            Files.copy(tempTransformed, transformed, StandardCopyOption.REPLACE_EXISTING);
        }
        
        LOGGER.info("Installed transformed mod: {}", transformed.getFileName());
        return transformed;
    }
    
    /**
     * Get the mods folder path.
     */
    public Path getModsFolder() {
        return modsFolder;
    }
}
