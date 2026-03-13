/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.ArrayList;
import java.util.ServiceLoader;
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
    private static List<String> skippedComplexMods = new ArrayList<>();
    
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

            // Step 0: Register shims BEFORE transforming so redirects are available
            registerShimsForTransform();

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

            // Step 4: Show complexity warnings for skipped mods
            if (!skippedComplexMods.isEmpty()) {
                showComplexityWarning();
            }

            LOGGER.info("RetroMod pre-launch complete!");
            
        } catch (Exception e) {
            LOGGER.error("RetroMod pre-launch error: {}", e.getMessage());
        }
    }
    
    /**
     * Register all version shims and polyfills so that the bytecode transformer
     * has redirects available BEFORE AOT transformation runs.
     * Without this, mods are transformed with an empty redirect map.
     */
    private void registerShimsForTransform() {
        try {
            RetroModTransformer transformer = RetroModTransformer.getInstance();

            // Load version shims via ServiceLoader
            ServiceLoader<VersionShim> shims = ServiceLoader.load(VersionShim.class);
            int shimCount = 0;
            for (VersionShim shim : shims) {
                try {
                    shim.registerRedirects(transformer);
                    shimCount++;
                } catch (Exception e) {
                    LOGGER.debug("Could not register shim: {}", e.getMessage());
                }
            }
            LOGGER.info("Registered {} version shims for transformation", shimCount);

            // Load polyfill providers via ServiceLoader
            try {
                ServiceLoader<com.retromod.polyfill.PolyfillProvider> polyfills =
                    ServiceLoader.load(com.retromod.polyfill.PolyfillProvider.class);
                int polyfillCount = 0;
                for (com.retromod.polyfill.PolyfillProvider provider : polyfills) {
                    try {
                        provider.registerPolyfills(transformer);
                        polyfillCount++;
                    } catch (Exception e) {
                        LOGGER.debug("Could not register polyfill: {}", e.getMessage());
                    }
                }
                if (polyfillCount > 0) {
                    LOGGER.info("Registered {} polyfill providers for transformation", polyfillCount);
                }
            } catch (Exception e) {
                LOGGER.debug("No polyfill providers found");
            }
        } catch (Exception e) {
            LOGGER.warn("Could not register shims for pre-launch transform: {}", e.getMessage());
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
            
            // Check if force_translate_complex is enabled
            boolean forceComplex = isForceTranslateEnabled();

            for (Path modJar : modsToTransform) {
                try {
                    String fileName = modJar.getFileName().toString();
                    String modVersion = extractModMinecraftVersion(modJar);

                    LOGGER.info("┌─ Processing: {}", fileName);
                    LOGGER.info("│  Mod version: {}", modVersion != null ? modVersion : "unknown");
                    LOGGER.info("│  Target: {}", targetVersion);

                    // Complexity check — warn if mod is unlikely to work
                    com.retromod.gui.ModComplexityAnalyzer analyzer =
                        new com.retromod.gui.ModComplexityAnalyzer();
                    com.retromod.gui.ModComplexityAnalyzer.ComplexityReport report =
                        analyzer.analyze(modJar);

                    if (report.isUnlikelyToWork() && !forceComplex) {
                        LOGGER.warn("│  ⚠ SKIPPED: Mod is unlikely to work (complexity score: {})", report.score());
                        LOGGER.warn("│  Reason: {}", report.reason());
                        LOGGER.warn("│  To force, set \"force_translate_complex\": true in config.json");
                        LOGGER.info("└─ Skipped (too complex)");
                        skippedComplexMods.add(fileName);
                        // DON'T move to processed — leave it for the user to decide
                        continue;
                    }

                    if (report.isUnlikelyToWork() && forceComplex) {
                        LOGGER.warn("│  ⚠ Force mode: proceeding despite high complexity ({})", report.score());
                    }

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
     * Show restart required message in logs and as a GUI popup.
     */
    private void showRestartMessage() {
        // Always log to console
        LOGGER.info("");
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   RESTART REQUIRED!                                        ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   RetroMod transformed {} mod(s):                          ║", String.format("%-2d", totalTransformed));
        for (String mod : transformedMods) {
            String display = mod.length() > 48 ? mod.substring(0, 45) + "..." : mod;
            LOGGER.info("║     - {}║", String.format("%-51s", display));
        }
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   Close Minecraft and launch again for mods to load.       ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   This is normal! Fabric needs a restart to see new mods.  ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        LOGGER.info("");

        // Show GUI popup if we can (client-side, not headless)
        if (EnvironmentDetector.canShowGui()) {
            showRestartPopup();
        }
    }

    /**
     * Show a popup dialog telling the user to restart Minecraft.
     * Runs on a background thread so it doesn't block the main thread during pre-launch.
     */
    private void showRestartPopup() {
        // Run on a separate thread so we don't block Fabric's launch sequence
        Thread popupThread = new Thread(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            StringBuilder message = new StringBuilder();
            message.append("RetroMod transformed ").append(totalTransformed).append(" mod(s):\n\n");

            for (String mod : transformedMods) {
                String display = mod.length() > 50 ? mod.substring(0, 47) + "..." : mod;
                message.append("  - ").append(display).append("\n");
            }

            message.append("\nPlease close Minecraft and launch it again\n");
            message.append("for the transformed mods to load.\n\n");
            message.append("This only happens the first time. After restarting,\n");
            message.append("your old mods will work normally.");

            JOptionPane.showMessageDialog(
                null,
                message.toString(),
                "RetroMod - Restart Required",
                JOptionPane.INFORMATION_MESSAGE
            );
        }, "RetroMod-RestartPopup");

        popupThread.setDaemon(true);
        popupThread.start();
    }

    /**
     * Check if force_translate_complex is enabled in config.
     */
    private boolean isForceTranslateEnabled() {
        try {
            Path configPath = Path.of("config/retromod/config.json");
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                return json.contains("\"force_translate_complex\": true") ||
                       json.contains("\"force_translate_complex\":true");
            }
        } catch (Exception e) {
            // Default to false
        }
        return false;
    }

    /**
     * Show warning about mods that were skipped due to high complexity.
     */
    private void showComplexityWarning() {
        LOGGER.warn("");
        LOGGER.warn("╔════════════════════════════════════════════════════════════╗");
        LOGGER.warn("║   MODS SKIPPED (UNLIKELY TO WORK)                          ║");
        LOGGER.warn("╠════════════════════════════════════════════════════════════╣");
        for (String mod : skippedComplexMods) {
            String display = mod.length() > 51 ? mod.substring(0, 48) + "..." : mod;
            LOGGER.warn("║   ⚠ {}║", String.format("%-54s", display));
        }
        LOGGER.warn("╠════════════════════════════════════════════════════════════╣");
        LOGGER.warn("║   These mods were NOT translated because they use          ║");
        LOGGER.warn("║   features RetroMod cannot fully transform (coremods,      ║");
        LOGGER.warn("║   heavy reflection, ASM manipulation, etc.).               ║");
        LOGGER.warn("║                                                            ║");
        LOGGER.warn("║   To try anyway, set in config/retromod/config.json:       ║");
        LOGGER.warn("║     \"force_translate_complex\": true                       ║");
        LOGGER.warn("╚════════════════════════════════════════════════════════════╝");
        LOGGER.warn("");

        // GUI warning if possible
        if (EnvironmentDetector.canShowGui()) {
            Thread warningThread = new Thread(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {}

                StringBuilder msg = new StringBuilder();
                msg.append("RetroMod skipped ").append(skippedComplexMods.size())
                   .append(" mod(s) because they are unlikely to work:\n\n");
                for (String mod : skippedComplexMods) {
                    msg.append("  - ").append(mod).append("\n");
                }
                msg.append("\nThese mods use features that RetroMod cannot fully\n");
                msg.append("transform (coremods, heavy reflection, ASM, etc.).\n\n");
                msg.append("To force translation anyway, set:\n");
                msg.append("  \"force_translate_complex\": true\n");
                msg.append("in config/retromod/config.json");

                JOptionPane.showMessageDialog(
                    null,
                    msg.toString(),
                    "RetroMod - Mods Skipped",
                    JOptionPane.WARNING_MESSAGE
                );
            }, "RetroMod-ComplexityWarning");
            warningThread.setDaemon(true);
            warningThread.start();
        }
    }
}
