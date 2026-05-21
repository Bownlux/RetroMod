/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import com.retromod.gui.RetromodGui;
import com.retromod.gui.TitleScreenButtonInjector;
import com.retromod.util.ZipSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * NeoForge entry point for Retromod.
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
 * Also handles Forge -> NeoForge migration automatically!
 * 
 * USER EXPERIENCE (CLIENT):
 * 
 * First Launch:
 *   1. Retromod shows a welcome dialog
 *   2. Opens file picker (Finder on Mac, Explorer on Windows)
 *   3. User selects mod JARs they want to use
 *   4. Retromod transforms them and puts in mods folder
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
 *   - Retromod auto-transforms on startup
 *   - Warnings logged to console
 */
// @Mod annotation must be present at runtime: NeoForge's mod scanner
// reads mods.toml, finds modId="retromod", and refuses to load any
// jar that declares mods in mods.toml without a matching @Mod("modId")
// class. Without this, the NeoForge log shows
//
//     Creating FMLModContainer instance for retromod with entrypoints []
//
// and RetromodNeoForge.<init> never fires (no transformation, no
// initialization). The annotation is resolved at compile time against
// the stub at src/main/java/net/neoforged/fml/common/Mod.java; at
// runtime NeoForge's real annotation shadows the stub.
@net.neoforged.fml.common.Mod("retromod")
public class RetromodNeoForge {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");
    
