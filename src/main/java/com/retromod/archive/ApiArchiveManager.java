/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.archive;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;

/**
 * API Archive Manager - Downloads and manages old Fabric/NeoForge API sources.
 * 
 * This class handles:
 * 1. Downloading archived mod loader JARs from Maven Central
 * 2. Extracting specific API classes from archives
 * 3. Caching downloaded archives locally
 * 4. Providing API classes for embedding into legacy mods
 */
public class ApiArchiveManager {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-archive");
    
    // Archive storage directory
    private static final Path ARCHIVE_DIR = Path.of("config/retromod/api-archive");
    
    // Maven repository URLs
    private static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";
    private static final String NEOFORGE_MAVEN = "https://maven.neoforged.net/releases/";
    private static final String FORGE_MAVEN = "https://maven.minecraftforge.net/";
    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";
    
    // Known Fabric API versions by Minecraft version
    private static final Map<String, String> FABRIC_API_VERSIONS = new LinkedHashMap<>() {{
        put("1.21", "0.100.0+1.21");
        put("1.21.1", "0.102.0+1.21.1");
        put("1.21.2", "0.106.0+1.21.2");
        put("1.21.3", "0.107.0+1.21.3");
        put("1.21.4", "0.110.0+1.21.4");
        put("1.21.5", "0.115.0+1.21.5");
        put("1.21.6", "0.120.0+1.21.6");
        put("1.21.7", "0.125.0+1.21.7");
        put("1.21.8", "0.130.0+1.21.8");
        put("1.21.9", "0.134.0+1.21.9");
        put("1.21.10", "0.138.0+1.21.10");
        put("1.21.11", "0.141.0+1.21.11");
    }};
    
    // Known NeoForge versions by Minecraft version
    private static final Map<String, String> NEOFORGE_VERSIONS = new LinkedHashMap<>() {{
        put("1.21", "21.0.0-beta");
        put("1.21.1", "21.1.0");
        put("1.21.3", "21.3.0");
        put("1.21.4", "21.4.0");
        put("1.21.5", "21.5.0");
        put("1.21.6", "21.6.0");
        put("1.21.7", "21.7.0");
        put("1.21.8", "21.8.0");
        put("1.21.9", "21.9.0");
        put("1.21.10", "21.10.0");
        put("1.21.11", "21.11.0");
    }};
    
    // Cache of loaded archive contents
    private final Map<String, Map<String, byte[]>> archiveCache = new ConcurrentHashMap<>();
    
    // Executor for async downloads
    private final ExecutorService downloadExecutor = Executors.newFixedThreadPool(2);
    
    public ApiArchiveManager() {
        try {
            Files.createDirectories(ARCHIVE_DIR);
        } catch (IOException e) {
            LOGGER.error("Could not create archive directory", e);
        }
    }
    
