/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.jar.*;
import java.util.regex.*;

/**
 * Transforms Quilt mods to work on newer Minecraft versions.
 * 
 * Quilt is a fork of Fabric with high compatibility:
 * - Uses quilt.mod.json instead of fabric.mod.json
 * - Same bytecode format as Fabric
 * - Same Mixin system
 * - Most Fabric mods work on Quilt unchanged
 * 
 * This transformer reuses FabricModTransformer for bytecode,
 * but handles quilt.mod.json metadata separately.
 */
public class QuiltModTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Quilt");
    
    private final String targetMcVersion;
    private final FabricModTransformer fabricTransformer;
    
    public QuiltModTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.fabricTransformer = new FabricModTransformer(targetMcVersion);
    }
    
    /**
     * Check if a JAR is a Quilt mod.
     */
    public static boolean isQuiltMod(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            return jar.getEntry("quilt.mod.json") != null;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Transform a Quilt mod JAR.
     * Uses Fabric transformer for bytecode, then updates quilt.mod.json.
     */
    public Path transformMod(Path sourceJar, Path outputDir) throws IOException {
        String originalName = sourceJar.getFileName().toString();
        
        LOGGER.info("Transforming Quilt mod: {}", originalName);
        
        // Check if native version
        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (isNativeVersion(modMcVersion)) {
            LOGGER.info("  {} is already for {} - passing through", originalName, targetMcVersion);
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }
        
        // Use Fabric transformer for the heavy lifting (bytecode transformation)
        // Quilt and Fabric bytecode is identical
        Path transformed = fabricTransformer.transformMod(sourceJar, outputDir);
        
        if (transformed != null && Files.exists(transformed)) {
            // Now update quilt.mod.json in the transformed JAR
            updateQuiltModJson(transformed);
        }
        
        return transformed;
    }
    
    /**
     * Update quilt.mod.json in a JAR to support target version.
     */
    private void updateQuiltModJson(Path jarPath) {
        try {
            // Read JAR, update quilt.mod.json, write back
            Path tempDir = Files.createTempDirectory("retromod-quilt-");
            
            // Extract
            try (JarFile jar = new JarFile(jarPath.toFile())) {
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    Path outPath = tempDir.resolve(entry.getName());
                    if (entry.isDirectory()) {
                        Files.createDirectories(outPath);
                    } else {
                        Files.createDirectories(outPath.getParent());
                        try (InputStream is = jar.getInputStream(entry)) {
                            Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                        }
                    }
                }
            }
            
            // Update quilt.mod.json
            Path quiltJson = tempDir.resolve("quilt.mod.json");
            if (Files.exists(quiltJson)) {
                String content = Files.readString(quiltJson);
                content = updateVersionInQuiltJson(content);
                Files.writeString(quiltJson, content);
            }
            
            // Repack
            repackJar(tempDir, jarPath);
            
            // Cleanup
            deleteDirectory(tempDir);
            
        } catch (Exception e) {
            LOGGER.debug("Could not update quilt.mod.json: {}", e.getMessage());
        }
    }
    
    /**
     * Update Minecraft version in quilt.mod.json content.
     */
    private String updateVersionInQuiltJson(String content) {
        // Quilt format: "minecraft": ">=1.20.1" or "minecraft": "1.20.1"
        // Update to target version
        
        // Pattern for "minecraft": "version" or "minecraft": ">=version"
        Pattern p = Pattern.compile("(\"minecraft\"\\s*:\\s*\")([^\"]+)(\")");
        Matcher m = p.matcher(content);
        
        if (m.find()) {
            String oldVersion = m.group(2);
            String newVersion = ">=" + targetMcVersion;
            content = m.replaceFirst("$1" + newVersion + "$3");
            LOGGER.debug("Updated quilt.mod.json: {} → {}", oldVersion, newVersion);
        }
        
        return content;
    }
    
    /**
     * Extract Minecraft version from quilt.mod.json.
     */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entry = jar.getEntry("quilt.mod.json");
            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                Pattern p = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(content);
                if (m.find()) {
                    return m.group(1);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Check if version is native (no transform needed).
     */
    private boolean isNativeVersion(String version) {
        if (version == null) return false;
        String clean = version.replace(">=", "").replace("~", "").replace("^", "").trim();
        return clean.equals(targetMcVersion) || version.contains(targetMcVersion);
    }
    
    /**
     * Repack directory into JAR.
     */
    private void repackJar(Path sourceDir, Path targetJar) throws IOException {
        Files.deleteIfExists(targetJar);
        
        try (var jos = new java.util.jar.JarOutputStream(
                new FileOutputStream(targetJar.toFile()))) {
            
            try (var stream = Files.walk(sourceDir)) {
                stream.filter(p -> !Files.isDirectory(p)).forEach(path -> {
                    try {
                        String entryName = sourceDir.relativize(path).toString().replace("\\", "/");
                        jos.putNextEntry(new JarEntry(entryName));
                        Files.copy(path, jos);
                        jos.closeEntry();
                    } catch (Exception e) {
                        // Ignore
                    }
                });
            }
        }
    }
    
    /**
     * Delete directory recursively.
     */
    private void deleteDirectory(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> -a.compareTo(b))
                  .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        } catch (Exception e) {
            // Ignore
        }
    }
}
