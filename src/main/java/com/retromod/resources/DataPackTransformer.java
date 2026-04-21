/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.resources;

import com.retromod.util.ZipSecurity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import java.util.regex.*;

/**
 * Transforms Data Packs to work on newer Minecraft versions.
 * 
 * What changes between versions:
 * - pack.mcmeta "pack_format" number (same as resource packs, different values)
 * - JSON schema changes for recipes, loot tables, worldgen
 * - Namespace changes (some vanilla namespaces renamed)
 * - Feature additions/removals
 * 
 * Data Pack Format History:
 * - 4: 1.13 - 1.14.4
 * - 5: 1.15 - 1.16.1
 * - 6: 1.16.2 - 1.16.5
 * - 7: 1.17 - 1.17.1
 * - 8: 1.18 - 1.18.1
 * - 9: 1.18.2
 * - 10: 1.19 - 1.19.3
 * - 12: 1.19.4
 * - 15: 1.20 - 1.20.1
 * - 18: 1.20.2
 * - 26: 1.20.3 - 1.20.4
 * - 41: 1.20.5 - 1.20.6
 * - 48: 1.21 - 1.21.1
 * - 57: 1.21.2 - 1.21.3
 * - 61: 1.21.4+
 */
public class DataPackTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-DataPacks");
    
    // Data pack format for target MC versions
    private static final Map<String, Integer> DATA_FORMATS = new HashMap<>();
    static {
        DATA_FORMATS.put("1.21", 48);
        DATA_FORMATS.put("1.21.1", 48);
        DATA_FORMATS.put("1.21.2", 57);
        DATA_FORMATS.put("1.21.3", 57);
        DATA_FORMATS.put("1.21.4", 61);
        DATA_FORMATS.put("1.21.5", 61);
        DATA_FORMATS.put("1.21.6", 61);
        DATA_FORMATS.put("1.21.7", 61);
        DATA_FORMATS.put("1.21.8", 61);
        DATA_FORMATS.put("1.21.9", 61);
        DATA_FORMATS.put("1.21.10", 61);
        DATA_FORMATS.put("1.21.11", 61);
        // Future
        DATA_FORMATS.put("1.22", 70);
        DATA_FORMATS.put("26.1.0", 80);
    }
    
    // Recipe type renames between versions
    private static final Map<String, String> RECIPE_TYPE_RENAMES = new HashMap<>();
    static {
        // 1.20+ changes
        RECIPE_TYPE_RENAMES.put("crafting_special_armordye", "crafting_special_armor_dye");
        RECIPE_TYPE_RENAMES.put("crafting_special_mapcloning", "crafting_special_map_cloning");
        RECIPE_TYPE_RENAMES.put("crafting_special_mapextending", "crafting_special_map_extending");
    }
    
    // Loot table changes
    private static final Map<String, String> LOOT_TABLE_RENAMES = new HashMap<>();
    static {
        LOOT_TABLE_RENAMES.put("minecraft:entities/zombie_pigman", "minecraft:entities/zombified_piglin");
    }
    
    private final String targetMcVersion;
    private final int targetDataFormat;
    
    public DataPackTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.targetDataFormat = DATA_FORMATS.getOrDefault(targetMcVersion, 61);
    }
    
    /**
     * Check if a file is a data pack.
     */
    public static boolean isDataPack(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".zip")) {
            try (ZipFile zip = new ZipFile(path.toFile())) {
                // Data packs have pack.mcmeta AND a data/ folder
                return zip.getEntry("pack.mcmeta") != null && 
                       zip.getEntry("data/") != null;
            } catch (Exception e) {
                return false;
            }
        }
        if (Files.isDirectory(path)) {
            return Files.exists(path.resolve("pack.mcmeta")) && 
                   Files.exists(path.resolve("data"));
        }
        return false;
    }
    
    /**
     * Get data pack format.
     */
    public int getPackFormat(Path packPath) {
        try {
            String mcmeta = readPackMcmeta(packPath);
            if (mcmeta != null) {
                Pattern p = Pattern.compile("\"pack_format\"\\s*:\\s*(\\d+)");
                Matcher m = p.matcher(mcmeta);
                if (m.find()) {
                    return Integer.parseInt(m.group(1));
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return -1;
    }
    
    /**
     * Check if pack needs transformation.
     */
    public boolean needsTransformation(Path packPath) {
        int format = getPackFormat(packPath);
        return format > 0 && format < targetDataFormat;
    }
    
    /**
     * Transform a data pack.
     */
    public Path transformPack(Path sourcePack, Path outputDir) throws IOException {
        String name = sourcePack.getFileName().toString();
        int oldFormat = getPackFormat(sourcePack);
        
        LOGGER.info("Transforming data pack: {} (format {} → {})", name, oldFormat, targetDataFormat);
        
        // If already correct format, just copy
        if (oldFormat >= targetDataFormat) {
            LOGGER.info("  Pack is already compatible - copying unchanged");
            Path dest = outputDir.resolve(name);
            if (Files.isDirectory(sourcePack)) {
                copyDirectory(sourcePack, dest);
            } else {
                Files.copy(sourcePack, dest, StandardCopyOption.REPLACE_EXISTING);
            }
            return dest;
        }
        
        // Create temp directory
        Path tempDir = Files.createTempDirectory("retromod-dp-");
        
        try {
            // Extract
            if (Files.isDirectory(sourcePack)) {
                copyDirectory(sourcePack, tempDir);
            } else {
                extractZip(sourcePack, tempDir);
            }
            
            // Transform pack.mcmeta
            transformPackMcmeta(tempDir);
            
            // Transform recipes if needed
            if (oldFormat < 41) {
                transformRecipes(tempDir);
            }
            
            // Transform loot tables if needed
            if (oldFormat < 10) {
                transformLootTables(tempDir);
            }
            
            // Repack
            String outputName = name.replace(".zip", "") + "-retromod.zip";
            Path outputPath = outputDir.resolve(outputName);
            packZip(tempDir, outputPath);
            
            LOGGER.info("  Transformed: {}", outputName);
            return outputPath;
            
        } finally {
            deleteDirectory(tempDir);
        }
    }
    
    /**
     * Transform pack.mcmeta.
     */
    private void transformPackMcmeta(Path packDir) throws IOException {
        Path mcmeta = packDir.resolve("pack.mcmeta");
        if (!Files.exists(mcmeta)) {
            Files.writeString(mcmeta, String.format("""
                {
                    "pack": {
                        "pack_format": %d,
                        "description": "Transformed by RetroMod"
                    }
                }
                """, targetDataFormat));
            return;
        }
        
        String content = Files.readString(mcmeta);
        content = content.replaceAll(
            "\"pack_format\"\\s*:\\s*\\d+",
            "\"pack_format\": " + targetDataFormat
        );
        Files.writeString(mcmeta, content);
    }
    
    /**
     * Transform recipes to new format.
     */
    private void transformRecipes(Path packDir) throws IOException {
        Path recipesDir = packDir.resolve("data");
        if (!Files.exists(recipesDir)) return;
        
        try (var stream = Files.walk(recipesDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .filter(p -> p.toString().contains("/recipes/") || p.toString().contains("/recipe/"))
                  .forEach(this::transformRecipeFile);
        }
    }
    
    private void transformRecipeFile(Path recipeFile) {
        try {
            String content = Files.readString(recipeFile);
            
            // Update recipe types
            for (var entry : RECIPE_TYPE_RENAMES.entrySet()) {
                content = content.replace("\"" + entry.getKey() + "\"", "\"" + entry.getValue() + "\"");
            }
            
            // Update item IDs if needed (pre-1.13 packs)
            // This is simplified - real implementation would need full ID mapping
            
            Files.writeString(recipeFile, content);
        } catch (Exception e) {
            // Ignore individual file errors
        }
    }
    
    /**
     * Transform loot tables.
     */
    private void transformLootTables(Path packDir) throws IOException {
        Path dataDir = packDir.resolve("data");
        if (!Files.exists(dataDir)) return;
        
        try (var stream = Files.walk(dataDir)) {
            stream.filter(p -> p.toString().endsWith(".json"))
                  .filter(p -> p.toString().contains("/loot_tables/") || p.toString().contains("/loot_table/"))
                  .forEach(this::transformLootTableFile);
        }
    }
    
    private void transformLootTableFile(Path lootFile) {
        try {
            String content = Files.readString(lootFile);
            
            // Update loot table references
            for (var entry : LOOT_TABLE_RENAMES.entrySet()) {
                content = content.replace(entry.getKey(), entry.getValue());
            }
            
            Files.writeString(lootFile, content);
        } catch (Exception e) {
            // Ignore
        }
    }
    
    // ========== Helper Methods ==========
    
    private String readPackMcmeta(Path packPath) throws IOException {
        if (Files.isDirectory(packPath)) {
            Path mcmeta = packPath.resolve("pack.mcmeta");
            return Files.exists(mcmeta) ? Files.readString(mcmeta) : null;
        } else {
            try (ZipFile zip = new ZipFile(packPath.toFile())) {
                var entry = zip.getEntry("pack.mcmeta");
                if (entry != null) {
                    return new String(zip.getInputStream(entry).readAllBytes());
                }
            }
        }
        return null;
    }
    
    private void extractZip(Path zipPath, Path outputDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path outPath = ZipSecurity.safeResolve(outputDir, entry.getName());
                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    try (InputStream is = zip.getInputStream(entry)) {
                        Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
        }
    }
    
    private void packZip(Path sourceDir, Path zipPath) throws IOException {
        Files.deleteIfExists(zipPath);
        try (var zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()))) {
            try (var stream = Files.walk(sourceDir)) {
                stream.filter(p -> !Files.isDirectory(p)).forEach(path -> {
                    try {
                        String entryName = sourceDir.relativize(path).toString().replace("\\", "/");
                        zos.putNextEntry(new ZipEntry(entryName));
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (Exception e) {
                        // Ignore
                    }
                });
            }
        }
    }
    
    private void copyDirectory(Path source, Path dest) throws IOException {
        try (var stream = Files.walk(source)) {
            stream.forEach(src -> {
                try {
                    Path dst = dest.resolve(source.relativize(src));
                    if (Files.isDirectory(src)) {
                        Files.createDirectories(dst);
                    } else {
                        Files.createDirectories(dst.getParent());
                        Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (Exception e) {
                    // Ignore
                }
            });
        }
    }
    
    private void deleteDirectory(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> -a.compareTo(b))
                  .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        } catch (Exception e) {
            // Ignore
        }
    }
}
