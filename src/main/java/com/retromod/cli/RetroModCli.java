/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.cli;

import com.retromod.core.*;
import com.retromod.embedder.*;
import com.retromod.aot.AotCompiler;
import com.retromod.archive.ApiArchiveManager;
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
 *   autofix <log-file> [--apply] - Analyze crash log, suggest/apply fixes
 *   diff <v1> <v2>              - Show API differences between versions
 *   archive <action>            - Manage API archives
 *   shims                       - List all registered shims
 */
public class RetroModCli {
    
    private static final String VERSION = "1.0.0-beta.1";
    private static final String TARGET_MC_VERSION = "26.1";
    
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
                case "score" -> scoreCommand(args);
                case "devhelp", "migrate" -> devhelpCommand(args);
                case "autofix" -> autofixCommand(args);
                case "gaps" -> gapsCommand(args);
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
        // Fabric shims - complete 1.14.4 to 26.1 chain
        shimRegistry.register(new Fabric_1_14_4_to_1_15_2());
        shimRegistry.register(new Fabric_1_15_2_to_1_16_5());
        shimRegistry.register(new Fabric_1_16_5_to_1_17());
        shimRegistry.register(new Fabric_1_17_to_1_17_1());
        shimRegistry.register(new Fabric_1_17_1_to_1_18());
        shimRegistry.register(new Fabric_1_18_to_1_18_1());
        shimRegistry.register(new Fabric_1_18_1_to_1_18_2());
        shimRegistry.register(new Fabric_1_18_2_to_1_19());
        shimRegistry.register(new Fabric_1_19_to_1_19_1());
        shimRegistry.register(new Fabric_1_19_1_to_1_19_2());
        shimRegistry.register(new Fabric_1_19_2_to_1_19_3());
        shimRegistry.register(new Fabric_1_19_3_to_1_19_4());
        shimRegistry.register(new Fabric_1_19_4_to_1_20());
        shimRegistry.register(new Fabric_1_20_to_1_20_1());
        shimRegistry.register(new Fabric_1_20_1_to_1_20_2());
        shimRegistry.register(new Fabric_1_20_2_to_1_20_3());
        shimRegistry.register(new Fabric_1_20_3_to_1_20_4());
        shimRegistry.register(new Fabric_1_20_4_to_1_20_5());
        shimRegistry.register(new Fabric_1_20_5_to_1_20_6());
        shimRegistry.register(new Fabric_1_20_6_to_1_21());
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
        shimRegistry.register(new Fabric_1_21_11_to_26_1());

        // NeoForge shims - complete 1.21 to 26.1 chain (step by step)
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
        
        // Forge shims - complete 1.21 to 26.1 chain (step by step)
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

        // Complexity analysis - warn before transforming
        System.out.println();
        System.out.println("┌─ Complexity Analysis ───────────────────────────────┐");

        com.retromod.gui.ModComplexityAnalyzer complexityAnalyzer =
            new com.retromod.gui.ModComplexityAnalyzer();
        com.retromod.gui.ModComplexityAnalyzer.ComplexityReport complexityReport =
            complexityAnalyzer.analyze(modPath);

        System.out.printf("│ Complexity Score: %-36d │%n", complexityReport.score());

        if (complexityReport.isUnlikelyToWork()) {
            System.out.println("│                                                      │");
            System.out.println("│ ⚠  WARNING: This mod is UNLIKELY TO WORK            │");
            System.out.println("│    after transformation. Risk factors:                │");
            for (String factor : complexityReport.riskFactors()) {
                String truncated = factor.length() > 50 ? factor.substring(0, 47) + "..." : factor;
                System.out.printf("│    • %-50s │%n", truncated);
            }
            System.out.println("│                                                      │");
            System.out.println("│    You can still try with: aot --force <mod.jar>     │");
        } else {
            System.out.println("│ ✓  Mod appears suitable for transformation          │");
            if (!complexityReport.riskFactors().isEmpty()) {
                System.out.println("│    Minor risk factors:                                │");
                for (String factor : complexityReport.riskFactors()) {
                    String truncated = factor.length() > 50 ? factor.substring(0, 47) + "..." : factor;
                    System.out.printf("│    • %-50s │%n", truncated);
                }
            }
        }

