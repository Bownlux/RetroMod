/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.embedder;

import java.util.Set;

/**
 * Information about a mod's version and compatibility requirements.
 */
public record ModVersionInfo(
    String modId,
    String modVersion,
    String targetMcVersion,
    String modLoaderType,      // "fabric", "forge", "neoforge"
    String modLoaderVersion,
    Set<String> modPackages,   // Java packages containing mod code
    Set<String> apiDependencies,
    boolean usesRemovedApis
) {
    /**
     * Check if this mod needs transformation to work on the target version.
     */
    public boolean needsTransformation(String currentMcVersion) {
        if (targetMcVersion == null || targetMcVersion.equals(currentMcVersion)) {
            return false;
        }
        
        // Parse and compare versions
        int[] target = parseVersion(targetMcVersion);
        int[] current = parseVersion(currentMcVersion);
        
        // Needs transformation if built for older version
        return compareVersions(target, current) < 0;
    }
    
    private int[] parseVersion(String version) {
        // Strip pre-release suffix (e.g., "1.0.0-beta.4" -> "1.0.0")
        int hyphen = version.indexOf('-');
        if (hyphen > 0) version = version.substring(0, hyphen);

        String[] parts = version.split("\\.");
        int[] result = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            try {
                result[i] = Integer.parseInt(parts[i]);
            } catch (NumberFormatException e) {
                result[i] = 0;
            }
        }
        return result;
    }
    
    private int compareVersions(int[] a, int[] b) {
        int len = Math.max(a.length, b.length);
        for (int i = 0; i < len; i++) {
            int va = i < a.length ? a[i] : 0;
            int vb = i < b.length ? b[i] : 0;
            if (va != vb) return va - vb;
        }
        return 0;
    }
}
