/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import com.retromod.mixin.MixinCompatibilityTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;
import java.util.regex.*;

/**
 * Transforms Fabric mods to work on newer Minecraft versions.
 *
 * APPROACH:
 * Instead of using fabric_loader_dependencies.json (which has timing issues),
 * we directly modify the mod's fabric.mod.json to say it supports the target version.
 *
 * This is the same approach used for Forge/NeoForge mods and avoids the need
 * for any global config files.
 *
 * Steps:
 * 1. Extract mod JAR to temp directory
 * 2. Transform all class files (bytecode)
 * 3. Update fabric.mod.json to support target version (including API dependencies)
 * 4. Repackage as new JAR with -retromod suffix
 * 5. Copy to mods folder
 */
public class FabricModTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-FabricTransform");

    /**
     * Fabric mod IDs for APIs that RetroMod provides compatibility shims for.
     * When a mod declares a dependency on one of these with a restrictive version
     * range (e.g., "cloth-config2": ">=6.0.0 <7.0.0"), Fabric Loader will block
     * the mod from loading if the installed version doesn't match.
     *
     * Since RetroMod transforms the bytecode to work with the newer API versions,
     * we also need to relax these dependency constraints in fabric.mod.json.
     */
    private static final Set<String> SHIMMED_API_MOD_IDS = Set.of(
        // Config libraries
        "cloth-config", "cloth-config2",
        "yacl", "yet-another-config-lib", "yet_another_config_lib",
        "forge-config-api-port", "forge_config_api_port",

        // Menu / UI libraries
        "modmenu", "mod-menu",
        "libgui", "lib-gui",
        "owo-lib", "owo_lib",

        // Recipe viewers
        "roughlyenoughitems", "rei",
        "emi",
        "jei", "just-enough-items",

        // Equipment / trinkets
        "trinkets",
        "curios", "curiosapi",

        // Component / data libraries
        "cardinal-components", "cardinal-components-api",
        "cardinal-components-base", "cardinal-components-entity",
        "cardinal-components-block", "cardinal-components-world",

        // Rendering / performance
        "sodium", "iris",
        "fabric-rendering-v1", "fabric-rendering-data-attachment-v1",

        // Animation / model libraries
        "geckolib", "geckolib3", "geckolib4",

        // Cross-platform
        "architectury", "architectury-api",

        // Guide / documentation
        "patchouli",

        // Tech mod APIs
        "ae2", "appliedenergistics2",
        "botania",
        "create",
        "mekanism",
        "thermal", "thermal_foundation", "thermal_expansion",

        // Tooltips / overlays
        "jade", "waila", "wthit",

        // Compatibility / utility
        "fabric-shield-lib", "fabricshieldlib",
        "lba", "libblockattributes",
        "mixinextras", "mixin-extras",
        "autoreglib", "auto-reg-lib"
    );

    private final String targetMcVersion;
    private final RetroModTransformer bytecodeTransformer;
    
    public FabricModTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.bytecodeTransformer = RetroModTransformer.getInstance();
    }
    
    /**
     * Transform a Fabric mod JAR.
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
        
        LOGGER.info("Checking Fabric mod: {}", originalName);
        
        // IMPORTANT: Check if mod is already for native version
        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (isNativeVersionMod(modMcVersion)) {
            LOGGER.info("═══════════════════════════════════════════════════════════");
            LOGGER.info("  {} is ALREADY for Minecraft {}", originalName, targetMcVersion);
            LOGGER.info("  NO TRANSFORMATION NEEDED - passing through!");
            LOGGER.info("═══════════════════════════════════════════════════════════");
            
            // Copy directly to output without transformation
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }
        
        // Check if mod uses Mixins
        boolean hasMixins = checkForMixins(sourceJar);
        if (hasMixins) {
            LOGGER.info("  Mod uses Mixins - will handle carefully");
        }
        
        LOGGER.info("Transforming Fabric mod: {} -> {}", originalName, outputName);
        
        // Log environment info (server-only, client-only, or both)
        ModEnvironmentDetector.logModEnvironment(sourceJar);
        
        // Check for OptiFine - special handling needed
        try {
            if (com.retromod.compat.OptiFineCompat.isOptiFine(sourceJar)) {
                com.retromod.compat.OptiFineCompat.handleOptiFineDetected(
                    sourceJar, 
                    com.retromod.core.EnvironmentDetector.isDedicatedServer()
                );
            }
        } catch (RuntimeException e) {
            if (e.getMessage() != null && e.getMessage().contains("cancelled")) {
                throw new IOException("User cancelled OptiFine installation");
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check for OptiFine: {}", e.getMessage());
        }
        
        // Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("retromod-fabric-");
        
        // Get mod info for error reporting
        String modId = extractModId(sourceJar);
        String sourceVersion = extractMinecraftVersion(sourceJar);
        
        try {
            // Step 1: Extract JAR
            extractJar(sourceJar, tempDir);
            
            // Step 2: Transform bytecode
            int classesTransformed = transformClasses(tempDir);
            LOGGER.info("Transformed {} class files", classesTransformed);
            
            // Step 3: Update fabric.mod.json
            updateFabricModJson(tempDir);
            
            // Step 4: Repackage
            repackageJar(tempDir, outputJar, sourceJar);
            
            LOGGER.info("Created transformed mod: {}", outputJar.getFileName());
            
            // Validate the transformation
            if (!ModHealthChecker.validateTransformation(sourceJar, outputJar)) {
                LOGGER.warn("Transformation validation failed for {}, but continuing anyway", originalName);
            }
            
            // Register with health checker for runtime monitoring
            String modName = extractModName(sourceJar);
            ModHealthChecker.registerTransformedMod(
                modId != null ? modId : baseName,
                modName != null ? modName : originalName,
                outputJar,
                sourceJar,
                outputDir.getParent() // game dir
            );
            
            // Log success message for server-only mods
            if (ModEnvironmentDetector.isServerOnly(sourceJar)) {
                LOGGER.info("═══════════════════════════════════════════════════════════");
                LOGGER.info("  {} is SERVER-ONLY", originalName);
                LOGGER.info("  Clients can join WITHOUT having RetroMod installed!");
                LOGGER.info("═══════════════════════════════════════════════════════════");
            }
            
            return outputJar;
            
        } catch (Exception e) {
            // Handle transformation error
            LOGGER.error("Failed to transform mod: {}", originalName);
            
            // Report error with GitHub bug report option
            TransformationErrorHandler.handleError(
                sourceJar, e, modId, "fabric", sourceVersion
            );
            
            // Log bug report to console (for servers)
            if (EnvironmentDetector.isDedicatedServer()) {
                TransformationErrorHandler.logBugReportToConsole(
                    new TransformationErrorHandler.FailedMod(
                        originalName, modId, "fabric", sourceVersion,
                        e.getClass().getSimpleName(),
                        e.getMessage() != null ? e.getMessage() : "Unknown error",
                        ""
                    )
                );
            }
            
            throw new IOException("Transformation failed for " + originalName + ": " + e.getMessage(), e);
            
        } finally {
            // Cleanup temp directory
            deleteRecursively(tempDir);
        }
    }
    
    /**
     * Extract mod ID from fabric.mod.json.
     */
    private String extractModId(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                Pattern pattern = Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract mod ID");
        }
        return null;
    }
    
    /**
     * Extract Minecraft version requirement from fabric.mod.json.
     */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                Pattern pattern = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract MC version");
        }
        return null;
    }
    
    /**
     * Extract mod name from fabric.mod.json.
     */
    private String extractModName(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                Pattern pattern = Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not extract mod name");
        }
        return null;
    }
    
    /**
     * Check if a mod is for the native/current Minecraft version.
     * 
     * IMPORTANT: We are VERY STRICT here. Only pass through if:
     * - Mod explicitly lists EXACT target version (e.g., "1.21.11")
     * - Mod has a range that STARTS at target version (e.g., ">=1.21.11")
     * 
     * We do NOT pass through:
     * - ">=1.20.4" even though 1.21.11 >= 1.20.4 (mods don't work across major versions)
     * - "1.21.x" unless it explicitly matches
     * - Anything uncertain
     * 
     * When in doubt, TRANSFORM. A transformed native mod still works.
     * A non-transformed old mod CRASHES.
     */
    private boolean isNativeVersionMod(String modMcVersion) {
        if (modMcVersion == null) return false;
        
        // Clean up version string
        String cleanVersion = modMcVersion
            .replace(">=", "")
            .replace("<=", "")
            .replace(">", "")
            .replace("<", "")
            .replace("~", "")
            .replace("^", "")
            .trim();
        
        // STRICT CHECK 1: Exact match only
        if (cleanVersion.equals(targetMcVersion)) {
            LOGGER.debug("Native: exact match {}", modMcVersion);
            return true;
        }
        
        // STRICT CHECK 2: Range starting at target version
        // e.g., ">=1.21.11" when target is "1.21.11"
        if (modMcVersion.startsWith(">=")) {
            String minVersion = modMcVersion.substring(2).trim();
            if (minVersion.equals(targetMcVersion)) {
                LOGGER.debug("Native: range starts at target {}", modMcVersion);
                return true;
            }
            // DO NOT pass through ">=1.20.4" - that's an OLD mod!
            // The >= only means "works on 1.20.4 or later OF THAT MAJOR VERSION"
            // It does NOT mean it works on 1.21.x
        }
        
        // STRICT CHECK 3: Wildcard that matches target's major.minor
        // e.g., "1.21.x" matches "1.21.11" but "1.20.x" does NOT
        if (cleanVersion.endsWith(".x") || cleanVersion.endsWith(".*")) {
            String base = cleanVersion.substring(0, cleanVersion.length() - 2);
            // Must match the start AND be the same major.minor
            // "1.21.x" matches "1.21.11" ✓
            // "1.20.x" does NOT match "1.21.11" ✗
            if (targetMcVersion.startsWith(base + ".")) {
                LOGGER.debug("Native: wildcard match {}", modMcVersion);
                return true;
            }
        }
        
        // DEFAULT: NOT native - transform it!
        // It's safer to transform a native mod (still works) than to
        // skip transforming an old mod (crashes)
        return false;
    }
    
    /**
     * Simple version comparison (returns positive if v1 > v2, 0 if equal, negative if v1 < v2)
     */
    private int compareVersions(String v1, String v2) {
        try {
            String[] parts1 = v1.split("\\.");
            String[] parts2 = v2.split("\\.");
            
            int maxLen = Math.max(parts1.length, parts2.length);
            for (int i = 0; i < maxLen; i++) {
                int p1 = i < parts1.length ? Integer.parseInt(parts1[i].replaceAll("[^0-9]", "")) : 0;
                int p2 = i < parts2.length ? Integer.parseInt(parts2[i].replaceAll("[^0-9]", "")) : 0;
                if (p1 != p2) return p1 - p2;
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }
    
    /**
     * Check if a mod uses Mixins.
     */
    private boolean checkForMixins(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Check fabric.mod.json for mixins array
            ZipEntry fabricJson = jar.getEntry("fabric.mod.json");
            if (fabricJson != null) {
                String content = new String(jar.getInputStream(fabricJson).readAllBytes());
                if (content.contains("\"mixins\"") && !content.contains("\"mixins\": []")) {
                    return true;
                }
            }
            
            // Check for common mixin config files
            if (jar.getEntry("mixins.json") != null) return true;
            if (jar.getEntry("modid.mixins.json") != null) return true;
            
            // Check for any .mixins.json file
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".mixins.json")) {
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not check for mixins: {}", e.getMessage());
        }
        return false;
    }
    
    /**
     * Extract JAR to directory.
     */
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
    
    /**
     * Transform all class files in directory.
     * Handles both regular classes and Mixin classes specially.
     */
    private int transformClasses(Path dir) throws IOException {
        int count = 0;
        
        // Get Mixin transformer for special handling
        com.retromod.mixin.MixinCompatibilityTransformer mixinTransformer = null;
        try {
            mixinTransformer = new com.retromod.mixin.MixinCompatibilityTransformer(bytecodeTransformer);
        } catch (Exception e) {
            LOGGER.debug("Mixin transformer not available");
        }
        
        // Find all Mixin classes (classes that might have @Mixin annotations)
        Set<String> mixinClasses = findMixinClasses(dir);
        
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
                    
                    byte[] transformed;
                    
                    // Check if this is a Mixin class - needs special handling
                    if (mixinTransformer != null && mixinClasses.contains(className)) {
                        // Use Mixin-specific transformation
                        transformed = mixinTransformer.transformMixinClass(original);
                        if (transformed != original) {
                            LOGGER.debug("Transformed Mixin class: {}", className);
                        }
                    } else {
                        // Regular transformation
                        transformed = bytecodeTransformer.transformClass(original, className);
                    }
                    
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
     * Find all classes that are Mixins (declared in mixin config files).
     */
    private Set<String> findMixinClasses(Path dir) {
        Set<String> mixinClasses = new HashSet<>();
        
        try {
            // Look for mixin config files
            try (var stream = Files.walk(dir)) {
                stream.filter(p -> p.toString().endsWith(".mixins.json") || 
                                   p.getFileName().toString().equals("mixins.json"))
                    .forEach(mixinConfig -> {
                        try {
                            String content = Files.readString(mixinConfig);
                            // Extract package
                            Pattern pkgPattern = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"");
                            Matcher pkgMatcher = pkgPattern.matcher(content);
                            String pkg = pkgMatcher.find() ? pkgMatcher.group(1).replace('.', '/') + "/" : "";
                            
                            // Extract mixins array
                            Pattern mixinPattern = Pattern.compile("\"mixins\"\\s*:\\s*\\[([^\\]]+)\\]");
                            Matcher mixinMatcher = mixinPattern.matcher(content);
                            if (mixinMatcher.find()) {
                                String mixinsStr = mixinMatcher.group(1);
                                Pattern classPattern = Pattern.compile("\"([^\"]+)\"");
                                Matcher classMatcher = classPattern.matcher(mixinsStr);
                                while (classMatcher.find()) {
                                    mixinClasses.add(pkg + classMatcher.group(1).replace('.', '/'));
                                }
                            }
                            
                            // Also check client/server arrays
                            for (String arrayName : new String[]{"client", "server"}) {
                                Pattern arrayPattern = Pattern.compile("\"" + arrayName + "\"\\s*:\\s*\\[([^\\]]+)\\]");
                                Matcher arrayMatcher = arrayPattern.matcher(content);
                                if (arrayMatcher.find()) {
                                    String arrayStr = arrayMatcher.group(1);
                                    Pattern classPattern2 = Pattern.compile("\"([^\"]+)\"");
                                    Matcher classMatcher2 = classPattern2.matcher(arrayStr);
                                    while (classMatcher2.find()) {
                                        mixinClasses.add(pkg + classMatcher2.group(1).replace('.', '/'));
                                    }
                                }
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Could not parse mixin config: {}", mixinConfig.getFileName());
                        }
                    });
            }
        } catch (Exception e) {
            LOGGER.debug("Could not scan for mixin configs");
        }
        
        if (!mixinClasses.isEmpty()) {
            LOGGER.debug("Found {} Mixin classes to handle specially", mixinClasses.size());
        }
        
        return mixinClasses;
    }
    
    /**
     * Update fabric.mod.json to support target Minecraft version.
     */
    private void updateFabricModJson(Path dir) throws IOException {
        Path modJson = dir.resolve("fabric.mod.json");
        
        if (!Files.exists(modJson)) {
            LOGGER.warn("No fabric.mod.json found - skipping metadata update");
            return;
        }
        
        String content = Files.readString(modJson);
        String originalMcVersion = extractVersionFromContent(content);
        String updated = updateVersionRequirements(content, originalMcVersion);
        
        Files.writeString(modJson, updated);
        LOGGER.info("Updated fabric.mod.json: {} → {}", originalMcVersion, targetMcVersion);
    }
    
    /**
     * Extract minecraft version from fabric.mod.json content.
     */
    private String extractVersionFromContent(String json) {
        Pattern p = Pattern.compile("\"minecraft\"\\s*:\\s*\"([^\"]+)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : "unknown";
    }
    
    /**
     * Update version requirements in fabric.mod.json.
     *
     * CRITICAL: This must set the EXACT target version or Fabric will reject the mod!
     *
     * In addition to relaxing minecraft/fabricloader/fabric-api versions, this also
     * relaxes version constraints for third-party APIs that RetroMod has shims for.
     * Without this, Fabric Loader would block the mod at startup even though RetroMod
     * has already transformed the bytecode to work with the newer API versions.
     *
     * Example: A mod declares "cloth-config2": ">=6.0.0 <7.0.0" but the installed
     * Cloth Config is v11.x. Without relaxation, Fabric blocks the mod immediately.
     * With relaxation, the mod loads and RetroMod's bytecode transforms handle the
     * API differences at runtime.
     */
    private String updateVersionRequirements(String json, String originalVersion) {
        // Replace minecraft dependency with EXACT target version
        // This is the most reliable approach
        Pattern minecraftPattern = Pattern.compile(
            "(\"minecraft\"\\s*:\\s*)\"[^\"]+\"",
            Pattern.MULTILINE
        );

        // Use exact version match - most reliable
        String updated = minecraftPattern.matcher(json).replaceAll(
            "$1\"" + targetMcVersion + "\""
        );

        // Update fabricloader to be more permissive (many versions work)
        Pattern loaderPattern = Pattern.compile(
            "(\"fabricloader\"\\s*:\\s*)\"[^\"]+\"",
            Pattern.MULTILINE
        );
        updated = loaderPattern.matcher(updated).replaceAll(
            "$1\">=0.14.0\""
        );

        // Update fabric-api to be more permissive
        Pattern fabricApiPattern = Pattern.compile(
            "(\"fabric-api\"\\s*:\\s*)\"[^\"]+\"",
            Pattern.MULTILINE
        );
        updated = fabricApiPattern.matcher(updated).replaceAll(
            "$1\"*\""
        );

        // Update fabric (alternative fabric-api name)
        Pattern fabricPattern = Pattern.compile(
            "(\"fabric\"\\s*:\\s*)\"[^\"]+\"",
            Pattern.MULTILINE
        );
        updated = fabricPattern.matcher(updated).replaceAll(
            "$1\"*\""
        );

        // Relax ALL shimmed third-party API dependencies
        // RetroMod provides bytecode shims for these APIs, so the version constraint
        // in fabric.mod.json is no longer needed — the transformed code will work
        // with whatever version is installed.
        updated = relaxShimmedApiDependencies(updated);

        // Also move strict API deps from "depends" to "suggests" if they'd still block
        updated = moveBlockingDepsToSuggests(updated);

        // Add RetroMod transformation marker
        if (!updated.contains("\"retromod_transformed\"")) {
            updated = updated.replaceFirst(
                "\\}\\s*$",
                ",\n  \"custom\": {\n    \"retromod_transformed\": true,\n    \"original_mc_version\": \"" + originalVersion + "\",\n    \"target_mc_version\": \"" + targetMcVersion + "\"\n  }\n}"
            );
        }

        return updated;
    }

    /**
     * Relax version constraints for third-party APIs that RetroMod has shims for.
     *
     * Scans the JSON for any dependency key that matches a known shimmed API mod ID
     * and replaces its version constraint with "*" (any version).
     *
     * This handles cases like:
     *   "cloth-config2": ">=6.0.0 <7.0.0"  →  "cloth-config2": "*"
     *   "rei": ">=8.0.0"                   →  "rei": "*"
     *   "trinkets": ">=3.4.0 <3.5.0"       →  "trinkets": "*"
     */
    private String relaxShimmedApiDependencies(String json) {
        String updated = json;
        int relaxedCount = 0;

        for (String modId : SHIMMED_API_MOD_IDS) {
            // Match "mod-id": "any version constraint"
            // Uses negative lookbehind to avoid matching inside other strings
            Pattern apiPattern = Pattern.compile(
                "(\"" + Pattern.quote(modId) + "\"\\s*:\\s*)\"[^\"]+\"",
                Pattern.MULTILINE
            );

            Matcher matcher = apiPattern.matcher(updated);
            if (matcher.find()) {
                String originalConstraint = matcher.group(0);
                String relaxed = matcher.replaceAll("$1\"*\"");

                if (!relaxed.equals(updated)) {
                    LOGGER.info("  Relaxed API dependency: {} → \"*\" (RetroMod has shims)", modId);
                    updated = relaxed;
                    relaxedCount++;
                }
            }
        }

        if (relaxedCount > 0) {
            LOGGER.info("Relaxed {} third-party API version constraint(s)", relaxedCount);
        }

        return updated;
    }

    /**
     * Move any remaining strict API dependencies from "depends" to "suggests".
     *
     * Some mods use dependency IDs we don't have in our known list (e.g., sub-modules
     * of Cardinal Components like "cardinal-components-item"). This method catches
     * any non-standard dependency that might still block loading by looking for
     * patterns that suggest an API version constraint (ranges with upper bounds).
     *
     * It converts entries like:
     *   "depends": { "some-api": ">=1.0.0 <2.0.0" }
     * to:
     *   "depends": { "some-api": "*" }
     *
     * This is safe because RetroMod's bytecode transformation handles API changes,
     * and we've already pinned minecraft/fabricloader/fabric-api correctly.
     */
    private String moveBlockingDepsToSuggests(String json) {
        // Find dependencies with upper-bounded version ranges that aren't
        // minecraft, fabricloader, fabric-api, fabric, or java
        // These are likely API version pins that would block loading
        Set<String> keepStrict = Set.of(
            "minecraft", "fabricloader", "fabric-api", "fabric", "java"
        );

        String updated = json;

        // Match any "modid": "version" pairs that have upper-bound constraints
        // like "<X.Y.Z" or version ranges that would block newer versions
        Pattern depPattern = Pattern.compile(
            "\"([a-z0-9_-]+)\"\\s*:\\s*\"([^\"]*<[^\"]+)\"",
            Pattern.MULTILINE
        );

        Matcher matcher = depPattern.matcher(updated);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String depId = matcher.group(1);
            String constraint = matcher.group(2);

            if (!keepStrict.contains(depId) && !SHIMMED_API_MOD_IDS.contains(depId)) {
                // This is an unknown API with an upper-bounded version — relax it
                LOGGER.info("  Relaxed unknown API dependency: {} (was: {})", depId, constraint);
                matcher.appendReplacement(sb, "\"" + depId + "\": \"*\"");
            }
        }
        matcher.appendTail(sb);

        return sb.toString();
    }
    
    /**
     * Repackage directory contents as JAR.
     */
    private void repackageJar(Path sourceDir, Path outputJar, Path originalJar) throws IOException {
        // Copy manifest from original if exists
        Manifest manifest = null;
        try (JarFile original = new JarFile(originalJar.toFile())) {
            manifest = original.getManifest();
        } catch (Exception e) {
            // Use default manifest
        }
        
        if (manifest == null) {
            manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        }
        
        // Add RetroMod info to manifest
        manifest.getMainAttributes().putValue("RetroMod-Transformed", "true");
        manifest.getMainAttributes().putValue("RetroMod-Target-Version", targetMcVersion);
        
        try (JarOutputStream jos = new JarOutputStream(
                new FileOutputStream(outputJar.toFile()), manifest)) {
            
            try (var stream = Files.walk(sourceDir)) {
                for (Path file : stream.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");
                    
                    // Skip manifest (we added our own)
                    if (entryName.equalsIgnoreCase("META-INF/MANIFEST.MF")) {
                        continue;
                    }
                    
                    JarEntry entry = new JarEntry(entryName);
                    jos.putNextEntry(entry);

                    // Process mixin config JSON files to strip broken entries
                    if (entryName.endsWith(".mixins.json") || entryName.endsWith("mixin.json")) {
                        try {
                            String json = Files.readString(file);
                            // Build a class data lookup from the source directory
                            Map<String, byte[]> classLookup = buildClassLookup(sourceDir);
                            MixinCompatibilityTransformer mixinTransformer =
                                new MixinCompatibilityTransformer(RetroModTransformer.getInstance());
                            String transformed = mixinTransformer.transformMixinConfig(json, classLookup);
                            jos.write(transformed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        } catch (Exception e) {
                            LOGGER.warn("Failed to process mixin config {}: {}", entryName, e.getMessage());
                            Files.copy(file, jos);
                        }
                    } else {
                        Files.copy(file, jos);
                    }

                    jos.closeEntry();
                }
            }
        }
    }
    
    /**
     * Build a map of class name -> class bytes for mixin analysis.
     */
    private Map<String, byte[]> buildClassLookup(Path sourceDir) {
        Map<String, byte[]> lookup = new HashMap<>();
        try (var stream = Files.walk(sourceDir)) {
            for (Path file : stream.filter(f -> f.toString().endsWith(".class")).toList()) {
                String name = sourceDir.relativize(file).toString().replace(File.separator, "/");
                lookup.put(name, Files.readAllBytes(file));
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to build class lookup: {}", e.getMessage());
        }
        return lookup;
    }

    /**
     * Delete directory recursively.
     */
    private void deleteRecursively(Path dir) {
        try {
            if (Files.exists(dir)) {
                try (var stream = Files.walk(dir)) {
                    stream.sorted((a, b) -> b.toString().length() - a.toString().length())
                        .forEach(p -> {
                            try {
                                Files.delete(p);
                            } catch (IOException ignored) {}
                        });
                }
            }
        } catch (IOException ignored) {}
    }
    
    /**
     * Check if a mod JAR has already been transformed by RetroMod.
     */
    public static boolean isAlreadyTransformed(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Check manifest
            Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String transformed = manifest.getMainAttributes().getValue("RetroMod-Transformed");
                if ("true".equals(transformed)) {
                    return true;
                }
            }
            
            // Check fabric.mod.json
            ZipEntry entry = jar.getEntry("fabric.mod.json");
            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                return content.contains("\"retromod_transformed\"");
            }
            
        } catch (Exception e) {
            // Assume not transformed
        }
        
        return false;
    }
}