    public RetromodNeoForge() {
        // Detect MC version from NeoForge's loader. Same reasoning as Forge:
        // Retromod.<clinit> can't run on NeoForge (Fabric ModInitializer
        // missing), so we populate RetromodVersion ourselves.
        try {
            Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
            Object versionInfo = fmlLoader.getMethod("versionInfo").invoke(null);
            String mcVersion = (String) versionInfo.getClass()
                    .getMethod("mcVersion").invoke(versionInfo);
            if (mcVersion != null) {
                RetromodVersion.TARGET_MC_VERSION = mcVersion;
            }
        } catch (Throwable ignored) {}

        LOGGER.info("Retromod initializing on NeoForge (target MC: {})...",
                RetromodVersion.TARGET_MC_VERSION);

        // Detect environment
        boolean isServer = EnvironmentDetector.isDedicatedServer();
        LOGGER.info("Environment: {}", isServer ? "Dedicated Server" : "Client");

        // Initialize the transformer
        RetromodTransformer transformer = RetromodTransformer.getInstance();

        // Load NeoForge-specific shims (including Forge migration shims)
        loadNeoForgeShims(transformer);

        // Register Forge SRG → Mojang member-name mappings.
        // NeoForge dropped SRG natively since 1.17 (it's been Mojang-named
        // throughout), but cross-loader scenarios still benefit: a Forge
        // SRG-baked mod (Jade for Forge 1.20.1, JEI Forge variants, anything
        // that ran through ForgeGradle's reobfJar) routed to NeoForge through
        // Retromod's Forge → NeoForge migration carries those SRG names with
        // it. Without this mapping the migrated mod crashes the same way
        // it would on Forge 64.x.
        try {
            int srgEntries = com.retromod.mapping.SrgToMojangMapper.getInstance()
                    .applyTo(transformer);
            if (srgEntries > 0) {
                LOGGER.info("Registered {} SRG → Mojang mapping(s)", srgEntries);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not register SRG mappings", e);
        }

        // AutoFix is OPT-IN on every loader. Mirror the security model from
        // Retromod.onInitialize (the Fabric entry point): logs/latest.log is
        // writable by any other mod via SLF4J, so a crafted log line that
        // matches AutoFixEngine's error-pattern regex could trick Retromod
        // into registering attacker-chosen method/field redirects on the
        // shared transformer. The opt-in flag must gate BOTH the persisted
        // fix loader AND the live log analyzer (further down) — turning
        // AutoFix off needs to actually turn it all the way off.
        // See the long-form security comment in Retromod.java for details.
        boolean autoFixEnabled = Boolean.parseBoolean(
                System.getProperty("retromod.autoFix", "false"));

        // Load auto-fix fixes from previous launch.
        // These are redirects/patches discovered by analyzing crash logs from
        // a prior launch. Must be loaded AFTER shims (so shim redirects take
        // priority) but BEFORE transformation (so fixes are applied during transform).
        if (autoFixEnabled) {
            try {
                AutoFixEngine autoFixEngine = new AutoFixEngine();
                int savedFixes = autoFixEngine.loadAndApplySavedFixes(transformer);
                if (savedFixes > 0) {
                    LOGGER.info("AutoFix: loaded {} saved fix(es) from previous launch", savedFixes);
                }
            } catch (Exception e) {
                LOGGER.debug("Could not load auto-fix saved fixes: {}", e.getMessage());
            }
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

        // Transform mods from retromod-input/ folder (same workflow as Fabric)
        int transformed = transformModsFromInput();

        // Also scan mods/ for incompatible mods and transform in place
        transformed += transformModsInPlace();

        // Arm the in-game restart prompt (#33), shown on the title screen.
        com.retromod.gui.RestartPrompt.markPending(transformed);

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
        //
        // SECURITY: gated on the same -Dretromod.autoFix flag set above.
        // The risk model is the same as Fabric's: latest.log is attacker-writable
        // through any mod's SLF4J logger, so off-by-default is the safer position.
        if (autoFixEnabled) {
            try {
                Path gameDir = Paths.get(".").toAbsolutePath().normalize();
                Path logFile = gameDir.resolve("logs/latest.log");
                if (Files.exists(logFile)) {
                    AutoFixEngine autoFixEngine = new AutoFixEngine();
                    java.util.List<AutoFixEngine.AppliedFix> fixes =
                        autoFixEngine.analyzeAndFix(logFile, transformer);
                    if (!fixes.isEmpty()) {
                        LOGGER.warn("AutoFix: registered {} redirect(s) from previous log (opt-in feature). "
                                + "Review each one — log lines are an attacker-writable surface:",
                                fixes.size());
                        for (AutoFixEngine.AppliedFix fix : fixes) {
                            LOGGER.warn("  AutoFix [{}] {} => {}",
                                    fix.errorType(), fix.description(), fix.action());
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Could not run auto-fix analysis: {}", e.getMessage());
            }
        } else {
            LOGGER.debug("AutoFix disabled by default. Enable with -Dretromod.autoFix=true "
                    + "(see security notes in Retromod.java).");
        }

        LOGGER.info("Retromod initialized!");
        
        if (isServer) {
            LOGGER.info("=======================================================");
            LOGGER.info("  Retromod: Server Mode Active (NeoForge)");
            LOGGER.info("=======================================================");
            LOGGER.info("  • Bytecode transformation: ENABLED");
            LOGGER.info("  • AOT compilation: ENABLED");
            LOGGER.info("  • Forge -> NeoForge migration: ENABLED");
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
            hybrid.initialize(modsFolder, RetromodVersion.TARGET_MC_VERSION);
            
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

        // Register the in-game title screen button (NeoForge event bus)
        try {
            TitleScreenButtonInjector.register();
            LOGGER.info("Title screen button registered");
        } catch (Exception e) {
            LOGGER.debug("Title screen button not available: {}", e.getMessage());
        }

        // Swing-based GUI as additional entry point
        try {
            RetromodGui gui = new RetromodGui(gameDir);

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
    
    private void loadNeoForgeShims(RetromodTransformer transformer) {
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
                if ("neoforge".equals(loaderType) ||
                    "forge".equals(loaderType) ||
                    "common".equals(loaderType)) {
                    // Only register shims whose target MC is <= the host. The
                    // 1.21.11→26.1 shim renames NeoForge/vanilla classes to 26.1 names
                    // (IItemHandler→ItemHandler, IFluidHandler→FluidHandler, …); applied
                    // on a 1.21.1 host those names don't exist → load crash. Same gate as
                    // the Fabric path (RetromodPreLaunch.mcVersionExceeds). See #38.
                    if (RetromodPreLaunch.mcVersionExceeds(
                            shim.getTargetVersion(), RetromodVersion.TARGET_MC_VERSION)) {
                        continue;
                    }
                    shim.registerRedirects(transformer);
                    count++;
                }
            }
            
            LOGGER.info("Loaded {} NeoForge/Forge version shims", count);
        } catch (Exception e) {
            LOGGER.error("Failed to load NeoForge shims", e);
        }
    }
    
    /**
     * Transform mods from the retromod-input/ folder.
     * Transforms the mod (bytecode + mods.toml version) and moves to mods/.
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

            List<Path> jars;
            try (var stream = Files.list(inputDir)) {
                jars = stream.filter(p -> p.toString().endsWith(".jar"))
                             .filter(Files::isRegularFile).toList();
            }

            if (jars.isEmpty()) return 0;

            ForgeModTransformer transformer = new ForgeModTransformer(RetromodVersion.TARGET_MC_VERSION);

            for (Path modJar : jars) {
                try {
                    String fileName = modJar.getFileName().toString();
                    LOGGER.info("Transforming from retromod-input/: {}", fileName);

                    Path transformed = transformer.transformMod(modJar, modsDir);
                    if (transformed != null) {
                        // Move original to processed
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
     * Backs up originals to mods/retromod-backups/.
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

            ForgeModTransformer transformer = new ForgeModTransformer(RetromodVersion.TARGET_MC_VERSION);
            ModVersionDetector detector = new ModVersionDetector();

            List<Path> jars;
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
                    if (info != null && info.needsTransformation(RetromodVersion.TARGET_MC_VERSION)) {
                        String fileName = modJar.getFileName().toString();
                        LOGGER.info("Found incompatible mod in mods/: {} ({})", fileName, info.targetMcVersion());

                        // Back up original
                        Files.createDirectories(backupDir);
                        Files.copy(modJar, backupDir.resolve(fileName),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);

                        // Transform to temp, then replace
                        Path tempDir = Files.createTempDirectory("retromod-inplace-");
                        Path transformed = transformer.transformMod(modJar, tempDir);
                        if (transformed != null) {
                            Files.move(transformed, modJar, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                            LOGGER.info("Transformed in place: {}", fileName);
                            count++;
                        }
                        // Clean up temp
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
                "Retromod transformed " + transformedCount + " mod(s).\n\n" +
                "Please close Minecraft and launch it again\n" +
                "for the changes to take effect.\n\n" +
                "This only happens the first time.",
                "Retromod - Restart Required",
                javax.swing.JOptionPane.INFORMATION_MESSAGE);
        }, "Retromod-RestartPopup");
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
                    if (info != null && info.needsTransformation(RetromodVersion.TARGET_MC_VERSION)) {
                        String sourceVersion = info.targetMcVersion();
                        
                        // Runtime-transform NeoForge mods that need it
                        if (sourceVersion != null &&
                            "neoforge".equals(info.modLoaderType())) {
                            LOGGER.info("Runtime transforming: {} ({} -> {})",
                                modFile.getName(), sourceVersion, RetromodVersion.TARGET_MC_VERSION);
                            
                            for (String pkg : info.modPackages()) {
                                RetromodTransformer.getInstance().addTransformablePackage(pkg);
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
