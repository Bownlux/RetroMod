/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import com.retromod.shim.ShimRegistry;
import com.retromod.embedder.ApiEmbedder;
import com.retromod.embedder.ModVersionInfo;
import com.retromod.aot.AotCompiler;
import com.retromod.mixin.MixinCompatibilityTransformer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Main entry point for RetroMod.
 * 
 * RetroMod allows older Minecraft mods to run on newer versions by:
 * 1. Transforming bytecode to redirect renamed/moved methods
 * 2. Embedding removed APIs directly into mod JARs
 * 3. Providing shim implementations for deleted functionalty
 */
public class RetroMod implements ModInitializer {
    
    public static final String MOD_ID = "retromod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    
    // Current target Minecraft version - auto-detected at runtime
    public static String TARGET_MC_VERSION = "1.21.4";

    // Initialize target version from mod loader
    static {
        try {
            // Try Fabric Loader first
            String mcVersion = net.fabricmc.loader.api.FabricLoader.getInstance()
                .getModContainer("minecraft")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse(null);
            if (mcVersion != null) {
                TARGET_MC_VERSION = mcVersion;
            }
        } catch (Exception e) {
            // Not Fabric - try NeoForge via reflection
            try {
                Class<?> fmlLoader = Class.forName("net.neoforged.fml.loading.FMLLoader");
                Object versionInfo = fmlLoader.getMethod("versionInfo").invoke(null);
                String mcVersion = (String) versionInfo.getClass().getMethod("mcVersion").invoke(versionInfo);
                if (mcVersion != null) {
                    TARGET_MC_VERSION = mcVersion;
                }
            } catch (Exception e2) {
                // Not NeoForge - try Forge via reflection
                try {
                    Class<?> mcpVersion = Class.forName("net.minecraftforge.versions.mcp.MCPVersion");
                    String mcVersion = (String) mcpVersion.getMethod("getMCVersion").invoke(null);
                    if (mcVersion != null) {
                        TARGET_MC_VERSION = mcVersion;
                    }
                } catch (Exception e3) {
                    // Fallback to default 1.21.4
                }
            }
        }
    }
    
    // Supported source versions (mods built for these can be transformed)
    public static final String[] SUPPORTED_SOURCE_VERSIONS = {
        // Fabric 1.14+ (when Fabric was created)
        "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4",
        "1.15", "1.15.1", "1.15.2",
        "1.16", "1.16.1", "1.16.2", "1.16.3", "1.16.4", "1.16.5",
        "1.17", "1.17.1",
        "1.18", "1.18.1", "1.18.2",
        "1.19", "1.19.1", "1.19.2", "1.19.3", "1.19.4",
        "1.20", "1.20.1", "1.20.2", "1.20.3", "1.20.4", "1.20.5", "1.20.6",
        // 1.21.x versions
        "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4",
        "1.21.5", "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10"
    };
    
    // Backup folder for original mods
    public static final Path BACKUP_FOLDER = Path.of("mods/retromod-backups");
    
    private static RetroMod instance;
    private ShimRegistry shimRegistry;
    private ApiEmbedder apiEmbedder;
    private ModVersionDetector versionDetector;
    private AotCompiler aotCompiler;
    private MixinCompatibilityTransformer mixinTransformer;
    
    // Configuration
    private boolean useAotCompilation = true;
    private boolean transformMixins = true;
    private boolean passNativeModsThrough = true; // Don't transform mods already for current version
    private boolean polyfillsEnabled = true;      // Enable re-implemented removed APIs
    
    // Supported target versions (add new MC versions here)
    public static final String[] SUPPORTED_TARGET_VERSIONS = {
        "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5",
        "1.21.6", "1.21.7", "1.21.8", "1.21.9", "1.21.10", "1.21.11",
        // 26.1 — first unobfuscated MC version
        "26.1", "26.1-pre.1", "26.1-pre.2", "26.1-pre-1", "26.1-pre-2",
        "26.1.0", "26.1.1", "26.1.2",
        // Future versions
        "26.2", "26.2.0", "26.2.1"
    };
    
