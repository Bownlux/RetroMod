/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.legacy;

import java.nio.file.*;
import java.util.*;

/**
 * Analysis result for a legacy mod.
 */
public class LegacyModAnalysis {
    public Path modPath;
    public LegacyModSupport.ModLoaderType modLoader;
    public String targetMcVersion;
    public LegacyModSupport.Epoch sourceEpoch;
    public int classFileVersion;
    public int sourceJavaVersion;
    public List<EpochTransition> epochTransitions = new ArrayList<>();
    public boolean needsVirtualLoader;
    public String complexity;
    public Map<String, Set<String>> apiUsage = new HashMap<>();
    
    public LegacyModAnalysis(Path path) {
        this.modPath = path;
    }
}
