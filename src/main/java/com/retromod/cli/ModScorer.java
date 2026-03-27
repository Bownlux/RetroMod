/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.cli;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.RetroModTransformer.*;
import com.retromod.embedder.ModVersionInfo;

import org.objectweb.asm.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Analyzes a mod JAR and produces a compatibility score indicating how
 * well it will work on the target MC version (26.1).
 *
 * Supports all three mod loaders: Fabric, Forge, and NeoForge.
 * Auto-detects the loader type from JAR metadata and scores against
 * the correct set of redirects and loader-specific APIs.
 *
 * Scans all .class files with ASM to collect external references (classes,
 * methods, fields), then checks each reference against a combined index
 * built from the MC client JAR, loader-specific APIs, and RetroMod's
 * registered redirects.
 */
public class ModScorer {

    // --- Mod loader types ---
    public enum ModLoader {
        FABRIC("fabric"),
        FORGE("forge"),
        NEOFORGE("neoforge"),
        UNKNOWN("unknown");

        private final String id;

        ModLoader(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public String toString() {
            return switch (this) {
                case FABRIC -> "Fabric";
                case FORGE -> "Forge";
                case NEOFORGE -> "NeoForge";
                case UNKNOWN -> "Unknown";
            };
        }
    }

    // --- Index of what exists in the target MC version ---
    private final Set<String> mcClasses = new HashSet<>(8000);
    private final Map<String, Set<String>> mcMethods = new HashMap<>(8000);  // class -> set of "name desc"
    private final Map<String, Set<String>> mcFields = new HashMap<>(8000);   // class -> set of "name"
    // Class hierarchy: class -> superclass (for walking inheritance chain)
    private final Map<String, String> mcSuperclasses = new HashMap<>(8000);
    private final Map<String, String[]> mcInterfaces = new HashMap<>(8000);

    // --- Redirects from RetroMod shims/polyfills ---
    private final Map<MethodKey, MethodTarget> methodRedirects;
    private final Map<String, String> classRedirects;
    private final Map<FieldKey, FieldTarget> fieldRedirects;

    // --- Prefixes that are always considered OK ---
    // These packages ship with MC/JVM/loaders, so references to them are never "missing".
    // We skip them during scoring to avoid false negatives.
    private static final String[] LIBRARY_PREFIXES = {
        "java/", "javax/", "jdk/", "sun/",
        "com/google/gson/", "com/google/common/",
        "org/slf4j/", "org/apache/logging/",
        "org/apache/commons/", "org/apache/maven/",
        "org/objectweb/asm/", "org/lwjgl/",
        "io/netty/", "com/mojang/logging/",
        "com/mojang/brigadier/", "com/mojang/authlib/",
        "com/mojang/datafixers/", "com/mojang/serialization/",
        "com/mojang/math/", "it/unimi/dsi/fastutil/",
        "org/joml/",
        // Mod loader internals (common to all loaders)
        "net/fabricmc/loader/", "net/fabricmc/api/",
        "cpw/mods/", "net/minecraftforge/fml/",
        "net/neoforged/fml/",
        // RetroMod itself
        "com/retromod/",
    };

    // --- Fabric API package prefixes ---
    private static final String[] FABRIC_API_PREFIXES = {
        "net/fabricmc/fabric/api/",
        "net/fabricmc/fabric/impl/",
        "net/fabricmc/fabric/mixin/",
    };

    // --- Forge API package prefixes ---
    private static final String[] FORGE_API_PREFIXES = {
        "net/minecraftforge/common/",
        "net/minecraftforge/client/",
        "net/minecraftforge/server/",
        "net/minecraftforge/event/",
        "net/minecraftforge/eventbus/",
        "net/minecraftforge/registries/",
        "net/minecraftforge/network/",
        "net/minecraftforge/items/",
        "net/minecraftforge/fluids/",
        "net/minecraftforge/energy/",
        "net/minecraftforge/data/",
        "net/minecraftforge/resource/",
    };

    // --- NeoForge API package prefixes ---
    private static final String[] NEOFORGE_API_PREFIXES = {
        "net/neoforged/neoforge/",
        "net/neoforged/bus/",
        "net/neoforged/api/",
        "net/neoforged/neoforgespi/",
    };

    // --- Results ---
    private final ScoreResult result = new ScoreResult();

    public ModScorer(RetroModTransformer transformer) {
        this.methodRedirects = transformer.getMethodRedirects();
        this.classRedirects = transformer.getClassRedirects();
        this.fieldRedirects = transformer.getFieldRedirects();
    }