    @Override
    public void onInitialize() {
        instance = this;
        LOGGER.info("RetroMod initializing - Target MC version: {}", TARGET_MC_VERSION);

        // Log the full environment (OS, CPU arch, rendering backend)
        EnvironmentDetector.logEnvironment();

        // Verify authenticity of this RetroMod build — informational only,
        // never blocks. An unsigned or modified build still runs fine.
        com.retromod.security.SignatureVerifier.verifyAndLog();

        // Load configuration
        loadConfig();

        // ALWAYS create folders (every launch, not just first)
        Path gameDir;
        try {
            gameDir = net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir();
            if (gameDir == null) {
                gameDir = Path.of(".");
            }
            ensureRetroModFolders(gameDir);
        } catch (Exception e) {
            LOGGER.warn("Could not create RetroMod folders", e);
            gameDir = Path.of(".");
        }

        // Each component is isolated so a failure in one doesn't crash the whole mod
        try {
            com.retromod.legacy.LegacyVersionSupport legacySupport =
                new com.retromod.legacy.LegacyVersionSupport();
            legacySupport.logSupportStatus(gameDir.resolve("mods"));
        } catch (Exception e) {
            LOGGER.warn("Could not initialize legacy version support", e);
        }

        try {
            CrossModDependencyResolver dependencyResolver =
                new CrossModDependencyResolver(TARGET_MC_VERSION);
            dependencyResolver.scanMods(gameDir.resolve("mods"));
            dependencyResolver.logResolutionInfo();
        } catch (Exception e) {
            LOGGER.warn("Could not initialize dependency resolver", e);
        }

        try {
            com.retromod.resources.ResourceManager resourceManager =
                new com.retromod.resources.ResourceManager(TARGET_MC_VERSION, gameDir);
            resourceManager.ensureFolders();
            resourceManager.processAll();
        } catch (Exception e) {
            LOGGER.warn("Could not initialize resource manager", e);
        }

        // Initialize core components
        try {
            shimRegistry = new ShimRegistry();
        } catch (Exception e) {
            LOGGER.warn("Could not initialize shim registry", e);
            shimRegistry = new ShimRegistry();
        }

        try {
            apiEmbedder = new ApiEmbedder();
        } catch (Exception e) {
            LOGGER.warn("Could not initialize API embedder", e);
        }

        versionDetector = new ModVersionDetector();

        // Register all known shims
        try {
            registerShims();
        } catch (Exception e) {
            LOGGER.warn("Could not register shims", e);
        }

        // Register polyfills (re-implemented removed APIs)
        try {
            registerPolyfills();
        } catch (Exception e) {
            LOGGER.warn("Could not register polyfills", e);
        }

        // Load auto-fix fixes from previous launch BEFORE transformation.
        // These are fixes that the AutoFixEngine discovered by analyzing crash logs
        // from a prior launch. They register redirects/patches so the next transform
        // incorporates the fixes automatically.
        //
        // SECURITY: loadAndApplySavedFixes READS entries from
        // config/retromod/auto-fixes.json and applies them unconditionally.
        // The file itself was written by an earlier run of AutoFixEngine.
        // If a malicious mod previously managed to get a redirect persisted
        // (back when AutoFix was always-on), disabling AutoFix today would
        // not remove those persisted entries — they'd still reload every
        // launch. Gate the READ on the same -Dretromod.autoFix opt-in flag
        // so that turning off AutoFix actually turns it all the way off.
        boolean autoFixEnabled = Boolean.parseBoolean(
                System.getProperty("retromod.autoFix", "false"));
        if (autoFixEnabled) {
            try {
                AutoFixEngine autoFixEngine = new AutoFixEngine();
                int savedFixes = autoFixEngine.loadAndApplySavedFixes(RetroModTransformer.getInstance());
                if (savedFixes > 0) {
                    LOGGER.info("AutoFix: loaded {} saved fix(es) from previous launch", savedFixes);
                }
            } catch (Exception e) {
                LOGGER.warn("Could not load auto-fix saved fixes", e);
            }
        }

        // Initialize AOT compiler
        try {
            aotCompiler = new AotCompiler(shimRegistry, TARGET_MC_VERSION);
        } catch (Exception e) {
            LOGGER.warn("Could not initialize AOT compiler", e);
        }

        // Initialize Mixin transformer (for mods with mixins)
        try {
            mixinTransformer = new MixinCompatibilityTransformer(RetroModTransformer.getInstance());
        } catch (Exception e) {
            LOGGER.warn("Could not initialize Mixin transformer", e);
        }

        // Scan mods folder for legacy mods
        try {
            if (useAotCompilation && aotCompiler != null) {
                performAotCompilation();
            } else {
                scanAndPrepare();
            }
        } catch (Exception e) {
            LOGGER.warn("Could not scan/compile mods", e);
        }

        // Bridge old HUD callbacks to new HudElementRegistry API (26.1+)
        try {
            com.retromod.shim.fabric.embedded.HudRenderCallbackShim.bridgeToNewApi();
        } catch (Throwable e) {
            // Catch Throwable — ExceptionInInitializerError from IdentifierShim
            // is an Error, not Exception. Non-critical, don't crash RetroMod.
            LOGGER.warn("Could not bridge HUD callbacks: {}", e.getMessage());
        }

        // Show in-game restart screen if mods were transformed during pre-launch
        try {
            if (RetroModPreLaunch.hasPendingRestart()) {
                scheduleRestartScreen();
            }
        } catch (Exception e) {
            LOGGER.warn("Could not show restart screen: {}", e.getMessage());
        }

        // Auto-fix: analyze the PREVIOUS launch's log for errors and prepare fixes.
        // Scans latest.log for known crash patterns (NoSuchMethodError, VerifyError,
        // mixin failures, etc.) and registers redirects so the NEXT retransformation
        // incorporates them.
        //
        // SECURITY: latest.log is a shared file that *any* mod can write to via
        // its own logger. A crafted log line that matches AutoFixEngine's
        // error-pattern regex can cause RetroMod to register attacker-chosen
        // method/field redirects into the shared transformer. The poisoned
        // redirects are constrained to real MC methods (fuzzy resolver requires
        // ≥85 match score against the indexed JAR), so this is not RCE — but
        // one mod could deliberately mis-route another mod's bytecode rewrites.
        //
        // Mitigation: auto-fix is OPT-IN via -Dretromod.autoFix=true. Off by
        // default. Users who want the convenience of auto-fix can enable it
        // knowing the tradeoff. Each registered redirect is also logged at
        // WARN so anomalies are visible even with the feature on.
        // (autoFixEnabled was resolved once at the top of this method; reuse it.)
        if (autoFixEnabled) {
            try {
                Path logFile = gameDir.resolve("logs/latest.log");
                if (java.nio.file.Files.exists(logFile)) {
                    AutoFixEngine autoFixEngine = new AutoFixEngine();
                    List<AutoFixEngine.AppliedFix> fixes =
                        autoFixEngine.analyzeAndFix(logFile, RetroModTransformer.getInstance());
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
                    + "(see security notes in RetroMod.java).");
        }

        LOGGER.info("RetroMod initialized - {} method redirects, {} class redirects registered",
                RetroModTransformer.getInstance().getMethodRedirectCount(),
                RetroModTransformer.getInstance().getClassRedirectCount());
    }

    /**
     * Schedule the in-game restart screen to appear once the MC client is ready.
     * Deferred so the title screen has time to initialize first.
     */
    private void scheduleRestartScreen() {
        // No in-game popup — just log the restart message.
        // The log already shows RESTART REQUIRED with the list of transformed mods.
        // In-game screens cause issues with Fabric's screen API changes in newer versions.
        List<String> mods = RetroModPreLaunch.getTransformedMods();
        if (!mods.isEmpty()) {
            LOGGER.info("Transformed {} mod(s) — restart to load them", mods.size());
        }
    }
    
    /**
     * Ensure RetroMod folders exist - called EVERY launch.
     */
    private void ensureRetroModFolders(Path gameDir) {
        try {
            // Create retromod-input folder
            Path inputFolder = gameDir.resolve("retromod-input");
            if (!java.nio.file.Files.exists(inputFolder)) {
                java.nio.file.Files.createDirectories(inputFolder);
                LOGGER.info("Created retromod-input/ folder");
                
                // Create README
                Path readme = inputFolder.resolve("README.txt");
                java.nio.file.Files.writeString(readme, getReadmeContent());
            }
            
            // Create processed subfolder
            Path processedFolder = inputFolder.resolve("processed");
            if (!java.nio.file.Files.exists(processedFolder)) {
                java.nio.file.Files.createDirectories(processedFolder);
            }
            
            // Create backups folder
            Path backupFolder = gameDir.resolve("retromod-backups");
            if (!java.nio.file.Files.exists(backupFolder)) {
                java.nio.file.Files.createDirectories(backupFolder);
            }
            
            // Also call ModHealthChecker for extra safety
            ModHealthChecker.ensureFoldersExist(gameDir);
            
        } catch (Exception e) {
            LOGGER.warn("Could not create folders: {}", e.getMessage());
        }
    }
    
    /**
     * Get README content for retromod-input folder.
     */
    private String getReadmeContent() {
        return """
            ═══════════════════════════════════════════════════════════════
            RETROMOD INPUT FOLDER
            ═══════════════════════════════════════════════════════════════
            
            Put your OLD mods here (NOT in the mods/ folder!)
            
            RetroMod will automatically:
            1. Transform them to work with your Minecraft version
            2. Copy the transformed versions to mods/
            3. Move the originals to processed/
            
            IMPORTANT: Mods that are ALREADY for your Minecraft version
            do NOT need to go here - put them directly in mods/
            
            RetroMod will NOT transform native mods - they pass through!
            
            ═══════════════════════════════════════════════════════════════
            NEED HELP? FOUND A BUG?
            ═══════════════════════════════════════════════════════════════
            
            Report bugs on GitHub:
            https://github.com/Bownlux/RetroMod/issues
            
            ═══════════════════════════════════════════════════════════════
            """;
    }
    
    /**
     * Check if a mod is for the native/current Minecraft version.
     * Native mods should NOT be transformed - they pass through untouched.
     */
    public boolean isNativeVersionMod(String modMinecraftVersion) {
        if (modMinecraftVersion == null) return false;
        
        // Clean up version string
        String cleanVersion = modMinecraftVersion
            .replace(">=", "")
            .replace("<=", "")
            .replace(">", "")
            .replace("<", "")
            .replace("~", "")
            .replace("^", "")
            .trim();
        
        // Check if it matches current target version
        if (cleanVersion.equals(TARGET_MC_VERSION)) {
            return true;
        }
        
        // Check if it's a range that includes current version
        if (modMinecraftVersion.contains(TARGET_MC_VERSION)) {
            return true;
        }
        
        // Check for wildcard versions like "1.21.x" or "1.21.*"
        if (cleanVersion.endsWith(".x") || cleanVersion.endsWith(".*")) {
            String base = cleanVersion.substring(0, cleanVersion.length() - 2);
            if (TARGET_MC_VERSION.startsWith(base)) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Load configuration from JSON file.
     * Auto-generates a default config if one doesn't exist.
     */
    private void loadConfig() {
        Path configDir = Path.of("config/retromod");
        Path configPath = configDir.resolve("config.json");

        try {
            java.nio.file.Files.createDirectories(configDir);
        } catch (Exception e) {
            LOGGER.warn("Could not create config directory", e);
        }

        if (configPath.toFile().exists()) {
            try {
                String json = java.nio.file.Files.readString(configPath);
                var config = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

                if (config.has("use_aot")) useAotCompilation = config.get("use_aot").getAsBoolean();
                if (config.has("transform_mixins")) transformMixins = config.get("transform_mixins").getAsBoolean();
                if (config.has("polyfills_enabled")) polyfillsEnabled = config.get("polyfills_enabled").getAsBoolean();

                LOGGER.info("Loaded config from {}", configPath);
            } catch (Exception e) {
                LOGGER.warn("Could not load config, using defaults", e);
            }
        } else {
            // Auto-generate default config
            generateDefaultConfig(configPath);
        }
    }

    /**
     * Generate a default config.json with comments explaining each option.
     */
    private void generateDefaultConfig(Path configPath) {
        String defaultConfig = """
                {
                  "_comment": "RetroMod Configuration - https://github.com/Bownlux/RetroMod",

                  "use_aot": true,
                  "use_hybrid": true,
                  "instruction_level_granularity": true,

                  "transform_mixins": true,
                  "transform_refmaps": true,

                  "remap_reflection": true,

                  "log_level": "INFO",
                  "log_transformations": false,

                  "target_mc_version": "auto",

                  "debug": false,
                  "dump_bytecode": false,

                  "force_translate_complex": false,

                  "polyfills_enabled": true,

                  "verify_transforms": true
                }
                """;
        try {
            java.nio.file.Files.writeString(configPath, defaultConfig);
            LOGGER.info("Generated default config at {}", configPath);
        } catch (Exception e) {
            LOGGER.warn("Could not generate default config", e);
        }
    }
    
    /**
     * Perform AOT compilation of all mods on first launch.
     * Creates backups before transforming.
     */
    private void performAotCompilation() {
        Path modsFolder = Path.of("mods");
        if (!modsFolder.toFile().exists()) {
            return;
        }
        
        // Create backup folder
        createBackupFolder();
        
        LOGGER.info("Starting AOT compilation of legacy mods...");
        LOGGER.info("Backups will be stored in: {}", BACKUP_FOLDER);
        
        List<AotCompiler.AotResult> results = aotCompiler.compileAllModsSync(
            modsFolder,
            (current, total, name) -> {
                LOGGER.info("AOT compiling [{}/{}]: {}", current, total, name);
            }
        );
        
        // Summarize results
        int success = 0, cached = 0, skipped = 0, failed = 0;
        for (var result : results) {
            switch (result.status()) {
                case SUCCESS -> success++;
                case CACHED -> cached++;
                case SKIPPED -> skipped++;
                case FAILED -> failed++;
            }
        }
        
        LOGGER.info("AOT compilation complete: {} compiled, {} cached, {} skipped, {} failed",
                success, cached, skipped, failed);
    }
    
    /**
     * Create backup folder for original mod JARs.
     */
    private void createBackupFolder() {
        try {
            if (!java.nio.file.Files.exists(BACKUP_FOLDER)) {
                java.nio.file.Files.createDirectories(BACKUP_FOLDER);
                LOGGER.info("Created backup folder: {}", BACKUP_FOLDER);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to create backup folder", e);
        }
    }
    
    /**
     * Backup a mod JAR before transformation.
     * @param modFile The mod file to backup
     * @return true if backup was successful
     */
    public boolean backupMod(Path modFile) {
        try {
            createBackupFolder();
            
            String fileName = modFile.getFileName().toString();
            Path backupPath = BACKUP_FOLDER.resolve(fileName);
            
            // Add timestamp if backup already exists
            if (java.nio.file.Files.exists(backupPath)) {
                String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
                String baseName = fileName.substring(0, fileName.lastIndexOf('.'));
                String extension = fileName.substring(fileName.lastIndexOf('.'));
                backupPath = BACKUP_FOLDER.resolve(baseName + "-" + timestamp + extension);
            }
            
            java.nio.file.Files.copy(modFile, backupPath);
            LOGGER.info("Backed up {} to {}", fileName, backupPath);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to backup mod: {}", modFile, e);
            return false;
        }
    }
    
    /**
     * Restore a mod from backup.
     * @param modName The name of the mod file to restore
     * @return true if restore was successful
     */
    public boolean restoreMod(String modName) {
        try {
            Path backupPath = BACKUP_FOLDER.resolve(modName);
            // Resolve mods folder from the Fabric game directory when available,
            // not CWD — this is more robust if RetroMod is launched from a
            // different working directory (e.g. CLI mode).
            Path modsFolder;
            try {
                modsFolder = net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getGameDir().resolve("mods");
            } catch (Throwable t) {
                modsFolder = Path.of("mods");
            }
            Path targetPath = modsFolder.resolve(modName);
            
            if (!java.nio.file.Files.exists(backupPath)) {
                LOGGER.error("No backup found for: {}", modName);
                return false;
            }
            
            // Delete the transformed version if it exists
            java.nio.file.Files.deleteIfExists(targetPath);
            
            // Also delete any -retromod version
            String baseName = modName.substring(0, modName.lastIndexOf('.'));
            java.nio.file.Files.list(modsFolder)
                .filter(p -> p.getFileName().toString().startsWith(baseName + "-retromod"))
                .forEach(p -> {
                    try { java.nio.file.Files.delete(p); } 
                    catch (Exception e) { /* ignore */ }
                });
            
            // Copy backup back
            java.nio.file.Files.copy(backupPath, targetPath);
            LOGGER.info("Restored {} from backup", modName);
            return true;
            
        } catch (Exception e) {
            LOGGER.error("Failed to restore mod: {}", modName, e);
            return false;
        }
    }
    
    /**
     * Register all version-specific shims.
     * Each shim handles compatibility for a specific version transition.
     */
    private void registerShims() {
        // Load shims via ServiceLoader (allows external shim packs)
        ServiceLoader<VersionShim> shims = ServiceLoader.load(VersionShim.class);

        // Use iterator with error handling to support lite builds where some
        // shim classes may be excluded from the JAR
        java.util.Iterator<VersionShim> it = shims.iterator();
        while (it.hasNext()) {
            VersionShim shim;
            try {
                shim = it.next();
            } catch (java.util.ServiceConfigurationError e) {
                // Class not found — expected in lite builds
                LOGGER.debug("Skipping unavailable shim: {}", e.getMessage());
                continue;
            }
            LOGGER.info("Loading shim: {} ({} -> {})",
                    shim.getShimName(), shim.getSourceVersion(), shim.getTargetVersion());
            shim.registerRedirects(RetroModTransformer.getInstance());
            shimRegistry.register(shim);
        }
        
        // Also register built-in shims
        registerBuiltInShims();
    }
    
    /**
     * Built-in shims for common version transitions.
     */
    private void registerBuiltInShims() {
        RetroModTransformer transformer = RetroModTransformer.getInstance();
        
        // === 1.21.9 -> 1.21.10 Compatibility ===
        // Example: Entity.getWorld() was renamed to Entity.getEntityWorld() in 1.21.9+
        // (This is a hypothetical example - real changes would be documented)
        
        // Method rename example
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/Entity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        // If the above method was REMOVED entirely, redirect to our embedded shim:
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", "method_removed_example", "()V",
            "com/retromod/shim/embedded/EntityShim", "method_removed_example", "(Lnet/minecraft/entity/Entity;)V"
        );
        
        // === Fabric API Changes ===
        // When Fabric API removes/renames events or utilities, redirect to embedded versions
        
        // Example: If FabricLoader.getModContainer changed signature
        transformer.registerMethodRedirect(
            "net/fabricmc/loader/api/FabricLoader", "getModContainer", 
            "(Ljava/lang/String;)Ljava/util/Optional;",
            "com/retromod/shim/fabric/FabricLoaderShim", "getModContainer",
            "(Ljava/lang/String;)Ljava/util/Optional;"
        );
        
        // === Rendering Backend Compatibility ===
        // Handles OpenGL → Vulkan / Metal transitions
        try {
            com.retromod.shim.api.fabric.RenderingBackendShim renderShim =
                new com.retromod.shim.api.fabric.RenderingBackendShim();
            renderShim.registerRedirects(transformer);
            shimRegistry.register(renderShim);
        } catch (Exception e) {
            LOGGER.warn("Could not register rendering backend shim", e);
        }

        LOGGER.info("Registered built-in shims for 1.21.x compatibility");
    }

    /**
     * Register polyfills (re-implemented removed APIs).
     * Polyfills provide stub classes for APIs that were completely removed,
     * preventing ClassNotFoundException and mixin hierarchy failures.
     */
    private void registerPolyfills() {
        com.retromod.polyfill.PolyfillRegistry polyfillRegistry =
            new com.retromod.polyfill.PolyfillRegistry();
        polyfillRegistry.setEnabled(polyfillsEnabled);
        polyfillRegistry.loadAndRegister(RetroModTransformer.getInstance());
    }

    /**
     * Scan mods folder and prepare any legacy mods.
     */
    private void scanAndPrepare() {
        Path modsFolder = Path.of("mods");
        if (!modsFolder.toFile().exists()) {
            return;
        }
        
        File[] modFiles = modsFolder.toFile().listFiles((dir, name) -> 
                name.endsWith(".jar") && !name.contains("-retromod"));
        
        if (modFiles == null) return;
        
        for (File modFile : modFiles) {
            try {
                ModVersionInfo info = versionDetector.detectVersion(modFile.toPath());
                
                if (info != null && info.needsTransformation(TARGET_MC_VERSION)) {
                    LOGGER.info("Found legacy mod: {} (built for {}, current: {})",
                            info.modId(), info.targetMcVersion(), TARGET_MC_VERSION);
                    
                    // Register the mod's packages for transformation
                    for (String pkg : info.modPackages()) {
                        RetroModTransformer.getInstance().addTransformablePackage(pkg);
                    }
                    
                    // Check if we need to embed any removed APIs
                    if (info.usesRemovedApis()) {
                        LOGGER.info("Mod {} uses removed APIs - embedding shims", info.modId());
                        apiEmbedder.embedRequiredShims(modFile.toPath(), info);
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("Failed to analyze mod: {}", modFile.getName(), e);
            }
        }
    }
    
    public static RetroMod getInstance() {
        return instance;
    }
    
    public ShimRegistry getShimRegistry() {
        return shimRegistry;
    }
    
    public ApiEmbedder getApiEmbedder() {
        return apiEmbedder;
    }
}