    /**
     * Get a class from an archived API version.
     * Downloads the archive if not cached.
     */
    public byte[] getArchivedClass(String loaderType, String mcVersion, String className) {
        String archiveKey = loaderType + "-" + mcVersion;
        
        // Check memory cache
        Map<String, byte[]> archive = archiveCache.get(archiveKey);
        if (archive != null && archive.containsKey(className)) {
            return archive.get(className);
        }
        
        // Load archive
        try {
            loadArchive(loaderType, mcVersion);
            archive = archiveCache.get(archiveKey);
            if (archive != null) {
                return archive.get(className);
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load archive for {} {}", loaderType, mcVersion, e);
        }
        
        return null;
    }
    
    /**
     * Load an archive into memory cache.
     */
    public void loadArchive(String loaderType, String mcVersion) throws IOException {
        String archiveKey = loaderType + "-" + mcVersion;
        
        if (archiveCache.containsKey(archiveKey)) {
            return; // Already loaded
        }
        
        Path archivePath = getArchivePath(loaderType, mcVersion);
        
        // Download if not present
        if (!Files.exists(archivePath)) {
            downloadArchive(loaderType, mcVersion, archivePath);
        }
        
        // Extract classes from archive
        Map<String, byte[]> classes = extractClasses(archivePath);
        archiveCache.put(archiveKey, classes);
        
        LOGGER.info("Loaded archive {} with {} classes", archiveKey, classes.size());
    }
    
    /**
     * Download an archived API JAR.
     */
    private void downloadArchive(String loaderType, String mcVersion, Path targetPath) 
            throws IOException {
        
        String url = getDownloadUrl(loaderType, mcVersion);
        
        LOGGER.info("Downloading archive: {}", url);
        
        try {
            URL downloadUrl = new URL(url);
            HttpURLConnection conn = (HttpURLConnection) downloadUrl.openConnection();
            conn.setRequestProperty("User-Agent", "RetroMod/1.0");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);
            
            if (conn.getResponseCode() != 200) {
                throw new IOException("Failed to download: HTTP " + conn.getResponseCode());
            }
            
            try (InputStream in = conn.getInputStream();
                 OutputStream out = new BufferedOutputStream(new FileOutputStream(targetPath.toFile()))) {
                
                byte[] buffer = new byte[8192];
                int read;
                long total = 0;
                
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                    total += read;
                }
                
                LOGGER.info("Downloaded {} bytes to {}", total, targetPath.getFileName());
            }
            
        } catch (Exception e) {
            // Clean up partial download
            Files.deleteIfExists(targetPath);
            throw new IOException("Download failed: " + url, e);
        }
    }
    
    /**
     * Get the Maven download URL for an archive.
     */
    private String getDownloadUrl(String loaderType, String mcVersion) {
        return switch (loaderType.toLowerCase()) {
            case "fabric" -> getFabricApiUrl(mcVersion);
            case "neoforge" -> getNeoForgeUrl(mcVersion);
            case "forge" -> getForgeUrl(mcVersion);
            default -> throw new IllegalArgumentException("Unknown loader type: " + loaderType);
        };
    }
    
    private String getFabricApiUrl(String mcVersion) {
        String apiVersion = FABRIC_API_VERSIONS.get(mcVersion);
        if (apiVersion == null) {
            throw new IllegalArgumentException("No Fabric API version known for MC " + mcVersion);
        }
        
        // Maven path: net/fabricmc/fabric-api/fabric-api/{version}/fabric-api-{version}.jar
        return FABRIC_MAVEN + 
               "net/fabricmc/fabric-api/fabric-api/" + apiVersion + 
               "/fabric-api-" + apiVersion + ".jar";
    }
    
    private String getNeoForgeUrl(String mcVersion) {
        String nfVersion = NEOFORGE_VERSIONS.get(mcVersion);
        if (nfVersion == null) {
            throw new IllegalArgumentException("No NeoForge version known for MC " + mcVersion);
        }
        
        // Maven path: net/neoforged/neoforge/{version}/neoforge-{version}.jar
        return NEOFORGE_MAVEN + 
               "net/neoforged/neoforge/" + nfVersion + 
               "/neoforge-" + nfVersion + ".jar";
    }
    
    private String getForgeUrl(String mcVersion) {
        // Forge uses different versioning
        // For simplicity, we'll focus on NeoForge for 1.21+
        throw new UnsupportedOperationException(
            "Legacy Forge not supported for 1.21+. Use NeoForge instead.");
    }
    
    /**
     * Get the local path for an archive.
     */
    private Path getArchivePath(String loaderType, String mcVersion) {
        return ARCHIVE_DIR.resolve(loaderType + "-" + mcVersion + ".jar");
    }
    
    /**
     * Extract all classes from a JAR archive.
     */
    private Map<String, byte[]> extractClasses(Path jarPath) throws IOException {
        Map<String, byte[]> classes = new HashMap<>();
        
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (entry.getName().endsWith(".class")) {
                    try (InputStream is = jar.getInputStream(entry)) {
                        String className = entry.getName().replace(".class", "");
                        classes.put(className, is.readAllBytes());
                    }
                }
            }
        }
        
        return classes;
    }
    
    /**
     * Pre-download archives for all known versions.
     * Can be run at startup to ensure archives are available.
     */
    public CompletableFuture<Void> preloadAllArchives() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        
        // Download Fabric API archives
        for (String mcVersion : FABRIC_API_VERSIONS.keySet()) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Path path = getArchivePath("fabric", mcVersion);
                    if (!Files.exists(path)) {
                        downloadArchive("fabric", mcVersion, path);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to preload Fabric API for {}", mcVersion, e);
                }
            }, downloadExecutor));
        }
        
        // Download NeoForge archives
        for (String mcVersion : NEOFORGE_VERSIONS.keySet()) {
            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    Path path = getArchivePath("neoforge", mcVersion);
                    if (!Files.exists(path)) {
                        downloadArchive("neoforge", mcVersion, path);
                    }
                } catch (Exception e) {
                    LOGGER.warn("Failed to preload NeoForge for {}", mcVersion, e);
                }
            }, downloadExecutor));
        }
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }
    
    /**
     * Get a list of all classes in an API that match a pattern.
     */
    public List<String> findClasses(String loaderType, String mcVersion, String packagePattern) {
        String archiveKey = loaderType + "-" + mcVersion;
        Map<String, byte[]> archive = archiveCache.get(archiveKey);
        
        if (archive == null) {
            try {
                loadArchive(loaderType, mcVersion);
                archive = archiveCache.get(archiveKey);
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        
        if (archive == null) return Collections.emptyList();
        
        String pattern = packagePattern.replace('.', '/');
        List<String> matches = new ArrayList<>();
        
        for (String className : archive.keySet()) {
            if (className.startsWith(pattern)) {
                matches.add(className);
            }
        }
        
        return matches;
    }
    
    /**
     * Compare two versions to find API differences.
     */
    public ApiDiff compareVersions(String loaderType, String oldVersion, String newVersion) 
            throws IOException {
        
        loadArchive(loaderType, oldVersion);
        loadArchive(loaderType, newVersion);
        
        Map<String, byte[]> oldClasses = archiveCache.get(loaderType + "-" + oldVersion);
        Map<String, byte[]> newClasses = archiveCache.get(loaderType + "-" + newVersion);
        
        Set<String> removed = new HashSet<>(oldClasses.keySet());
        removed.removeAll(newClasses.keySet());
        
        Set<String> added = new HashSet<>(newClasses.keySet());
        added.removeAll(oldClasses.keySet());
        
        // Check for modified classes (same name, different content)
        Set<String> modified = new HashSet<>();
        for (String className : oldClasses.keySet()) {
            if (newClasses.containsKey(className)) {
                byte[] oldBytes = oldClasses.get(className);
                byte[] newBytes = newClasses.get(className);
                if (!Arrays.equals(oldBytes, newBytes)) {
                    modified.add(className);
                }
            }
        }
        
        return new ApiDiff(oldVersion, newVersion, removed, added, modified);
    }
    
    /**
     * Shutdown the download executor.
     */
    public void shutdown() {
        downloadExecutor.shutdown();
        try {
            downloadExecutor.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            downloadExecutor.shutdownNow();
        }
    }
    
    /**
     * Clear the archive cache.
     */
    public void clearCache() {
        archiveCache.clear();
    }
    
    /**
     * Get archive cache statistics.
     */
    public Map<String, Integer> getCacheStats() {
        Map<String, Integer> stats = new HashMap<>();
        for (var entry : archiveCache.entrySet()) {
            stats.put(entry.getKey(), entry.getValue().size());
        }
        return stats;
    }
    
    // --- Record classes ---
    
    public record ApiDiff(
        String oldVersion,
        String newVersion,
        Set<String> removedClasses,
        Set<String> addedClasses,
        Set<String> modifiedClasses
    ) {
        public boolean hasChanges() {
            return !removedClasses.isEmpty() || !addedClasses.isEmpty() || !modifiedClasses.isEmpty();
        }
        
        public int totalChanges() {
            return removedClasses.size() + addedClasses.size() + modifiedClasses.size();
        }
    }
}
