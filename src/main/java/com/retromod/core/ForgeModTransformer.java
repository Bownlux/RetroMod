/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.regex.*;

/**
 * Transforms Forge and NeoForge mods to work on newer Minecraft versions.
 *
 * Same approach as FabricModTransformer but handles mods.toml and
 * neoforge.mods.toml metadata instead of fabric.mod.json.
 *
 * Steps:
 * 1. Extract mod JAR to temp directory
 * 2. Transform all class files (bytecode)
 * 3. Update mods.toml / neoforge.mods.toml version range
 * 4. Repackage as new JAR
 * 5. Copy to mods folder
 */
public class ForgeModTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-ForgeTransform");

    /**
     * Forge/NeoForge mod IDs for APIs that RetroMod provides compatibility shims for.
     * When a mod declares a dependency with a restrictive versionRange in mods.toml,
     * the mod loader will block the mod from loading. Since RetroMod transforms the
     * bytecode, we also relax these constraints.
     */
    private static final Set<String> SHIMMED_API_MOD_IDS = Set.of(
        // Tech / content mod APIs
        "mekanism", "mekanismapi",
        "ae2", "appliedenergistics2",
        "botania", "botania_api",
        "create",
        "thermal", "thermal_foundation", "thermal_expansion", "cofh_core",

        // Equipment
        "curios", "curiosapi",
        "baubles",

        // Recipe viewers
        "jei", "just_enough_items",
        "nei",

        // Tooltips / overlays
        "jade", "waila", "wthit",

        // Config libraries
        "cloth_config", "cloth-config",

        // Animation / model
        "geckolib", "geckolib3", "geckolib4",

        // Cross-platform
        "architectury",

        // Guide
        "patchouli",

        // Utility
        "autoreglib"
    );

    private final String targetMcVersion;
    private final RetroModTransformer bytecodeTransformer;

    public ForgeModTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.bytecodeTransformer = RetroModTransformer.getInstance();
    }

    /**
     * Transform a Forge or NeoForge mod JAR.
     *
     * @param sourceJar Path to the original mod JAR
     * @param outputDir Directory to write the transformed JAR
     * @return Path to the transformed JAR, or null if failed/skipped
     */
    public Path transformMod(Path sourceJar, Path outputDir) throws IOException {
        String originalName = sourceJar.getFileName().toString();
        String baseName = originalName.replace(".jar", "");
        String outputName = baseName + "-retromod.jar";
        Path outputJar = outputDir.resolve(outputName);

        LOGGER.info("Checking Forge/NeoForge mod: {}", originalName);

        // Check if mod is already for native version
        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (targetMcVersion.equals(modMcVersion)) {
            LOGGER.info("  {} is already for MC {} - no transformation needed", originalName, targetMcVersion);
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }

        LOGGER.info("Transforming Forge/NeoForge mod: {} -> {}", originalName, outputName);

        // Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("retromod-forge-");

        try {
            // Step 1: Extract JAR
            extractJar(sourceJar, tempDir);

            // Step 2: Transform bytecode
            int classesTransformed = transformClasses(tempDir);
            LOGGER.info("Transformed {} class files", classesTransformed);

            // Step 3: Update mods.toml / neoforge.mods.toml
            updateModsToml(tempDir, "META-INF/mods.toml");
            updateModsToml(tempDir, "META-INF/neoforge.mods.toml");

            // Step 4: Repackage
            repackageJar(tempDir, outputJar);

            LOGGER.info("Created transformed mod: {}", outputJar.getFileName());
            return outputJar;

        } catch (Exception e) {
            LOGGER.error("Failed to transform {}: {}", originalName, e.getMessage());
            return null;
        } finally {
            // Clean up temp directory
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /**
     * Extract minecraft version from a Forge/NeoForge mod JAR.
     */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try neoforge.mods.toml first
            ZipEntry entry = jar.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = jar.getEntry("META-INF/mods.toml");

            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                // Match versionRange = "[1.20.6]" or "[1.20.6,)" etc
                Pattern p = Pattern.compile("versionRange\\s*=\\s*\"\\[([0-9.]+)");
                Matcher m = p.matcher(content);
                // Skip the first match (usually forge/neoforge version), find minecraft
                while (m.find()) {
                    String version = m.group(1);
                    if (version.startsWith("1.")) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    private void extractJar(Path jarPath, Path outputDir) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path outputPath = outputDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    Files.createDirectories(outputPath.getParent());
                    try (InputStream is = jar.getInputStream(entry)) {
                        Files.copy(is, outputPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }

    private int transformClasses(Path dir) throws IOException {
        int count = 0;

        try (var stream = Files.walk(dir)) {
            var classFiles = stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("META-INF"))
                .toList();

            for (Path classFile : classFiles) {
                try {
                    byte[] original = Files.readAllBytes(classFile);
                    String className = dir.relativize(classFile).toString()
                        .replace(".class", "")
                        .replace(File.separator, "/");

                    byte[] transformed = bytecodeTransformer.transformClass(original, className);
                    if (transformed != null && transformed != original) {
                        Files.write(classFile, transformed);
                        count++;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not transform class: {}", classFile.getFileName());
                }
            }
        }

        return count;
    }

    /**
     * Update mods.toml or neoforge.mods.toml to target the correct MC version.
     */
    private void updateModsToml(Path dir, String tomlPath) throws IOException {
        Path tomlFile = dir.resolve(tomlPath);
        if (!Files.exists(tomlFile)) return;

        String content = Files.readString(tomlFile);
        String original = content;

        // Update minecraft versionRange
        // Matches: versionRange = "[1.20.6]" or "[1.20.6,)" or "[1.20,1.21)"
        // Only update the minecraft dependency, not the forge/neoforge one
        // Strategy: find [[dependencies.xxx]] blocks and update the minecraft one
        content = updateMinecraftVersionRange(content);

        // Add RetroMod marker if not present
        if (!content.contains("retromod_transformed")) {
            content = content + "\n# Transformed by RetroMod (original version modified)\n";
        }

        if (!content.equals(original)) {
            Files.writeString(tomlFile, content);
            LOGGER.info("Updated {}: minecraft versionRange -> [{}]", tomlPath, targetMcVersion);
        }
    }

    /**
     * Update the minecraft versionRange in TOML content.
     * Finds the [[dependencies.xxx]] block with modId = "minecraft" and
     * updates its versionRange to the target version.
     *
     * Also relaxes version ranges for third-party APIs that RetroMod has shims for.
     * Without this, the mod loader would block the mod at startup even though
     * RetroMod has already transformed the bytecode to work with newer API versions.
     */
    private String updateMinecraftVersionRange(String toml) {
        // TOML structure:
        //   [[dependencies.modname]]
        //   modId = "minecraft"
        //   mandatory = true
        //   versionRange = "[1.20.6]"
        //
        //   [[dependencies.modname]]
        //   modId = "cloth_config"
        //   mandatory = true
        //   versionRange = "[6.0,7.0)"

        StringBuilder result = new StringBuilder();
        String[] lines = toml.split("\n");
        String currentDepModId = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Detect start of a new dependency block
            if (trimmed.startsWith("[[dependencies")) {
                currentDepModId = null;
            }

            // Detect modId = "xxx"
            Pattern modIdPattern = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
            Matcher modIdMatcher = modIdPattern.matcher(trimmed);
            if (modIdMatcher.matches()) {
                currentDepModId = modIdMatcher.group(1);
            }

            // If we're in a dependency block, check if we should update versionRange
            if (currentDepModId != null && trimmed.startsWith("versionRange")) {
                if ("minecraft".equals(currentDepModId)) {
                    // Minecraft dependency: set to target version
                    result.append("versionRange = \"[").append(targetMcVersion).append(",)\"\n");
                    LOGGER.info("  Updated minecraft versionRange -> [{},...)", targetMcVersion);
                    currentDepModId = null;
                } else if (SHIMMED_API_MOD_IDS.contains(currentDepModId)) {
                    // Shimmed API dependency: relax to accept any version
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed API dependency: {} -> [0,...) (RetroMod has shims)", currentDepModId);
                    currentDepModId = null;
                } else if ("forge".equals(currentDepModId) || "neoforge".equals(currentDepModId)) {
                    // Forge/NeoForge loader: also relax
                    result.append("versionRange = \"[0,)\"\n");
                    currentDepModId = null;
                } else {
                    // Unknown dependency: make non-mandatory if it has a strict range
                    result.append(line).append("\n");
                }
            } else if (currentDepModId != null && SHIMMED_API_MOD_IDS.contains(currentDepModId)
                       && trimmed.startsWith("mandatory")) {
                // Make shimmed API dependencies non-mandatory as a safety net
                result.append("mandatory = false\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    private void repackageJar(Path sourceDir, Path outputJar) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputJar)))) {

            try (var walk = Files.walk(sourceDir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");

                    JarEntry entry = new JarEntry(entryName);
                    jos.putNextEntry(entry);
                    Files.copy(file, jos);
                    jos.closeEntry();
                }
            }
        }
    }
}
