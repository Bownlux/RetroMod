/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;
import java.util.regex.*;

/**
 * Pre-launch entry point for Fabric.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * CRITICAL: This runs BEFORE Fabric scans the mods folder!
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * RetroMod supports TWO input locations:
 * 1. .minecraft/retromod-input/           (PRIMARY - recommended)
 * 2. .minecraft/mods/retromod-input/      (SECONDARY - for convenience)
 * 
 * Users naturally look in the mods folder first, so we support both.
 * A guide file in mods/ directs users to the right place.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 * WHY RESTART IS REQUIRED:
 * ═══════════════════════════════════════════════════════════════════════════
 * 
 * Fabric Loader scans the mods folder ONCE at startup, before mods initialize.
 * Even though PreLaunch runs early, it's not early enough - Fabric has already
 * read the mod list by the time we run.
 * 
 * The sequence is:
 * 1. Fabric scans mods/ folder (WE CAN'T CHANGE THIS)
 * 2. Fabric loads mod metadata
 * 3. PreLaunch entrypoints run (RetroMod transforms here)
 * 4. Mods initialize
 * 
 * So transformed mods only appear on the NEXT launch.
 * This is a Fabric limitation, not a RetroMod limitation.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RetroModPreLaunch implements PreLaunchEntrypoint {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod");
    
    // Two input locations
    private static final String PRIMARY_INPUT = "retromod-input";
    private static final String SECONDARY_INPUT = "mods/retromod-input";
    private static final String PROCESSED_SUFFIX = "/processed";
    
    // Track transformation results
    private static int totalTransformed = 0;
    private static List<String> transformedMods = new ArrayList<>();
    
    @Override
    public void onPreLaunch() {
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║  RetroMod v1.0.0-beta.1                                    ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir == null) {
                LOGGER.warn("Could not determine game directory, using current directory");
                gameDir = Path.of(".");
            }
            String targetVersion = getMinecraftVersion();
            
            LOGGER.info("Target Minecraft version: {}", targetVersion);
            
            // Step 1: Create all folders and guide files
            createFoldersAndGuides(gameDir);
            
            // Step 2: Transform mods from BOTH input locations
            int fromPrimary = transformModsFromFolder(
                gameDir.resolve(PRIMARY_INPUT),
                gameDir.resolve(PRIMARY_INPUT + PROCESSED_SUFFIX),
                gameDir.resolve("mods"),
                targetVersion
            );
            
            int fromSecondary = transformModsFromFolder(
                gameDir.resolve(SECONDARY_INPUT),
                gameDir.resolve(SECONDARY_INPUT + PROCESSED_SUFFIX),
                gameDir.resolve("mods"),
                targetVersion
            );
            
            totalTransformed = fromPrimary + fromSecondary;
            
            // Step 3: Show restart message if we transformed anything
            if (totalTransformed > 0) {
                showRestartMessage();
            }
            
            LOGGER.info("RetroMod pre-launch complete!");
            
        } catch (Exception e) {
            LOGGER.error("RetroMod pre-launch error: {}", e.getMessage());
        }
    }
    
    /**
     * Get the actual Minecraft version.
     */
    private String getMinecraftVersion() {
        try {
            return FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("1.21.11");
        } catch (Exception e) {
            return "1.21.11";
        }
    }
    
    /**
     * Create all folders and guide files.
     */
    private void createFoldersAndGuides(Path gameDir) {
        try {
            // Create PRIMARY input folder (.minecraft/retromod-input/)
            Path primaryInput = gameDir.resolve(PRIMARY_INPUT);
            Path primaryProcessed = gameDir.resolve(PRIMARY_INPUT + PROCESSED_SUFFIX);
            Files.createDirectories(primaryInput);
            Files.createDirectories(primaryProcessed);
            
            // Create SECONDARY input folder (.minecraft/mods/retromod-input/)
            Path secondaryInput = gameDir.resolve(SECONDARY_INPUT);
            Path secondaryProcessed = gameDir.resolve(SECONDARY_INPUT + PROCESSED_SUFFIX);
            Files.createDirectories(secondaryInput);
            Files.createDirectories(secondaryProcessed);
            
            // Create README in primary folder
            createPrimaryReadme(primaryInput);
            
            // Create README in secondary folder
            createSecondaryReadme(secondaryInput);
            
            // Create GUIDE in mods folder (most important!)
            createModsFolderGuide(gameDir.resolve("mods"));
            
            LOGGER.info("Created retromod-input/ folders");
            
        } catch (Exception e) {
            LOGGER.error("Could not create folders: {}", e.getMessage());
        }
    }
    
    /**
     * Create README in primary input folder.
     */
    private void createPrimaryReadme(Path folder) {
        try {
            Path readme = folder.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                    ╔════════════════════════════════════════════════════════════╗
                    ║  RETROMOD INPUT FOLDER (PRIMARY)                           ║
                    ╠════════════════════════════════════════════════════════════╣
                    ║                                                            ║
                    ║  Put your OLD mods here!                                   ║
                    ║                                                            ║
                    ║  HOW TO USE:                                               ║
                    ║  1. Drop old .jar mod files into this folder               ║
                    ║  2. Start Minecraft                                        ║
                    ║  3. RetroMod transforms them automatically                 ║
                    ║  4. ⚠️ RESTART Minecraft (required first time)             ║
                    ║  5. Your mods work!                                        ║
                    ║                                                            ║
                    ║  WHY RESTART?                                              ║
                    ║  Fabric scans mods BEFORE RetroMod can run. Transformed    ║
                    ║  mods only load on the next launch. This is a Fabric       ║
                    ║  limitation, not a RetroMod bug.                           ║
                    ║                                                            ║
                    ║  AFTER TRANSFORMATION:                                     ║
                    ║  - Originals move to: processed/                           ║
                    ║  - Transformed mods go to: mods/                           ║
                    ║                                                            ║
                    ║  ALTERNATIVE LOCATION:                                     ║
                    ║  You can also use: mods/retromod-input/                    ║
                    ║                                                            ║
                    ╚════════════════════════════════════════════════════════════╝
                    """);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Create README in secondary input folder (mods/retromod-input/).
     */
    private void createSecondaryReadme(Path folder) {
        try {
            Path readme = folder.resolve("README.txt");
            if (!Files.exists(readme)) {
                Files.writeString(readme, """
                    ╔════════════════════════════════════════════════════════════╗
                    ║  RETROMOD INPUT FOLDER (SECONDARY)                         ║
                    ╠════════════════════════════════════════════════════════════╣
                    ║                                                            ║
                    ║  This folder works too! Put old mods here.                 ║
                    ║                                                            ║
                    ║  Both locations work:                                      ║
                    ║  • .minecraft/retromod-input/      (primary)               ║
                    ║  • .minecraft/mods/retromod-input/ (this folder)           ║
                    ║                                                            ║
                    ║  Same instructions:                                        ║
                    ║  1. Drop old .jar files here                               ║
                    ║  2. Start Minecraft                                        ║
                    ║  3. RESTART Minecraft                                      ║
                    ║  4. Mods work!                                             ║
                    ║                                                            ║
                    ╚════════════════════════════════════════════════════════════╝
                    """);
            }
        } catch (Exception e) {
            // Ignore
        }
    }
    
    /**
     * Create guide file in mods folder to help users find retromod-input.
     */
    private void createModsFolderGuide(Path modsFolder) {
        try {
            Path guide = modsFolder.resolve("!RETROMOD-READ-ME-FIRST!.txt");
            
            // Always update the guide (in case instructions change)
            Files.writeString(guide, """
                ╔════════════════════════════════════════════════════════════╗
                ║                                                            ║
                ║   ██████╗ ███████╗████████╗██████╗  ██████╗               ║
                ║   ██╔══██╗██╔════╝╚══██╔══╝██╔══██╗██╔═══██╗              ║
                ║   ██████╔╝█████╗     ██║   ██████╔╝██║   ██║              ║
                ║   ██╔══██╗██╔══╝     ██║   ██╔══██╗██║   ██║              ║
                ║   ██║  ██║███████╗   ██║   ██║  ██║╚██████╔╝              ║
                ║   ╚═╝  ╚═╝╚══════╝   ╚═╝   ╚═╝  ╚═╝ ╚═════╝               ║
                ║                                                            ║
                ║   MOD INSTALLATION GUIDE                                   ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   ⚠️  DO NOT PUT OLD MODS DIRECTLY IN THIS FOLDER!  ⚠️    ║
                ║                                                            ║
                ║   If you put old mods here, Minecraft will CRASH with      ║
                ║   "mod requires Minecraft 1.20.x" errors!                  ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   WHERE TO PUT OLD MODS:                                   ║
                ║                                                            ║
                ║   Option 1 (Recommended):                                  ║
                ║   📁 .minecraft/retromod-input/                            ║
                ║                                                            ║
                ║   Option 2 (Also works):                                   ║
                ║   📁 .minecraft/mods/retromod-input/                       ║
                ║      (There's a folder inside THIS mods folder!)           ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   STEP BY STEP:                                            ║
                ║                                                            ║
                ║   1. Find the retromod-input folder                        ║
                ║      (either location above)                               ║
                ║                                                            ║
                ║   2. Put your OLD mod .jar files there                     ║
                ║      Example: sodium-1.20.4.jar                            ║
                ║                                                            ║
                ║   3. Start Minecraft                                       ║
                ║      RetroMod will transform the mods                      ║
                ║                                                            ║
                ║   4. ⚠️ RESTART Minecraft ⚠️                               ║
                ║      (This is required! Transformed mods load on restart)  ║
                ║                                                            ║
                ║   5. Your old mods now work! 🎉                            ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   WHERE DO MODS GO AFTER TRANSFORMATION?                   ║
                ║                                                            ║
                ║   • Transformed mods appear in THIS folder (mods/)         ║
                ║     with "-retromod" added to the name                     ║
                ║     Example: sodium-1.20.4-retromod.jar                    ║
                ║                                                            ║
                ║   • Original mods move to retromod-input/processed/        ║
                ║     (as a backup)                                          ║
                ║                                                            ║
                ╠════════════════════════════════════════════════════════════╣
                ║                                                            ║
                ║   WHY IS RESTART REQUIRED?                                 ║
                ║                                                            ║
                ║   Fabric Loader scans this folder ONCE at startup.         ║
                ║   RetroMod transforms mods, but Fabric already read        ║
                ║   the folder. On restart, Fabric sees the new mods.        ║
                ║                                                            ║
                ║   This is a Fabric limitation, not a RetroMod bug.         ║
                ║                                                            ║
                ╚════════════════════════════════════════════════════════════╝
                
                Need help? Visit: github.com/Bownlux/MC-RetroMod/issues
                """);
            
        } catch (Exception e) {
            LOGGER.debug("Could not create guide file: {}", e.getMessage());
        }
    }
    
    /**
     * Transform all mods from a specific input folder.
     */
    private int transformModsFromFolder(Path inputFolder, Path processedFolder, 
                                        Path outputFolder, String targetVersion) {
        if (!Files.exists(inputFolder)) {
            return 0;
        }
        
        int count = 0;
        
        try {
            // Find JAR files (not in subfolders, not README)
            List<Path> modsToTransform;
            try (var stream = Files.list(inputFolder)) {
                modsToTransform = stream
                    .filter(p -> p.toString().toLowerCase().endsWith(".jar"))
                    .filter(p -> Files.isRegularFile(p))
                    .toList();
            }
            
            if (modsToTransform.isEmpty()) {
                return 0;
            }
            
            LOGGER.info("Found {} mod(s) in {}", modsToTransform.size(), inputFolder.getFileName());
            
            // Create folders
            Files.createDirectories(processedFolder);
            Files.createDirectories(outputFolder);
            
            // Create transformer
            FabricModTransformer transformer = new FabricModTransformer(targetVersion);
            
            for (Path modJar : modsToTransform) {
                try {
                    String fileName = modJar.getFileName().toString();
                    String modVersion = extractModMinecraftVersion(modJar);
                    
                    LOGGER.info("┌─ Processing: {}", fileName);
                    LOGGER.info("│  Mod version: {}", modVersion != null ? modVersion : "unknown");
                    LOGGER.info("│  Target: {}", targetVersion);
                    
                    // ALWAYS transform unless EXACT match
                    boolean needsTransform = !isExactVersionMatch(modVersion, targetVersion);
                    
                    if (!needsTransform) {
                        LOGGER.info("│  Status: Already compatible (exact match)");
                        LOGGER.info("└─ Copying directly to mods/");
                        Files.copy(modJar, outputFolder.resolve(fileName), 
                            StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        LOGGER.info("│  Status: Needs transformation");
                        Path transformed = transformer.transformMod(modJar, outputFolder);
                        if (transformed != null) {
                            LOGGER.info("└─ Created: {}", transformed.getFileName());
                            transformedMods.add(fileName);
                        } else {
                            LOGGER.warn("└─ Transformation failed!");
                        }
                    }
                    
                    // Move original to processed
                    Path processedPath = processedFolder.resolve(fileName);
                    Files.move(modJar, processedPath, StandardCopyOption.REPLACE_EXISTING);
                    
                    count++;
                    
                } catch (Exception e) {
                    LOGGER.error("Failed to process {}: {}", modJar.getFileName(), e.getMessage());
                }
            }
            
        } catch (Exception e) {
            LOGGER.error("Error scanning {}: {}", inputFolder, e.getMessage());
        }
        
        return count;
    }
    
    /**
     * Check if mod version EXACTLY matches target (very strict).
     */
    private boolean isExactVersionMatch(String modVersion, String targetVersion) {
        if (modVersion == null) return false;
        
        // Clean the version
        String clean = modVersion
            .replace(">=", "")
            .replace("<=", "")
            .replace(">", "")
            .replace("<", "")
            .replace("~", "")
            .replace("^", "")
            .trim();
        
        // Only exact match counts
        return clean.equals(targetVersion);
    }
    
    /**
     * Extract Minecraft version from mod JAR.
     */
    private String extractModMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try Fabric
            ZipEntry fabricJson = jar.getEntry("fabric.mod.json");
            if (fabricJson != null) {
                String content = new String(jar.getInputStream(fabricJson).readAllBytes());
                Pattern p = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(content);
                if (m.find()) return m.group(1);
            }
            
            // Try Quilt
            ZipEntry quiltJson = jar.getEntry("quilt.mod.json");
            if (quiltJson != null) {
                String content = new String(jar.getInputStream(quiltJson).readAllBytes());
                Pattern p = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(content);
                if (m.find()) return m.group(1);
            }
            
            // Try Forge/NeoForge
            ZipEntry modsToml = jar.getEntry("META-INF/mods.toml");
            if (modsToml == null) modsToml = jar.getEntry("META-INF/neoforge.mods.toml");
            if (modsToml != null) {
                String content = new String(jar.getInputStream(modsToml).readAllBytes());
                Pattern p = Pattern.compile("versionRange\\s*=\\s*\"\\[([0-9.]+)");
                Matcher m = p.matcher(content);
                if (m.find()) return m.group(1);
            }
            
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Show restart required message.
     */
    private void showRestartMessage() {
        LOGGER.info("");
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   ⚠️  RESTART REQUIRED!  ⚠️                                ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   RetroMod transformed {} mod(s):                          ║", String.format("%-2d", totalTransformed));
        for (String mod : transformedMods) {
            String display = mod.length() > 48 ? mod.substring(0, 45) + "..." : mod;
            LOGGER.info("║     • {}║", String.format("%-51s", display));
        }
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   Close Minecraft and launch again for mods to load.       ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   This is normal! Fabric needs a restart to see new mods.  ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        LOGGER.info("");
    }
}
