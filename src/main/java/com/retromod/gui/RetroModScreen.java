/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.gui;

import com.retromod.core.*;
import com.retromod.aot.AotCompiler;
import com.retromod.shim.ShimRegistry;
import com.retromod.util.McReflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.io.FilenameFilter;
import java.lang.reflect.Method;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Reflection-based mod manager screen for RetroMod.
 *
 * Accessible from the title screen via the injected RetroMod button.
 * Uses the native OS file picker (java.awt.FileDialog) for mod selection,
 * then shows transformation results as an in-game Minecraft screen.
 *
 * This class uses reflection for all Minecraft interactions because the
 * project is built with Maven (no Minecraft classes on compile classpath).
 * At runtime inside Minecraft, all classes are available.
 *
 * Flow:
 *   1. User clicks RetroMod button on title screen
 *   2. This class opens the native OS file picker (macOS Finder / Windows Explorer)
 *   3. Selected mods are analyzed for complexity
 *   4. If deemed "unlikely to work", user is warned (unless force mode)
 *   5. Remaining mods are transformed and installed to mods/
 *   6. Results shown as an in-game Minecraft screen
 */
public class RetroModScreen {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Screen");

    private final Object minecraftClient; // MinecraftClient instance
    private final Object parentScreen;    // TitleScreen instance

    public record TransformResult(String modName, Status status, String message) {
        public enum Status { SUCCESS, SKIPPED, FAILED, COMPLEX_WARNING }
    }

    public RetroModScreen(Object client, Object parent) {
        this.minecraftClient = client;
        this.parentScreen = parent;
    }

    /**
     * Open the RetroMod manager — shows a file picker and transforms mods.
     */
    public void open() {
        // Run file picker on background thread to avoid freezing Minecraft
        CompletableFuture.runAsync(() -> {
            try {
                File[] selectedFiles = showNativeFilePicker();

                if (selectedFiles != null && selectedFiles.length > 0) {
                    List<TransformResult> results = transformMods(selectedFiles);
                    showResults(results);
                }
            } catch (Exception e) {
                LOGGER.error("RetroMod manager error", e);
            }
        });
    }

