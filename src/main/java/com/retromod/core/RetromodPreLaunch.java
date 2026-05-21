/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.retromod.util.ZipSecurity;
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
 * Retromod supports TWO input locations:
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
 * 3. PreLaunch entrypoints run (Retromod transforms here)
 * 4. Mods initialize
 * 
 * So transformed mods only appear on the NEXT launch.
 * This is a Fabric limitation, not a Retromod limitation.
 * 
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RetromodPreLaunch implements PreLaunchEntrypoint {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod");
    
    // Two input locations
    private static final String PRIMARY_INPUT = "retromod-input";
    private static final String SECONDARY_INPUT = "mods/retromod-input";
    private static final String PROCESSED_SUFFIX = "/processed";
    
    // Track transformation results (accessible for in-game notification)
    private static int totalTransformed = 0;
    private static List<String> transformedMods = new ArrayList<>();
    private static List<String> skippedComplexMods = new ArrayList<>();

    /** Check if mods were transformed this launch (for in-game restart screen). */
    public static boolean hasPendingRestart() {
        return totalTransformed > 0;
    }

    /** Get the list of mod filenames that were transformed this launch. */
    public static List<String> getTransformedMods() {
        return List.copyOf(transformedMods);
    }

    /** Get the total number of mods transformed this launch. */
    public static int getTotalTransformed() {
        return totalTransformed;
    }
    
    @Override
    public void onPreLaunch() {
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║  Retromod v1.0.0-beta.7                                    ║");
        LOGGER.info("╚════════════════════════════════════════════════════════════╝");
        
        try {
            Path gameDir = FabricLoader.getInstance().getGameDir();
            if (gameDir == null) {
                LOGGER.warn("Could not determine game directory, using current directory");
                gameDir = Path.of(".");
            }
            String targetVersion = getMinecraftVersion();
            
            LOGGER.info("Target Minecraft version: {}", targetVersion);

            // Step 0: Register shims BEFORE transforming so redirects are available.
            // Pass the host MC version so 26.1-only transformations (intermediary→Mojang
            // remap, 26.1 class moves) are NOT applied to pre-26.1 hosts, where the
            // Fabric runtime still uses intermediary names. See bugs #21 and #29.
            registerShimsForTransform(targetVersion);

            // Step 0.5: Load auto-fix fixes from previous launch.
            // These are redirects/patches discovered by analyzing crash logs from
            // a prior launch. Must be loaded AFTER shims (so shim redirects take
            // priority) but BEFORE transformation (so fixes are applied during transform).
            try {
                AutoFixEngine autoFixEngine = new AutoFixEngine();
                int savedFixes = autoFixEngine.loadAndApplySavedFixes(
                    RetromodTransformer.getInstance());
                if (savedFixes > 0) {
                    LOGGER.info("AutoFix: loaded {} saved fix(es) from previous launch", savedFixes);
                }
            } catch (Exception e) {
                LOGGER.debug("Could not load auto-fix saved fixes: {}", e.getMessage());
            }

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
                // Arm the in-game restart prompt (#33), shown on the title screen.
                com.retromod.gui.RestartPrompt.markPending(totalTransformed);
            }

            // Step 4: Show complexity warnings for skipped mods
            if (!skippedComplexMods.isEmpty()) {
                showComplexityWarning();
            }

            LOGGER.info("Retromod pre-launch complete!");
            
        } catch (Exception e) {
            LOGGER.error("Retromod pre-launch error: {}", e.getMessage());
        }
    }
    
    /**
     * Whether the host MC version is 26.1+ — the first unobfuscated MC release,
     * where the Fabric runtime switched from intermediary names
     * ({@code net.minecraft.class_XXXX}) to Mojang official names.
     *
     * <p>This gates all intermediary→Mojang remapping and 26.1 class moves.
     * A Fabric mod built for any pre-26.1 version already references the
     * intermediary names that the pre-26.1 runtime exposes, so it loads fine
     * untouched. Remapping those references to Mojang names (Identifier,
     * ParticleType, BuiltInRegistries, Component, …) on a pre-26.1 host
     * produces classes that DO NOT EXIST at runtime, crashing the mod with
     * {@code ClassNotFoundException} (bugs #21, #29).
     *
     * <p>MC 26.1 uses a year-based version scheme (26 = 2026), whose major
     * component sorts above the legacy {@code 1.x.y} scheme numerically.
     * Only an explicit major of 26+ is treated as unobfuscated; an
     * unparseable/unknown version defaults to {@code true} to preserve the
     * primary (published) 26.1 behavior.
     */
    static boolean isUnobfuscatedTarget(String hostVersion) {
        if (hostVersion == null) return true;
        try {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)").matcher(hostVersion.trim());
            if (!m.find()) return true; // unknown → assume 26.1+ (published behavior)
            return Integer.parseInt(m.group(1)) >= 26;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Whether MC version {@code a} is strictly newer than {@code b}, by numeric
     * per-component comparison (missing trailing components count as 0). Used to
     * skip version shims whose target is newer than the host — those rewrite a
     * mod's references to names the host doesn't have yet. On an unparseable
     * version we return {@code false} (don't skip) so we never over-exclude.
     */
    static boolean mcVersionExceeds(String a, String b) {
        try {
            return compareMcVersions(a, b) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Numeric per-component MC version compare ({@code 1.21.8} vs {@code 26.1} → negative). */
    static int compareMcVersions(String a, String b) {
        int[] pa = parseMcVersion(a);
        int[] pb = parseMcVersion(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parseMcVersion(String v) {
        if (v == null) return new int[0];
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)*)").matcher(v.trim());
        if (!m.find()) return new int[0];
        String[] parts = m.group(1).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }

    /**
     * Register all version shims and polyfills so that the bytecode transformer
     * has redirects available BEFORE AOT transformation runs.
     * Without this, mods are transformed with an empty redirect map.
     *
     * @param hostVersion the running MC version; gates 26.1-only remapping
     */
    private void registerShimsForTransform(String hostVersion) {
        try {
            RetromodTransformer transformer = RetromodTransformer.getInstance();

            // Load version shims via ServiceLoader
            ServiceLoader<VersionShim> shims = ServiceLoader.load(VersionShim.class);
            int shimCount = 0;
            int skippedNewer = 0;
            java.util.Iterator<VersionShim> shimIt = shims.iterator();
            while (shimIt.hasNext()) {
                VersionShim shim;
                try {
                    shim = shimIt.next();
                } catch (java.util.ServiceConfigurationError e) {
                    // Class not found — expected in lite builds
                    continue;
                }
                try {
                    // Only register shims whose TARGET version is <= the host MC.
                    // A shim X→Y rewrites references to Y-version names; if Y is newer
                    // than the host, those names don't exist at runtime and the shim
                    // BREAKS the mod. The clearest case is the 1.21.11→26.1 shim: it
                    // renames Fabric API classes (ScreenEvents$BeforeRender→BeforeExtract,
                    // ExtendedScreenHandlerFactory→ExtendedMenuProvider, …) and rewrites
                    // rendering types — applied on a 1.21.8/1.21.1 host that produces
                    // 26.1-only names → NoClassDefFoundError/VerifyError (bugs #31/#32/#35).
                    // Unlike the intermediary remap, Fabric API names are identical in the
                    // mod and the runtime, so these renames bite even on Fabric.
                    if (mcVersionExceeds(shim.getTargetVersion(), hostVersion)) {
                        skippedNewer++;
                        continue;
                    }
                    shim.registerRedirects(transformer);
                    shimCount++;
                } catch (Exception e) {
                    LOGGER.debug("Could not register shim: {}", e.getMessage());
                }
            }
            LOGGER.info("Registered {} version shims for transformation ({} skipped as newer than host MC {})",
                shimCount, skippedNewer, hostVersion);

            // Register intermediary→Mojang class mappings for bytecode remapping.
            // CRITICAL: this is a 26.1+ ONLY transformation. MC 26.1 was the first
            // version to drop obfuscation; before it, the Fabric RUNTIME exposes MC
            // under intermediary names (net.minecraft.class_XXXX). A Fabric mod built
            // for any pre-26.1 version already references those intermediary names and
            // loads fine on a pre-26.1 host untouched. Remapping them to Mojang names
            // here would rewrite working references into 26.1 names that don't exist at
            // a pre-26.1 runtime → ClassNotFoundException (bugs #21, #29). So we only
            // apply the remap / 26.1 class moves / Identifier ctor redirects on 26.1+.
            try {
                com.retromod.mapping.IntermediaryToMojangMapper mapper =
                    com.retromod.mapping.IntermediaryToMojangMapper.getInstance();
                if (!mapper.isLoaded()) {
                    LOGGER.warn("IntermediaryToMojangMapper not loaded — bytecode class remapping disabled");
                } else if (!isUnobfuscatedTarget(hostVersion)) {
                    LOGGER.info("Host MC {} is pre-26.1 (obfuscated; Fabric runtime uses "
                        + "intermediary names) — skipping intermediary→Mojang remap and 26.1 "
                        + "class moves so mods keep their working names (bugs #21/#29)", hostVersion);
                } else {
                    // IMPORTANT: ASM ClassRemapper is single-pass, so we must compose
                    // intermediary→Mojang with class moves to get intermediary→final26.1 names.
                    // Otherwise class_4064→CycleOption stops there, but CycleOption→OptionInstance
                    // never fires because the bytecode had class_4064, not CycleOption.
                    java.util.Map<String, String> classMoveMap = mapper.getClassMoves();

                    // First: register intermediary→Mojang, but if the Mojang target was
                    // itself moved in 26.1, point directly to the final name
                    int classRedirects = 0;
                    int composed = 0;
                    for (java.util.Map.Entry<String, String> entry : mapper.getClassMap().entrySet()) {
                        String intermediary = entry.getKey();
                        String mojang = entry.getValue();
                        // Check if this Mojang name was moved in 26.1
                        String finalName = classMoveMap.getOrDefault(mojang, mojang);
                        if (!finalName.equals(mojang)) composed++;
                        transformer.registerClassRedirect(intermediary, finalName);
                        classRedirects++;
                    }
                    // Also register method and field name mappings
                    transformer.registerIntermediaryNameMappings(
                        mapper.getMethodMap(), mapper.getFieldMap());
                    // Also register 26.1 class moves directly (for mods that already
                    // use Mojang names, e.g. Jade targeting 1.20+ with GuiGraphics)
                    int classMoves = 0;
                    for (java.util.Map.Entry<String, String> entry : classMoveMap.entrySet()) {
                        transformer.registerClassRedirect(entry.getKey(), entry.getValue());
                        classMoves++;
                    }
                    LOGGER.info("Composed {}/{} intermediary mappings with class moves", composed, classRedirects);
                    // Register constructor→factory redirects for 26.1 API changes
                    // ResourceLocation(String) → Identifier.parse(String)
                    transformer.registerConstructorRedirect(
                        "net/minecraft/resources/Identifier", "(Ljava/lang/String;)V",
                        "net/minecraft/resources/Identifier", "parse",
                        "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;");
                    // ResourceLocation(String, String) → Identifier.fromNamespaceAndPath(String, String)
                    transformer.registerConstructorRedirect(
                        "net/minecraft/resources/Identifier", "(Ljava/lang/String;Ljava/lang/String;)V",
                        "net/minecraft/resources/Identifier", "fromNamespaceAndPath",
                        "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;");
                    LOGGER.info("Registered {} intermediary→Mojang class redirects + {} class moves + 2 constructor redirects",
                        classRedirects, classMoves);
                }
            } catch (Exception e) {
                LOGGER.warn("Could not register intermediary→Mojang mappings: {}", e.getMessage());
            }

            // Load polyfill providers via ServiceLoader
            try {
                ServiceLoader<com.retromod.polyfill.PolyfillProvider> polyfills =
                    ServiceLoader.load(com.retromod.polyfill.PolyfillProvider.class);
                int polyfillCount = 0;
                java.util.Iterator<com.retromod.polyfill.PolyfillProvider> polyfillIt = polyfills.iterator();
                while (polyfillIt.hasNext()) {
                    com.retromod.polyfill.PolyfillProvider provider;
                    try {
                        provider = polyfillIt.next();
                    } catch (java.util.ServiceConfigurationError e) {
                        // Class not found — expected in lite builds
                        continue;
                    }
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
            // Initialize fuzzy resolver — last-resort fallback for unresolved references.
            // Auto-detects the MC JAR from the classpath (Fabric Loader always has it loaded).
            try {
                transformer.initFuzzyResolver(null);
            } catch (Exception e) {
                LOGGER.debug("Could not initialize fuzzy resolver: {}", e.getMessage());
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
                .orElse("26.1");
        } catch (Exception e) {
            return "26.1";
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
                    ║  3. Retromod transforms them automatically                 ║
                    ║  4. ⚠️ RESTART Minecraft (required first time)             ║
                    ║  5. Your mods work!                                        ║
                    ║                                                            ║
                    ║  WHY RESTART?                                              ║
                    ║  Fabric scans mods BEFORE Retromod can run. Transformed    ║
                    ║  mods only load on the next launch. This is a Fabric       ║
                    ║  limitation, not a Retromod bug.                           ║
                    ║                                                            ║
                    ║  AFTER TRANSFORMATION:                                     ║
                    ║  - Originals move to: processed/                           ║
                    ║  - Transformed mods go to: mods/                           ║
                    ║                                                            ║
                    ║  ⚠️  Do NOT drop new mods in processed/!                   ║
                    ║  That subfolder is the AFTER-transform staging area.      ║
                    ║  Mods dropped there are treated as already-handled and    ║
                    ║  skipped on the next scan.                                ║
                    ║                                                            ║
                    ║  ALTERNATIVE LOCATION:                                     ║
                    ║  You can also use: mods/retromod-input/                    ║
                    ║  (Same rule — don't use its processed/ subfolder either.) ║
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
                    ║  ⚠️  Drop mods HERE, not in the processed/ subfolder.      ║
                    ║  processed/ is where originals go AFTER transformation —  ║
                    ║  mods dropped there are treated as already-handled and    ║
                    ║  skipped on the next scan.                                ║
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
                ║   ⚠️  IMPORTANT: drop mods directly in retromod-input/.    ║
                ║   Do NOT put them in the `processed/` subfolder! That's    ║
                ║   where Retromod moves originals AFTER it transforms       ║
                ║   them — mods dropped there are skipped and won't load.   ║
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
                ║      Retromod will transform the mods                      ║
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
                ║   Retromod transforms mods, but Fabric already read        ║
                ║   the folder. On restart, Fabric sees the new mods.        ║
                ║                                                            ║
                ║   This is a Fabric limitation, not a Retromod bug.         ║
                ║                                                            ║
                ╚════════════════════════════════════════════════════════════╝
                
                Need help? Visit: github.com/Bownlux/Retromod/issues
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

        // Validate directories are not symlinks (prevent symlink attacks)
        try {
            ZipSecurity.validateNotSymlink(inputFolder);
            ZipSecurity.validateNotSymlink(outputFolder);
        } catch (java.io.IOException e) {
            LOGGER.error("Security check failed: {}", e.getMessage());
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

                    // Always transform — never skip based on complexity.
                    // Retromod should try its best with every mod, even complex ones.
                    if (report.isUnlikelyToWork()) {
                        LOGGER.warn("│  ⚠ High complexity (score: {}) — some features may not work", report.score());
                        LOGGER.warn("│  Reason: {}", report.reason());
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
     * Show restart required message in logs.
     * The in-game screen is shown later by Retromod.onInitialize() via InGameScreenFactory.
     */
    private void showRestartMessage() {
        LOGGER.info("");
        LOGGER.info("╔════════════════════════════════════════════════════════════╗");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   RESTART REQUIRED!                                        ║");
        LOGGER.info("║                                                            ║");
        LOGGER.info("║   Retromod transformed {} mod(s):                          ║", String.format("%-2d", totalTransformed));
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
        LOGGER.warn("║   features Retromod cannot fully transform (coremods,      ║");
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
                msg.append("Retromod skipped ").append(skippedComplexMods.size())
                   .append(" mod(s) because they are unlikely to work:\n\n");
                for (String mod : skippedComplexMods) {
                    msg.append("  - ").append(mod).append("\n");
                }
                msg.append("\nThese mods use features that Retromod cannot fully\n");
                msg.append("transform (coremods, heavy reflection, ASM, etc.).\n\n");
                msg.append("To force translation anyway, set:\n");
                msg.append("  \"force_translate_complex\": true\n");
                msg.append("in config/retromod/config.json");

                JOptionPane.showMessageDialog(
                    null,
                    msg.toString(),
                    "Retromod - Mods Skipped",
                    JOptionPane.WARNING_MESSAGE
                );
            }, "Retromod-ComplexityWarning");
            warningThread.setDaemon(true);
            warningThread.start();
        }
    }
}
