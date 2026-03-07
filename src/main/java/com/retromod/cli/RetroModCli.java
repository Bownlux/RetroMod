/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.cli;

import com.retromod.core.*;
import com.retromod.embedder.*;
import com.retromod.aot.AotCompiler;
import com.retromod.archive.ApiArchiveManager;
import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.shim.ShimRegistry;
import com.retromod.shim.fabric.*;
import com.retromod.shim.neoforge.*;
import com.retromod.shim.forge.*;
import com.retromod.legacy.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Standalone command-line tool for processing mods.
 * 
 * Usage:
 *   java -jar retromod-cli.jar <command> [options]
 * 
 * Commands:
 *   analyze <mod.jar>           - Analyze a mod and show compatibility info
 *   transform <mod.jar>         - Transform a mod for the target version
 *   aot <mod.jar>               - AOT compile a mod (embed shims, pre-transform)
 *   batch <mods-folder>         - Process all mods in a folder
 *   diff <v1> <v2>              - Show API differences between versions
 *   archive <action>            - Manage API archives
 *   shims                       - List all registered shims
 */
public class RetroModCli {
    
    private static final String VERSION = "1.0.0-beta.1";
    private static final String TARGET_MC_VERSION = "1.21.11";
    
    private static ShimRegistry shimRegistry;
    private static ModVersionDetector detector;
    private static ApiArchiveManager archiveManager;
    