    /**
     * Show the native OS file picker dialog.
     * Uses java.awt.FileDialog which opens the real macOS Finder / Windows Explorer dialog.
     */
    private File[] showNativeFilePicker() throws Exception {
        final File[][] result = {null};

        // FileDialog must be created on the AWT event thread
        java.awt.EventQueue.invokeAndWait(() -> {
            // Create a hidden frame as parent for the dialog
            Frame frame = new Frame();
            frame.setUndecorated(true);
            frame.setVisible(false);

            FileDialog dialog = new FileDialog(frame, "RetroMod — Select Mod JARs to Transform", FileDialog.LOAD);
            dialog.setDirectory(System.getProperty("user.home"));
            dialog.setMultipleMode(true);

            // Filter to only show .jar files
            dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".jar"));

            dialog.setVisible(true);

            File[] files = dialog.getFiles();
            if (files != null && files.length > 0) {
                result[0] = files;
            }

            dialog.dispose();
            frame.dispose();
        });

        return result[0];
    }

    /**
     * Transform selected mod files.
     */
    private List<TransformResult> transformMods(File[] modFiles) {
        List<TransformResult> results = new CopyOnWriteArrayList<>();

        Path gameDir = getGameDir();
        Path modsFolder = gameDir.resolve("mods");
        ModCompatibilityChecker checker = new ModCompatibilityChecker(gameDir);
        ModComplexityAnalyzer complexityAnalyzer = new ModComplexityAnalyzer();
        boolean forceComplex = isForceTranslateEnabled();

        for (File modFile : modFiles) {
            String name = modFile.getName();
            LOGGER.info("Processing: {}", name);

            try {
                Path modPath = modFile.toPath();

                // Complexity check
                ModComplexityAnalyzer.ComplexityReport report = complexityAnalyzer.analyze(modPath);

                if (report.isUnlikelyToWork() && !forceComplex) {
                    LOGGER.warn("Skipped {} — complexity score {} (unlikely to work: {})",
                        name, report.score(), report.reason());
                    results.add(new TransformResult(
                        name,
                        TransformResult.Status.COMPLEX_WARNING,
                        "Unlikely to work: " + report.reason() +
                            ". Enable \"force_translate_complex\" in config to try anyway."
                    ));
                    continue;
                }

                // Analyze
                ModCompatibilityChecker.IncompatibleMod analysis = checker.analyzeJar(modPath);

                if (analysis != null) {
                    Path transformed = checker.transformAndInstall(modPath);
                    results.add(new TransformResult(name, TransformResult.Status.SUCCESS,
                        "Transformed to " + transformed.getFileName()));
                    LOGGER.info("Transformed: {} -> {}", name, transformed.getFileName());
                } else {
                    Files.copy(modPath, modsFolder.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                    results.add(new TransformResult(name, TransformResult.Status.SKIPPED,
                        "Already compatible, copied"));
                }

            } catch (Exception e) {
                LOGGER.error("Failed to transform {}", name, e);
                results.add(new TransformResult(name, TransformResult.Status.FAILED,
                    e.getClass().getSimpleName() + ": " +
                        (e.getMessage() != null ? e.getMessage() : "Unknown error")));
            }
        }

        return results;
    }

    /**
     * Show transformation results as an in-game Minecraft screen.
     */
    private void showResults(List<TransformResult> results) {
        long success = results.stream().filter(r -> r.status() == TransformResult.Status.SUCCESS).count();
        long failed = results.stream().filter(r -> r.status() == TransformResult.Status.FAILED).count();
        long skipped = results.stream().filter(r -> r.status() == TransformResult.Status.SKIPPED).count();
        long complex = results.stream().filter(r -> r.status() == TransformResult.Status.COMPLEX_WARNING).count();

        List<String> resultLines = new ArrayList<>();

        if (success > 0) resultLines.add(success + " mod(s) transformed successfully");
        if (skipped > 0) resultLines.add(skipped + " mod(s) already compatible");
        if (complex > 0) resultLines.add(complex + " mod(s) skipped (too complex)");
        if (failed > 0) resultLines.add(failed + " mod(s) failed");

        resultLines.add("");

        for (TransformResult r : results) {
            String prefix = switch (r.status()) {
                case SUCCESS -> "[OK]";
                case SKIPPED -> "[SKIP]";
                case FAILED -> "[FAIL]";
                case COMPLEX_WARNING -> "[WARN]";
            };
            resultLines.add(prefix + " " + r.modName());
            if (r.status() == TransformResult.Status.COMPLEX_WARNING) {
                resultLines.add("  " + r.message());
            }
        }

        // Show results in-game
        InGameScreenFactory.showTransformResults(resultLines, success > 0);
    }

    /**
     * Get the game directory.
     * Tries multiple approaches to find the game dir across all loaders.
     */
    private Path getGameDir() {
        // Try MC client field (yarn: runDirectory, mojang: gameDirectory)
        java.lang.reflect.Field dirField = McReflect.findField(
            minecraftClient.getClass(),
            "runDirectory", "gameDirectory"
        );
        if (dirField != null) {
            try {
                Object dir = dirField.get(minecraftClient);
                if (dir instanceof File f) return f.toPath();
                if (dir instanceof Path p) return p;
            } catch (Exception ignored) {}
        }

        // Try FabricLoader.getInstance().getGameDir()
        try {
            Class<?> fabricLoader = Class.forName("net.fabricmc.loader.api.FabricLoader");
            Object instance = fabricLoader.getMethod("getInstance").invoke(null);
            return (Path) fabricLoader.getMethod("getGameDir").invoke(instance);
        } catch (Exception ignored) {}

        // Try NeoForge FMLPaths
        try {
            Class<?> fmlPaths = Class.forName("net.neoforged.fml.loading.FMLPaths");
            Object gameDirPath = fmlPaths.getMethod("getOrCreateGameRelativePath",
                Path.class).invoke(null, Path.of("."));
            if (gameDirPath instanceof Path p) return p;
        } catch (Exception ignored) {}

        // Fallback
        return Path.of(".").toAbsolutePath().normalize();
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
}
