/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Manages transformation of resource packs and data packs.
 * 
 * USAGE:
 * 1. Put old resource packs in: retromod-input/resourcepacks/
 * 2. Put old data packs in: retromod-input/datapacks/
 * 3. RetroMod transforms them on startup
 * 4. Transformed packs go to: resourcepacks/ and saves/[world]/datapacks/
 * 
 * This mirrors the mod transformation workflow for consistency.
 */
public class ResourceManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Resources");
    
    private final String targetMcVersion;
    private final Path gameDir;
    private final ResourcePackTransformer rpTransformer;
    private final DataPackTransformer dpTransformer;
    
    // Statistics
    private int resourcePacksTransformed = 0;
    private int dataPacksTransformed = 0;
    
    public ResourceManager(String targetMcVersion, Path gameDir) {
        this.targetMcVersion = targetMcVersion;
        this.gameDir = gameDir;
        this.rpTransformer = new ResourcePackTransformer(targetMcVersion);
        this.dpTransformer = new DataPackTransformer(targetMcVersion);
    }
    
    /**
     * Initialize resource folders.
     */
    public void ensureFolders() {
        try {
            // Create input folders
            Path inputDir = gameDir.resolve("retromod-input");
            Path rpInput = inputDir.resolve("resourcepacks");
            Path dpInput = inputDir.resolve("datapacks");
            
            Files.createDirectories(rpInput);
            Files.createDirectories(dpInput);
            
            // Create processed folders
            Files.createDirectories(rpInput.resolve("processed"));
            Files.createDirectories(dpInput.resolve("processed"));
            
            // Create README in resource input folders
            createReadme(rpInput, "RESOURCE PACKS");
            createReadme(dpInput, "DATA PACKS");
            
        } catch (Exception e) {
            LOGGER.warn("Could not create resource folders: {}", e.getMessage());
        }
    }
    
    private void createReadme(Path folder, String type) throws IOException {
        Path readme = folder.resolve("README.txt");
        if (Files.exists(readme)) return;
        
        Files.writeString(readme, String.format("""
            ═══════════════════════════════════════════════════════════
            RETROMOD %s INPUT
            ═══════════════════════════════════════════════════════════
            
            Put your OLD %s here!
            
            RetroMod will automatically:
            1. Transform them to work with Minecraft %s
            2. Copy transformed versions to the correct folder
            3. Move originals to processed/
            
            SUPPORTED FORMATS:
            - .zip files
            - Folders (unzipped packs)
            
            ═══════════════════════════════════════════════════════════
            """, type, type.toLowerCase(), targetMcVersion));
    }
    
    /**
     * Scan and transform all resource packs and data packs.
     */
    public void processAll() {
        processResourcePacks();
        processDataPacks();
        
        if (resourcePacksTransformed > 0 || dataPacksTransformed > 0) {
            LOGGER.info("Resource transformation complete: {} resource packs, {} data packs",
                resourcePacksTransformed, dataPacksTransformed);
        }
    }
    
    /**
     * Process resource packs from retromod-input/resourcepacks/
     */
    private void processResourcePacks() {
        Path inputDir = gameDir.resolve("retromod-input/resourcepacks");
        Path outputDir = gameDir.resolve("resourcepacks");
        Path processedDir = inputDir.resolve("processed");
        
        if (!Files.exists(inputDir)) return;
        
        try {
            Files.createDirectories(outputDir);
            
            // Find all resource packs
            try (var stream = Files.list(inputDir)) {
                stream.filter(p -> !p.getFileName().toString().equals("processed"))
                      .filter(p -> !p.getFileName().toString().equals("README.txt"))
                      .filter(p -> isResourcePack(p))
                      .forEach(pack -> {
                          try {
                              processResourcePack(pack, outputDir, processedDir);
                          } catch (Exception e) {
                              LOGGER.warn("Could not process resource pack {}: {}", 
                                  pack.getFileName(), e.getMessage());
                          }
                      });
            }
        } catch (Exception e) {
            LOGGER.debug("Could not process resource packs: {}", e.getMessage());
        }
    }
    
    private void processResourcePack(Path pack, Path outputDir, Path processedDir) throws IOException {
        String name = pack.getFileName().toString();
        
        // Check if already processed
        Path processedMarker = processedDir.resolve(name + ".done");
        if (Files.exists(processedMarker)) {
            return; // Already processed
        }
        
        // Check if needs transformation
        if (!rpTransformer.needsTransformation(pack)) {
            // Just copy to output
            Path dest = outputDir.resolve(name);
            if (!Files.exists(dest)) {
                if (Files.isDirectory(pack)) {
                    copyDirectory(pack, dest);
                } else {
                    Files.copy(pack, dest);
                }
                LOGGER.info("Copied resource pack (already compatible): {}", name);
            }
        } else {
            // Transform
            rpTransformer.transformPack(pack, outputDir);
            resourcePacksTransformed++;
        }
        
        // Move original to processed
        Path processed = processedDir.resolve(name);
        if (Files.isDirectory(pack)) {
            moveDirectory(pack, processed);
        } else {
            Files.move(pack, processed, StandardCopyOption.REPLACE_EXISTING);
        }
        
        // Create marker
        Files.writeString(processedMarker, "Processed by RetroMod");
    }
    
    /**
     * Process data packs from retromod-input/datapacks/
     */
    private void processDataPacks() {
        Path inputDir = gameDir.resolve("retromod-input/datapacks");
        Path processedDir = inputDir.resolve("processed");
        
        if (!Files.exists(inputDir)) return;
        
        // Data packs go to saves/[world]/datapacks/, but we don't know the world name
        // So we'll put them in a staging area and let user copy to their world
        Path outputDir = gameDir.resolve("retromod-output/datapacks");
        
        try {
            Files.createDirectories(outputDir);
            
            // Create instructions
            Path instructions = outputDir.resolve("INSTRUCTIONS.txt");
            if (!Files.exists(instructions)) {
                Files.writeString(instructions, """
                    ═══════════════════════════════════════════════════════════
                    TRANSFORMED DATA PACKS
                    ═══════════════════════════════════════════════════════════
                    
                    These data packs have been transformed by RetroMod.
                    
                    TO USE THEM:
                    1. Copy the .zip files to your world's datapacks folder:
                       .minecraft/saves/[YourWorld]/datapacks/
                    
                    2. In-game, run: /reload
                    
                    ═══════════════════════════════════════════════════════════
                    """);
            }
            
            // Find all data packs
            try (var stream = Files.list(inputDir)) {
                stream.filter(p -> !p.getFileName().toString().equals("processed"))
                      .filter(p -> !p.getFileName().toString().equals("README.txt"))
                      .filter(p -> isDataPack(p))
                      .forEach(pack -> {
                          try {
                              processDataPack(pack, outputDir, processedDir);
                          } catch (Exception e) {
                              LOGGER.warn("Could not process data pack {}: {}", 
                                  pack.getFileName(), e.getMessage());
                          }
                      });
            }
        } catch (Exception e) {
            LOGGER.debug("Could not process data packs: {}", e.getMessage());
        }
    }
    
    private void processDataPack(Path pack, Path outputDir, Path processedDir) throws IOException {
        String name = pack.getFileName().toString();
        
        // Check if already processed
        Path processedMarker = processedDir.resolve(name + ".done");
        if (Files.exists(processedMarker)) {
            return;
        }
        
        // Check if needs transformation
        if (!dpTransformer.needsTransformation(pack)) {
            Path dest = outputDir.resolve(name);
            if (!Files.exists(dest)) {
                if (Files.isDirectory(pack)) {
                    copyDirectory(pack, dest);
                } else {
                    Files.copy(pack, dest);
                }
                LOGGER.info("Copied data pack (already compatible): {}", name);
            }
        } else {
            dpTransformer.transformPack(pack, outputDir);
            dataPacksTransformed++;
        }
        
        // Move original
        Path processed = processedDir.resolve(name);
        if (Files.isDirectory(pack)) {
            moveDirectory(pack, processed);
        } else {
            Files.move(pack, processed, StandardCopyOption.REPLACE_EXISTING);
        }
        
        Files.writeString(processedMarker, "Processed by RetroMod");
    }
    
    // ========== Helper Methods ==========
    
    private boolean isResourcePack(Path path) {
        return ResourcePackTransformer.isResourcePack(path);
    }
    
    private boolean isDataPack(Path path) {
        return DataPackTransformer.isDataPack(path);
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
                } catch (Exception e) {}
            });
        }
    }
    
    private void moveDirectory(Path source, Path dest) throws IOException {
        copyDirectory(source, dest);
        deleteDirectory(source);
    }
    
    private void deleteDirectory(Path dir) {
        try (var stream = Files.walk(dir)) {
            stream.sorted((a, b) -> -a.compareTo(b))
                  .forEach(p -> { try { Files.delete(p); } catch (Exception e) {} });
        } catch (Exception e) {}
    }
    
    // Getters
    public int getResourcePacksTransformed() { return resourcePacksTransformed; }
    public int getDataPacksTransformed() { return dataPacksTransformed; }
}
