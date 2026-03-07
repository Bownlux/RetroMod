/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim;

import com.retromod.core.VersionShim;

import java.util.*;

/**
 * Registry for managing loaded version shims.
 */
public class ShimRegistry {
    
    // Maps: sourceVersion -> List of shims for that version
    private final Map<String, List<VersionShim>> shimsBySourceVersion = new HashMap<>();
    
    // Maps: modLoader -> sourceVersion -> List of shims
    private final Map<String, Map<String, List<VersionShim>>> shimsByLoaderAndVersion = new HashMap<>();
    
    // All registered shims
    private final List<VersionShim> allShims = new ArrayList<>();
    
    /**
     * Register a version shim.
     */
    public void register(VersionShim shim) {
        allShims.add(shim);
        
        // Index by source version
        shimsBySourceVersion
            .computeIfAbsent(shim.getSourceVersion(), k -> new ArrayList<>())
            .add(shim);
        
        // Index by loader and version
        shimsByLoaderAndVersion
            .computeIfAbsent(shim.getModLoaderType(), k -> new HashMap<>())
            .computeIfAbsent(shim.getSourceVersion(), k -> new ArrayList<>())
            .add(shim);
    }
    
    /**
     * Get all shims for a specific source version.
     */
    public List<VersionShim> getShimsForVersion(String sourceVersion) {
        return shimsBySourceVersion.getOrDefault(sourceVersion, Collections.emptyList());
    }
    
    /**
     * Get shims for a specific mod loader and source version.
     */
    public List<VersionShim> getShimsForLoaderAndVersion(String modLoader, String sourceVersion) {
        Map<String, List<VersionShim>> byVersion = shimsByLoaderAndVersion.get(modLoader);
        if (byVersion == null) return Collections.emptyList();
        return byVersion.getOrDefault(sourceVersion, Collections.emptyList());
    }
    
    /**
     * Find the best shim chain to transform from sourceVersion to targetVersion.
     * 
     * For example, to go from 1.21.7 to 1.21.10, this might return:
     * [Shim_1.21.7_to_1.21.8, Shim_1.21.8_to_1.21.9, Shim_1.21.9_to_1.21.10]
     */
    public List<VersionShim> findShimChain(String modLoader, String sourceVersion, String targetVersion) {
        // BFS to find shortest path through version shims
        Queue<ShimPath> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();
        
        queue.add(new ShimPath(sourceVersion, new ArrayList<>()));
        visited.add(sourceVersion);
        
        while (!queue.isEmpty()) {
            ShimPath current = queue.poll();
            
            if (current.version.equals(targetVersion)) {
                return current.shims;
            }
            
            for (VersionShim shim : getShimsForLoaderAndVersion(modLoader, current.version)) {
                String nextVersion = shim.getTargetVersion();
                
                if (!visited.contains(nextVersion)) {
                    visited.add(nextVersion);
                    
                    List<VersionShim> newPath = new ArrayList<>(current.shims);
                    newPath.add(shim);
                    
                    queue.add(new ShimPath(nextVersion, newPath));
                }
            }
        }
        
        // No path found
        return Collections.emptyList();
    }
    
    /**
     * Get all registered shims.
     */
    public List<VersionShim> getAllShims() {
        return Collections.unmodifiableList(allShims);
    }
    
    /**
     * Get supported source versions for a mod loader.
     */
    public Set<String> getSupportedVersions(String modLoader) {
        Map<String, List<VersionShim>> byVersion = shimsByLoaderAndVersion.get(modLoader);
        if (byVersion == null) return Collections.emptySet();
        return Collections.unmodifiableSet(byVersion.keySet());
    }
    
    private record ShimPath(String version, List<VersionShim> shims) {}
}
