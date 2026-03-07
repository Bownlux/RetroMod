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
     */
    private String updateMinecraftVersionRange(String toml) {
        // Find all versionRange entries that follow a modId = "minecraft" line
        // TOML structure:
        //   [[dependencies.modname]]
        //   modId = "minecraft"
        //   ...
        //   versionRange = "[1.20.6]"

        StringBuilder result = new StringBuilder();
        String[] lines = toml.split("\n");
        boolean inMinecraftDep = false;

        for (String line : lines) {
            String trimmed = line.trim();

            // Detect start of a new dependency block
            if (trimmed.startsWith("[[dependencies")) {
                inMinecraftDep = false;
            }

            // Detect modId = "minecraft"
            if (trimmed.matches("modId\\s*=\\s*\"minecraft\"")) {
                inMinecraftDep = true;
            }

            // If we're in the minecraft dependency block, update versionRange
            if (inMinecraftDep && trimmed.startsWith("versionRange")) {
                result.append("versionRange = \"[").append(targetMcVersion).append(",)\"\n");
                inMinecraftDep = false; // Done with this block
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