    /**
     * Index all classes, methods, and fields from a JAR file.
     */
    private void indexJar(Path jarPath) throws IOException {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class") || entry.isDirectory()) continue;
                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    String[] currentClass = new String[1];
                    cr.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public void visit(int version, int access, String name, String sig,
                                String superName, String[] interfaces) {
                            currentClass[0] = name;
                            mcClasses.add(name);
                            // Track hierarchy for method resolution
                            if (superName != null) mcSuperclasses.put(name, superName);
                            if (interfaces != null) mcInterfaces.put(name, interfaces);
                        }
                        @Override
                        public MethodVisitor visitMethod(int access, String name, String desc,
                                String sig, String[] exceptions) {
                            mcMethods.computeIfAbsent(currentClass[0], k -> new HashSet<>())
                                .add(name + desc);
                            return null;
                        }
                        @Override
                        public FieldVisitor visitField(int access, String name, String desc,
                                String sig, Object value) {
                            mcFields.computeIfAbsent(currentClass[0], k -> new HashSet<>())
                                .add(name);
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);
                } catch (Exception e) {
                    // Skip unreadable class files
                }
            }
        }
    }

    /**
     * Load the MC client JAR to build the target version index.
     */
    public void loadMcJar(Path mcJarPath) throws IOException {
        indexJar(mcJarPath);
    }

    /**
     * Load the Fabric API JAR (including nested JARs inside META-INF/jars/).
     */
    public void loadFabricApiJar(Path fabricApiPath) throws IOException {
        indexJar(fabricApiPath);

        // Also index nested JARs inside META-INF/jars/
        try (JarFile jar = new JarFile(fabricApiPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().startsWith("META-INF/jars/") && entry.getName().endsWith(".jar")) {
                    // Extract nested JAR to a temp file and index it
                    Path temp = Files.createTempFile("retromod-fapi-", ".jar");
                    try {
                        try (InputStream is = jar.getInputStream(entry);
                             OutputStream os = Files.newOutputStream(temp)) {
                            is.transferTo(os);
                        }
                        indexJar(temp);
                    } finally {
                        Files.deleteIfExists(temp);
                    }
                }
            }
        }
    }

    /**
     * Load a Forge or NeoForge library JAR to build the loader API index.
     * Typically found in the Prism Launcher libraries folder, e.g.:
     *   libraries/net/minecraftforge/forge/1.21.x-xx.x.x/forge-1.21.x-xx.x.x.jar
     *   libraries/net/neoforged/neoforge/xx.x.x/neoforge-xx.x.x.jar
     */
    public void loadLoaderJar(Path loaderJarPath) throws IOException {
        indexJar(loaderJarPath);
    }

    /**
     * Auto-detect the mod loader type by examining JAR metadata files.
     *
     * Detection order:
     * 1. fabric.mod.json -> Fabric
     * 2. META-INF/neoforge.mods.toml -> NeoForge
     * 3. META-INF/mods.toml -> Forge (post-1.13)
     * 4. mcmod.info -> Forge (legacy, pre-1.13)
     *
     * @param modJarPath path to the mod JAR
     * @return the detected mod loader type
     */
    public static ModLoader detectModLoader(Path modJarPath) throws IOException {
        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            if (jar.getJarEntry("fabric.mod.json") != null) {
                return ModLoader.FABRIC;
            }
            if (jar.getJarEntry("META-INF/neoforge.mods.toml") != null) {
                return ModLoader.NEOFORGE;
            }
            if (jar.getJarEntry("META-INF/mods.toml") != null) {
                return ModLoader.FORGE;
            }
            if (jar.getJarEntry("mcmod.info") != null) {
                return ModLoader.FORGE;
            }

            // Heuristic: scan for loader-specific class references
            Enumeration<JarEntry> entries = jar.entries();
            boolean hasForgeRefs = false;
            boolean hasNeoForgeRefs = false;
            boolean hasFabricRefs = false;

            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String name = entry.getName();
                if (name.startsWith("net/minecraftforge/")) hasForgeRefs = true;
                if (name.startsWith("net/neoforged/")) hasNeoForgeRefs = true;
                if (name.startsWith("net/fabricmc/")) hasFabricRefs = true;
            }

            if (hasNeoForgeRefs) return ModLoader.NEOFORGE;
            if (hasForgeRefs) return ModLoader.FORGE;
            if (hasFabricRefs) return ModLoader.FABRIC;
        }
        return ModLoader.UNKNOWN;
    }

    /**
     * Analyze a mod JAR and produce a compatibility score.
     * Auto-detects the mod loader type from the JAR.
     *
     * @param modJarPath path to the mod JAR
     * @param modInfo    detected mod version info (may be null)
     * @return the score result
     */
    public ScoreResult analyze(Path modJarPath, ModVersionInfo modInfo) throws IOException {
        // Auto-detect mod loader
        ModLoader loader;
        if (modInfo != null && modInfo.modLoaderType() != null) {
            loader = switch (modInfo.modLoaderType()) {
                case "fabric" -> ModLoader.FABRIC;
                case "forge" -> ModLoader.FORGE;
                case "neoforge" -> ModLoader.NEOFORGE;
                default -> detectModLoader(modJarPath);
            };
        } else {
            loader = detectModLoader(modJarPath);
        }
        result.detectedLoader = loader;

        // Determine which packages belong to the mod itself
        Set<String> modPackages = new HashSet<>();
        if (modInfo != null && modInfo.modPackages() != null) {
            modPackages.addAll(modInfo.modPackages());
        }

        // First pass: collect all classes in the mod (for mod-internal detection)
        Set<String> modClasses = new HashSet<>();
        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class")) {
                    String className = entry.getName().replace(".class", "");
                    modClasses.add(className);
                }
            }
        }

        // Second pass: scan bytecode for all external references
        Set<String> referencedClasses = new LinkedHashSet<>();
        Map<String, Set<String>> referencedMethods = new LinkedHashMap<>();  // owner -> set of "name desc"
        Map<String, Set<String>> referencedFields = new LinkedHashMap<>();   // owner -> set of "name"
        List<String> mixinTargets = new ArrayList<>();
        List<String> loaderSpecificFindings = new ArrayList<>();

        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            // Extract mixin targets based on loader type
            mixinTargets.addAll(extractMixinTargetsForLoader(jar, loader));

            // Collect loader-specific API usage details
            collectLoaderSpecificFindings(jar, loader, loaderSpecificFindings);

            // Scan all class files
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (!entry.getName().endsWith(".class")) continue;

                try (InputStream is = jar.getInputStream(entry)) {
                    ClassReader cr = new ClassReader(is);
                    ReferenceCollector collector = new ReferenceCollector(
                            modClasses, referencedClasses, referencedMethods, referencedFields);
                    cr.accept(collector, ClassReader.SKIP_DEBUG);
                } catch (Exception e) {
                    // Skip unreadable classes
                }
            }
        }

        // --- Score class references ---
        int totalClasses = 0;
        int resolvableClasses = 0;
        int redirectedClasses = 0;
        List<String> missingClasses = new ArrayList<>();

        for (String cls : referencedClasses) {
            if (isModInternal(cls, modClasses) || isLibrary(cls)) continue;
            totalClasses++;

            if (mcClasses.contains(cls) || isLoaderApiClass(cls, loader)) {
                resolvableClasses++;
            } else if (classRedirects.containsKey(cls)) {
                resolvableClasses++;
                redirectedClasses++;
            } else {
                missingClasses.add(cls);
            }
        }

        // --- Score method references ---
        int totalMethods = 0;
        int resolvableMethods = 0;
        int redirectedMethods = 0;
        List<String> missingMethods = new ArrayList<>();

        for (var entry : referencedMethods.entrySet()) {
            String owner = entry.getKey();
            if (isModInternal(owner, modClasses) || isLibrary(owner)) continue;

            for (String nameDesc : entry.getValue()) {
                totalMethods++;

                // Check if the method exists in MC (walk class hierarchy)
                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                if (isMethodInHierarchy(resolvedOwner, nameDesc)
                        || isMethodInHierarchy(owner, nameDesc)) {
                    resolvableMethods++;
                    continue;
                }

                // Check if there's a method redirect
                int descStart = nameDesc.indexOf('(');
                String name = nameDesc.substring(0, descStart);
                String desc = nameDesc.substring(descStart);
                MethodKey key = new MethodKey(owner, name, desc);
                if (methodRedirects.containsKey(key)) {
                    resolvableMethods++;
                    redirectedMethods++;
                    continue;
                }

                // Check with resolved owner too
                MethodKey resolvedKey = new MethodKey(resolvedOwner, name, desc);
                if (methodRedirects.containsKey(resolvedKey)) {
                    resolvableMethods++;
                    redirectedMethods++;
                    continue;
                }

                // Check if it's a loader API method (always considered available via polyfill)
                if (isLoaderApiClass(owner, loader)) {
                    resolvableMethods++;
                    continue;
                }

                missingMethods.add(owner.replace('/', '.') + "." + name + desc);
            }
        }

        // --- Score field references ---
        int totalFields = 0;
        int resolvableFields = 0;
        int redirectedFields = 0;
        List<String> missingFields = new ArrayList<>();

        for (var entry : referencedFields.entrySet()) {
            String owner = entry.getKey();
            if (isModInternal(owner, modClasses) || isLibrary(owner)) continue;

            for (String fieldName : entry.getValue()) {
                totalFields++;

                // Check if field exists (walk class hierarchy)
                String resolvedOwner = classRedirects.getOrDefault(owner, owner);
                if (isFieldInHierarchy(resolvedOwner, fieldName)
                        || isFieldInHierarchy(owner, fieldName)) {
                    resolvableFields++;
                    continue;
                }

                FieldKey key = new FieldKey(owner, fieldName);
                if (fieldRedirects.containsKey(key)) {
                    resolvableFields++;
                    redirectedFields++;
                    continue;
                }

                FieldKey resolvedKey = new FieldKey(resolvedOwner, fieldName);
                if (fieldRedirects.containsKey(resolvedKey)) {
                    resolvableFields++;
                    redirectedFields++;
                    continue;
                }

                if (isLoaderApiClass(owner, loader)) {
                    resolvableFields++;
                    continue;
                }

                missingFields.add(owner.replace('/', '.') + "." + fieldName);
            }
        }

        // --- Score mixin targets ---
        int totalMixins = mixinTargets.size();
        int validMixins = 0;
        List<String> brokenMixins = new ArrayList<>();

        for (String target : mixinTargets) {
            String internal = target.replace('.', '/');
            String resolvedTarget = classRedirects.getOrDefault(internal, internal);
            if (mcClasses.contains(resolvedTarget) || mcClasses.contains(internal)) {
                validMixins++;
            } else {
                brokenMixins.add(target);
            }
        }

        // --- Compute scores ---
        // Each category is scored 0-100 based on what percentage of references are resolvable.
        // "Resolvable" means the reference either exists in the target MC version, has a
        // RetroMod redirect/polyfill, or belongs to a loader API (always available at runtime).
        double classScore = totalClasses > 0
                ? (double) resolvableClasses / totalClasses * 100 : 100;
        double methodScore = totalMethods > 0
                ? (double) (resolvableMethods) / totalMethods * 100 : 100;
        double fieldScore = totalFields > 0
                ? (double) (resolvableFields) / totalFields * 100 : 100;
        double mixinScore = totalMixins > 0
                ? (double) validMixins / totalMixins * 100 : 100;

        // Weighted overall score:
        //   Methods (40%) — most common source of breakage, highest weight
        //   Classes (30%) — missing classes are fatal but less frequent
        //   Fields  (20%) — field changes are less common than method changes
        //   Mixins  (10%) — only applies to mods using Mixin, lower weight
        // A mod scoring 90+ will almost certainly work; below 50 is likely broken.
        double overallScore = classScore * 0.3 + methodScore * 0.4 + fieldScore * 0.2 + mixinScore * 0.1;

        result.overallScore = (int) Math.round(overallScore);
        result.classScore = (int) Math.round(classScore);
        result.methodScore = (int) Math.round(methodScore);
        result.fieldScore = (int) Math.round(fieldScore);
        result.mixinScore = (int) Math.round(mixinScore);

        result.totalClasses = totalClasses;
        result.resolvableClasses = resolvableClasses;
        result.redirectedClasses = redirectedClasses;
        result.totalMethods = totalMethods;
        result.resolvableMethods = resolvableMethods;
        result.redirectedMethods = redirectedMethods;
        result.totalFields = totalFields;
        result.resolvableFields = resolvableFields;
        result.redirectedFields = redirectedFields;
        result.totalMixins = totalMixins;
        result.validMixins = validMixins;

        result.missingClasses = missingClasses;
        result.missingMethods = missingMethods;
        result.missingFields = missingFields;
        result.brokenMixins = brokenMixins;
        result.loaderSpecificFindings = loaderSpecificFindings;

        return result;
    }

    /**
     * Extract mixin targets for the detected loader type.
     * All three loaders support Mixin, but the config location varies.
     */
    private List<String> extractMixinTargetsForLoader(JarFile jar, ModLoader loader) {
        List<String> targets = new ArrayList<>();

        switch (loader) {
            case FABRIC -> {
                JarEntry fabricJson = jar.getJarEntry("fabric.mod.json");
                if (fabricJson != null) {
                    targets.addAll(extractMixinTargets(jar, fabricJson));
                }
            }
            case FORGE -> {
                // Forge mods can have mixin configs referenced in mods.toml or in
                // META-INF/MANIFEST.MF (MixinConfigs attribute), or standalone mixin JSON files
                targets.addAll(extractForgeMixinTargets(jar));
            }
            case NEOFORGE -> {
                // NeoForge mods use neoforge.mods.toml or manifest for mixin configs
                targets.addAll(extractNeoForgeMixinTargets(jar));
            }
            default -> {
                // Try all approaches
                JarEntry fabricJson = jar.getJarEntry("fabric.mod.json");
                if (fabricJson != null) {
                    targets.addAll(extractMixinTargets(jar, fabricJson));
                }
                targets.addAll(extractForgeMixinTargets(jar));
                targets.addAll(extractNeoForgeMixinTargets(jar));
            }
        }
        return targets;
    }

    /**
     * Extract mixin targets from Forge mod JARs.
     * Forge uses the MANIFEST.MF MixinConfigs attribute or standalone mixin JSON files.
     */
    private List<String> extractForgeMixinTargets(JarFile jar) {
        List<String> targets = new ArrayList<>();
        List<String> mixinConfigs = new ArrayList<>();

        // Check MANIFEST.MF for MixinConfigs
        try {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mixinConfigsAttr = manifest.getMainAttributes().getValue("MixinConfigs");
                if (mixinConfigsAttr != null) {
                    for (String config : mixinConfigsAttr.split(",")) {
                        mixinConfigs.add(config.trim());
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        // Also look for mixin config references in mods.toml
        JarEntry modsToml = jar.getJarEntry("META-INF/mods.toml");
        if (modsToml != null) {
            try (InputStream is = jar.getInputStream(modsToml)) {
                String toml = new String(is.readAllBytes());
                // Look for [[mixins]] entries or mixinConfigs lines
                extractMixinConfigsFromToml(toml, mixinConfigs);
            } catch (IOException e) {
                // Ignore
            }
        }

        // Also scan for common mixin JSON patterns at root level
        scanForMixinJsonConfigs(jar, mixinConfigs);

        // Read each mixin config
        for (String configName : mixinConfigs) {
            JarEntry configEntry = jar.getJarEntry(configName);
            if (configEntry == null) continue;
            try (InputStream cis = jar.getInputStream(configEntry)) {
                String configJson = new String(cis.readAllBytes());
                targets.addAll(extractTargetsFromMixinConfig(configJson));
            } catch (IOException e) {
                // Ignore
            }
        }
        return targets;
    }

    /**
     * Extract mixin targets from NeoForge mod JARs.
     * NeoForge uses the MANIFEST.MF MixinConfigs attribute or neoforge.mods.toml.
     */
    private List<String> extractNeoForgeMixinTargets(JarFile jar) {
        List<String> targets = new ArrayList<>();
        List<String> mixinConfigs = new ArrayList<>();

        // Check MANIFEST.MF for MixinConfigs
        try {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest != null) {
                String mixinConfigsAttr = manifest.getMainAttributes().getValue("MixinConfigs");
                if (mixinConfigsAttr != null) {
                    for (String config : mixinConfigsAttr.split(",")) {
                        mixinConfigs.add(config.trim());
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        }

        // Check neoforge.mods.toml
        JarEntry neoforgeToml = jar.getJarEntry("META-INF/neoforge.mods.toml");
        if (neoforgeToml != null) {
            try (InputStream is = jar.getInputStream(neoforgeToml)) {
                String toml = new String(is.readAllBytes());
                extractMixinConfigsFromToml(toml, mixinConfigs);
            } catch (IOException e) {
                // Ignore
            }
        }

        // Scan for mixin JSON configs
        scanForMixinJsonConfigs(jar, mixinConfigs);

        // Read each mixin config
        for (String configName : mixinConfigs) {
            JarEntry configEntry = jar.getJarEntry(configName);
            if (configEntry == null) continue;
            try (InputStream cis = jar.getInputStream(configEntry)) {
                String configJson = new String(cis.readAllBytes());
                targets.addAll(extractTargetsFromMixinConfig(configJson));
            } catch (IOException e) {
                // Ignore
            }
        }
        return targets;
    }

    /**
     * Extract mixin config references from TOML content.
     * Looks for lines like: config = "modid.mixins.json"
     */
    private void extractMixinConfigsFromToml(String toml, List<String> mixinConfigs) {
        // Look for [[mixins]] sections with config = "..."
        int idx = 0;
        while ((idx = toml.indexOf("[[mixins]]", idx)) != -1) {
            idx += "[[mixins]]".length();
            int nextSection = toml.indexOf("[[", idx);
            String section = nextSection >= 0 ? toml.substring(idx, nextSection) : toml.substring(idx);
            // Find config = "..."
            int configIdx = section.indexOf("config");
            if (configIdx >= 0) {
                int eqIdx = section.indexOf('=', configIdx);
                if (eqIdx >= 0) {
                    int qStart = section.indexOf('"', eqIdx);
                    if (qStart >= 0) {
                        int qEnd = section.indexOf('"', qStart + 1);
                        if (qEnd >= 0) {
                            mixinConfigs.add(section.substring(qStart + 1, qEnd));
                        }
                    }
                }
            }
        }
    }

    /**
     * Scan the JAR root for common mixin config JSON files.
     * Many mods place them as *.mixins.json at the JAR root.
     */
    private void scanForMixinJsonConfigs(JarFile jar, List<String> mixinConfigs) {
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            // Only root-level files, no directories
            if (!name.contains("/") && name.endsWith(".mixins.json")) {
                if (!mixinConfigs.contains(name)) {
                    mixinConfigs.add(name);
                }
            }
        }
    }

    /**
     * Collect loader-specific API usage findings for the report.
     * Checks for loader-specific patterns like @SubscribeEvent, ForgeRegistries,
     * Event.register(), capability system, etc.
     */
    private void collectLoaderSpecificFindings(JarFile jar, ModLoader loader,
            List<String> findings) {
        // Track what loader-specific APIs the mod uses
        Set<String> forgePatterns = new LinkedHashSet<>();
        Set<String> neoforgePatterns = new LinkedHashSet<>();
        Set<String> fabricPatterns = new LinkedHashSet<>();

        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;

            try (InputStream is = jar.getInputStream(entry)) {
                ClassReader cr = new ClassReader(is);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public void visitMethodInsn(int opcode, String owner, String mName,
                                    String mDesc, boolean isInterface) {
                                // Forge: ForgeRegistries field access, event bus
                                if (owner.startsWith("net/minecraftforge/registries/ForgeRegistries")) {
                                    forgePatterns.add("ForgeRegistries usage");
                                }
                                if (owner.equals("net/minecraftforge/common/MinecraftForge") &&
                                        mName.equals("EVENT_BUS")) {
                                    forgePatterns.add("MinecraftForge.EVENT_BUS");
                                }
                                // NeoForge: event bus, capabilities
                                if (owner.startsWith("net/neoforged/neoforge/common/NeoForge")) {
                                    neoforgePatterns.add("NeoForge common API");
                                }
                                if (owner.contains("neoforged") && owner.contains("capabilit")) {
                                    neoforgePatterns.add("NeoForge capability system");
                                }
                                // Fabric: Event.register() patterns
                                if (owner.startsWith("net/fabricmc/fabric/api/") &&
                                        mName.equals("register")) {
                                    fabricPatterns.add("Fabric Event.register()");
                                }
                            }

                            @Override
                            public void visitFieldInsn(int opcode, String owner, String fName,
                                    String fDesc) {
                                if (owner.startsWith("net/minecraftforge/registries/ForgeRegistries")) {
                                    forgePatterns.add("ForgeRegistries." + fName);
                                }
                                if (owner.startsWith("net/neoforged/neoforge/registries/NeoForgeRegistries")) {
                                    neoforgePatterns.add("NeoForgeRegistries." + fName);
                                }
                            }
                        };
                    }

                    @Override
                    public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
                        // Forge: @SubscribeEvent, @Mod, @EventBusSubscriber
                        if (desc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                            forgePatterns.add("@SubscribeEvent annotation");
                        }
                        if (desc.equals("Lnet/minecraftforge/fml/common/Mod;")) {
                            forgePatterns.add("@Mod annotation");
                        }
                        if (desc.contains("EventBusSubscriber")) {
                            forgePatterns.add("@EventBusSubscriber annotation");
                        }
                        // NeoForge
                        if (desc.equals("Lnet/neoforged/bus/api/SubscribeEvent;")) {
                            neoforgePatterns.add("@SubscribeEvent annotation (NeoForge)");
                        }
                        if (desc.equals("Lnet/neoforged/fml/common/Mod;")) {
                            neoforgePatterns.add("@Mod annotation (NeoForge)");
                        }
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG);

                // Re-read for method-level annotations (need code visiting)
                is.reset();
            } catch (Exception e) {
                // Skip unreadable classes
            }
        }

        // Re-scan for method-level annotations (the above visitAnnotation is class-level only)
        entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            if (!entry.getName().endsWith(".class")) continue;

            try (InputStream is = jar.getInputStream(entry)) {
                ClassReader cr = new ClassReader(is);
                cr.accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override
                    public MethodVisitor visitMethod(int access, String name, String desc,
                            String signature, String[] exceptions) {
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override
                            public AnnotationVisitor visitAnnotation(String aDesc, boolean visible) {
                                if (aDesc.equals("Lnet/minecraftforge/eventbus/api/SubscribeEvent;")) {
                                    forgePatterns.add("@SubscribeEvent annotation");
                                }
                                if (aDesc.equals("Lnet/neoforged/bus/api/SubscribeEvent;")) {
                                    neoforgePatterns.add("@SubscribeEvent annotation (NeoForge)");
                                }
                                return null;
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG);
            } catch (Exception e) {
                // Skip
            }
        }

        // Add findings based on detected loader
        switch (loader) {
            case FORGE -> {
                if (!forgePatterns.isEmpty()) {
                    findings.add("Forge API usage detected:");
                    findings.addAll(forgePatterns.stream().map(s -> "  - " + s).toList());
                }
            }
            case NEOFORGE -> {
                if (!neoforgePatterns.isEmpty()) {
                    findings.add("NeoForge API usage detected:");
                    findings.addAll(neoforgePatterns.stream().map(s -> "  - " + s).toList());
                }
            }
            case FABRIC -> {
                if (!fabricPatterns.isEmpty()) {
                    findings.add("Fabric API usage detected:");
                    findings.addAll(fabricPatterns.stream().map(s -> "  - " + s).toList());
                }
            }
            default -> {
                // Report all found patterns
                if (!forgePatterns.isEmpty()) {
                    findings.add("Forge API usage detected:");
                    findings.addAll(forgePatterns.stream().map(s -> "  - " + s).toList());
                }
                if (!neoforgePatterns.isEmpty()) {
                    findings.add("NeoForge API usage detected:");
                    findings.addAll(neoforgePatterns.stream().map(s -> "  - " + s).toList());
                }
                if (!fabricPatterns.isEmpty()) {
                    findings.add("Fabric API usage detected:");
                    findings.addAll(fabricPatterns.stream().map(s -> "  - " + s).toList());
                }
            }
        }
    }

    /**
     * Extract mixin target classes from the mod's mixin configs (Fabric).
     */
    private List<String> extractMixinTargets(JarFile jar, JarEntry fabricJsonEntry) {
        List<String> targets = new ArrayList<>();
        try (InputStream is = jar.getInputStream(fabricJsonEntry)) {
            String json = new String(is.readAllBytes());
            // Parse mixin config file names from fabric.mod.json
            List<String> mixinConfigs = new ArrayList<>();
            int idx = 0;
            while ((idx = json.indexOf("\"mixins\"", idx)) != -1) {
                int arrStart = json.indexOf('[', idx);
                int arrEnd = json.indexOf(']', arrStart);
                if (arrStart == -1 || arrEnd == -1) break;
                String arr = json.substring(arrStart + 1, arrEnd);
                // Extract string entries
                int si = 0;
                while ((si = arr.indexOf('"', si)) != -1) {
                    int ei = arr.indexOf('"', si + 1);
                    if (ei == -1) break;
                    String configName = arr.substring(si + 1, ei);
                    if (configName.endsWith(".json") || configName.endsWith(".mixins.json")) {
                        mixinConfigs.add(configName);
                    } else if (!configName.isEmpty() && !configName.contains(":")) {
                        // Could also be just a bare config name
                        mixinConfigs.add(configName);
                    }
                    si = ei + 1;
                }
                idx = arrEnd;
            }

            // For each mixin config, read the target classes
            for (String configName : mixinConfigs) {
                JarEntry configEntry = jar.getJarEntry(configName);
                if (configEntry == null) continue;
                try (InputStream cis = jar.getInputStream(configEntry)) {
                    String configJson = new String(cis.readAllBytes());
                    targets.addAll(extractTargetsFromMixinConfig(configJson));
                }
            }
        } catch (Exception e) {
            // Ignore parse errors
        }
        return targets;
    }

    /**
     * Extract target class names from a mixin config JSON.
     * Looks for the "package" field and class lists in "mixins", "client", "server".
     */
    private List<String> extractTargetsFromMixinConfig(String json) {
        List<String> targets = new ArrayList<>();

        // Extract package prefix
        String pkg = extractJsonString(json, "package");

        // Extract mixin class names from mixins, client, and server arrays
        for (String section : new String[]{"mixins", "client", "server"}) {
            List<String> classNames = extractJsonStringArray(json, section);
            for (String cls : classNames) {
                String fullName = pkg != null ? pkg + "." + cls : cls;
                // The mixin class itself targets something -- we'd need to read
                // the @Mixin annotation. For now, track the mixin class as a target
                // reference that needs the mixin target to exist.
                targets.add(fullName);
            }
        }

        return targets;
    }

    /**
     * Extract a string value from a JSON key (simple parser, no full JSON library needed).
     */
    private String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return null;
        int colonIdx = json.indexOf(':', idx + search.length());
        if (colonIdx == -1) return null;
        int quoteStart = json.indexOf('"', colonIdx + 1);
        if (quoteStart == -1) return null;
        int quoteEnd = json.indexOf('"', quoteStart + 1);
        if (quoteEnd == -1) return null;
        return json.substring(quoteStart + 1, quoteEnd);
    }

    /**
     * Extract a string array from a JSON key (simple parser).
     */
    private List<String> extractJsonStringArray(String json, String key) {
        List<String> result = new ArrayList<>();
        String search = "\"" + key + "\"";
        int idx = json.indexOf(search);
        if (idx == -1) return result;
        int arrStart = json.indexOf('[', idx);
        if (arrStart == -1) return result;
        int arrEnd = json.indexOf(']', arrStart);
        if (arrEnd == -1) return result;
        String arr = json.substring(arrStart + 1, arrEnd);
        int si = 0;
        while ((si = arr.indexOf('"', si)) != -1) {
            int ei = arr.indexOf('"', si + 1);
            if (ei == -1) break;
            result.add(arr.substring(si + 1, ei));
            si = ei + 1;
        }
        return result;
    }

    /**
     * Check if a method exists in the class or any of its superclasses/interfaces.
     * Walks up the inheritance chain to find inherited methods.
     */
    private boolean isMethodInHierarchy(String className, String nameDesc) {
        Set<String> visited = new HashSet<>();
        String current = className;
        while (current != null && visited.add(current)) {
            Set<String> methods = mcMethods.get(current);
            if (methods != null && methods.contains(nameDesc)) {
                return true;
            }
            // Check interfaces
            String[] ifaces = mcInterfaces.get(current);
            if (ifaces != null) {
                for (String iface : ifaces) {
                    if (isMethodInHierarchy(iface, nameDesc)) return true;
                }
            }
            // Walk up to superclass
            current = mcSuperclasses.get(current);
        }
        return false;
    }

    /**
     * Check if a field exists in the class or any of its superclasses.
     */
    private boolean isFieldInHierarchy(String className, String fieldName) {
        Set<String> visited = new HashSet<>();
        String current = className;
        while (current != null && visited.add(current)) {
            Set<String> fields = mcFields.get(current);
            if (fields != null && fields.contains(fieldName)) {
                return true;
            }
            current = mcSuperclasses.get(current);
        }
        return false;
    }

    private boolean isModInternal(String className, Set<String> modClasses) {
        return modClasses.contains(className);
    }

    private boolean isLibrary(String className) {
        for (String prefix : LIBRARY_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Check if a class belongs to the loader's API, based on the detected loader type.
     * These classes are always considered available (provided by the loader at runtime).
     */
    private boolean isLoaderApiClass(String className, ModLoader loader) {
        return switch (loader) {
            case FABRIC -> isFabricApiClass(className);
            case FORGE -> isForgeApiClass(className);
            case NEOFORGE -> isNeoForgeApiClass(className);
            case UNKNOWN -> isFabricApiClass(className) ||
                    isForgeApiClass(className) || isNeoForgeApiClass(className);
        };
    }

    private boolean isFabricApiClass(String className) {
        for (String prefix : FABRIC_API_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isForgeApiClass(String className) {
        for (String prefix : FORGE_API_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    private boolean isNeoForgeApiClass(String className) {
        for (String prefix : NEOFORGE_API_PREFIXES) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * ASM visitor that collects all external class, method, and field references.
     * Walks every class in the mod JAR, recording what MC/loader classes, methods,
     * and fields are referenced in bytecode. These references are then checked against
     * the target MC version index to determine compatibility.
     *
     * <p>Mod-internal references (classes within the mod's own JAR) are tracked via
     * modClasses but excluded from scoring since they're always available.</p>
     */
    private static class ReferenceCollector extends ClassVisitor {
        private final Set<String> modClasses;
        private final Set<String> referencedClasses;
        private final Map<String, Set<String>> referencedMethods;
        private final Map<String, Set<String>> referencedFields;

        ReferenceCollector(Set<String> modClasses,
                Set<String> referencedClasses,
                Map<String, Set<String>> referencedMethods,
                Map<String, Set<String>> referencedFields) {
            super(Opcodes.ASM9);
            this.modClasses = modClasses;
            this.referencedClasses = referencedClasses;
            this.referencedMethods = referencedMethods;
            this.referencedFields = referencedFields;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            if (superName != null) addClassRef(superName);
            if (interfaces != null) {
                for (String iface : interfaces) addClassRef(iface);
            }
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc,
                String signature, String[] exceptions) {
            // Parse types from the method descriptor
            addTypesFromDescriptor(desc);
            return new MethodVisitor(Opcodes.ASM9) {
                @Override
                public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean isInterface) {
                    addClassRef(owner);
                    referencedMethods.computeIfAbsent(owner, k -> new LinkedHashSet<>())
                            .add(name + desc);
                }

                @Override
                public void visitFieldInsn(int opcode, String owner, String name, String desc) {
                    addClassRef(owner);
                    referencedFields.computeIfAbsent(owner, k -> new LinkedHashSet<>())
                            .add(name);
                }

                @Override
                public void visitTypeInsn(int opcode, String type) {
                    // NEW, INSTANCEOF, CHECKCAST, ANEWARRAY
                    if (type != null && !type.startsWith("[")) {
                        addClassRef(type);
                    }
                }

                @Override
                public void visitLdcInsn(Object value) {
                    if (value instanceof Type t && t.getSort() == Type.OBJECT) {
                        addClassRef(t.getInternalName());
                    }
                }
            };
        }

        @Override
        public FieldVisitor visitField(int access, String name, String desc,
                String signature, Object value) {
            addTypesFromDescriptor(desc);
            return null;
        }

        private void addClassRef(String internalName) {
            if (internalName != null && !internalName.startsWith("[")) {
                referencedClasses.add(internalName);
            }
        }

        private void addTypesFromDescriptor(String desc) {
            try {
                Type type = Type.getType(desc);
                addTypeRef(type);
                if (type.getSort() == Type.METHOD) {
                    for (Type arg : type.getArgumentTypes()) addTypeRef(arg);
                    addTypeRef(type.getReturnType());
                }
            } catch (Exception e) {
                // Ignore malformed descriptors
            }
        }

        private void addTypeRef(Type type) {
            if (type.getSort() == Type.OBJECT) {
                addClassRef(type.getInternalName());
            } else if (type.getSort() == Type.ARRAY) {
                addTypeRef(type.getElementType());
            }
        }
    }

    // --- Result container ---

    /**
     * Holds the compatibility analysis results for a single mod.
     * All scores are 0-100 percentages. Higher is better.
     */
    public static class ScoreResult {
        public ModLoader detectedLoader = ModLoader.UNKNOWN;
        public int overallScore;
        public int classScore, methodScore, fieldScore, mixinScore;
        public int totalClasses, resolvableClasses, redirectedClasses;
        public int totalMethods, resolvableMethods, redirectedMethods;
        public int totalFields, resolvableFields, redirectedFields;
        public int totalMixins, validMixins;
        public List<String> missingClasses = List.of();
        public List<String> missingMethods = List.of();
        public List<String> missingFields = List.of();
        public List<String> brokenMixins = List.of();
        public List<String> loaderSpecificFindings = List.of();

        public String getVerdict() {
            if (overallScore >= 90) return "EXCELLENT -- mod should work perfectly";
            if (overallScore >= 75) return "GOOD -- most features will work";
            if (overallScore >= 50) return "FAIR -- some features may break";
            if (overallScore >= 25) return "POOR -- significant issues expected";
            return "INCOMPATIBLE -- major rework needed";
        }

        public String getVerdictIcon() {
            if (overallScore >= 75) return "OK";
            if (overallScore >= 50) return "WARN";
            return "FAIL";
        }
    }
}
