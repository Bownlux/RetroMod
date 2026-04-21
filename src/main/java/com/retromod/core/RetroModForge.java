/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import com.retromod.gui.RetroModGui;
import com.retromod.gui.TitleScreenButtonInjector;
import com.retromod.util.ZipSecurity;
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

        // Load auto-fix fixes from previous launch.
        // These are redirects/patches discovered by analyzing crash logs from
        // a prior launch. Must be loaded AFTER shims (so shim redirects take
        // priority) but BEFORE transformation (so fixes are applied during transform).
        try {
            AutoFixEngine autoFixEngine = new AutoFixEngine();
            int savedFixes = autoFixEngine.loadAndApplySavedFixes(transformer);
            if (savedFixes > 0) {
                LOGGER.info("AutoFix: loaded {} saved fix(es) from previous launch", savedFixes);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not load auto-fix saved fixes: {}", e.getMessage());
        }

        // Initialize fuzzy resolver — last-resort fallback for unresolved references.
        // Auto-detects the MC JAR from the classpath.
        try {
            transformer.initFuzzyResolver(null);
        } catch (Exception e) {
            LOGGER.debug("Could not initialize fuzzy resolver: {}", e.getMessage());
        }

        // Initialize hybrid AOT/JIT engine
        initializeHybridEngine();

        // Transform mods from retromod-input/ folder
        int transformed = transformModsFromInput();

        // Also scan mods/ for incompatible mods and transform in place
        transformed += transformModsInPlace();

        // CLIENT: Show GUI for first-time setup or add mods button
        // SERVER: Skip GUI, just log
        if (!isServer && EnvironmentDetector.canShowGui()) {
            if (transformed > 0) {
                showRestartPopup(transformed);
            } else {
                initializeClientGui();
            }
        } else {
            LOGGER.info("Server mode - GUI disabled");
            if (transformed > 0) {
                LOGGER.info("Transformed {} mod(s) - please restart for changes to take effect", transformed);
            }
        }

        // Scan for mods that can be runtime-transformed (minor versions)
        scanForRuntimeTransformableMods();

        // Auto-fix: analyze the PREVIOUS launch's log for errors and prepare fixes.
        // Scans latest.log for known crash patterns (NoSuchMethodError, VerifyError,
        // mixin failures, etc.) and registers fixes so the NEXT retransformation
        // incorporates them.
        try {
            Path gameDir = Paths.get(".").toAbsolutePath().normalize();
            Path logFile = gameDir.resolve("logs/latest.log");
            if (Files.exists(logFile)) {
                AutoFixEngine autoFixEngine = new AutoFixEngine();
                java.util.List<AutoFixEngine.AppliedFix> fixes =
                    autoFixEngine.analyzeAndFix(logFile, transformer);
                if (!fixes.isEmpty()) {
                    LOGGER.info("AutoFix: found and applied {} fix(es) from previous log", fixes.size());
                    for (AutoFixEngine.AppliedFix fix : fixes) {
                        LOGGER.info("  [{}] {} => {}", fix.errorType(), fix.description(), fix.action());
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not run auto-fix analysis: {}", e.getMessage());
        }

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
            hybrid.initialize(modsFolder, RetroMod.TARGET_MC_VERSION);
            
            LOGGER.info("Hybrid AOT/JIT engine initialized");
        } catch (Exception e) {
            LOGGER.warn("Could not initialize hybrid engine: {}", e.getMessage());
        }
    }
    
    /**
     * Initialize GUI components (client only).
     * Registers both the in-game title screen button and the Swing-based
     * first-run setup / floating "Add Mods" button as a fallback.
     */
    private void initializeClientGui() {
        Path gameDir = Paths.get(".").toAbsolutePath().normalize();

        // Register the in-game title screen button (Forge event bus)
        try {
            TitleScreenButtonInjector.register();
            LOGGER.info("Title screen button registered");
        } catch (Exception e) {
            LOGGER.debug("Title screen button not available: {}", e.getMessage());
        }

        // Swing-based GUI as additional entry point
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
            LOGGER.info("Use CLI instead: retromod aot <mod.jar>");
        }
    }
    
    private void loadForgeShims(RetroModTransformer transformer) {
        try {
            java.util.ServiceLoader<VersionShim> loader = 
                java.util.ServiceLoader.load(VersionShim.class);
            
            int count = 0;
            java.util.Iterator<VersionShim> it = loader.iterator();
            while (it.hasNext()) {
                VersionShim shim;
                try {
                    shim = it.next();
                } catch (java.util.ServiceConfigurationError e) {
                    // Class not found — expected in lite builds
                    continue;
                }
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
    
    /**
     * Transform mods from the retromod-input/ folder.
     */
    private int transformModsFromInput() {
        int count = 0;
        try {
            Path gameDir = Paths.get(".").toAbsolutePath().normalize();
            Path inputDir = gameDir.resolve("retromod-input");
            Path modsDir = gameDir.resolve("mods");
            Path processedDir = inputDir.resolve("processed");

            // Validate directories are not symlinks
            ZipSecurity.validateNotSymlink(inputDir);
            ZipSecurity.validateNotSymlink(modsDir);

            Files.createDirectories(inputDir);
            Files.createDirectories(processedDir);

            if (!Files.isDirectory(inputDir)) return 0;

            java.util.List<Path> jars;
            try (var stream = Files.list(inputDir)) {
                jars = stream.filter(p -> p.toString().endsWith(".jar"))
                             .filter(Files::isRegularFile).toList();
            }

            if (jars.isEmpty()) return 0;

            ForgeModTransformer transformer = new ForgeModTransformer(RetroMod.TARGET_MC_VERSION);

            for (Path modJar : jars) {
                try {
                    String fileName = modJar.getFileName().toString();
                    LOGGER.info("Transforming from retromod-input/: {}", fileName);

                    Path transformed = transformer.transformMod(modJar, modsDir);
                    if (transformed != null) {
                        Files.move(modJar, processedDir.resolve(fileName),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        count++;
                    }
                } catch (Exception e) {
                    LOGGER.error("Failed to transform {}: {}", modJar.getFileName(), e.getMessage());
                }
            }

            if (count > 0) {
                LOGGER.info("Transformed {} mod(s) from retromod-input/", count);
            }
        } catch (Exception e) {
            LOGGER.error("Error scanning retromod-input/: {}", e.getMessage());
        }
        return count;
    }

    /**
     * Scan mods/ for incompatible mods and transform them in place.
     */
    private int transformModsInPlace() {
        int count = 0;
        try {
            Path gameDir = Paths.get(".").toAbsolutePath().normalize();
            Path modsDir = gameDir.resolve("mods");
            Path backupDir = modsDir.resolve("retromod-backups");

            // Validate directories are not symlinks
            ZipSecurity.validateNotSymlink(modsDir);
            ZipSecurity.validateNotSymlink(backupDir);

            if (!Files.isDirectory(modsDir)) return 0;

            ForgeModTransformer transformer = new ForgeModTransformer(RetroMod.TARGET_MC_VERSION);
            ModVersionDetector detector = new ModVersionDetector();

            java.util.List<Path> jars;
            try (var stream = Files.list(modsDir)) {
                jars = stream.filter(p -> p.toString().endsWith(".jar"))
                             .filter(Files::isRegularFile)
                             .filter(p -> !p.getFileName().toString().contains("-retromod"))
                             .filter(p -> !p.getFileName().toString().startsWith("retromod"))
                             .toList();
            }

            for (Path modJar : jars) {
                try {
                    var info = detector.detectVersion(modJar);
                    if (info != null && info.needsTransformation(RetroMod.TARGET_MC_VERSION)) {
                        String fileName = modJar.getFileName().toString();
                        LOGGER.info("Found incompatible mod in mods/: {} ({})", fileName, info.targetMcVersion());

                        Files.createDirectories(backupDir);
                        Files.copy(modJar, backupDir.resolve(fileName),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        Path tempDir = Files.createTempDirectory("retromod-inplace-");
                        Path transformed = transformer.transformMod(modJar, tempDir);
                        if (transformed != null) {
                            Files.move(transformed, modJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info("Transformed in place: {}", fileName);
                            count++;
                        }
                        try (var walk = Files.walk(tempDir)) {
                            walk.sorted(java.util.Comparator.reverseOrder())
                                .forEach(p -> { try { Files.delete(p); } catch (Exception ignored) {} });
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not check: {}", modJar.getFileName());
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error scanning mods/ for transformation: {}", e.getMessage());
        }
        return count;
    }

    /**
     * Show a popup telling the user to restart Minecraft.
     */
    private void showRestartPopup(int transformedCount) {
        if (!EnvironmentDetector.canShowGui()) return;

        Thread popupThread = new Thread(() -> {
            try {
                javax.swing.UIManager.setLookAndFeel(
                    javax.swing.UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}

            javax.swing.JOptionPane.showMessageDialog(null,
                "RetroMod transformed " + transformedCount + " mod(s).\n\n" +
                "Please close Minecraft and launch it again\n" +
                "for the changes to take effect.\n\n" +
                "This only happens the first time.",
                "RetroMod - Restart Required",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }, "RetroMod-RestartPopup");
        popupThread.setDaemon(true);
        popupThread.start();
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
                    if (info != null && info.needsTransformation(RetroMod.TARGET_MC_VERSION)) {
                        String sourceVersion = info.targetMcVersion();
                        
                        // Runtime-transform Forge mods that need it
                        if (sourceVersion != null) {
                            LOGGER.info("Runtime transforming: {} ({} -> {})",
                                modFile.getName(), sourceVersion, RetroMod.TARGET_MC_VERSION);
                            
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
