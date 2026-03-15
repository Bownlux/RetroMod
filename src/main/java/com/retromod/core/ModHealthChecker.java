/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.jar.*;

/**
 * Monitors mod health and detects broken mods at runtime.
 * 
 * This handles the case where:
 * - Game launches successfully
 * - But a transformed mod doesn't work properly
 * 
 * When a broken mod is detected:
 * 1. Shows user a warning
 * 2. Directs them to GitHub Issues to report
 * 3. Offers to restore the original and retry
 */
public class ModHealthChecker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Health");
    
    // Folder for original mod backups (before transformation)
    private static final String BACKUP_FOLDER = "retromod-backups";
    
    // Track mods being monitored
    private static final Map<String, ModHealthInfo> monitoredMods = new ConcurrentHashMap<>();
    
    // Track errors per mod
    private static final Map<String, List<String>> modErrors = new ConcurrentHashMap<>();
    
    // Error threshold before considering mod "broken"
    private static final int ERROR_THRESHOLD = 3;
    
    public record ModHealthInfo(
        String modId,
        String modName,
        Path transformedPath,
        Path backupPath,
        long transformTime,
        boolean isHealthy
    ) {}
    
    /**
     * Create backup folders and ensure they exist.
     * Call this early during mod initialization.
     */
    public static void ensureFoldersExist(Path gameDir) {
        try {
            // Create retromod-input folder
            Path inputFolder = gameDir.resolve("retromod-input");
            if (!Files.exists(inputFolder)) {
                Files.createDirectories(inputFolder);
                LOGGER.info("Created retromod-input/ folder");
                
                // Create README
                createReadme(inputFolder);
            }
            
            // Create processed subfolder
            Path processedFolder = inputFolder.resolve("processed");
            if (!Files.exists(processedFolder)) {
                Files.createDirectories(processedFolder);
            }
            
            // Create backups folder
            Path backupFolder = gameDir.resolve(BACKUP_FOLDER);
            if (!Files.exists(backupFolder)) {
                Files.createDirectories(backupFolder);
                LOGGER.info("Created retromod-backups/ folder");
            }
            
        } catch (IOException e) {
            LOGGER.error("Could not create RetroMod folders", e);
        }
    }
    
    /**
     * Create the README.txt in retromod-input folder.
     */
    private static void createReadme(Path inputFolder) {
        try {
            Path readme = inputFolder.resolve("README.txt");
            Files.writeString(readme, """
                ═══════════════════════════════════════════════════════════════
                RETROMOD INPUT FOLDER
                ═══════════════════════════════════════════════════════════════
                
                Put your OLD mods here (NOT in the mods/ folder!)
                
                RetroMod will automatically:
                1. Transform them to work with your Minecraft version
                2. Copy the transformed versions to mods/
                3. Move the originals to processed/
                
                STEPS:
                1. Download old mods (e.g., from Modrinth or CurseForge)
                2. Put the .jar files in THIS folder
                3. Start Minecraft
                4. Done! The mods will be transformed automatically.
                
                WHY NOT PUT DIRECTLY IN MODS FOLDER?
                
                Fabric checks mod versions BEFORE RetroMod can help.
                If you put old mods directly in mods/, Fabric will crash!
                
                By using this folder, RetroMod transforms mods first.
                
                ═══════════════════════════════════════════════════════════════
                SERVER-ONLY MODS
                ═══════════════════════════════════════════════════════════════
                
                If a mod is server-only (like Lithium), then:
                - Only the SERVER needs RetroMod installed
                - Players can join WITHOUT having RetroMod!
                - The transformed mod just works on the server
                
                RetroMod will tell you if a mod is server-only.
                
                ═══════════════════════════════════════════════════════════════
                NEED HELP? FOUND A BUG?
                ═══════════════════════════════════════════════════════════════
                
                Report bugs on GitHub:
                https://github.com/Bownlux/MC-RetroMod/issues

                - Open an issue for bug reports
                - Ask questions
                - Share mod compatibility info
                
                ═══════════════════════════════════════════════════════════════
                """);
        } catch (IOException e) {
            LOGGER.debug("Could not create README", e);
        }
    }
    
    /**
     * Register a mod for health monitoring.
     * Called after successful transformation.
     */
    public static void registerTransformedMod(String modId, String modName, 
                                               Path transformedPath, Path originalPath,
                                               Path gameDir) {
        
        // Create backup of original
        Path backupPath = null;
        try {
            Path backupFolder = gameDir.resolve(BACKUP_FOLDER);
            Files.createDirectories(backupFolder);
            
            String backupName = originalPath.getFileName().toString()
                .replace(".jar", "-original.jar");
            backupPath = backupFolder.resolve(backupName);
            
            if (!Files.exists(backupPath)) {
                Files.copy(originalPath, backupPath, StandardCopyOption.REPLACE_EXISTING);
                LOGGER.debug("Created backup: {}", backupPath.getFileName());
            }
        } catch (IOException e) {
            LOGGER.warn("Could not create backup for {}", modId);
        }
        
        ModHealthInfo info = new ModHealthInfo(
            modId, modName, transformedPath, backupPath,
            System.currentTimeMillis(), true
        );
        
        monitoredMods.put(modId, info);
        modErrors.put(modId, new CopyOnWriteArrayList<>());
        
        LOGGER.debug("Monitoring mod health: {}", modId);
    }
    
    /**
     * Report an error from a mod.
     * Call this when catching exceptions from mod code.
     */
    public static void reportModError(String modId, String errorMessage, Throwable error) {
        List<String> errors = modErrors.computeIfAbsent(modId, k -> new CopyOnWriteArrayList<>());
        
        String fullError = errorMessage + ": " + 
            (error != null ? error.getClass().getSimpleName() + " - " + error.getMessage() : "Unknown");
        
        errors.add(fullError);
        
        LOGGER.warn("Mod error reported for {}: {}", modId, fullError);
        
        // Check if mod is now considered broken
        if (errors.size() >= ERROR_THRESHOLD) {
            markModAsBroken(modId, errors);
        }
    }
    
    /**
     * Report an error by class name (tries to detect which mod).
     */
    public static void reportErrorByClass(String className, Throwable error) {
        // Try to find which mod this class belongs to
        for (Map.Entry<String, ModHealthInfo> entry : monitoredMods.entrySet()) {
            String modId = entry.getKey();
            // Simple heuristic: check if class name contains mod ID
            if (className.toLowerCase().contains(modId.toLowerCase())) {
                reportModError(modId, "Error in " + className, error);
                return;
            }
        }
        
        // Couldn't determine mod, log generically
        LOGGER.warn("Error in unknown mod class {}: {}", className, 
            error != null ? error.getMessage() : "Unknown");
    }
    
    /**
     * Mark a mod as broken and notify user.
     */
    private static void markModAsBroken(String modId, List<String> errors) {
        ModHealthInfo info = monitoredMods.get(modId);
        if (info == null || !info.isHealthy()) return;
        
        // Update status
        monitoredMods.put(modId, new ModHealthInfo(
            info.modId(), info.modName(), info.transformedPath(),
            info.backupPath(), info.transformTime(), false
        ));
        
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  MOD BROKEN AFTER TRANSFORMATION!");
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  Mod: {} ({})", info.modName(), modId);
        LOGGER.error("");
        LOGGER.error("  The game launched but this mod isn't working properly.");
        LOGGER.error("  This might be a RetroMod compatibility issue.");
        LOGGER.error("");
        LOGGER.error("  PLEASE REPORT THIS BUG:");
        LOGGER.error("  → https://github.com/Bownlux/MC-RetroMod/issues");
        LOGGER.error("");
        LOGGER.error("  Recent errors:");
        for (String err : errors) {
            LOGGER.error("    • {}", err);
        }
        LOGGER.error("═══════════════════════════════════════════════════════════");
        
        // Show GUI warning if possible
        if (EnvironmentDetector.canShowGui()) {
            showBrokenModDialog(info, errors);
        }
    }
    
    /**
     * Show dialog for broken mod.
     */
    private static void showBrokenModDialog(ModHealthInfo info, List<String> errors) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            StringBuilder message = new StringBuilder();
            message.append("⚠️ Mod Not Working Properly!\n\n");
            message.append("═══════════════════════════════════════\n\n");
            message.append("Mod: ").append(info.modName()).append("\n\n");
            message.append("The game launched but this mod isn't\n");
            message.append("working correctly after transformation.\n\n");
            message.append("═══════════════════════════════════════\n\n");
            message.append("Please report this bug on GitHub:\n");
            message.append("→ github.com/Bownlux/MC-RetroMod/issues\n\n");
            message.append("═══════════════════════════════════════\n\n");
            message.append("Recent errors:\n");
            for (int i = 0; i < Math.min(3, errors.size()); i++) {
                message.append("• ").append(truncate(errors.get(i), 50)).append("\n");
            }
            
            int choice = JOptionPane.showOptionDialog(
                null,
                message.toString(),
                "RetroMod - Mod Issue Detected",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE,
                null,
                new String[]{"Open GitHub Issues", "Restore Original", "Ignore"},
                "Open GitHub Issues"
            );
            
            if (choice == 0) {
                // Open GitHub Issues
                openGitHub();
            } else if (choice == 1) {
                // Restore original
                restoreOriginal(info);
            }
        });
    }
    
    /**
     * Restore the original mod from backup.
     */
    public static boolean restoreOriginal(ModHealthInfo info) {
        if (info.backupPath() == null || !Files.exists(info.backupPath())) {
            LOGGER.error("No backup available for {}", info.modId());
            return false;
        }
        
        try {
            // Delete the broken transformed version
            if (Files.exists(info.transformedPath())) {
                Files.delete(info.transformedPath());
                LOGGER.info("Deleted broken transformed mod: {}", info.transformedPath().getFileName());
            }
            
            // Copy original back to retromod-input for re-transformation
            Path inputFolder = info.transformedPath().getParent().getParent().resolve("retromod-input");
            Path destination = inputFolder.resolve(
                info.backupPath().getFileName().toString().replace("-original.jar", ".jar")
            );
            
            Files.copy(info.backupPath(), destination, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Restored original to retromod-input/: {}", destination.getFileName());
            
            JOptionPane.showMessageDialog(
                null,
                "Original mod restored!\n\n" +
                "The broken transformed version has been deleted.\n" +
                "The original has been placed back in retromod-input/\n\n" +
                "Please restart Minecraft to try again.\n" +
                "If it still doesn't work, report on GitHub Issues",
                "Mod Restored",
                JOptionPane.INFORMATION_MESSAGE
            );
            
            return true;
            
        } catch (IOException e) {
            LOGGER.error("Could not restore original for {}", info.modId(), e);
            return false;
        }
    }
    
    /**
     * Open GitHub Issues in browser.
     */
    private static void openGitHub() {
        try {
            String url = "https://github.com/Bownlux/MC-RetroMod/issues/new?title=Bug%20Report%20-%20Mod%20Not%20Working";
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(java.net.URI.create(url));
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            LOGGER.warn("Could not open browser");
        }
    }
    
    /**
     * Get health status of a mod.
     */
    public static boolean isModHealthy(String modId) {
        ModHealthInfo info = monitoredMods.get(modId);
        return info == null || info.isHealthy();
    }
    
    /**
     * Get all broken mods.
     */
    public static List<ModHealthInfo> getBrokenMods() {
        return monitoredMods.values().stream()
            .filter(info -> !info.isHealthy())
            .toList();
    }
    
    /**
     * Clear error history for a mod (e.g., after restart).
     */
    public static void clearErrors(String modId) {
        modErrors.remove(modId);
    }
    
    /**
     * Truncate string for display.
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
    
    /**
     * Check if a mod's transformation might have issues.
     * Called after transformation to do basic validation.
     */
    public static boolean validateTransformation(Path originalJar, Path transformedJar) {
        try {
            // Basic validation: check that transformed JAR is valid
            try (JarFile jar = new JarFile(transformedJar.toFile())) {
                // Check it has entries
                if (!jar.entries().hasMoreElements()) {
                    LOGGER.warn("Transformed JAR is empty!");
                    return false;
                }
                
                // Check for fabric.mod.json (Fabric mods)
                if (jar.getEntry("fabric.mod.json") == null &&
                    jar.getEntry("META-INF/mods.toml") == null) {
                    LOGGER.warn("Transformed JAR missing mod metadata!");
                    return false;
                }
            }
            
            // Check file sizes are reasonable
            long originalSize = Files.size(originalJar);
            long transformedSize = Files.size(transformedJar);
            
            // Transformed should be similar size (within 50%)
            if (transformedSize < originalSize * 0.5 || transformedSize > originalSize * 2) {
                LOGGER.warn("Transformed JAR size differs significantly: {} -> {}", 
                    originalSize, transformedSize);
                // This is a warning, not a failure
            }
            
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Transformation validation failed", e);
            return false;
        }
    }
}
