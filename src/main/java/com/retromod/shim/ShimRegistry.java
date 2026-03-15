/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim;

import com.retromod.core.VersionShim;

import java.util.*;
import java.util.logging.Logger;

/**
 * Registry for managing loaded version shims.
 *
 * <p>Supports fuzzy version matching for intermediate Minecraft versions. Shims are
 * registered for "milestone" versions (e.g. 1.14.4, 1.15.2, 1.16.5), but mods may
 * target any intermediate version (e.g. 1.14.1, 1.15.1, 1.16.2). When a mod targets
 * an intermediate version, the registry automatically resolves it to the nearest
 * milestone version before performing BFS pathfinding. This allows shim chains to be
 * found even when no shim is registered with the exact source version the mod targets.</p>
 *
 * <p>For Minecraft 1.17 and later, every version has its own shim, so no fuzzy
 * matching is needed.</p>
 */
public class ShimRegistry {

    private static final Logger LOGGER = Logger.getLogger(ShimRegistry.class.getName());

    /**
     * Maps intermediate MC versions to their nearest milestone version.
     * Pre-1.17 versions are grouped into milestone releases that represent
     * the most stable/complete version of that minor release line.
     */
    private static final Map<String, String> VERSION_ALIASES;

    static {
        Map<String, String> aliases = new HashMap<>();

        // 1.13.x → 1.13.2
        aliases.put("1.13",   "1.13.2");
        aliases.put("1.13.1", "1.13.2");

        // 1.14.x → 1.14.4
        aliases.put("1.14",   "1.14.4");
        aliases.put("1.14.1", "1.14.4");
        aliases.put("1.14.2", "1.14.4");
        aliases.put("1.14.3", "1.14.4");

        // 1.15.x → 1.15.2
        aliases.put("1.15",   "1.15.2");
        aliases.put("1.15.1", "1.15.2");

        // 1.16.x → 1.16.5
        aliases.put("1.16",   "1.16.5");
        aliases.put("1.16.1", "1.16.5");
        aliases.put("1.16.2", "1.16.5");
        aliases.put("1.16.3", "1.16.5");
        aliases.put("1.16.4", "1.16.5");

        VERSION_ALIASES = Collections.unmodifiableMap(aliases);
    }

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
     * Resolve a Minecraft version to its nearest milestone version.
     *
     * <p>If the given version is an intermediate version (e.g. 1.16.2), it will be
     * mapped to the corresponding milestone (e.g. 1.16.5). If the version is already
     * a milestone or has no known alias, it is returned as-is.</p>
     *
     * @param version the Minecraft version string to resolve
     * @return the milestone version, or the original version if no alias exists
     */
    public static String resolveVersion(String version) {
        return VERSION_ALIASES.getOrDefault(version, version);
    }

    /**
     * Get all known Minecraft versions that this registry can handle.
     *
     * <p>This includes both milestone versions (that have registered shims) and
     * intermediate versions (that are mapped to milestones via {@link #VERSION_ALIASES}).</p>
     *
     * @return an unmodifiable set of all known version strings
     */
    public Set<String> getAllKnownVersions() {
        Set<String> versions = new HashSet<>();

        // Add all milestone versions that have registered shims
        versions.addAll(shimsBySourceVersion.keySet());

        // Add all intermediate versions from the alias map
        versions.addAll(VERSION_ALIASES.keySet());

        return Collections.unmodifiableSet(versions);
    }

    /**
     * Find the best shim chain to transform from sourceVersion to targetVersion.
     *
     * <p>If the sourceVersion is an intermediate version (e.g. 1.16.2), it will be
     * automatically resolved to the nearest milestone version (e.g. 1.16.5) before
     * performing BFS pathfinding. The same resolution is applied to the targetVersion.</p>
     *
     * <p>For example, to go from 1.21.7 to 1.21.10, this might return:
     * [Shim_1.21.7_to_1.21.8, Shim_1.21.8_to_1.21.9, Shim_1.21.9_to_1.21.10]</p>
     */
    public List<VersionShim> findShimChain(String modLoader, String sourceVersion, String targetVersion) {
        // Resolve intermediate versions to their nearest milestone
        String resolvedSource = resolveVersion(sourceVersion);
        String resolvedTarget = resolveVersion(targetVersion);

        if (!resolvedSource.equals(sourceVersion)) {
            LOGGER.info("Resolved source version " + sourceVersion + " → " + resolvedSource
                    + " (fuzzy version matching)");
        }
        if (!resolvedTarget.equals(targetVersion)) {
            LOGGER.info("Resolved target version " + targetVersion + " → " + resolvedTarget
                    + " (fuzzy version matching)");
        }

        // BFS to find shortest path through version shims
        Queue<ShimPath> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        queue.add(new ShimPath(resolvedSource, new ArrayList<>()));
        visited.add(resolvedSource);

        while (!queue.isEmpty()) {
            ShimPath current = queue.poll();

            if (current.version.equals(resolvedTarget)) {
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