        System.out.println("└──────────────────────────────────────────────────────┘");
        System.out.println();
    }

    /**
     * AOT compile a mod - the preferred transformation method.
     */
    private static void aotCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: aot [--force] <mod.jar> [--output <output.jar>]");
            System.exit(1);
        }

        boolean forceTranslate = false;
        Path modPath = null;
        Path outputPath = null;

        for (int i = 1; i < args.length; i++) {
            if ("--force".equals(args[i])) {
                forceTranslate = true;
            } else if ("--output".equals(args[i]) && i + 1 < args.length) {
                outputPath = Path.of(args[++i]);
            } else if (modPath == null) {
                modPath = Path.of(args[i]);
            }
        }

        if (modPath == null) {
            System.err.println("Usage: aot [--force] <mod.jar> [--output <output.jar>]");
            System.exit(1);
        }

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║           RetroMod AOT Compilation                   ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.println();

        // Complexity check BEFORE transforming
        com.retromod.gui.ModComplexityAnalyzer complexityAnalyzer =
            new com.retromod.gui.ModComplexityAnalyzer();
        com.retromod.gui.ModComplexityAnalyzer.ComplexityReport complexityReport =
            complexityAnalyzer.analyze(modPath);

        if (complexityReport.isUnlikelyToWork() && !forceTranslate) {
            System.out.println("⚠  WARNING: This mod is UNLIKELY TO WORK after transformation.");
            System.out.println();
            System.out.println("Risk factors:");
            for (String factor : complexityReport.riskFactors()) {
                System.out.println("  • " + factor);
            }
            System.out.println();
            System.out.println("Complexity score: " + complexityReport.score() + "/100");
            System.out.println();
            System.out.println("The mod uses features that RetroMod cannot fully transform.");
            System.out.println("It will likely crash or behave incorrectly.");
            System.out.println();
            System.out.println("To try anyway, use:  aot --force " + modPath.getFileName());
            System.out.println();
            return;
        }

        if (complexityReport.isUnlikelyToWork() && forceTranslate) {
            System.out.println("⚠  Force mode: proceeding despite high complexity score ("
                + complexityReport.score() + ")");
            System.out.println();
        }

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

        String sourceMcVersion = info.targetMcVersion();
        if (sourceMcVersion == null || sourceMcVersion.isEmpty()) {
            System.err.println("Warning: Could not determine source MC version. Trying all shims...");
            // Apply all shims as a best-effort transformation
            RetroModTransformer transformer = RetroModTransformer.getInstance();
            for (VersionShim shim : shimRegistry.getAllShims()) {
                shim.registerRedirects(transformer);
            }
            transformJar(modPath, outputPath, transformer, info);
            System.out.println("✓ Transformation complete (all shims applied): " + outputPath);
            verifyIfRequested(outputPath, modPath.getFileName().toString(), args);
            return;
        }

        List<VersionShim> chain = shimRegistry.findShimChain(
            info.modLoaderType(),
            sourceMcVersion,
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
        verifyIfRequested(outputPath, modPath.getFileName().toString(), args);
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
                
                // For 26.1+, ALL mods get metadata patching regardless
                // of whether bytecode transformation is needed
                boolean needs26Patch = TARGET_MC_VERSION.startsWith("26.");
                boolean needsBytecodeTransform = info.needsTransformation(TARGET_MC_VERSION);

                Path outputPath = outputFolder.resolve(modFile.getName());
                String status;

                if (needsBytecodeTransform) {
                    if (useAot) {
                        Path result = aotCompiler.compileModAot(modFile.toPath());
                        // Copy AOT result; we'll post-process for metadata
                        outputPath = outputFolder.resolve(
                            modFile.getName().replace(".jar", "-aot.jar"));
                        Files.copy(result, outputPath,
                            StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        // JIT transform
                        RetroModTransformer transformer = RetroModTransformer.getInstance();
                        List<VersionShim> chain = shimRegistry.findShimChain(
                            info.modLoaderType(), info.targetMcVersion(), TARGET_MC_VERSION);
                        for (VersionShim shim : chain) {
                            shim.registerRedirects(transformer);
                        }
                        transformJar(modFile.toPath(), outputPath, transformer, info);
                    }
                    status = "OK";
                } else if (!needs26Patch) {
                    // No transformation or patching needed
                    Files.copy(modFile.toPath(), outputPath,
                        StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("COPIED (already compatible)");
                    skipped++;
                    continue;
                } else {
                    // Copy first, then patch metadata
                    Files.copy(modFile.toPath(), outputPath,
                        StandardCopyOption.REPLACE_EXISTING);
                    status = "PATCHED (version constraints)";
                }

                // Post-process: patch mod metadata for 26.1+ compatibility
                if (needs26Patch) {
                    patchModMetadata(outputPath);
                }

                long elapsed = System.currentTimeMillis() - start;
                totalTime += elapsed;
                System.out.printf("%s (%d ms)%n", status, elapsed);
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
                    System.err.println("Usage: archive download <loader> <version> [--yes]");
                    System.exit(1);
                }
                String loader = args[2];
                String version = args[3];
                boolean autoYes = args.length > 4 && "--yes".equalsIgnoreCase(args[4]);

                // Explicit user-consent download — see ApiArchiveManager
                // Javadoc for the network-policy rationale. The CLI is
                // the only entry point allowed to drive this; we prompt
                // before any network traffic so the user knows exactly
                // what's being fetched and from where.
                boolean downloaded = archiveManager.downloadArchiveWithUserConsent(
                    loader, version,
                    () -> promptForDownloadConsent(loader, version, autoYes));
                if (downloaded) {
                    System.out.println("✓ Download complete");
                } else {
                    System.out.println("Skipped — no download performed.");
                }
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
                boolean autoYes = args.length > 2 && "--yes".equalsIgnoreCase(args[2]);
                archiveManager.preloadAllArchives(() -> promptForPreloadConsent(autoYes)).join();
                System.out.println("✓ Preload complete (or skipped on user decline)");
            }
            case "clear" -> {
                archiveManager.clearCache();
                System.out.println("✓ Cache cleared");
            }
            default -> System.err.println("Unknown action: " + action);
        }
    }

    /**
     * Interactive consent prompt for a single archive download.
     * Returns true if the user types y / yes. Pass --yes on the command
     * line to skip the prompt (intended for scripted / CI usage where
     * the user has already consented at the script-invocation level).
     */
    private static boolean promptForDownloadConsent(String loader, String version, boolean autoYes) {
        System.out.println();
        System.out.println("────────────────────────────────────────────────────");
        System.out.println("  RetroMod is about to download a file from the");
        System.out.println("  internet. This is the only network operation");
        System.out.println("  RetroMod will perform; it does not initiate any");
        System.out.println("  other downloads without explicit consent.");
        System.out.println();
        System.out.println("  Loader:  " + loader);
        System.out.println("  MC ver:  " + version);
        System.out.println("  Source:  Maven (fabricmc.net / neoforged.net /");
        System.out.println("           minecraftforge.net depending on loader)");
        System.out.println("────────────────────────────────────────────────────");

        if (autoYes) {
            System.out.println("  --yes flag passed: proceeding without prompt.");
            return true;
        }

        System.out.print("Proceed with download? [y/N] ");
        try {
            String line = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in)).readLine();
            return line != null && (line.equalsIgnoreCase("y") || line.equalsIgnoreCase("yes"));
        } catch (java.io.IOException e) {
            System.err.println("Could not read response — treating as 'no'.");
            return false;
        }
    }

    /**
     * Interactive consent prompt for the bulk preload action. Same shape
     * as the single-download prompt, but lists how many archives will be
     * fetched so the user can decline a long-running batch.
     */
    private static boolean promptForPreloadConsent(boolean autoYes) {
        System.out.println();
        System.out.println("────────────────────────────────────────────────────");
        System.out.println("  RetroMod is about to bulk-download API archives");
        System.out.println("  for every known MC version (~22 JARs total,");
        System.out.println("  several MB each). All come from official Maven");
        System.out.println("  repositories (fabricmc.net / neoforged.net).");
        System.out.println("────────────────────────────────────────────────────");

        if (autoYes) {
            System.out.println("  --yes flag passed: proceeding without prompt.");
            return true;
        }

        System.out.print("Proceed with preload? [y/N] ");
        try {
            String line = new java.io.BufferedReader(
                new java.io.InputStreamReader(System.in)).readLine();
            return line != null && (line.equalsIgnoreCase("y") || line.equalsIgnoreCase("yes"));
        } catch (java.io.IOException e) {
            System.err.println("Could not read response — treating as 'no'.");
            return false;
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
                // Re-validate entry names on the output side. safeEntryName
                // throws on path-traversal patterns (../, absolute paths, etc.)
                // so a malicious input JAR can't propagate a bad name into
                // our freshly-written output JAR.
                outJar.putNextEntry(new java.util.jar.JarEntry(
                        com.retromod.util.ZipSecurity.safeEntryName(entry.getName())));

                if (!entry.isDirectory()) {
                    try (var is = inJar.getInputStream(entry)) {
                        // Bounded read — defends against a mod JAR with a
                        // falsified-size entry that would otherwise allocate
                        // gigabytes when decompressed.
                        byte[] data = com.retromod.util.ZipSecurity.safeReadAllBytes(is);

                        if (entry.getName().endsWith(".class") &&
                                shouldTransformClass(entry.getName(), info)) {
                            data = transformer.transformClass(data, entry.getName());
                        } else if (entry.getName().equals("fabric.mod.json")) {
                            // Relax version dependencies for compatibility
                            data = relaxFabricModDependencies(data);
                        } else if (entry.getName().equals("quilt.mod.json")) {
                            // Relax Quilt version dependencies too
                            data = relaxFabricModDependencies(data);
                        } else if (entry.getName().equals("META-INF/mods.toml") ||
                                   entry.getName().equals("META-INF/neoforge.mods.toml")) {
                            // Relax NeoForge/Forge version dependencies
                            data = relaxNeoForgeDependencies(data);
                        } else if (entry.getName().endsWith(".mixins.json") ||
                                   entry.getName().endsWith("mixin.json") ||
                                   (entry.getName().contains("mixin") && entry.getName().endsWith(".json"))) {
                            // Make mixin configs non-fatal so @Accessor/@Invoker
                            // on removed fields don't crash the game
                            data = makeMixinConfigNonFatal(data);
                        } else if (entry.getName().startsWith("META-INF/jars/") &&
                                   entry.getName().endsWith(".jar")) {
                            // Process JiJ (Jar-in-Jar) nested JARs — patch their
                            // mixin configs and fabric.mod.json too
                            data = transformNestedJar(data);
                        }

                        outJar.write(data);
                    }
                }

                outJar.closeEntry();
            }
        }
    }

    /**
     * Run verification if --verify flag is present or verify_transforms config is on.
     */
    private static void verifyIfRequested(Path outputJar, String modName, String[] args) {
        boolean verify = TransformVerifier.isEnabled();
        for (String arg : args) {
            if ("--verify".equals(arg)) { verify = true; break; }
        }
        if (!verify) return;

        var result = TransformVerifier.verifyAndReport(outputJar, modName, TARGET_MC_VERSION);
        if (result.passed()) {
            System.out.println("✓ Verification passed");
        } else {
            System.out.println("✗ Verification found " + result.issueCount() + " issue(s)");
            for (var issue : result.issues()) {
                System.out.println("  - " + issue.toReadableString(TARGET_MC_VERSION));
            }
        }
    }

    /**
     * Relax version constraints in fabric.mod.json so the mod can load on the target MC version.
     */
    private static byte[] relaxFabricModDependencies(byte[] jsonData) {
        try {
            String json = new String(jsonData, java.nio.charset.StandardCharsets.UTF_8);

            // Parse and modify the JSON
            // Simple but effective: replace minecraft version constraint with "*"
            // and relax fabricloader to a minimum version

            // Use a simple JSON approach since we don't have a JSON library
            // Replace "minecraft" dependency value
            json = json.replaceAll(
                "(\"minecraft\"\\s*:\\s*)(?:\"[^\"]*\"|\\[[^\\]]*\\]|\\{[^}]*\\})",
                "$1\"*\""
            );

            // Relax fabricloader constraint to be permissive
            json = json.replaceAll(
                "(\"fabricloader\"\\s*:\\s*)\"[^\"]*\"",
                "$1\">=0.14.0\""
            );

            // Relax fabric-api submodule constraints
            json = json.replaceAll(
                "(\"fabric-[a-z-]+(?:-v[0-9]+)?\"\\s*:\\s*)\"(?:>=)?[0-9][^\"]*\"",
                "$1\"*\""
            );

            return json.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            // If anything goes wrong, return the original data
            return jsonData;
        }
    }

    /**
     * Make a mixin config JSON non-fatal by setting "required": false and
     * "injectors": {"defaultRequire": 0}. This prevents crashes from @Accessor/@Invoker
     * targeting fields/methods removed in newer MC versions.
     */
    private static byte[] makeMixinConfigNonFatal(byte[] jsonData) {
        try {
            String json = new String(jsonData, java.nio.charset.StandardCharsets.UTF_8);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            // Only process actual mixin configs (must have "package" field)
            if (!root.has("package")) return jsonData;

            root.addProperty("required", false);

            com.google.gson.JsonObject injectors = root.has("injectors") && root.get("injectors").isJsonObject()
                ? root.getAsJsonObject("injectors")
                : new com.google.gson.JsonObject();
            injectors.addProperty("defaultRequire", 0);
            root.add("injectors", injectors);

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
            return gson.toJson(root).getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return jsonData;
        }
    }

    /**
     * Transform a nested JAR (Jar-in-Jar / JiJ) by processing its mixin configs
     * and fabric.mod.json. This handles bundled Fabric API modules that contain
     * @Accessor/@Invoker targeting removed fields.
     */
    private static byte[] transformNestedJar(byte[] jarData) {
        try {
            var bais = new java.io.ByteArrayInputStream(jarData);
            var baos = new java.io.ByteArrayOutputStream(jarData.length);
            boolean modified = false;

            try (var jis = new java.util.jar.JarInputStream(bais);
                 var jos = new java.util.jar.JarOutputStream(baos)) {

                java.util.jar.JarEntry entry;
                while ((entry = jis.getNextJarEntry()) != null) {
                    jos.putNextEntry(new java.util.jar.JarEntry(
                            com.retromod.util.ZipSecurity.safeEntryName(entry.getName())));

                    if (!entry.isDirectory()) {
                        byte[] data = com.retromod.util.ZipSecurity.safeReadAllBytes(jis);

                        if (entry.getName().endsWith(".mixins.json") ||
                            entry.getName().endsWith("mixin.json") ||
                            (entry.getName().contains("mixin") && entry.getName().endsWith(".json"))) {
                            byte[] patched = makeMixinConfigNonFatal(data);
                            if (patched != data) modified = true;
                            data = patched;
                        } else if (entry.getName().equals("fabric.mod.json")) {
                            data = relaxFabricModDependencies(data);
                            modified = true;
                        }

                        jos.write(data);
                    }

                    jos.closeEntry();
                }
            }

            return modified ? baos.toByteArray() : jarData;
        } catch (Exception e) {
            return jarData; // Return original on any error
        }
    }

    /**
     * Relax version constraints in mods.toml / neoforge.mods.toml so the mod
     * can load on 26.1+. Patches minecraft, neoforge, and forge version ranges
     * to be permissive, and makes non-core dependencies optional.
     */
    private static byte[] relaxNeoForgeDependencies(byte[] tomlData) {
        try {
            String toml = new String(tomlData, java.nio.charset.StandardCharsets.UTF_8);

            // Split by [[dependencies. headers and process each block
            StringBuilder result = new StringBuilder();
            String[] blocks = toml.split("(?=\\[\\[dependencies\\.)");

            for (String block : blocks) {
                if (!block.contains("modId") && !block.contains("modId")) {
                    // Preamble or non-dependency block - keep as-is
                    result.append(block);
                    continue;
                }

                boolean isMinecraft = block.contains("\"minecraft\"");
                boolean isNeoForge = block.contains("\"neoforge\"");
                boolean isForge = block.contains("\"forge\"");
                boolean isCoreDependent = isMinecraft || isNeoForge || isForge;

                // Patch version ranges for all dependencies
                // Handle Maven range format: [1.21,1.21.1) or [1.21.8,1.22)
                block = block.replaceAll(
                    "(versionRange\\s*=\\s*\")\\[([^,\"]+),[^\"]*\"",
                    "$1[$2,)\""
                );

                // Handle bare version format: "1.21.8" (no brackets)
                block = block.replaceAll(
                    "(versionRange\\s*=\\s*\")([0-9][^\"\\[\\]]*)\"",
                    "$1[$2,)\""
                );

                // Make non-core dependencies optional
                if (!isCoreDependent) {
                    // Change type="required" to type="optional"
                    block = block.replaceAll(
                        "(type\\s*=\\s*\")required\"",
                        "$1optional\""
                    );
                    // Also handle old mandatory=true format
                    block = block.replaceAll(
                        "(mandatory\\s*=\\s*)true",
                        "$1false"
                    );
                }

                result.append(block);
            }

            return result.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            return tomlData;
        }
    }

    /**
     * Post-process a JAR to patch mod metadata (version constraints) in-place.
     * Rewrites fabric.mod.json, quilt.mod.json, mods.toml, neoforge.mods.toml
     * to relax version ranges for 26.1+ compatibility.
     */
    private static void patchModMetadata(Path jarPath) throws Exception {
        Path tempJar = jarPath.resolveSibling(jarPath.getFileName() + ".tmp");

        try (var inJar = new java.util.jar.JarFile(jarPath.toFile());
             var outJar = new java.util.jar.JarOutputStream(
                     new FileOutputStream(tempJar.toFile()))) {

            var entries = inJar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                // Re-validate entry names on the output side. safeEntryName
                // throws on path-traversal patterns (../, absolute paths, etc.)
                // so a malicious input JAR can't propagate a bad name into
                // our freshly-written output JAR.
                outJar.putNextEntry(new java.util.jar.JarEntry(
                        com.retromod.util.ZipSecurity.safeEntryName(entry.getName())));

                if (!entry.isDirectory()) {
                    try (var is = inJar.getInputStream(entry)) {
                        // Bounded read — defends against a mod JAR with a
                        // falsified-size entry that would otherwise allocate
                        // gigabytes when decompressed.
                        byte[] data = com.retromod.util.ZipSecurity.safeReadAllBytes(is);

                        if (entry.getName().equals("fabric.mod.json") ||
                                entry.getName().equals("quilt.mod.json")) {
                            data = relaxFabricModDependencies(data);
                        } else if (entry.getName().equals("META-INF/mods.toml") ||
                                   entry.getName().equals("META-INF/neoforge.mods.toml")) {
                            data = relaxNeoForgeDependencies(data);
                        }

                        outJar.write(data);
                    }
                }

                outJar.closeEntry();
            }
        }

        // Replace original with patched version
        Files.move(tempJar, jarPath, StandardCopyOption.REPLACE_EXISTING);
    }

    private static boolean shouldTransformClass(String entryName, ModVersionInfo info) {
        String pkg = entryName.substring(0, Math.max(0, entryName.lastIndexOf('/') + 1));
        return info.modPackages().contains(pkg);
    }
    
    /**
     * Transform a legacy mod (1.8-1.20.x) to run on modern 26.1.
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
        System.out.println("   Transform mods from MC 1.8+ to run on 26.1");
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
                            String json = new String(
                                com.retromod.util.ZipSecurity.safeReadAllBytes(is),
                                java.nio.charset.StandardCharsets.UTF_8);
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
    
    /**
     * Help mod developers update their own mod to a newer Minecraft version.
     *
     * Scans the mod's source JAR, shows every API call that changed between
     * the mod's target version and the current version, and outputs a
     * migration guide with find-and-replace suggestions they can apply
     * to their own source code.
     */
    private static void devhelpCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: devhelp <mod.jar> [--to <version>]");
            System.err.println();
            System.err.println("Scans your mod and tells you exactly what needs to change");
            System.err.println("to update it to a newer Minecraft version.");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  retromod devhelp mymod-1.21.4.jar");
            System.err.println("  retromod devhelp mymod-1.21.4.jar --to 26.1");
            System.exit(1);
        }

        Path modPath = Path.of(args[1]);
        if (!Files.exists(modPath)) {
            System.err.println("File not found: " + modPath);
            System.exit(1);
        }

        String targetVersion = TARGET_MC_VERSION;
        for (int i = 2; i < args.length; i++) {
            if ("--to".equals(args[i]) && i + 1 < args.length) {
                targetVersion = args[++i];
            }
        }

        System.out.println();
        System.out.println("=================================================================");
        System.out.println("  RetroMod Developer Migration Helper");
        System.out.println("=================================================================");
        System.out.println();

        // Detect mod info
        ModVersionInfo info = detector.detectVersion(modPath);
        if (info == null) {
            System.err.println("Could not read mod metadata. Is this a valid mod JAR?");
            System.exit(1);
        }

        String sourceVersion = info.targetMcVersion();
        System.out.println("  Mod:    " + info.modId() + " (v" + info.modVersion() + ")");
        System.out.println("  Loader: " + info.modLoaderType());
        System.out.println("  From:   MC " + sourceVersion);
        System.out.println("  To:     MC " + targetVersion);
        System.out.println();

        if (sourceVersion.equals(targetVersion)) {
            System.out.println("  Your mod already targets " + targetVersion + ". Nothing to do!");
            return;
        }

        // Find shim chain
        List<VersionShim> chain = shimRegistry.findShimChain(
            info.modLoaderType(), sourceVersion, targetVersion);

        if (chain.isEmpty()) {
            System.out.println("  No migration data available for " + sourceVersion + " -> " + targetVersion);
            return;
        }

        // Collect all redirects
        System.out.println("=================================================================");
        System.out.println("  MIGRATION GUIDE: " + sourceVersion + " -> " + targetVersion);
        System.out.println("=================================================================");
        System.out.println();

        int totalMethods = 0;
        int totalClasses = 0;

        for (VersionShim shim : chain) {
            RetroModTransformer temp = RetroModTransformer.getInstance();
            shim.registerRedirects(temp);

            int methods = temp.getMethodRedirectCount();
            int classes = temp.getClassRedirectCount();

            if (methods > 0 || classes > 0) {
                System.out.println("--- " + shim.getShimName() + " ---");
                System.out.println();

                // Show class renames
                var classRedirects = temp.getClassRedirects();
                if (classRedirects != null && !classRedirects.isEmpty()) {
                    System.out.println("  Class renames:");
                    for (var entry : classRedirects.entrySet()) {
                        String oldName = entry.getKey().replace('/', '.');
                        String newName = entry.getValue().replace('/', '.');
                        System.out.println("    " + oldName);
                        System.out.println("      -> " + newName);
                    }
                    System.out.println();
                }

                // Show method renames
                var methodRedirects = temp.getMethodRedirects();
                if (methodRedirects != null && !methodRedirects.isEmpty()) {
                    System.out.println("  Method changes:");
                    for (var entry : methodRedirects.entrySet()) {
                        System.out.println("    " + entry.getKey());
                        System.out.println("      -> " + entry.getValue());
                    }
                    System.out.println();
                }

                // Show embedded shims
                String[] shimClasses = shim.getShimClasses();
                if (shimClasses.length > 0) {
                    System.out.println("  New shim classes available:");
                    for (String cls : shimClasses) {
                        System.out.println("    " + cls);
                    }
                    System.out.println();
                }

                totalMethods += methods;
                totalClasses += classes;
            }
        }

        System.out.println("=================================================================");
        System.out.println("  SUMMARY");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("  " + chain.size() + " version steps");
        System.out.println("  " + totalClasses + " class renames");
        System.out.println("  " + totalMethods + " method changes");
        System.out.println();
        System.out.println("  To update your mod:");
        System.out.println("  1. Apply the class/method renames above to your source code");
        System.out.println("  2. Update your fabric.mod.json / mods.toml version targets");
        System.out.println("  3. Rebuild against the new Minecraft version");
        System.out.println();
        System.out.println("  Or just use RetroMod and skip the manual work :)");
        System.out.println();
    }

    /**
     * Score a mod JAR for compatibility with the target MC version.
     * Usage: score <mod.jar> [--mc-jar <path>] [--fabric-api <path>] [--verbose]
     */
    private static void scoreCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: score <mod.jar> [--mc-jar <path>] [--fabric-api <path>] [--verbose]");
            System.exit(1);
        }

        Path modPath = Path.of(args[1]);
        if (!Files.exists(modPath)) {
            System.err.println("File not found: " + modPath);
            System.exit(1);
        }

        // Parse options
        boolean verbose = false;
        Path mcJarPath = null;
        Path fabricApiPath = null;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--verbose", "-v" -> verbose = true;
                case "--mc-jar" -> {
                    if (i + 1 < args.length) mcJarPath = Path.of(args[++i]);
                }
                case "--fabric-api" -> {
                    if (i + 1 < args.length) fabricApiPath = Path.of(args[++i]);
                }
            }
        }

        // Auto-detect MC JAR if not specified
        if (mcJarPath == null) {
            mcJarPath = Path.of(System.getProperty("user.home"),
                    "Library/Application Support/PrismLauncher/libraries/com/mojang/minecraft/26.1-pre-2/minecraft-26.1-pre-2-client.jar");
            if (!Files.exists(mcJarPath)) {
                // Try common alternative locations
                Path altPath = Path.of(System.getProperty("user.home"),
                        ".minecraft/versions/26.1/26.1.jar");
                if (Files.exists(altPath)) {
                    mcJarPath = altPath;
                }
            }
        }

        // Auto-detect Fabric API JAR if not specified
        if (fabricApiPath == null) {
            fabricApiPath = Path.of(System.getProperty("user.home"),
                    "Library/Application Support/PrismLauncher/instances/26.1-pre-2-fabric/minecraft/mods/fabric-api-0.143.14+26.1.jar");
        }

        // Initialize transformer and load all shims + polyfills
        RetroModTransformer transformer = RetroModTransformer.getInstance();
        for (VersionShim shim : shimRegistry.getAllShims()) {
            shim.registerRedirects(transformer);
        }
        // Load polyfills via ServiceLoader
        com.retromod.polyfill.PolyfillRegistry polyfillRegistry = new com.retromod.polyfill.PolyfillRegistry();
        polyfillRegistry.loadAndRegister(transformer);

        // Create scorer and load target version index
        ModScorer scorer = new ModScorer(transformer);

        if (Files.exists(mcJarPath)) {
            System.err.println("Loading MC index from: " + mcJarPath.getFileName());
            scorer.loadMcJar(mcJarPath);
        } else {
            System.err.println("Warning: MC JAR not found at " + mcJarPath);
            System.err.println("  Use --mc-jar <path> to specify the Minecraft client JAR");
        }

        if (fabricApiPath != null && Files.exists(fabricApiPath)) {
            System.err.println("Loading Fabric API index from: " + fabricApiPath.getFileName());
            scorer.loadFabricApiJar(fabricApiPath);
        }

        // Detect mod info
        ModVersionInfo info = detector.detectVersion(modPath);

        // Run analysis
        System.err.println("Analyzing: " + modPath.getFileName());
        ModScorer.ScoreResult result = scorer.analyze(modPath, info);

        // Print report
        String modName = info != null && info.modId() != null
                ? info.modId() + " " + (info.modVersion() != null ? info.modVersion() : "")
                : modPath.getFileName().toString();
        String sourceLine = info != null && info.targetMcVersion() != null
                ? info.modLoaderType() + " " + info.targetMcVersion()
                : "unknown";

        int W = 60; // box width
        System.out.println();
        printBoxTop(W);
        printBoxLine(W, "  RetroMod Compatibility Score");
        printBoxSep(W);
        printBoxLine(W, "  Mod: " + modName.trim());
        printBoxLine(W, "  Source: " + sourceLine);
        printBoxLine(W, "  Target: MC " + TARGET_MC_VERSION);
        printBoxSep(W);
        printBoxLine(W, "  Overall Score: " + result.overallScore + "/100");
        printBoxLine(W, "");

        String clsIcon = result.classScore >= 75 ? "OK" : (result.classScore >= 50 ? "!!" : "XX");
        String mtdIcon = result.methodScore >= 75 ? "OK" : (result.methodScore >= 50 ? "!!" : "XX");
        String fldIcon = result.fieldScore >= 75 ? "OK" : (result.fieldScore >= 50 ? "!!" : "XX");
        String mixIcon = result.mixinScore >= 75 ? "OK" : (result.mixinScore >= 50 ? "!!" : "XX");

        printBoxLine(W, String.format("  [%s] Class references:  %d/%d resolvable (%d%%)",
                clsIcon, result.resolvableClasses, result.totalClasses, result.classScore));
        printBoxLine(W, String.format("  [%s] Method calls:      %d/%d redirectable (%d%%)",
                mtdIcon, result.resolvableMethods + result.redirectedMethods, result.totalMethods, result.methodScore));
        printBoxLine(W, String.format("  [%s] Field accesses:    %d/%d resolvable (%d%%)",
                fldIcon, result.resolvableFields + result.redirectedFields, result.totalFields, result.fieldScore));
        printBoxLine(W, String.format("  [%s] Mixin targets:     %d/%d valid (%d%%)",
                mixIcon, result.validMixins, result.totalMixins, result.mixinScore));
        printBoxLine(W, "");
        printBoxLine(W, "  Estimated: " + result.getVerdict());
        printBoxBottom(W);
        System.out.println();

        // Verbose output
        if (verbose) {
            if (!result.missingClasses.isEmpty()) {
                System.out.println("Missing Classes (" + result.missingClasses.size() + "):");
                for (String cls : result.missingClasses) {
                    System.out.println("  - " + cls.replace('/', '.'));
                }
                System.out.println();
            }

            if (!result.missingMethods.isEmpty()) {
                System.out.println("Unresolvable Method Calls (" + result.missingMethods.size() + "):");
                int shown = 0;
                for (String m : result.missingMethods) {
                    System.out.println("  - " + m);
                    if (++shown >= 50) {
                        System.out.println("  ... and " + (result.missingMethods.size() - shown) + " more");
                        break;
                    }
                }
                System.out.println();
            }

            if (!result.missingFields.isEmpty()) {
                System.out.println("Unresolvable Field Accesses (" + result.missingFields.size() + "):");
                for (String f : result.missingFields) {
                    System.out.println("  - " + f);
                }
                System.out.println();
            }

            if (!result.brokenMixins.isEmpty()) {
                System.out.println("Broken Mixin Targets (" + result.brokenMixins.size() + "):");
                for (String m : result.brokenMixins) {
                    System.out.println("  - " + m);
                }
                System.out.println();
            }

            // Suggestions
            if (!result.missingClasses.isEmpty() || !result.missingMethods.isEmpty()) {
                System.out.println("Suggestions:");
                boolean hasFabricMissing = result.missingClasses.stream()
                        .anyMatch(c -> c.startsWith("net/fabricmc/"));
                boolean hasNbtMissing = result.missingClasses.stream()
                        .anyMatch(c -> c.contains("nbt") || c.contains("Nbt"));
                boolean hasRenderMissing = result.missingMethods.stream()
                        .anyMatch(m -> m.contains("render") || m.contains("Render") || m.contains("GlStateManager"));

                if (hasFabricMissing) {
                    System.out.println("  - Enable Fabric API polyfills (fabric_api category)");
                }
                if (hasNbtMissing) {
                    System.out.println("  - Enable NBT polyfill for removed NBT classes");
                }
                if (hasRenderMissing) {
                    System.out.println("  - Enable rendering polyfill for GlStateManager/RenderType changes");
                }
                if (result.missingClasses.size() > 10) {
                    System.out.println("  - This mod may need manual porting for heavily changed APIs");
                }
                System.out.println();
            }
        }
    }

    // --- Box drawing helpers for score output ---

    private static void printBoxTop(int w) {
        System.out.print("+");
        System.out.print("=".repeat(w));
        System.out.println("+");
    }

    private static void printBoxSep(int w) {
        System.out.print("+");
        System.out.print("-".repeat(w));
        System.out.println("+");
    }

    private static void printBoxBottom(int w) {
        System.out.print("+");
        System.out.print("=".repeat(w));
        System.out.println("+");
    }

    private static void printBoxLine(int w, String text) {
        if (text.length() > w - 2) text = text.substring(0, w - 2);
        System.out.printf("| %-" + (w - 2) + "s |%n", text);
    }

    /**
     * Analyze a crash log or game log and report (or apply) auto-fixes.
     *
     * Usage:
     *   retromod autofix <log-file>           Analyze and print suggested fixes
     *   retromod autofix <log-file> --apply   Analyze and apply fixes to transformer
     */
    private static void autofixCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: autofix <log-file> [--apply]");
            System.err.println();
            System.err.println("  <log-file>   Path to a crash report or latest.log");
            System.err.println("  --apply      Apply fixes to the transformer (registers redirects)");
            System.err.println();
            System.err.println("Examples:");
            System.err.println("  retromod autofix logs/latest.log");
            System.err.println("  retromod autofix crash-reports/crash-2026-04-07.txt --apply");
            System.exit(1);
        }

        Path logFile = Path.of(args[1]);
        boolean apply = args.length > 2 && "--apply".equals(args[2]);

        if (!Files.exists(logFile)) {
            System.err.println("File not found: " + logFile);
            System.exit(1);
        }

        System.out.println();
        System.out.println("=================================================================");
        System.out.println("           RetroMod Auto-Fix Analysis");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Log file: " + logFile);
        System.out.println("Mode:     " + (apply ? "APPLY (registering fixes)" : "ANALYZE (dry run)"));
        System.out.println();

        com.retromod.core.AutoFixEngine engine = new com.retromod.core.AutoFixEngine();

        List<com.retromod.core.AutoFixEngine.AppliedFix> fixes;
        if (apply) {
            // Register all shims first so the transformer has context
            RetroModTransformer transformer = RetroModTransformer.getInstance();
            for (VersionShim shim : shimRegistry.getAllShims()) {
                try {
                    shim.registerRedirects(transformer);
                } catch (Exception e) {
                    // Ignore shim errors in CLI mode
                }
            }
            fixes = engine.analyzeAndFix(logFile, transformer);
        } else {
            fixes = engine.analyzeOnly(logFile);
        }

        if (fixes.isEmpty()) {
            System.out.println("  No actionable errors found in the log.");
            System.out.println();
            System.out.println("  If you expected fixes, check that:");
            System.out.println("  - The log file contains actual error messages");
            System.out.println("  - The errors are from mod compatibility issues (not config errors)");
            System.out.println();
            return;
        }

        System.out.println("Found " + fixes.size() + " actionable error(s):");
        System.out.println();

        // Group fixes by error type for cleaner output
        Map<String, List<com.retromod.core.AutoFixEngine.AppliedFix>> byType = new LinkedHashMap<>();
        for (com.retromod.core.AutoFixEngine.AppliedFix fix : fixes) {
            byType.computeIfAbsent(fix.errorType(), k -> new ArrayList<>()).add(fix);
        }

        for (Map.Entry<String, List<com.retromod.core.AutoFixEngine.AppliedFix>> entry : byType.entrySet()) {
            System.out.println("-----------------------------------------------------------------");
            System.out.println("  " + entry.getKey() + " (" + entry.getValue().size() + " occurrence(s))");
            System.out.println("-----------------------------------------------------------------");

            for (com.retromod.core.AutoFixEngine.AppliedFix fix : entry.getValue()) {
                System.out.println("  Error:  " + fix.description());
                System.out.println("  " + (apply ? "Action:" : "Suggestion:") + " " + fix.action());
                System.out.println();
            }
        }

        System.out.println("=================================================================");
        if (apply) {
            System.out.println("  " + fixes.size() + " fix(es) applied.");
            System.out.println("  Retransform your mods to incorporate the fixes:");
            System.out.println("    retromod batch <mods-folder> --aot");
        } else {
            System.out.println("  " + fixes.size() + " fix(es) suggested.");
            System.out.println("  To apply fixes, re-run with --apply:");
            System.out.println("    retromod autofix " + logFile + " --apply");
        }
        System.out.println("=================================================================");
        System.out.println();
    }

    private static void printUsage() {
        System.out.println();
        System.out.println("=================================================================");
        System.out.println("           RetroMod CLI v" + VERSION);
        System.out.println("   Backwards Compatibility Layer for Minecraft Mods");
        System.out.println("");
        System.out.println("   Supports: Fabric, Forge, NeoForge");
        System.out.println("   Versions: 1.8 -> 26.1");
        System.out.println("=================================================================");
        System.out.println();
        System.out.println("Usage: retromod <command> [options]");
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
        System.out.println("Legacy Commands (1.8-1.20.x -> 26.1):");
        System.out.println("  legacy <mod.jar>               Transform legacy mod to 26.1");
        System.out.println();
        System.out.println("Developer Commands:");
        System.out.println("  devhelp <mod.jar> [target]     Show what to change when updating your mod");
        System.out.println("  migrate <mod.jar> [target]     Alias for devhelp");
        System.out.println();
        System.out.println("Analysis Commands:");
        System.out.println("  score <mod.jar> [options]      Score mod compatibility with 26.1");
        System.out.println("  autofix <log-file> [--apply]   Analyze crash log, suggest/apply fixes");
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
        System.out.println("  # Transform a 1.21.8 Fabric mod to 26.1");
        System.out.println("  retromod aot mymod-1.21.8.jar");
        System.out.println();
        System.out.println("  # Transform a legacy 1.12.2 Forge mod to 26.1");
        System.out.println("  retromod legacy oldmod-1.12.2.jar");
        System.out.println();
        System.out.println("  # Batch process all mods");
        System.out.println("  retromod batch ./mods --aot");
        System.out.println();
        System.out.println("  # See what API changes you need for updating your own mod");
        System.out.println("  retromod devhelp mymod-1.21.4.jar 26.1");
        System.out.println();
        System.out.println("  # Cross-mod gap report — what's missing across a whole mods folder");
        System.out.println("  retromod gaps ./mods --mc-jar minecraft-26.1.jar");
        System.out.println();
        System.out.println("Target Minecraft version: " + TARGET_MC_VERSION);
        System.out.println();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // GAPS COMMAND — cross-mod gap report
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Produce a cross-mod gap report: for every mod JAR in the given folder,
     * transform its classes, verify each against the target MC index, and
     * aggregate unresolved-reference findings ranked by frequency across mods.
     *
     * <p>The output is the data-driven prioritization tool for deciding which
     * polyfills/shims to write next. Example for a 50-mod folder:</p>
     * <pre>
     *   Top gaps (ranked by number of mods affected):
     *     1. (42 mods) [MISSING_CLASS] net/minecraft/util/math/BlockPos
     *           → net/minecraft/core/BlockPos
     *     2. (37 mods) [MISSING_CLASS] net/minecraft/util/text/TextFormatting
     *           → net/minecraft/ChatFormatting
     *     ...
     * </pre>
     *
     * <p>Doesn't modify any mods on disk — purely diagnostic. Runs transformation
     * in-memory for each class.</p>
     *
     * <p>Usage: {@code retromod gaps <mods-folder> [--mc-jar <path>] [--output <file>]}</p>
     */
    private static void gapsCommand(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: gaps <mods-folder> [--mc-jar <path>] [--output <file>]");
            System.err.println("  --mc-jar   Path to target Minecraft JAR (required for verification)");
            System.err.println("  --output   Write the report to this file instead of stdout");
            System.exit(1);
        }

        Path modsFolder = Path.of(args[1]);
        if (!Files.isDirectory(modsFolder)) {
            System.err.println("Not a directory: " + modsFolder);
            System.exit(1);
        }

        Path mcJar = null;
        Path outputPath = null;
        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--mc-jar" -> {
                    if (i + 1 >= args.length) { System.err.println("--mc-jar needs a path"); System.exit(1); }
                    mcJar = Path.of(args[++i]);
                }
                case "--output" -> {
                    if (i + 1 >= args.length) { System.err.println("--output needs a path"); System.exit(1); }
                    outputPath = Path.of(args[++i]);
                }
                default -> System.err.println("Ignoring unknown flag: " + args[i]);
            }
        }

        // Enable verification for this run (disabled by default in the transformer).
        // Setting the property BEFORE touching RetroModTransformer is not enough —
        // the transformer reads it at class-init time. For CLI use, we add a
        // public static check instead (see RetroModTransformer.isVerificationEnabled)
        // and we gate on that. For now, if verification isn't on, we tell the user.
        if (!RetroModTransformer.isVerificationEnabled()) {
            System.err.println("NOTE: verification is not enabled.");
            System.err.println("Re-run with: -Dretromod.verifyTransforms=true");
            System.err.println("  (e.g. mvn exec:java -Dexec.mainClass=... -Dretromod.verifyTransforms=true ...)");
            System.exit(2);
        }

        RetroModTransformer transformer = RetroModTransformer.getInstance();
        transformer.setTargetMcVersion(TARGET_MC_VERSION);

        // Wire Fabric intermediary→Mojang mappings into the transformer.
        // Without this, Fabric mods (which ship intermediary class_XXXX /
        // method_XXXX / field_XXXX names in their bytecode) go through
        // transformation with those names untouched, then show up as
        // "missing" in the verifier — a gap report full of noise from
        // unresolved intermediary names rather than the real gaps.
        // RetroModPreLaunch does the same wiring for the runtime Fabric
        // entry; the CLI does it here for consistency.
        com.retromod.mapping.IntermediaryToMojangMapper.applyTo(transformer);

        if (mcJar != null) {
            if (!Files.exists(mcJar)) {
                System.err.println("MC JAR not found: " + mcJar);
                System.exit(1);
            }
            transformer.initFuzzyResolver(mcJar);
        } else {
            System.err.println("WARN: no --mc-jar supplied; verification will skip all classes");
            System.err.println("      (the fuzzy resolver has no MC index to check against)");
        }

        com.retromod.core.verify.CrossModGapReport aggregated =
                new com.retromod.core.verify.CrossModGapReport(TARGET_MC_VERSION);

        int modsProcessed = 0;
        int modsFailed = 0;

        try (var stream = Files.list(modsFolder)) {
            for (Path jar : (Iterable<Path>) stream::iterator) {
                if (!jar.toString().endsWith(".jar")) continue;
                // Honor mod-author opt-out before doing any work on the JAR.
                // Even though `gaps` only reads + verifies (doesn't write a
                // new JAR), running our verifier on a JAR whose author asked
                // us to leave it alone is still RetroMod-authored analysis
                // they didn't ask for. Skip cleanly.
                if (com.retromod.util.OptOutCheck.isOptedOut(jar)) {
                    com.retromod.util.OptOutCheck.logSkipped(jar);
                    continue;
                }
                System.out.println("[gaps] scanning " + jar.getFileName());
                try {
                    com.retromod.core.verify.VerificationReport perMod = verifyOneMod(transformer, jar);
                    if (perMod != null) {
                        aggregated.merge(perMod);
                        modsProcessed++;
                    }
                } catch (Exception e) {
                    System.err.println("  FAILED: " + e.getMessage());
                    modsFailed++;
                }
            }
        }

        System.out.println();
        System.out.printf("Processed %d mod%s (%d failed)%n",
                modsProcessed, modsProcessed == 1 ? "" : "s", modsFailed);
        System.out.println();

        StringBuilder out = new StringBuilder();
        aggregated.writeTo(out);

        if (outputPath != null) {
            Files.writeString(outputPath, out.toString());
            System.out.println("Report written to " + outputPath);
        } else {
            System.out.println(out);
        }
    }

    /**
     * Open one mod JAR, transform + verify each class, return the per-mod report.
     * In-memory only — does not write a new JAR.
     *
     * <p>Pipeline: (1) read all classes; (2) transform each through the main
     * pipeline (which includes iterative loop + reflection remapping);
     * (3) optionally run bridge synthesis; (4) optionally run pattern matching;
     * (5) run the verifier against the final bytecode. Each step is gated by its
     * own system-property flag — all must be enabled independently via
     * {@code -Dretromod.*} to fully exercise this command.</p>
     */
    private static com.retromod.core.verify.VerificationReport verifyOneMod(
            RetroModTransformer transformer, Path jarPath) throws Exception {

        ModVersionInfo info = detector.detectVersion(jarPath);
        String modId = info != null ? info.modId() : jarPath.getFileName().toString();

        // First pass: enumerate every class in the JAR to build modOwnClasses.
        // Required so the verifier doesn't flag mod-internal refs as "missing from MC".
        java.util.Set<String> modOwnClasses = new java.util.HashSet<>();
        java.util.Map<String, byte[]> classBytesByName = new java.util.LinkedHashMap<>();

        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                var entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;
                String internalName = entry.getName().substring(0, entry.getName().length() - 6);
                modOwnClasses.add(internalName);
                try (var in = jar.getInputStream(entry)) {
                    // Bounded read — a crafted mod could otherwise ship a
                    // .class entry with declared-size=0 but gigabytes of
                    // compressed data, OOMing the gaps command.
                    classBytesByName.put(internalName,
                            com.retromod.util.ZipSecurity.safeReadAllBytes(in));
                }
            }
        }

        com.retromod.core.verify.VerificationReport report =
                new com.retromod.core.verify.VerificationReport(
                        modId, TARGET_MC_VERSION, classBytesByName.size());

        // Pattern-matching context — shared across all class visits for this mod
        com.retromod.core.pattern.MatchContext matchCtx = null;
        if (RetroModTransformer.isPatternMatchingEnabled()) {
            com.retromod.core.verify.McSymbolIndex idx = transformer.getFuzzyResolver() != null
                    ? new com.retromod.core.verify.FuzzyBackedSymbolIndex(
                            transformer.getFuzzyResolver(), TARGET_MC_VERSION)
                    : com.retromod.core.pattern.MatchContext.empty().mcIndex();
            matchCtx = new com.retromod.core.pattern.MatchContext(
                    modOwnClasses,
                    com.retromod.core.verify.LoaderApiRenames.getInstance(),
                    idx);
        }

        int bridgeCountBefore = transformer.getBridgeSynthesizer().getBridgesSynthesized();

        // Second pass: transform each class, then apply optional post-processing.
        //
        // Parallel execution when -Dretromod.parallelism is > 1 (default = all cores).
        // Each per-class pipeline is independent: transform, bridge-synth, verify,
        // and pattern-match all work from the class's own bytes and the shared,
        // thread-safe redirect tables. The verifier's report accumulator is
        // thread-safe thanks to its use of synchronizedList-wrapped internals —
        // we serialize only the final per-mod report aggregation, not the
        // per-class pipeline steps.
        final com.retromod.core.pattern.MatchContext finalMatchCtx = matchCtx;
        com.retromod.core.parallel.RetroModExecutors.parallelForEachEntry(
                classBytesByName,
                (className, bytes) -> {
                    byte[] transformed = transformer.transformClass(bytes, className);
                    transformed = transformer.synthesizeBridges(transformed, modOwnClasses);
                    transformer.verifyClass(transformed, className, modOwnClasses, report);
                    if (finalMatchCtx != null) {
                        // VerificationReport.addPatternMatch is synchronized
                        // internally — safe to call from worker threads.
                        for (var m : transformer.matchPatterns(transformed, finalMatchCtx)) {
                            report.addPatternMatch(m);
                        }
                    }
                });

        int bridgesThisMod = transformer.getBridgeSynthesizer().getBridgesSynthesized()
                           - bridgeCountBefore;
        report.setBridgesSynthesized(bridgesThisMod);

        System.out.println("  " + report.summaryLine());
        return report;
    }
}
