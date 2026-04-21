/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.regex.*;
import java.util.zip.*;

/**
 * Handles cross-mod dependencies where old mods depend on new/native mods.
 * Optimized for fast scanning with parallel processing and caching.
 */
public class CrossModDependencyResolver {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Dependencies");
    
    // Dependencies graph (modId -> list of dependency modIds) - thread-safe
    private final Map<String, List<String>> dependencyGraph = new ConcurrentHashMap<>();
    
    // Native mods (mods already for current MC version) - thread-safe
    private final Set<String> nativeMods = ConcurrentHashMap.newKeySet();
    
    private final String targetMcVersion;
    
    public CrossModDependencyResolver(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
    }
    
    /**
     * Scan all mods and build dependency graph (parallel for speed).
     */
    public void scanMods(Path modsFolder) {
        if (!Files.exists(modsFolder)) return;
        
        try (var stream = Files.list(modsFolder)) {
            List<Path> jars = stream.filter(p -> p.toString().endsWith(".jar")).toList();
            if (jars.isEmpty()) return;
            
            // Parallel scan for faster startup
            int threads = Math.max(1, Math.min(4, Runtime.getRuntime().availableProcessors() - 1));
            ExecutorService executor = Executors.newFixedThreadPool(threads);
            
            List<Future<?>> futures = new ArrayList<>(jars.size());
            for (Path jar : jars) {
                futures.add(executor.submit(() -> scanMod(jar)));
            }
            
            // Wait for all scans (with timeout)
            for (Future<?> f : futures) {
                try { f.get(3, TimeUnit.SECONDS); } catch (Exception ignored) {}
            }
            executor.shutdownNow();
            
        } catch (Exception e) {
            LOGGER.debug("Could not scan mods: {}", e.getMessage());
        }
        
        // Only log if there are dependencies
        if (!dependencyGraph.isEmpty()) {
            int totalDeps = dependencyGraph.values().stream().mapToInt(List::size).sum();
            LOGGER.info("Found {} mods with {} dependencies", dependencyGraph.size(), totalDeps);
        }
    }
    
    /**
     * Scan a single mod JAR (fast path - only read metadata).
     */
    private void scanMod(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try Fabric first (most common)
            ZipEntry fabricJson = jar.getEntry("fabric.mod.json");
            if (fabricJson != null) {
                scanFabricMod(jar, fabricJson);
                return;
            }
            
            // Try Forge/NeoForge
            ZipEntry modsToml = jar.getEntry("META-INF/mods.toml");
            if (modsToml == null) modsToml = jar.getEntry("META-INF/neoforge.mods.toml");
            if (modsToml != null) {
                scanForgeMod(jar, modsToml);
            }
        } catch (Exception e) {
            // Silently ignore - not all JARs are mods
        }
    }
    
    /**
     * Scan Fabric mod for dependencies (optimized - no API caching).
     */
    private void scanFabricMod(JarFile jar, ZipEntry entry) throws IOException {
        String content = new String(jar.getInputStream(entry).readAllBytes());
        
        // Extract mod ID
        String modId = extractJsonValue(content, "id");
        if (modId == null) return;
        
        // Extract Minecraft version and check if native
        String mcVersion = extractJsonValue(content, "minecraft");
        if (mcVersion != null && isNativeVersion(mcVersion)) {
            nativeMods.add(modId);
        }
        
        // Extract dependencies
        List<String> deps = new ArrayList<>();
        
        // Parse "depends" object
        Pattern depsPattern = Pattern.compile("\"depends\"\\s*:\\s*\\{([^}]+)\\}");
        Matcher depsMatcher = depsPattern.matcher(content);
        if (depsMatcher.find()) {
            String depsContent = depsMatcher.group(1);
            Pattern depPattern = Pattern.compile("\"([^\"]+)\"\\s*:");
            Matcher depMatcher = depPattern.matcher(depsContent);
            while (depMatcher.find()) {
                String dep = depMatcher.group(1);
                // Skip built-in deps
                if (!dep.equals("minecraft") && !dep.equals("fabricloader") && 
                    !dep.equals("fabric-api") && !dep.equals("java")) {
                    deps.add(dep);
                }
            }
        }
        
        if (!deps.isEmpty()) {
            dependencyGraph.put(modId, deps);
        }
    }
    
    /**
     * Scan Forge/NeoForge mod for dependencies (optimized).
     */
    private void scanForgeMod(JarFile jar, ZipEntry entry) throws IOException {
        String content = new String(jar.getInputStream(entry).readAllBytes());
        
        // Extract mod ID (first occurrence)
        Pattern modIdPattern = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
        Matcher modIdMatcher = modIdPattern.matcher(content);
        if (!modIdMatcher.find()) return;
        String modId = modIdMatcher.group(1);
        
        // Extract dependencies (simplified)
        List<String> deps = new ArrayList<>();
        Pattern depPattern = Pattern.compile("\\[\\[dependencies\\." + modId + "\\]\\].*?modId\\s*=\\s*\"([^\"]+)\"", Pattern.DOTALL);
        Matcher depMatcher = depPattern.matcher(content);
        while (depMatcher.find()) {
            String dep = depMatcher.group(1);
            if (!dep.equals("minecraft") && !dep.equals("forge") && !dep.equals("neoforge")) {
                deps.add(dep);
            }
        }
        
        if (!deps.isEmpty()) {
            dependencyGraph.put(modId, deps);
        }
    }
    
    /**
     * Check if a version string indicates native version (fast check).
     */
    private boolean isNativeVersion(String version) {
        if (version == null) return false;
        // Quick check - if version contains target, it's native
        return version.contains(targetMcVersion) || 
               version.replace(">=", "").replace("~", "").trim().equals(targetMcVersion);
    }
    
    /** Get dependencies for a mod. */
    public List<String> getDependencies(String modId) {
        return dependencyGraph.getOrDefault(modId, Collections.emptyList());
    }
    
    /** Check if a mod is native (no transform needed). */
    public boolean isNativeMod(String modId) {
        return nativeMods.contains(modId);
    }
    
    /** Log dependency resolution info (only if there are deps). */
    public void logResolutionInfo() {
        if (dependencyGraph.isEmpty()) return;
        
        LOGGER.info("Cross-mod dependencies detected:");
        for (var entry : dependencyGraph.entrySet()) {
            String status = nativeMods.contains(entry.getKey()) ? "native" : "transform";
            LOGGER.info("  {} [{}] → {}", entry.getKey(), status, entry.getValue());
        }
    }
    
    /** Extract JSON value by key (fast). */
    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf("\"" + key + "\"");
        if (idx == -1) return null;
        int start = json.indexOf("\"", idx + key.length() + 2) + 1;
        int end = json.indexOf("\"", start);
        return (start > 0 && end > start) ? json.substring(start, end) : null;
    }
}