    public static void main(String[] args) {
        if (args.length == 0) {
            printUsage();
            return;
        }
        
        // Initialize
        shimRegistry = new ShimRegistry();
        detector = new ModVersionDetector();
        archiveManager = new ApiArchiveManager();
        registerAllShims();
        
        // Create RetroMod folders in current directory
        try {
            ModHealthChecker.ensureFoldersExist(Path.of("."));
        } catch (Exception e) {
            // Ignore - might not have access
        }
        
        String command = args[0].toLowerCase();
        
        try {
            switch (command) {
                case "analyze" -> analyzeCommand(args);
                case "transform" -> transformCommand(args);
                case "aot" -> aotCommand(args);
                case "embed" -> embedCommand(args);
                case "batch" -> batchCommand(args);
                case "diff" -> diffCommand(args);
                case "archive" -> archiveCommand(args);
                case "shims" -> shimsCommand(args);
                case "legacy" -> legacyCommand(args);
                case "overrides" -> overridesCommand(args);
                case "prepare" -> prepareCommand(args);
                case "help", "-h", "--help" -> printUsage();
                case "version", "-v", "--version" -> 
                    System.out.println("RetroMod CLI v" + VERSION + " (Target: MC " + TARGET_MC_VERSION + ")");
                default -> {
                    System.err.println("Unknown command: " + command);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (System.getenv("RETROMOD_DEBUG") != null) {
                e.printStackTrace();
            }
            System.exit(1);
        } finally {
            archiveManager.shutdown();
        }
    }
    
    private static void registerAllShims() {
        // Fabric shims - complete 1.21 to 1.21.11 chain
        shimRegistry.register(new Fabric_1_21_to_1_21_1());
        shimRegistry.register(new Fabric_1_21_1_to_1_21_2());
        shimRegistry.register(new Fabric_1_21_2_to_1_21_3());
        shimRegistry.register(new Fabric_1_21_3_to_1_21_4());
        shimRegistry.register(new Fabric_1_21_4_to_1_21_5());
        shimRegistry.register(new Fabric_1_21_5_to_1_21_6());
        shimRegistry.register(new Fabric_1_21_6_to_1_21_7());
        shimRegistry.register(new Fabric_1_21_7_to_1_21_8());
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        shimRegistry.register(new Fabric_1_21_9_to_1_21_10());
        shimRegistry.register(new Fabric_1_21_10_to_1_21_11());
        
        // NeoForge shims - complete 1.21 to 1.21.11 chain (step by step)
        shimRegistry.register(new NeoForge_1_21_to_1_21_1());
        shimRegistry.register(new NeoForge_1_21_1_to_1_21_2());
        shimRegistry.register(new NeoForge_1_21_2_to_1_21_3());
        shimRegistry.register(new NeoForge_1_21_3_to_1_21_4());
        shimRegistry.register(new NeoForge_1_21_4_to_1_21_5());
        shimRegistry.register(new NeoForge_1_21_5_to_1_21_6());
        shimRegistry.register(new NeoForge_1_21_6_to_1_21_7());
        shimRegistry.register(new NeoForge_1_21_7_to_1_21_8());
        shimRegistry.register(new NeoForge_1_21_8_to_1_21_9());
        shimRegistry.register(new NeoForge_1_21_9_to_1_21_10());
        shimRegistry.register(new NeoForge_1_21_10_to_1_21_11());
        
        // Forge shims - complete 1.21 to 1.21.11 chain (step by step)
        shimRegistry.register(new Forge_1_21_to_1_21_1());
        shimRegistry.register(new Forge_1_21_1_to_1_21_2());
        shimRegistry.register(new Forge_1_21_2_to_1_21_3());
        shimRegistry.register(new Forge_1_21_3_to_1_21_4());
        shimRegistry.register(new Forge_1_21_4_to_1_21_5());
        shimRegistry.register(new Forge_1_21_5_to_1_21_6());
        shimRegistry.register(new Forge_1_21_6_to_1_21_7());
        shimRegistry.register(new Forge_1_21_7_to_1_21_8());
        shimRegistry.register(new Forge_1_21_8_to_1_21_9());
        shimRegistry.register(new Forge_1_21_9_to_1_21_10());
        shimRegistry.register(new Forge_1_21_10_to_1_21_11());
        
        // Forge shims - legacy Forge to NeoForge transition
        shimRegistry.register(new Forge_1_20_to_NeoForge_1_21());
    }
    
    /**
     * Analyze a mod and display compatibility information.
     */
    private static void analyzeCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: analyze <mod.jar>");
            System.exit(1);
        }
        
        Path modPath = Path.of(args[1]);
        if (!Files.exists(modPath)) {
            System.err.println("File not found: " + modPath);
            System.exit(1);
        }
        
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║              RetroMod Mod Analysis                   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("File: " + modPath.getFileName());
        System.out.println("Size: " + Files.size(modPath) / 1024 + " KB");
        System.out.println();
        
        ModVersionInfo info = detector.detectVersion(modPath);
        
        if (info == null) {
            System.out.println("⚠  Could not detect mod metadata.");
            System.out.println("   This may not be a valid Fabric/NeoForge mod.");
            return;
        }
        
        System.out.println("┌─ Mod Information ────────────────────────────────────┐");
        System.out.printf("│ Mod ID:          %-36s │%n", info.modId());
        System.out.printf("│ Mod Version:     %-36s │%n", info.modVersion());
        System.out.printf("│ Target MC:       %-36s │%n", info.targetMcVersion());
        System.out.printf("│ Mod Loader:      %-36s │%n", info.modLoaderType());
        System.out.printf("│ Loader Version:  %-36s │%n", 
            info.modLoaderVersion() != null ? info.modLoaderVersion() : "unknown");
        System.out.println("└──────────────────────────────────────────────────────┘");
        
        System.out.println();
        System.out.println("┌─ Packages (" + info.modPackages().size() + ") ───────────────────────────────────┐");
        int pkgCount = 0;
        for (String pkg : info.modPackages()) {
            if (pkgCount++ < 10) {
                System.out.printf("│   %s%n", pkg.replace('/', '.'));
            }
        }
        if (info.modPackages().size() > 10) {
            System.out.printf("│   ... and %d more%n", info.modPackages().size() - 10);
        }
        System.out.println("└──────────────────────────────────────────────────────┘");
        
        // Compatibility analysis
        System.out.println();
        System.out.println("┌─ Compatibility Analysis ────────────────────────────┐");
        
        if (info.needsTransformation(TARGET_MC_VERSION)) {
            System.out.println("│ ⚠  TRANSFORMATION REQUIRED                          │");
            System.out.printf("│    Built for: %-10s  Current: %-10s       │%n",
                info.targetMcVersion(), TARGET_MC_VERSION);
            
            List<VersionShim> chain = shimRegistry.findShimChain(
                info.modLoaderType(),
                info.targetMcVersion(),
                TARGET_MC_VERSION
            );
            
            if (chain.isEmpty()) {
                System.out.println("│                                                      │");
                System.out.println("│ ❌ No shim chain available for this transition!     │");
                System.out.println("│    The mod cannot be automatically transformed.      │");
            } else {
                System.out.println("│                                                      │");
                System.out.println("│ ✓  Shim chain available:                            │");
                for (VersionShim shim : chain) {
                    System.out.printf("│    → %s%n", shim.getShimName());
                }
            }
        } else {
            System.out.println("│ ✓  COMPATIBLE - No transformation needed            │");
        }
        
        System.out.println("└──────────────────────────────────────────────────────┘");
        System.out.println();
    }
    
    /**
     * AOT compile a mod - the preferred transformation method.
     */
    private static void aotCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: aot <mod.jar> [--output <output.jar>]");
            System.exit(1);
        }
        
        Path modPath = Path.of(args[1]);
        Path outputPath = null;
        
        for (int i = 2; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = Path.of(args[++i]);
            }
        }
        
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║           RetroMod AOT Compilation                   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        
        AotCompiler compiler = new AotCompiler(shimRegistry, TARGET_MC_VERSION);
        
        System.out.println("Input:  " + modPath.getFileName());
        
        long startTime = System.currentTimeMillis();
        Path result = compiler.compileModAot(modPath);
        long duration = System.currentTimeMillis() - startTime;
        
        System.out.println("Output: " + result.getFileName());
        System.out.printf("Time:   %d ms%n", duration);
        System.out.println();
        
        if (result.equals(modPath)) {
            System.out.println("✓ Mod is already compatible or was skipped.");
        } else {
            System.out.println("✓ AOT compilation complete!");
            System.out.println();
            System.out.println("The compiled mod includes:");
            System.out.println("  • Pre-transformed bytecode (fast loading)");
            System.out.println("  • Embedded API shims for removed functionality");
            System.out.println("  • JIT fallback markers for obfuscated code");
        }
        System.out.println();
    }
    
    /**
     * Transform a mod using JIT-style transformation (writes to new JAR).
     */
    private static void transformCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: transform <mod.jar> [--output <output.jar>]");
            System.exit(1);
        }
        
        Path modPath = Path.of(args[1]);
        Path outputPath = modPath.resolveSibling(
            modPath.getFileName().toString().replace(".jar", "-transformed.jar")
        );
        
        for (int i = 2; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = Path.of(args[++i]);
            }
        }
        
        System.out.println("Transforming: " + modPath.getFileName());
        System.out.println("Output: " + outputPath.getFileName());
        
        ModVersionInfo info = detector.detectVersion(modPath);
        if (info == null) {
            System.err.println("Could not analyze mod.");
            System.exit(1);
        }
        
        List<VersionShim> chain = shimRegistry.findShimChain(
            info.modLoaderType(),
            info.targetMcVersion(),
            TARGET_MC_VERSION
        );
        
        if (chain.isEmpty()) {
            System.out.println("No transformation needed or no shim available.");
            return;
        }
        
        RetroModTransformer transformer = RetroModTransformer.getInstance();
        for (VersionShim shim : chain) {
            System.out.println("Applying: " + shim.getShimName());
            shim.registerRedirects(transformer);
        }
        
        transformJar(modPath, outputPath, transformer, info);
        System.out.println("✓ Transformation complete: " + outputPath);
    }
    
    /**
     * Embed removed APIs into a mod JAR.
     */
    private static void embedCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: embed <mod.jar>");
            System.exit(1);
        }
        
        Path modPath = Path.of(args[1]);
        
        System.out.println("Embedding APIs into: " + modPath.getFileName());
        
        ModVersionInfo info = detector.detectVersion(modPath);
        if (info == null) {
            System.err.println("Could not analyze mod.");
            System.exit(1);
        }
        
        ApiEmbedder embedder = new ApiEmbedder();
        embedder.embedRequiredShims(modPath, info);
        
        System.out.println("✓ API embedding complete");
    }
    
    /**
     * Process all mods in a folder.
     */
    private static void batchCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: batch <mods-folder> [--output <output-folder>] [--aot]");
            System.exit(1);
        }
        
        Path modsFolder = Path.of(args[1]);
        Path outputFolder = modsFolder.resolve("retromod-output");
        boolean useAot = false;
        
        for (int i = 2; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputFolder = Path.of(args[++i]);
            } else if ("--aot".equals(args[i])) {
                useAot = true;
            }
        }
        
        Files.createDirectories(outputFolder);
        
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║           RetroMod Batch Processing                  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();
        System.out.println("Input:  " + modsFolder);
        System.out.println("Output: " + outputFolder);
        System.out.println("Mode:   " + (useAot ? "AOT Compilation" : "JIT Transform"));
        System.out.println();
        
        File[] modFiles = modsFolder.toFile().listFiles(
            (dir, name) -> name.endsWith(".jar") && !name.contains("-aot") && !name.contains("-transformed")
        );
        
        if (modFiles == null || modFiles.length == 0) {
            System.out.println("No JAR files found.");
            return;
        }
        
        int processed = 0, skipped = 0, failed = 0;
        long totalTime = 0;
        
        AotCompiler aotCompiler = useAot ? new AotCompiler(shimRegistry, TARGET_MC_VERSION) : null;
        
        for (int i = 0; i < modFiles.length; i++) {
            File modFile = modFiles[i];
            System.out.printf("[%d/%d] %s... ", i + 1, modFiles.length, modFile.getName());
            
            try {
                long start = System.currentTimeMillis();
                
                ModVersionInfo info = detector.detectVersion(modFile.toPath());
                if (info == null) {
                    System.out.println("SKIPPED (not a mod)");
                    skipped++;
                    continue;
                }
                
                if (!info.needsTransformation(TARGET_MC_VERSION)) {
                    // Copy unchanged
                    Files.copy(modFile.toPath(), 
                        outputFolder.resolve(modFile.getName()),
                        StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("COPIED (already compatible)");
                    skipped++;
                    continue;
                }
                
                if (useAot) {
                    Path result = aotCompiler.compileModAot(modFile.toPath());
                    Files.copy(result, 
                        outputFolder.resolve(modFile.getName().replace(".jar", "-aot.jar")),
                        StandardCopyOption.REPLACE_EXISTING);
                } else {
                    // JIT transform
                    Path output = outputFolder.resolve(modFile.getName());
                    RetroModTransformer transformer = RetroModTransformer.getInstance();
                    
                    List<VersionShim> chain = shimRegistry.findShimChain(
                        info.modLoaderType(), info.targetMcVersion(), TARGET_MC_VERSION);
                    for (VersionShim shim : chain) {
                        shim.registerRedirects(transformer);
                    }
                    
                    transformJar(modFile.toPath(), output, transformer, info);
                }
                
                long elapsed = System.currentTimeMillis() - start;
                totalTime += elapsed;
                System.out.printf("OK (%d ms)%n", elapsed);
                processed++;
                
            } catch (Exception e) {
                System.out.println("FAILED: " + e.getMessage());
                failed++;
            }
        }
        
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════");
        System.out.printf("Summary: %d processed, %d skipped, %d failed%n", processed, skipped, failed);
        System.out.printf("Total time: %d ms (avg: %d ms/mod)%n", 
            totalTime, processed > 0 ? totalTime / processed : 0);
    }
    
    /**
     * Show API differences between two versions.
     */
    private static void diffCommand(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("Usage: diff <loader> <version1> <version2>");
            System.err.println("Example: diff fabric 1.21.8 1.21.9");
            System.exit(1);
        }
        
        String loader = args[1];
        String v1 = args[2];
        String v2 = args.length > 3 ? args[3] : TARGET_MC_VERSION;
        
        System.out.println();
        System.out.println("API Differences: " + loader + " " + v1 + " → " + v2);
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println();
        
        // Show shim chain
        List<VersionShim> chain = shimRegistry.findShimChain(loader, v1, v2);
        
        if (chain.isEmpty()) {
            System.out.println("No shim data available for this transition.");
            return;
        }
        
        for (VersionShim shim : chain) {
            System.out.println("┌─ " + shim.getShimName() + " ─────────────────────────────");
            
            RetroModTransformer temp = RetroModTransformer.getInstance();
            shim.registerRedirects(temp);
            
            System.out.println("│ Method redirects: " + temp.getMethodRedirectCount());
            System.out.println("│ Class redirects:  " + temp.getClassRedirectCount());
            
            String[] shimClasses = shim.getShimClasses();
            if (shimClasses.length > 0) {
                System.out.println("│ Embedded shims:");
                for (String cls : shimClasses) {
                    System.out.println("│   • " + cls);
                }
            }
            System.out.println("└──────────────────────────────────────────────────────");
            System.out.println();
        }
    }
    
    /**
     * Manage API archives.
     */
    private static void archiveCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: archive <action> [options]");
            System.err.println("Actions:");
            System.err.println("  download <loader> <version>  - Download an API archive");
            System.err.println("  list                         - List cached archives");
            System.err.println("  preload                      - Download all known archives");
            System.err.println("  clear                        - Clear archive cache");
            System.exit(1);
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "download" -> {
                if (args.length < 4) {
                    System.err.println("Usage: archive download <loader> <version>");
                    System.exit(1);
                }
                String loader = args[2];
                String version = args[3];
                System.out.println("Downloading " + loader + " API for MC " + version + "...");
                archiveManager.loadArchive(loader, version);
                System.out.println("✓ Download complete");
            }
            case "list" -> {
                System.out.println("Cached archives:");
                var stats = archiveManager.getCacheStats();
                if (stats.isEmpty()) {
                    System.out.println("  (none)");
                } else {
                    for (var entry : stats.entrySet()) {
                        System.out.printf("  %s: %d classes%n", entry.getKey(), entry.getValue());
                    }
                }
            }
            case "preload" -> {
                System.out.println("Downloading all known API archives...");
                archiveManager.preloadAllArchives().join();
                System.out.println("✓ Preload complete");
            }
            case "clear" -> {
                archiveManager.clearCache();
                System.out.println("✓ Cache cleared");
            }
            default -> System.err.println("Unknown action: " + action);
        }
    }
    
    /**
     * List all registered shims.
     */
    private static void shimsCommand(String[] args) {
        System.out.println();
        System.out.println("Registered Version Shims");
        System.out.println("════════════════════════════════════════════════════════");
        System.out.println();
        
        List<VersionShim> allShims = shimRegistry.getAllShims();
        
        String currentLoader = "";
        for (VersionShim shim : allShims) {
            if (!shim.getModLoaderType().equals(currentLoader)) {
                currentLoader = shim.getModLoaderType();
                System.out.println("┌─ " + currentLoader.toUpperCase() + " ────────────────────────────────────────");
            }
            System.out.printf("│  %s → %s  (%s)%n",
                shim.getSourceVersion(),
                shim.getTargetVersion(),
                shim.getShimName());
        }
        System.out.println("└──────────────────────────────────────────────────────");
        System.out.println();
        System.out.println("Total: " + allShims.size() + " shims registered");
    }
    
    /**
     * Transform a JAR file using the configured transformer.
     */
    private static void transformJar(Path input, Path output, 
            RetroModTransformer transformer, ModVersionInfo info) throws Exception {
        
        try (var inJar = new java.util.jar.JarFile(input.toFile());
             var outJar = new java.util.jar.JarOutputStream(
                     new FileOutputStream(output.toFile()))) {
            
            var entries = inJar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                outJar.putNextEntry(new java.util.jar.JarEntry(entry.getName()));
                
                if (!entry.isDirectory()) {
                    try (var is = inJar.getInputStream(entry)) {
                        byte[] data = is.readAllBytes();
                        
                        if (entry.getName().endsWith(".class") && 
                                shouldTransformClass(entry.getName(), info)) {
                            data = transformer.transformClass(data, entry.getName());
                        }
                        
                        outJar.write(data);
                    }
                }
                
                outJar.closeEntry();
            }
        }
    }
    

    private static boolean shouldTransformClass(String entryName, ModVersionInfo info) {
        String pkg = entryName.substring(0, Math.max(0, entryName.lastIndexOf('/') + 1));
        return info.modPackages().contains(pkg);
    }
    
    /**
     * Transform a legacy mod (1.8-1.20.x) to run on modern 1.21.x.
     */
    private static void legacyCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: legacy <mod.jar> [--output <output.jar>]");
            System.exit(1);
        }
        
        Path modPath = Path.of(args[1]);
        Path outputPath = null;
        
        for (int i = 2; i < args.length; i++) {
            if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = Path.of(args[++i]);
            }
        }
        
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("         RetroMod LEGACY Transformation");
        System.out.println("   Transform mods from MC 1.8+ to run on 1.21.x");
        System.out.println("=================================================================");
        System.out.println();
        
        // Create legacy support instance
        LegacyModSupport legacySupport = new LegacyModSupport(
            modPath.getParent(), TARGET_MC_VERSION
        );
        
        // Analyze the mod
        System.out.println("Analyzing mod: " + modPath.getFileName());
        LegacyModAnalysis analysis = legacySupport.analyzeMod(modPath);
        
        System.out.println();
        System.out.println("Analysis Results:");
        System.out.println("  Mod Loader:      " + analysis.modLoader);
        System.out.println("  Target MC:       " + analysis.targetMcVersion);
        System.out.println("  Source Epoch:    " + analysis.sourceEpoch.name);
        System.out.println("  Java Version:    " + analysis.sourceJavaVersion);
        System.out.println("  Class File Ver:  " + analysis.classFileVersion);
        System.out.println("  Complexity:      " + analysis.complexity);
        System.out.println("  Virtual Loader:  " + (analysis.needsVirtualLoader ? "Required" : "Not needed"));
        System.out.println("  Transformations: " + analysis.epochTransitions.size());
        System.out.println();
        
        if (analysis.epochTransitions.isEmpty()) {
            System.out.println("Mod appears to be compatible - no transformation needed.");
            return;
        }
        
        // Show transformation plan
        System.out.println("Transformation Plan:");
        for (EpochTransition t : analysis.epochTransitions) {
            System.out.println("  -> " + t.name());
        }
        System.out.println();
        
        // Transform
        Path result = legacySupport.transformMod(modPath, analysis);
        
        if (outputPath != null && !result.equals(outputPath)) {
            Files.move(result, outputPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            result = outputPath;
        }
        
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("                    Transformation Complete");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Output: " + result);
        System.out.println();
        System.out.println("Notes:");
        System.out.println("  - The transformed mod may require additional testing");
        System.out.println("  - Some features may not work if APIs were removed entirely");
        System.out.println("  - Check the log output above for any warnings");
        System.out.println();
    }
    
    // =========================================================================
    // DEPENDENCY OVERRIDES COMMAND
    // =========================================================================
    
    /**
     * Generate Fabric dependency overrides to bypass version checks.
     * This is CRITICAL - Fabric blocks mods before RetroMod can transform them.
     */
    private static void overridesCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: retromod overrides <minecraft-dir> [target-version]");
            System.err.println("  minecraft-dir: Path to .minecraft folder");
            System.err.println("  target-version: Target MC version (default: " + TARGET_MC_VERSION + ")");
            return;
        }
        
        Path minecraftDir = Paths.get(args[1]);
        String targetVersion = args.length > 2 ? args[2] : TARGET_MC_VERSION;
        
        if (!Files.isDirectory(minecraftDir)) {
            System.err.println("Error: Not a valid directory: " + minecraftDir);
            return;
        }
        
        Path modsDir = minecraftDir.resolve("mods");
        Path configDir = minecraftDir.resolve("config");
        
        if (!Files.isDirectory(modsDir)) {
            System.err.println("Error: No mods folder found in: " + minecraftDir);
            return;
        }
        
        Files.createDirectories(configDir);
        
        System.out.println("=================================================================");
        System.out.println("       RetroMod Dependency Override Generator");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Scanning mods folder: " + modsDir);
        System.out.println("Target Minecraft version: " + targetVersion);
        System.out.println();
        
        // Collect all mod IDs that need overrides
        Set<String> modIds = new java.util.HashSet<>();
        
        try (var stream = Files.list(modsDir)) {
            for (Path jar : stream.filter(p -> p.toString().endsWith(".jar")).toList()) {
                try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jar.toFile())) {
                    // Check fabric.mod.json
                    var fabricEntry = jarFile.getEntry("fabric.mod.json");
                    if (fabricEntry != null) {
                        try (var is = jarFile.getInputStream(fabricEntry)) {
                            String json = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                            // Simple JSON parsing for mod ID
                            int idStart = json.indexOf("\"id\"");
                            if (idStart > 0) {
                                int colonPos = json.indexOf(":", idStart);
                                int quoteStart = json.indexOf("\"", colonPos + 1);
                                int quoteEnd = json.indexOf("\"", quoteStart + 1);
                                if (quoteStart > 0 && quoteEnd > quoteStart) {
                                    String modId = json.substring(quoteStart + 1, quoteEnd);
                                    modIds.add(modId);
                                    System.out.println("  Found mod: " + modId + " (" + jar.getFileName() + ")");
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("  Warning: Could not read " + jar.getFileName() + ": " + e.getMessage());
                }
            }
        }
        
        if (modIds.isEmpty()) {
            System.out.println("No Fabric mods found that need overrides.");
            return;
        }
        
        // Generate fabric_loader_dependencies.json
        Path overridesFile = configDir.resolve("fabric_loader_dependencies.json");
        
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": 1,\n");
        json.append("  \"overrides\": {\n");
        
        boolean first = true;
        for (String modId : modIds) {
            if (!first) json.append(",\n");
            first = false;
            
            json.append("    \"").append(modId).append("\": {\n");
            json.append("      \"-depends\": {\n");
            json.append("        \"minecraft\": \"*\",\n");
            json.append("        \"fabricloader\": \"*\"\n");
            json.append("      },\n");
            json.append("      \"+depends\": {\n");
            json.append("        \"minecraft\": \">=1.14\",\n");
            json.append("        \"fabricloader\": \">=0.14.0\"\n");
            json.append("      }\n");
            json.append("    }");
        }
        
        json.append("\n  }\n");
        json.append("}\n");
        
        Files.writeString(overridesFile, json.toString());
        
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("Generated dependency overrides for " + modIds.size() + " mods");
        System.out.println("File: " + overridesFile);
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("IMPORTANT:");
        System.out.println("  This bypasses Fabric's version check so mods can LOAD.");
        System.out.println("  However, mods may still CRASH if APIs are incompatible.");
        System.out.println("  Use 'retromod prepare' to also transform the mods.");
        System.out.println();
    }
    
    /**
     * Full preparation: generate overrides + transform all mods.
     */
    private static void prepareCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: retromod prepare <minecraft-dir> [--aot]");
            System.err.println("  minecraft-dir: Path to .minecraft folder");
            System.err.println("  --aot: Use AOT compilation (recommended)");
            return;
        }
        
        Path minecraftDir = Paths.get(args[1]);
        boolean useAot = Arrays.asList(args).contains("--aot");
        
        System.out.println("=================================================================");
        System.out.println("         RetroMod Full Preparation");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Minecraft directory: " + minecraftDir);
        System.out.println("Mode: " + (useAot ? "AOT Compilation" : "JIT Transform"));
        System.out.println();
        
        // Step 1: Generate overrides
        System.out.println("Step 1: Generating dependency overrides...");
        overridesCommand(new String[]{"overrides", args[1], TARGET_MC_VERSION});
        
        // Step 2: Transform mods
        System.out.println("Step 2: Transforming mods...");
        Path modsDir = minecraftDir.resolve("mods");
        
        if (useAot) {
            batchCommand(new String[]{"batch", modsDir.toString(), "--aot"});
        } else {
            batchCommand(new String[]{"batch", modsDir.toString()});
        }
        
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("                    Preparation Complete");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Your mods folder has been prepared for " + TARGET_MC_VERSION);
        System.out.println();
        System.out.println("Note: Some mods may still have runtime issues if they use");
        System.out.println("      APIs that have changed significantly.");
        System.out.println();
    }
    
    private static void printUsage() {
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("           RetroMod CLI v" + VERSION);
        System.out.println("   Backwards Compatibility Layer for Minecraft Mods");
        System.out.println("");
        System.out.println("   Supports: Fabric, Forge, NeoForge");
        System.out.println("   Versions: 1.8 -> 1.21.11");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Usage: java -jar retromod-cli.jar <command> [options]");
        System.out.println();
        System.out.println("Modern Commands (1.21.x):");
        System.out.println("  analyze <mod.jar>              Analyze a mod's compatibility");
        System.out.println("  aot <mod.jar>                  AOT compile (recommended)");
        System.out.println("  transform <mod.jar>            JIT-style transformation");
        System.out.println("  embed <mod.jar>                Embed removed APIs only");
        System.out.println("  batch <folder> [--aot]         Process all mods in folder");
        System.out.println();
        System.out.println("Preparation Commands (IMPORTANT for cross-version):");
        System.out.println("  prepare <mc-dir> [--aot]       Full prep: overrides + transform");
        System.out.println("  overrides <mc-dir>             Generate dependency overrides only");
        System.out.println();
        System.out.println("Legacy Commands (1.8-1.20.x -> 1.21.x):");
        System.out.println("  legacy <mod.jar>               Transform legacy mod to 1.21.x");
        System.out.println();
        System.out.println("Utility Commands:");
        System.out.println("  diff <loader> <v1> <v2>        Show API differences");
        System.out.println("  archive <action>               Manage API archives");
        System.out.println("  shims                          List registered shims");
        System.out.println("  help                           Show this help");
        System.out.println("  version                        Show version");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --output <path>                Specify output file/folder");
        System.out.println("  --aot                          Use AOT compilation in batch");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  # RECOMMENDED: Full preparation for running old mods");
        System.out.println("  retromod prepare ~/.minecraft --aot");
        System.out.println();
        System.out.println("  # Transform a 1.21.8 Fabric mod to 1.21.11");
        System.out.println("  retromod aot mymod-1.21.8.jar");
        System.out.println();
        System.out.println("  # Transform a legacy 1.12.2 Forge mod to 1.21.11");
        System.out.println("  retromod legacy oldmod-1.12.2.jar");
        System.out.println();
        System.out.println("  # Batch process all mods");
        System.out.println("  retromod batch ./mods --aot");
        System.out.println();
        System.out.println("Target Minecraft version: " + TARGET_MC_VERSION);
        System.out.println();
    }
}
