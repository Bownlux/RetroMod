/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.util.ZipSecurity;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
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

    /** Maximum size for a single ZIP entry (50 MB) to prevent zip bomb attacks. */
    private static final long MAX_ENTRY_SIZE = 50 * 1024 * 1024;
    /** Maximum total extracted size (500 MB) to prevent zip bomb attacks. */
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024;

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

            // Step 2.25: Wrap entrypoint methods in try-catch for graceful failure
            // If a mod's onInitializeClient/onInitialize throws, the entire game crashes.
            // Wrapping in try-catch lets other mods still load.
            wrapEntrypoints(tempDir);

            // Step 2.5: Remap intermediary names to Mojang official names
            // MC 26.1+ uses official namespace — all class_XXXX, field_XXXX,
            // method_XXXX references must be remapped in mixin configs, refmaps,
            // and access wideners
            remapIntermediaryNames(tempDir);

            // Step 2.75: Strip bundled Fabric API JARs
            // Old mods bundle old versions of Fabric API modules (e.g. fabric-key-binding-api-v1).
            // These conflict with the modern Fabric API and cause field/method name mismatches.
            stripBundledFabricApiJars(tempDir);

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

            // Debug scan: if debug mode is enabled in config, scan the transformed
            // bytecode for potential issues (missing classes, methods, fields, broken
            // mixin targets) and log warnings. This helps mod authors and users
            // diagnose compatibility problems without crashing.
            if (isDebugEnabled()) {
                debugScanTransformedMod(outputJar, modName != null ? modName : originalName);
            }

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
            long totalSize = 0;
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                Path outputPath = ZipSecurity.safeResolve(outputDir, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(outputPath);
                } else {
                    if (entry.getSize() > MAX_ENTRY_SIZE) {
                        throw new IOException("ZIP entry too large: " + entry.getName()
                            + " (" + entry.getSize() + " bytes, max " + MAX_ENTRY_SIZE + ")");
                    }
                    totalSize += Math.max(entry.getSize(), 0);
                    if (totalSize > MAX_TOTAL_SIZE) {
                        throw new IOException("ZIP total extracted size exceeds limit ("
                            + MAX_TOTAL_SIZE + " bytes) — possible zip bomb");
                    }
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
                        // First: apply Mixin-specific annotation transforms
                        // (remap @Mixin targets, @Inject method targets, etc.)
                        transformed = mixinTransformer.transformMixinClass(original);
                        if (transformed != original) {
                            LOGGER.debug("Transformed Mixin annotations: {}", className);
                        }
                        // Second: also apply bytecode-level class remapping
                        // (remap type references, field/method owner classes, descriptors)
                        byte[] remapped = bytecodeTransformer.transformClass(
                            transformed != null ? transformed : original, className);
                        if (remapped != null && remapped != transformed) {
                            transformed = remapped;
                        }
                    } else {
                        // Regular transformation
                        transformed = bytecodeTransformer.transformClass(original, className);
                    }
                    
                    if (transformed != null && transformed != original) {
                        Files.write(classFile, transformed);
                        count++;
                    }

                    // Post-processing: inject missing abstract methods for Button subclasses.
                    // In MC 26.1, AbstractButton added extractContents(GuiGraphicsExtractor, int, int, float)V
                    // as abstract. Old mods that extend Button/AbstractButton don't implement it,
                    // causing AbstractMethodError at runtime.
                    byte[] current = (transformed != null && transformed != original) ? transformed : original;
                    byte[] patched = injectMissingAbstractMethods(current, className);
                    if (patched != null && patched != current) {
                        Files.write(classFile, patched);
                        if (transformed == null || transformed == original) count++;
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not transform class: {}", classFile.getFileName());
                }
            }
        }

        return count;
    }

    /**
     * Inject missing abstract methods for classes that extend Button or AbstractButton.
     *
     * In MC 26.1, AbstractButton gained a new abstract method:
     *   extractContents(GuiGraphicsExtractor, int, int, float)V
     *
     * Old mod widgets (e.g., ModMenu's ModMenuButtonWidget) that extend Button don't
     * implement this method, causing AbstractMethodError when the game tries to call it.
     *
     * This injects a no-op implementation: { return; }
     */
    private byte[] injectMissingAbstractMethods(byte[] classBytes, String className) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            // Quick check: does this class extend Button or AbstractButton?
            String superName = reader.getSuperName();
            if (superName == null) return null;

            // Check for Button and AbstractButton class hierarchies
            // Mojang names used in 26.1 (no obfuscation)
            Set<String> buttonSuperclasses = Set.of(
                "net/minecraft/client/gui/components/Button",
                "net/minecraft/client/gui/components/AbstractButton",
                "net/minecraft/client/gui/components/AbstractWidget"
            );

            if (!buttonSuperclasses.contains(superName)) {
                return null;
            }

            // Parse the class to check for existing extractContents method
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            // The method signature for extractContents in 26.1:
            // void extractContents(GuiGraphicsExtractor extractor, int mouseX, int mouseY, float partialTick)
            // Descriptor: (Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V
            // Also check alternative descriptor forms since the class might have been remapped
            String methodName = "extractContents";
            String methodDesc = "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";

            // Check if extractContents already exists
            boolean hasMethod = false;
            for (MethodNode method : classNode.methods) {
                if (method.name.equals(methodName)) {
                    hasMethod = true;
                    break;
                }
            }

            if (hasMethod) {
                return null; // already has the method
            }

            // Inject a no-op implementation (can't call super — it's abstract):
            //   public void extractContents(GuiGraphicsExtractor g, int x, int y, float t) {
            //       // no-op — old mod doesn't know about this method
            //   }
            MethodNode newMethod = new MethodNode(
                Opcodes.ACC_PUBLIC,
                methodName,
                methodDesc,
                null,
                null
            );

            // Generate bytecode: just return immediately
            newMethod.instructions = new InsnList();
            newMethod.instructions.add(new InsnNode(Opcodes.RETURN));
            newMethod.maxStack = 0;
            newMethod.maxLocals = 5;

            classNode.methods.add(newMethod);

            // Write the modified class
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            classNode.accept(writer);
            byte[] result = writer.toByteArray();
            LOGGER.info("Injected missing extractContents() into {} (extends {})", className, superName);
            return result;
        } catch (Exception e) {
            LOGGER.debug("Could not inject abstract methods into {}: {}", className, e.getMessage());
            return null;
        }
    }

    /**
     * Wrap Fabric entrypoint methods (onInitialize, onInitializeClient, onInitializeServer)
     * in try-catch blocks so that a failure in one mod doesn't crash the entire game.
     *
     * This is critical for compatibility: old mods may reference removed classes/methods
     * in their initialization code. Without wrapping, one mod's failure kills the game.
     * With wrapping, the error is logged and other mods can still load.
     */
    private void wrapEntrypoints(Path dir) {
        Set<String> entrypointMethods = Set.of(
            "onInitialize", "onInitializeClient", "onInitializeServer",
            "onPreLaunch"
        );

        try (var stream = Files.walk(dir)) {
            var classFiles = stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("META-INF"))
                .filter(p -> !p.toString().contains("com/retromod/"))
                .toList();

            for (Path classFile : classFiles) {
                try {
                    byte[] original = Files.readAllBytes(classFile);
                    ClassReader reader = new ClassReader(original);
                    ClassNode classNode = new ClassNode();
                    reader.accept(classNode, 0);

                    boolean modified = false;
                    boolean hasEntrypoint = false;
                    for (MethodNode method : classNode.methods) {
                        if (entrypointMethods.contains(method.name)
                                && method.desc.equals("()V")
                                && (method.access & Opcodes.ACC_PUBLIC) != 0
                                && (method.access & Opcodes.ACC_STATIC) == 0) {
                            // Wrap method body in try-catch(Throwable)
                            if (wrapMethodInTryCatch(method, classNode.name)) {
                                modified = true;
                                hasEntrypoint = true;
                                LOGGER.info("Wrapped entrypoint {}.{}() in try-catch for safety",
                                    classNode.name.replace('/', '.'), method.name);
                            } else {
                                hasEntrypoint = true;
                            }
                        }
                    }

                    // Also wrap lifecycle callback lambdas and callback methods in
                    // entrypoint classes. Old mods register CLIENT_STARTED, END_CLIENT_TICK
                    // etc. callbacks in their entrypoints. These fire independently and can
                    // crash the game even if the entrypoint itself is wrapped.
                    //
                    // We wrap:
                    // 1. Lambda methods: lambda$onInitialize* (lambda bodies)
                    // 2. Callback methods used as method references (onKeyPressed, etc.)
                    //    These are private/package-private static void methods that get
                    //    passed as method references to Event.register().
                    if (hasEntrypoint) {
                        for (MethodNode method : classNode.methods) {
                            boolean isCallbackLambda = method.name.startsWith("lambda$onInitialize")
                                    && method.desc.endsWith(")V")
                                    && (method.access & Opcodes.ACC_STATIC) != 0;

                            // Also wrap void methods that look like lifecycle/tick callbacks.
                            // Only wrap methods with names matching known callback patterns
                            // to avoid swallowing errors from render/tick/mouse/key methods.
                            boolean isCallbackMethod = !method.name.contains("$")
                                    && !method.name.equals("<init>")
                                    && !method.name.equals("<clinit>")
                                    && !entrypointMethods.contains(method.name)
                                    && method.desc.endsWith(")V")
                                    && isLikelyCallbackMethod(method.name);

                            if (isCallbackLambda || isCallbackMethod) {
                                if (wrapMethodInTryCatch(method, classNode.name)) {
                                    modified = true;
                                    LOGGER.info("Wrapped callback {}.{}() in try-catch",
                                        classNode.name.replace('/', '.'), method.name);
                                }
                            }
                        }
                    }

                    if (modified) {
                        // Use ClassWriter with COMPUTE_FRAMES but override getCommonSuperClass
                        // since we can't load Minecraft classes during offline transformation
                        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
                            @Override
                            protected String getCommonSuperClass(String type1, String type2) {
                                // Can't resolve MC class hierarchy offline — use Object as fallback
                                try {
                                    return super.getCommonSuperClass(type1, type2);
                                } catch (Exception e) {
                                    return "java/lang/Object";
                                }
                            }
                        };
                        classNode.accept(writer);
                        Files.write(classFile, writer.toByteArray());
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not wrap entrypoints in: {}", classFile.getFileName());
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan for entrypoints: {}", e.getMessage());
        }
    }

    /**
     * Check if a method name looks like a lifecycle/event callback that should be
     * wrapped in try-catch. We use an allowlist approach to avoid wrapping
     * render/tick/mouse/key methods that should NOT have errors silenced.
     */
    private static boolean isLikelyCallbackMethod(String name) {
        // Known callback patterns from Fabric lifecycle/event APIs
        return name.startsWith("on")           // onKeyPressed, onEntityJoin, onClientStarted...
            || name.equals("loadComplete")
            || name.equals("registerClientCommand")
            || name.equals("registerServerCommand")
            || name.equals("playerJoin")
            || name.equals("playerLeave")
            || name.equals("reload");
        // Explicitly NOT matching: render, tick, draw, mouseClicked, keyPressed,
        // charTyped, close, removed, init, resize — these are core Screen/Widget
        // methods where errors should propagate normally.
    }

    /**
     * Wrap a method's body in: try { ...original... } catch (Throwable t) { log(t); }
     * Returns true if the method was modified.
     */
    private boolean wrapMethodInTryCatch(MethodNode method, String className) {
        if (method.instructions == null || method.instructions.size() == 0) {
            return false;
        }

        // Don't double-wrap — check if there's already a handler for Throwable
        if (method.tryCatchBlocks != null) {
            for (TryCatchBlockNode tcb : method.tryCatchBlocks) {
                if ("java/lang/Throwable".equals(tcb.type)) {
                    return false;
                }
            }
        }

        InsnList newInsns = new InsnList();
        LabelNode tryStart = new LabelNode();
        LabelNode tryEnd = new LabelNode();
        LabelNode catchHandler = new LabelNode();
        LabelNode methodEnd = new LabelNode();

        // try {
        newInsns.add(tryStart);

        // Copy all original instructions (except the final RETURN)
        InsnList originalInsns = method.instructions;
        // We'll insert the original instructions between try/catch labels

        // Move all original instructions into our new list
        // First, add them all
        while (originalInsns.size() > 0) {
            AbstractInsnNode insn = originalInsns.getFirst();
            originalInsns.remove(insn);
            newInsns.add(insn);
        }

        // } // end try
        newInsns.add(tryEnd);
        newInsns.add(new JumpInsnNode(Opcodes.GOTO, methodEnd));

        // catch (Throwable t) {
        //   RetroModErrorHandler.handleNonFatal(className, t);
        // }
        newInsns.add(catchHandler);
        // Store exception
        newInsns.add(new VarInsnNode(Opcodes.ASTORE, method.maxLocals));
        // Call RetroModErrorHandler.handleNonFatal(String className, Throwable t)
        // This deduplicates — only logs each unique error once
        newInsns.add(new LdcInsnNode(className.replace('/', '.')));
        newInsns.add(new VarInsnNode(Opcodes.ALOAD, method.maxLocals));
        newInsns.add(new MethodInsnNode(Opcodes.INVOKESTATIC,
            "com/retromod/core/RetroModErrorHandler", "handleNonFatal",
            "(Ljava/lang/String;Ljava/lang/Throwable;)V", false));
        // }
        newInsns.add(new InsnNode(Opcodes.RETURN));

        // End of method
        newInsns.add(methodEnd);
        newInsns.add(new InsnNode(Opcodes.RETURN));

        method.instructions = newInsns;

        // Add try-catch block entry
        if (method.tryCatchBlocks == null) {
            method.tryCatchBlocks = new ArrayList<>();
        }
        method.tryCatchBlocks.add(new TryCatchBlockNode(
            tryStart, tryEnd, catchHandler, "java/lang/Throwable"));

        // Update max locals (we need one extra slot for the caught exception)
        method.maxLocals++;
        // Max stack needs at least 4 for StringBuilder chain
        method.maxStack = Math.max(method.maxStack, 4);

        return true;
    }

    /**
     * Remap all intermediary names to Mojang official names in:
     * - Access widener / class tweaker files (namespace + class/field/method names)
     * - Mixin config JSON files (target class names)
     * - Mixin refmap JSON files (field/method names + descriptors)
     * - JiJ (Jar-in-Jar) nested mod JARs
     *
     * Strip old bundled Fabric API JARs from META-INF/jars/.
     * Old mods bundle outdated Fabric API modules that conflict with the modern
     * Fabric API installed in the mods folder. Their mixins use old field/method
     * names that no longer exist in 26.1.
     */
    private void stripBundledFabricApiJars(Path dir) {
        Path jarsDir = dir.resolve("META-INF/jars");
        if (!Files.isDirectory(jarsDir)) return;

        try (var stream = Files.list(jarsDir)) {
            List<Path> toDelete = stream
                .filter(p -> p.getFileName().toString().startsWith("fabric-"))
                .toList();
            for (Path jar : toDelete) {
                LOGGER.info("Stripping bundled Fabric API module: {}", jar.getFileName());
                Files.delete(jar);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not strip bundled Fabric API JARs: {}", e.getMessage());
        }

        // Also remove the JARs from fabric.mod.json "jars" array
        // (done in updateFabricModJson step)
    }

    /**
     * MC 26.1+ uses Mojang's official namespace. Old Fabric mods use intermediary
     * names (class_XXXX, field_XXXX, method_XXXX) throughout their metadata files.
     * Without remapping these, Fabric Loader can't resolve mixin targets.
     */
    private void remapIntermediaryNames(Path dir) {
        com.retromod.mapping.IntermediaryToMojangMapper mapper =
            com.retromod.mapping.IntermediaryToMojangMapper.getInstance();

        if (!mapper.isLoaded()) {
            LOGGER.warn("Intermediary→Mojang mapper not loaded, skipping metadata remap");
            return;
        }

        int remappedFiles = 0;

        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                String entryPath = dir.relativize(file).toString()
                    .replace(File.separator, "/");

                String nameLower = name.toLowerCase(java.util.Locale.ROOT);
                if (nameLower.endsWith(".accesswidener") || nameLower.endsWith(".classtweaker")) {
                    remapAccessWidener(file, mapper);
                    remappedFiles++;
                } else if (isMixinConfigFile(entryPath)) {
                    remapMixinConfig(file, mapper);
                    remappedFiles++;
                } else if (name.endsWith("-refmap.json") || name.contains("refmap")) {
                    remapRefmap(file, mapper);
                    remappedFiles++;
                } else if (name.endsWith(".jar")) {
                    // JiJ nested JAR — remap its contents recursively
                    remapNestedJar(file, mapper);
                    remappedFiles++;
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan for intermediary metadata: {}", e.getMessage());
        }

        if (remappedFiles > 0) {
            LOGGER.info("Remapped intermediary→Mojang in {} metadata files", remappedFiles);
        }
    }

    /**
     * Remap an access widener file from intermediary to official namespace.
     */
    private void remapAccessWidener(Path awFile,
                                     com.retromod.mapping.IntermediaryToMojangMapper mapper) {
        try {
            String content = Files.readString(awFile);
            if (!content.contains("intermediary")) return;

            String[] lines = content.split("\n");
            StringBuilder patched = new StringBuilder();

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();

                if (i == 0) {
                    patched.append(line.replace("intermediary", "official")).append("\n");
                    continue;
                }

                if (line.isEmpty() || line.startsWith("#")) {
                    patched.append(line).append("\n");
                    continue;
                }

                // Remap all intermediary references in the line
                String remapped = mapper.remapString(line);
                patched.append(remapped).append("\n");
            }

            Files.writeString(awFile, patched.toString());
            LOGGER.info("  Remapped access widener: {}", awFile.getFileName());

        } catch (Exception e) {
            LOGGER.warn("Failed to remap access widener {}: {}", awFile.getFileName(), e.getMessage());
        }
    }

    /**
     * Remap mixin config JSON file — update target class references.
     *
     * Mixin configs don't directly contain class_XXXX names in the JSON
     * (the targets come from @Mixin annotations), but the refmap filename
     * and package declarations may need adjustment.
     */
    private void remapMixinConfig(Path configFile,
                                   com.retromod.mapping.IntermediaryToMojangMapper mapper) {
        try {
            String content = Files.readString(configFile);
            // Remap any intermediary class references in the config
            String remapped = mapper.remapString(content);
            // Also make the mixin config non-fatal: set required=false and defaultRequire=0.
            // This prevents broken mixins (CommandKeys, Lithium, FerriteCore, etc.) from
            // crashing the game. Instead they log warnings and the game continues.
            remapped = makeMixinConfigNonFatal(remapped);
            if (!remapped.equals(content)) {
                Files.writeString(configFile, remapped);
                LOGGER.debug("  Remapped mixin config (non-fatal): {}", configFile.getFileName());
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to remap mixin config {}: {}", configFile.getFileName(), e.getMessage());
        }
    }

    /**
     * Remap a mixin refmap JSON file.
     *
     * Refmaps map intermediary names to their target names. Format:
     * {
     *   "mappings": { "MixinClass": { "field_XXXX:Ldesc;": "target" } },
     *   "data": {
     *     "intermediary": { "MixinClass": { "field_XXXX:Ldesc;": "obf:Ldesc;" } }
     *   }
     * }
     *
     * We need to add/replace the entries with official namespace mappings.
     */
    private void remapRefmap(Path refmapFile,
                              com.retromod.mapping.IntermediaryToMojangMapper mapper) {
        try {
            String content = Files.readString(refmapFile);
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

            boolean changed = false;

            // Remap the "mappings" section
            if (root.has("mappings") && root.get("mappings").isJsonObject()) {
                com.google.gson.JsonObject mappings = root.getAsJsonObject("mappings");
                com.google.gson.JsonObject remapped = remapRefmapSection(mappings, mapper);
                root.add("mappings", remapped);
                changed = true;
            }

            // If there's an "intermediary" section in "data", remap it and
            // add as "official" section
            if (root.has("data") && root.get("data").isJsonObject()) {
                com.google.gson.JsonObject data = root.getAsJsonObject("data");

                // Use intermediary section as base if available
                com.google.gson.JsonObject source = null;
                if (data.has("intermediary") && data.get("intermediary").isJsonObject()) {
                    source = data.getAsJsonObject("intermediary");
                } else if (data.has("named") && data.get("named").isJsonObject()) {
                    source = data.getAsJsonObject("named");
                }

                if (source != null) {
                    com.google.gson.JsonObject officialData = remapRefmapSection(source, mapper);
                    data.add("official", officialData);
                    changed = true;
                }
            }

            if (changed) {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                    .setPrettyPrinting()
                    .disableHtmlEscaping()
                    .create();
                Files.writeString(refmapFile, gson.toJson(root));
                LOGGER.info("  Remapped refmap: {}", refmapFile.getFileName());
            }

        } catch (Exception e) {
            LOGGER.warn("Failed to remap refmap {}: {}", refmapFile.getFileName(), e.getMessage());
        }
    }

    /**
     * Remap a refmap section — replace all intermediary names with Mojang names.
     */
    private com.google.gson.JsonObject remapRefmapSection(
            com.google.gson.JsonObject section,
            com.retromod.mapping.IntermediaryToMojangMapper mapper) {

        com.google.gson.JsonObject result = new com.google.gson.JsonObject();

        for (String mixinClassName : section.keySet()) {
            if (!section.get(mixinClassName).isJsonObject()) {
                result.add(mixinClassName, section.get(mixinClassName));
                continue;
            }

            com.google.gson.JsonObject entries = section.getAsJsonObject(mixinClassName);
            com.google.gson.JsonObject remappedEntries = new com.google.gson.JsonObject();

            for (String key : entries.keySet()) {
                String value = entries.get(key).getAsString();

                // Remap the key (e.g., "field_25318:Lnet/minecraft/class_3300;")
                String remappedKey = mapper.remapString(key);

                // Remap the value (e.g., "a:Larg;")
                String remappedValue = mapper.remapString(value);

                remappedEntries.addProperty(remappedKey, remappedValue);
            }

            result.add(mixinClassName, remappedEntries);
        }

        return result;
    }

    /**
     * Remap a nested JAR (Jar-in-Jar) by extracting, remapping, and repacking.
     * Also applies MixinCompatibilityTransformer to strip broken mixin entries
     * (fixes ExtraSounds/SoundCategories VerifyError from corrupted mixin bytecode).
     */
    private void remapNestedJar(Path jarFile,
                                 com.retromod.mapping.IntermediaryToMojangMapper mapper) {
        try {
            Path tempDir = Files.createTempDirectory("retromod-jij-");
            try {
                // Extract nested JAR
                extractJar(jarFile, tempDir);

                // Transform bytecode
                transformClasses(tempDir);

                // Recursively remap intermediary names in metadata
                int remapped = 0;
                // Build class lookup once for mixin stripping in this nested JAR
                Map<String, byte[]> nestedClassLookup = null;

                try (var stream = Files.walk(tempDir)) {
                    for (Path file : stream.filter(Files::isRegularFile).toList()) {
                        String name = file.getFileName().toString();
                        String entryPath = tempDir.relativize(file).toString()
                            .replace(File.separator, "/");

                        String nameLower = name.toLowerCase(java.util.Locale.ROOT);
                        if (nameLower.endsWith(".accesswidener") || nameLower.endsWith(".classtweaker")) {
                            remapAccessWidener(file, mapper);
                            remapped++;
                        } else if (isMixinConfigFile(entryPath)) {
                            // First: remap intermediary names
                            remapMixinConfig(file, mapper);
                            // Second: strip broken mixin entries using MixinCompatibilityTransformer
                            // This fixes nested JARs like ExtraSounds' soundcategories where
                            // mixin bytecode gets corrupted by the fuzzy matcher or redirects
                            try {
                                String json = Files.readString(file);
                                if (nestedClassLookup == null) {
                                    nestedClassLookup = buildClassLookup(tempDir);
                                }
                                MixinCompatibilityTransformer mixinTransformer =
                                    new MixinCompatibilityTransformer(RetroModTransformer.getInstance());
                                String transformed = mixinTransformer.transformMixinConfig(json, nestedClassLookup);
                                // makeMixinConfigNonFatal is already called by remapMixinConfig above,
                                // but call it again on the stripped version to be safe
                                transformed = makeMixinConfigNonFatal(transformed);
                                Files.writeString(file, transformed, java.nio.charset.StandardCharsets.UTF_8);
                                LOGGER.debug("  Stripped broken mixins in nested JAR config: {}", entryPath);
                            } catch (Exception e) {
                                LOGGER.debug("Could not strip mixins in nested JAR config {}: {}", entryPath, e.getMessage());
                            }
                            remapped++;
                        } else if (name.endsWith("-refmap.json") || name.contains("refmap")) {
                            remapRefmap(file, mapper);
                            remapped++;
                        }
                    }
                }

                // Also remap fabric.mod.json in nested JAR
                Path nestedModJson = tempDir.resolve("fabric.mod.json");
                if (Files.exists(nestedModJson)) {
                    updateFabricModJson(tempDir);
                }

                if (remapped > 0) {
                    // Repackage the nested JAR
                    Path tempJar = Files.createTempFile("retromod-jij-", ".jar");
                    repackageJar(tempDir, tempJar, jarFile);
                    Files.move(tempJar, jarFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("  Remapped nested JAR: {}", jarFile.getFileName());
                }

            } finally {
                deleteRecursively(tempDir);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not remap nested JAR {}: {}", jarFile.getFileName(), e.getMessage());
        }
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

        // Strip bundled Fabric API JAR references from "jars" array
        updated = stripFabricApiJarReferences(updated);

        Files.writeString(modJson, updated);
        LOGGER.info("Updated fabric.mod.json: {} → {}", originalMcVersion, targetMcVersion);
    }
    
    /**
     * Strip references to bundled Fabric API JARs from the "jars" array in fabric.mod.json.
     * Old mods bundle outdated Fabric API modules that conflict with the modern API.
     */
    private String stripFabricApiJarReferences(String json) {
        // Remove individual JAR entries that reference fabric- prefixed JARs
        // Pattern matches: { "file": "META-INF/jars/fabric-xxx.jar" }
        String result = json.replaceAll(
            "\\{\\s*\"file\"\\s*:\\s*\"META-INF/jars/fabric-[^\"]+\\.jar\"\\s*\\}\\s*,?",
            ""
        );
        // Clean up trailing commas in the jars array
        result = result.replaceAll(",\\s*]", "]");
        // Clean up empty jars arrays
        result = result.replaceAll("\"jars\"\\s*:\\s*\\[\\s*\\]\\s*,?", "");
        return result;
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
        // Handle both string format: "minecraft": "1.20.5"
        // and array format: "minecraft": ["1.20.5", "1.20.6"]

        // First: replace array-style version constraints
        Pattern minecraftArrayPattern = Pattern.compile(
            "(\"minecraft\"\\s*:\\s*)\\[[^\\]]+\\]",
            Pattern.MULTILINE
        );
        String updated = minecraftArrayPattern.matcher(json).replaceAll(
            "$1\"" + targetMcVersion + "\""
        );

        // Then: replace string-style version constraints
        Pattern minecraftPattern = Pattern.compile(
            "(\"minecraft\"\\s*:\\s*)\"[^\"]+\"",
            Pattern.MULTILINE
        );
        updated = minecraftPattern.matcher(updated).replaceAll(
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

        // Rename old Fabric API module IDs to their 26.1 equivalents
        updated = updated.replace("\"fabric-key-binding-api-v1\"", "\"fabric-key-mapping-api-v1\"");

        // Relax ALL shimmed third-party API dependencies
        // RetroMod provides bytecode shims for these APIs, so the version constraint
        // in fabric.mod.json is no longer needed — the transformed code will work
        // with whatever version is installed.
        updated = relaxShimmedApiDependencies(updated);

        // Also move strict API deps from "depends" to "suggests" if they'd still block
        updated = moveBlockingDepsToSuggests(updated);

        // Move non-core dependencies from "depends" to "suggests"
        // This prevents Fabric Loader from rejecting mods when optional APIs
        // (fabric-api, fabric, mixinextras, etc.) aren't installed.
        // Only minecraft, fabricloader, and java MUST stay in depends.
        updated = moveNonCoreDepsToSuggests(updated);

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
     * Move non-core dependencies from "depends" to "suggests" using Gson.
     *
     * Fabric Loader treats "depends" as hard requirements — if the mod isn't
     * installed, the game won't launch. For old transformed mods, APIs like
     * fabric-api, fabric, mixinextras etc. may not be present or may have
     * incompatible versions. Moving them to "suggests" allows the mod to load
     * even when these optional dependencies are missing.
     *
     * Only minecraft, fabricloader, and java stay in depends.
     */
    private String moveNonCoreDepsToSuggests(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            Set<String> coreDeps = Set.of("minecraft", "fabricloader", "java");

            if (!root.has("depends") || !root.get("depends").isJsonObject()) {
                return json;
            }

            com.google.gson.JsonObject depends = root.getAsJsonObject("depends");
            com.google.gson.JsonObject suggests = root.has("suggests") && root.get("suggests").isJsonObject()
                ? root.getAsJsonObject("suggests")
                : new com.google.gson.JsonObject();

            // Collect non-core deps to move
            List<String> toMove = new ArrayList<>();
            for (String key : depends.keySet()) {
                if (!coreDeps.contains(key)) {
                    toMove.add(key);
                }
            }

            if (toMove.isEmpty()) {
                return json;
            }

            for (String key : toMove) {
                suggests.add(key, depends.get(key));
                depends.remove(key);
                LOGGER.info("  Moved dependency to suggests: {}", key);
            }

            root.add("suggests", suggests);

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .disableHtmlEscaping()
                .create();
            String result = gson.toJson(root);

            LOGGER.info("Moved {} non-core dependencies from depends to suggests", toMove.size());
            return result;

        } catch (Exception e) {
            LOGGER.warn("Could not restructure dependencies (regex fallback): {}", e.getMessage());
            return json;
        }
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
            
            // Class lookup for mixin config stripping — may be populated when processing mixin configs.
            // Modified class bytes are written from this map instead of from disk.
            Map<String, byte[]> classLookupForStripping = null;

            // First pass: process mixin configs to detect and strip broken entries
            // This must happen before writing class files so we can use the modified bytes
            try (var preStream = Files.walk(sourceDir)) {
                for (Path file : preStream.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");
                    if (isMixinConfigFile(entryName)) {
                        try {
                            String json = Files.readString(file);
                            if (classLookupForStripping == null) {
                                classLookupForStripping = buildClassLookup(sourceDir);
                            }
                            MixinCompatibilityTransformer mixinTransformer =
                                new MixinCompatibilityTransformer(RetroModTransformer.getInstance());
                            String transformed = mixinTransformer.transformMixinConfig(json, classLookupForStripping);
                            // Make mixin configs non-fatal: set "required": false so @Accessor/@Invoker
                            // failures on removed fields don't crash the game. Also set defaultRequire=0
                            // so all injection points are optional.
                            transformed = makeMixinConfigNonFatal(transformed);
                            // Write modified config back to disk for the second pass
                            Files.writeString(file, transformed, java.nio.charset.StandardCharsets.UTF_8);
                        } catch (Exception e) {
                            LOGGER.warn("Failed to process mixin config {}: {}", entryName, e.getMessage());
                        }
                    }
                }
            }

            // Pre-pass 1.5: Process JiJ (Jar-in-Jar) nested JARs in META-INF/jars/
            // Fabric Loader loads these as separate mods. Their mixin configs need
            // the same non-fatal treatment, bytecode transformation, and refmap cleaning.
            Path jijDir = sourceDir.resolve("META-INF/jars");
            if (Files.isDirectory(jijDir)) {
                try (var jijStream = Files.list(jijDir)) {
                    for (Path jijJar : jijStream.filter(p -> p.toString().endsWith(".jar")).toList()) {
                        processNestedJiJJar(jijJar);
                    }
                }
            }

            // Second pre-pass: scan refmaps for unresolved intermediary references.
            // Strip mixin entries whose refmap targets still contain class_/field_/method_
            // after all remapping. These reference removed MC classes/fields/methods.
            try (var refmapStream = Files.walk(sourceDir)) {
                for (Path file : refmapStream.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");
                    if (entryName.endsWith("-refmap.json") || entryName.contains("refmap")) {
                        try {
                            stripBrokenRefmapEntries(sourceDir, file);
                        } catch (Exception e) {
                            LOGGER.debug("Failed to process refmap {}: {}", entryName, e.getMessage());
                        }
                    }
                }
            }

            // Also write modified class files back to disk
            if (classLookupForStripping != null) {
                for (var entry : classLookupForStripping.entrySet()) {
                    Path classFile = sourceDir.resolve(entry.getKey());
                    if (Files.exists(classFile)) {
                        Files.write(classFile, entry.getValue());
                    }
                }
            }

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

                    // All files (including mixin configs and stripped classes)
                    // were already written back to disk in the pre-pass above
                    Files.copy(file, jos);

                    jos.closeEntry();
                }
            }

            // Inject synthetic classes (ASM-generated polyfills with MC-typed fields)
            for (var entry : RetroModTransformer.getInstance().getSyntheticClasses().entrySet()) {
                String classPath = entry.getKey() + ".class";
                jos.putNextEntry(new JarEntry(classPath));
                jos.write(entry.getValue());
                jos.closeEntry();
                LOGGER.info("  Injected synthetic class: {}", entry.getKey());
            }
        }
    }
    
    /**
     * Scan a refmap JSON file for entries that still contain unresolved intermediary
     * references (class_XXXX, method_XXXX, field_XXXX). For each broken entry,
     * find the corresponding mixin config and strip the mixin class that uses it.
     * Also rewrite the refmap to remove the broken entries.
     */
    private void stripBrokenRefmapEntries(Path sourceDir, Path refmapFile) throws IOException {
        String content = Files.readString(refmapFile);
        com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(content).getAsJsonObject();

        boolean changed = false;
        Set<String> brokenMixinClasses = new HashSet<>();

        // Check all sections for unresolved intermediary references
        for (String sectionName : List.of("mappings", "data")) {
            com.google.gson.JsonElement sectionEl = root.get(sectionName);
            if (sectionEl == null || !sectionEl.isJsonObject()) continue;

            com.google.gson.JsonObject section = sectionEl.getAsJsonObject();

            // For "data", look inside sub-sections like "intermediary", "named"
            if ("data".equals(sectionName)) {
                for (String sub : new ArrayList<>(section.keySet())) {
                    if (!section.get(sub).isJsonObject()) continue;
                    com.google.gson.JsonObject subSection = section.getAsJsonObject(sub);
                    for (String mixinClass : new ArrayList<>(subSection.keySet())) {
                        if (!subSection.get(mixinClass).isJsonObject()) continue;
                        com.google.gson.JsonObject entries = subSection.getAsJsonObject(mixinClass);
                        for (String key : new ArrayList<>(entries.keySet())) {
                            String value = entries.get(key).getAsString();
                            if (containsUnresolvedIntermediary(key) || containsUnresolvedIntermediary(value)) {
                                brokenMixinClasses.add(mixinClass);
                                entries.remove(key);
                                changed = true;
                            }
                        }
                    }
                }
            } else {
                // "mappings" section
                for (String mixinClass : new ArrayList<>(section.keySet())) {
                    if (!section.get(mixinClass).isJsonObject()) continue;
                    com.google.gson.JsonObject entries = section.getAsJsonObject(mixinClass);
                    for (String key : new ArrayList<>(entries.keySet())) {
                        String value = entries.get(key).getAsString();
                        if (containsUnresolvedIntermediary(key) || containsUnresolvedIntermediary(value)) {
                            brokenMixinClasses.add(mixinClass);
                            entries.remove(key);
                            changed = true;
                        }
                    }
                }
            }
        }

        if (changed) {
            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
            Files.writeString(refmapFile, gson.toJson(root));
            LOGGER.info("Cleaned {} broken refmap entries for mixin classes: {}",
                brokenMixinClasses.size(), brokenMixinClasses);
        }
    }

    /**
     * Check if a file path looks like a mixin config JSON.
     * Handles standard patterns (modid.mixins.json) AND non-standard ones
     * like mixins.modmenu.json (ModMenu) or paths containing "mixins/" directory.
     */
    private boolean isMixinConfigFile(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith(".mixins.json")) return true;       // modid.mixins.json
        if (name.endsWith("mixin.json")) return true;         // modid.mixin.json
        if (name.startsWith("mixins.") && name.endsWith(".json")) return true; // mixins.modid.json
        if (path.contains("mixins/") && name.endsWith(".json") && name.contains("mixin")) return true; // mixins/common/nochatreports.mixins.json
        return false;
    }

    /**
     * Check if a refmap key or value contains unresolved intermediary references.
     */
    private boolean containsUnresolvedIntermediary(String s) {
        if (s == null) return false;
        return s.contains("class_") || s.contains("field_") || s.contains("method_");
    }

    /**
     * Make a mixin config JSON non-fatal by setting "required": false and
     * "injectors": {"defaultRequire": 0}. This prevents crashes from @Accessor/@Invoker
     * targeting fields/methods that were removed in newer MC versions.
     * @Accessor has no per-annotation "require" field, so the only way to make it
     * non-fatal is at the config level.
     */
    private String makeMixinConfigNonFatal(String json) {
        try {
            com.google.gson.JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();

            // Set "required": false — makes the entire mixin config non-fatal
            root.addProperty("required", false);

            // Set "injectors": {"defaultRequire": 0} — makes all injection points optional
            com.google.gson.JsonObject injectors = root.has("injectors") && root.get("injectors").isJsonObject()
                ? root.getAsJsonObject("injectors")
                : new com.google.gson.JsonObject();
            injectors.addProperty("defaultRequire", 0);
            root.add("injectors", injectors);

            com.google.gson.Gson gson = new com.google.gson.GsonBuilder()
                .setPrettyPrinting().disableHtmlEscaping().create();
            String result = gson.toJson(root);
            LOGGER.debug("Set mixin config to non-fatal (required=false, defaultRequire=0)");
            return result;
        } catch (Exception e) {
            LOGGER.warn("Failed to make mixin config non-fatal: {}", e.getMessage());
            return json;
        }
    }

    /**
     * Process a nested JiJ (Jar-in-Jar) mod JAR.
     * Extracts the JAR, patches mixin configs to be non-fatal, transforms bytecode
     * with intermediary→Mojang remapping, cleans refmaps, and repacks.
     */
    private void processNestedJiJJar(Path jijJar) {
        String name = jijJar.getFileName().toString();
        try {
            Path tempDir = Files.createTempDirectory("retromod-jij-");
            try {
                // Extract nested JAR
                try (JarFile jf = new JarFile(jijJar.toFile())) {
                    var entries = jf.entries();
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement();
                        Path target = ZipSecurity.safeResolve(tempDir, entry.getName());
                        if (entry.isDirectory()) {
                            Files.createDirectories(target);
                        } else {
                            Files.createDirectories(target.getParent());
                            try (InputStream is = jf.getInputStream(entry)) {
                                Files.copy(is, target, StandardCopyOption.REPLACE_EXISTING);
                            }
                        }
                    }
                }

                boolean modified = false;

                // Transform class files (intermediary→Mojang remapping)
                try (var classStream = Files.walk(tempDir)) {
                    for (Path file : classStream.filter(f -> f.toString().endsWith(".class")).toList()) {
                        try {
                            byte[] original = Files.readAllBytes(file);
                            String className = tempDir.relativize(file).toString()
                                .replace(File.separator, "/").replace(".class", "");
                            byte[] transformed = bytecodeTransformer.transformClass(original, className);
                            if (transformed != null && transformed != original) {
                                Files.write(file, transformed);
                                modified = true;
                            }
                        } catch (Exception e) {
                            LOGGER.debug("Failed to transform class in JiJ {}: {}", name, e.getMessage());
                        }
                    }
                }

                // Patch mixin configs to be non-fatal
                try (var mixinStream = Files.walk(tempDir)) {
                    for (Path file : mixinStream.filter(Files::isRegularFile).toList()) {
                        String entryName = tempDir.relativize(file).toString()
                            .replace(File.separator, "/");
                        if (isMixinConfigFile(entryName)) {
                            try {
                                String json = Files.readString(file);
                                // Strip broken mixin entries
                                Map<String, byte[]> jijClassLookup = buildClassLookup(tempDir);
                                MixinCompatibilityTransformer mixinTransformer =
                                    new MixinCompatibilityTransformer(RetroModTransformer.getInstance());
                                String transformed = mixinTransformer.transformMixinConfig(json, jijClassLookup);
                                // Make non-fatal
                                transformed = makeMixinConfigNonFatal(transformed);
                                Files.writeString(file, transformed, java.nio.charset.StandardCharsets.UTF_8);
                                modified = true;
                                LOGGER.info("  Patched JiJ mixin config: {} in {}", entryName, name);
                            } catch (Exception e) {
                                LOGGER.warn("Failed to process JiJ mixin config {}: {}", entryName, e.getMessage());
                            }
                        }
                    }
                }

                // Clean refmaps
                try (var refmapStream = Files.walk(tempDir)) {
                    for (Path file : refmapStream.filter(Files::isRegularFile).toList()) {
                        String entryName = tempDir.relativize(file).toString()
                            .replace(File.separator, "/");
                        if (entryName.endsWith("-refmap.json") || entryName.contains("refmap")) {
                            try {
                                stripBrokenRefmapEntries(tempDir, file);
                                modified = true;
                            } catch (Exception e) {
                                LOGGER.debug("Failed to process JiJ refmap {}: {}", entryName, e.getMessage());
                            }
                        }
                    }
                }

                // Repack the JiJ JAR if we modified anything
                if (modified) {
                    Manifest manifest = null;
                    Path manifestFile = tempDir.resolve("META-INF/MANIFEST.MF");
                    if (Files.exists(manifestFile)) {
                        try (InputStream is = Files.newInputStream(manifestFile)) {
                            manifest = new Manifest(is);
                        }
                    }
                    if (manifest == null) {
                        manifest = new Manifest();
                        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                    }

                    Path tempJar = jijJar.getParent().resolve(name + ".tmp");
                    try (JarOutputStream jos = new JarOutputStream(
                            new FileOutputStream(tempJar.toFile()), manifest)) {
                        try (var stream = Files.walk(tempDir)) {
                            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                                String entryName = tempDir.relativize(file).toString()
                                    .replace(File.separator, "/");
                                if (entryName.equalsIgnoreCase("META-INF/MANIFEST.MF")) continue;
                                JarEntry entry = new JarEntry(entryName);
                                jos.putNextEntry(entry);
                                Files.copy(file, jos);
                                jos.closeEntry();
                            }
                        }
                    }
                    Files.move(tempJar, jijJar, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("  Repacked JiJ mod: {}", name);
                }
            } finally {
                deleteRecursively(tempDir);
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to process JiJ mod {}: {}", name, e.getMessage());
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

    // ── Debug scanning ──────────────────────────────────────────────────────
    // The following methods implement a post-transformation bytecode scan that
    // logs potential issues (missing classes, methods, fields, broken mixin
    // targets, constructor mismatches) without crashing. Enabled only when
    // "debug": true is set in config/retromod/config.json.

    /**
     * Check if debug mode is enabled in config/retromod/config.json.
     * Uses the same config pattern as {@code isForceTranslateEnabled()} in
     * RetroModPreLaunch — a simple string check to avoid pulling in a full
     * JSON parser dependency at this layer.
     */
    private static boolean isDebugEnabled() {
        try {
            Path configPath = Path.of("config/retromod/config.json");
            if (Files.exists(configPath)) {
                String json = Files.readString(configPath);
                return json.contains("\"debug\": true") ||
                       json.contains("\"debug\":true");
            }
        } catch (Exception e) {
            // Default to false — never crash for config reading
        }
        return false;
    }

    /**
     * Scan a transformed mod JAR for potential runtime issues and log them.
     *
     * This method opens the transformed JAR, uses ASM to collect all external
     * class, method, and field references from every .class file, then attempts
     * to resolve each reference against the runtime classpath (which includes
     * the MC JAR and Fabric API at this point). Any reference that cannot be
     * resolved is logged as a warning with the [RetroMod-Debug] prefix.
     *
     * Additionally scans for:
     * - Broken mixin targets (classes referenced in mixin configs that don't exist)
     * - Constructor signature mismatches (constructors whose parameter count
     *   doesn't match any constructor on the target class)
     *
     * This method NEVER throws — all exceptions are caught and logged. It is
     * designed to be purely informational; transformation continues regardless
     * of scan results.
     *
     * @param transformedJar path to the transformed mod JAR
     * @param modName        human-readable mod name for log messages
     */
    void debugScanTransformedMod(Path transformedJar, String modName) {
        try {
            LOGGER.info("[RetroMod-Debug] Starting debug scan of transformed mod: {}", modName);

            // Collect all classes defined inside the mod (internal references are skipped)
            Set<String> modClasses = new HashSet<>();
            // Collect external references from bytecode
            Set<String> referencedClasses = new LinkedHashSet<>();
            Map<String, Set<String>> referencedMethods = new LinkedHashMap<>(); // owner -> "name desc"
            Map<String, Set<String>> referencedFields = new LinkedHashMap<>();  // owner -> "name"
            // Track constructors separately for parameter-count checking
            Map<String, Set<String>> referencedCtors = new LinkedHashMap<>();   // owner -> set of descs

            try (JarFile jar = new JarFile(transformedJar.toFile())) {
                // First pass: collect mod-internal class names
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".class") && !entry.isDirectory()) {
                        modClasses.add(entry.getName().replace(".class", ""));
                    }
                }

                // Second pass: scan bytecode for all external references
                entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.getName().endsWith(".class") || entry.isDirectory()) continue;

                    try (InputStream is = jar.getInputStream(entry)) {
                        ClassReader cr = new ClassReader(is);
                        cr.accept(new ClassVisitor(Opcodes.ASM9) {
                            @Override
                            public void visit(int version, int access, String name,
                                              String signature, String superName, String[] interfaces) {
                                if (superName != null) referencedClasses.add(superName);
                                if (interfaces != null) {
                                    for (String iface : interfaces) referencedClasses.add(iface);
                                }
                            }

                            @Override
                            public MethodVisitor visitMethod(int access, String name,
                                                             String desc, String signature,
                                                             String[] exceptions) {
                                return new MethodVisitor(Opcodes.ASM9) {
                                    @Override
                                    public void visitMethodInsn(int opcode, String owner,
                                                                String mName, String mDesc,
                                                                boolean isInterface) {
                                        referencedClasses.add(owner);
                                        referencedMethods
                                            .computeIfAbsent(owner, k -> new LinkedHashSet<>())
                                            .add(mName + mDesc);
                                        // Track constructors for parameter-count checking
                                        if ("<init>".equals(mName)) {
                                            referencedCtors
                                                .computeIfAbsent(owner, k -> new LinkedHashSet<>())
                                                .add(mDesc);
                                        }
                                    }

                                    @Override
                                    public void visitFieldInsn(int opcode, String owner,
                                                               String fName, String fDesc) {
                                        referencedClasses.add(owner);
                                        referencedFields
                                            .computeIfAbsent(owner, k -> new LinkedHashSet<>())
                                            .add(fName);
                                    }

                                    @Override
                                    public void visitTypeInsn(int opcode, String type) {
                                        if (type != null && !type.startsWith("[")) {
                                            referencedClasses.add(type);
                                        }
                                    }
                                };
                            }
                        }, ClassReader.SKIP_DEBUG);
                    } catch (Exception e) {
                        // Skip unreadable class files — don't crash the scan
                    }
                }

                // --- Scan for broken mixin targets ---
                // Mixin target classes are listed in mixin config JSON files referenced
                // from fabric.mod.json. If a target class doesn't exist at runtime the
                // mixin will fail to apply and likely crash the game.
                debugScanMixinTargets(jar, modName);
            }

            // --- Resolve references against the runtime classpath ---
            int issueCount = 0;

            // Prefixes that are always available (JVM, libraries bundled with MC)
            // and should not be flagged as missing.
            String[] safeLibraryPrefixes = {
                "java/", "javax/", "jdk/", "sun/",
                "com/google/gson/", "com/google/common/",
                "org/slf4j/", "org/apache/logging/",
                "org/apache/commons/", "org/objectweb/asm/", "org/lwjgl/",
                "io/netty/", "com/mojang/",
                "it/unimi/dsi/fastutil/", "org/joml/",
                "net/fabricmc/loader/", "net/fabricmc/api/",
                "net/fabricmc/fabric/",
                "com/retromod/",
            };

            // Check class references
            for (String cls : referencedClasses) {
                if (modClasses.contains(cls)) continue;
                if (isSafeLibrary(cls, safeLibraryPrefixes)) continue;
                if (!canResolveClass(cls)) {
                    LOGGER.info("[RetroMod-Debug] WARN: {}: class {} not found in MC {}",
                            modName, cls.replace('/', '.'), targetMcVersion);
                    issueCount++;
                }
            }

            // Check method references
            for (var entry : referencedMethods.entrySet()) {
                String owner = entry.getKey();
                if (modClasses.contains(owner)) continue;
                if (isSafeLibrary(owner, safeLibraryPrefixes)) continue;
                // Only check methods on classes we CAN resolve — if the class itself
                // is missing, we already logged that above.
                if (!canResolveClass(owner)) continue;

                for (String nameDesc : entry.getValue()) {
                    int descStart = nameDesc.indexOf('(');
                    if (descStart < 0) continue;
                    String mName = nameDesc.substring(0, descStart);
                    String mDesc = nameDesc.substring(descStart);
                    if (!canResolveMethod(owner, mName, mDesc)) {
                        LOGGER.info("[RetroMod-Debug] WARN: {}: {}.{}() not found in MC {}",
                                modName, owner.replace('/', '.'), mName, targetMcVersion);
                        issueCount++;
                    }
                }
            }

            // Check field references
            for (var entry : referencedFields.entrySet()) {
                String owner = entry.getKey();
                if (modClasses.contains(owner)) continue;
                if (isSafeLibrary(owner, safeLibraryPrefixes)) continue;
                if (!canResolveClass(owner)) continue;

                for (String fieldName : entry.getValue()) {
                    if (!canResolveField(owner, fieldName)) {
                        LOGGER.info("[RetroMod-Debug] WARN: {}: {}.{} not found in MC {}",
                                modName, owner.replace('/', '.'), fieldName, targetMcVersion);
                        issueCount++;
                    }
                }
            }

            // Check constructor parameter counts
            // If a class exists but none of its constructors match the parameter
            // count the mod is trying to invoke, that's a signature mismatch.
            for (var entry : referencedCtors.entrySet()) {
                String owner = entry.getKey();
                if (modClasses.contains(owner)) continue;
                if (isSafeLibrary(owner, safeLibraryPrefixes)) continue;
                if (!canResolveClass(owner)) continue;

                for (String desc : entry.getValue()) {
                    if (!canResolveMethod(owner, "<init>", desc)) {
                        int paramCount = countParameters(desc);
                        LOGGER.info("[RetroMod-Debug] WARN: {}: constructor {}.(<init>) with {} params not found in MC {}",
                                modName, owner.replace('/', '.'), paramCount, targetMcVersion);
                        issueCount++;
                    }
                }
            }

            if (issueCount == 0) {
                LOGGER.info("[RetroMod-Debug] Scan complete for {}: no issues found", modName);
            } else {
                LOGGER.info("[RetroMod-Debug] Scan complete for {}: {} potential issue(s) found", modName, issueCount);
            }

        } catch (Exception e) {
            // NEVER crash — just log and move on
            LOGGER.info("[RetroMod-Debug] Could not complete debug scan for {}: {}", modName, e.getMessage());
        }
    }

    /**
     * Scan mixin config files inside the JAR for target classes that don't
     * exist on the runtime classpath. Broken mixin targets cause crashes
     * when the mixin framework tries to apply them.
     */
    private void debugScanMixinTargets(JarFile jar, String modName) {
        try {
            // Read fabric.mod.json to find mixin config file names
            ZipEntry fabricJson = jar.getEntry("fabric.mod.json");
            if (fabricJson == null) return;

            String content = new String(jar.getInputStream(fabricJson).readAllBytes());
            // Extract mixin config filenames from the "mixins" array
            // Pattern: "mixins": ["foo.mixins.json", "bar.mixins.json"]
            var matcher = Pattern.compile("\"mixins\"\\s*:\\s*\\[([^]]*)]").matcher(content);
            if (!matcher.find()) return;

            String mixinsArray = matcher.group(1);
            var configMatcher = Pattern.compile("\"([^\"]+\\.json)\"").matcher(mixinsArray);

            while (configMatcher.find()) {
                String configName = configMatcher.group(1);
                ZipEntry configEntry = jar.getEntry(configName);
                if (configEntry == null) continue;

                String configContent = new String(jar.getInputStream(configEntry).readAllBytes());

                // Extract the "package" prefix and mixin class names from
                // "mixins", "client", and "server" arrays
                var pkgMatcher = Pattern.compile("\"package\"\\s*:\\s*\"([^\"]+)\"").matcher(configContent);
                String pkg = pkgMatcher.find() ? pkgMatcher.group(1).replace('.', '/') + "/" : "";

                // Look for @Mixin target annotations by scanning the "mixins",
                // "client", and "server" arrays — but we actually need the TARGET
                // classes, not the mixin classes. Targets are specified inside the
                // mixin class bytecode via @Mixin(targets=...). We check them by
                // looking for the "target" field pattern in the config JSON, which
                // some mixin configs use, and also by checking the "package" +
                // class name resolution.
                //
                // The simplest approach: scan for class names in the mixin config
                // that look like MC class references (contain dots with "net.minecraft")
                var targetMatcher = Pattern.compile("\"(net\\.minecraft\\.[^\"]+)\"").matcher(configContent);
                while (targetMatcher.find()) {
                    String target = targetMatcher.group(1);
                    String internal = target.replace('.', '/');
                    if (!canResolveClass(internal)) {
                        LOGGER.info("[RetroMod-Debug] WARN: {}: mixin target {} not found in MC {}",
                                modName, target, targetMcVersion);
                    }
                }
            }
        } catch (Exception e) {
            // Never crash during mixin scanning
        }
    }

    /**
     * Check if a class name starts with any of the safe library prefixes.
     * Classes matching these prefixes are provided by the JVM, MC runtime,
     * or mod loaders and should not be reported as missing.
     */
    private static boolean isSafeLibrary(String className, String[] prefixes) {
        for (String prefix : prefixes) {
            if (className.startsWith(prefix)) return true;
        }
        return false;
    }

    /**
     * Try to resolve a class by internal name against the runtime classpath.
     * Returns true if the class can be loaded (i.e., exists in the MC JAR,
     * Fabric API, or other runtime libraries).
     */
    private static boolean canResolveClass(String internalName) {
        try {
            String dotName = internalName.replace('/', '.');
            Class.forName(dotName, false, FabricModTransformer.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        } catch (Exception e) {
            // SecurityException, LinkageError, etc. — treat as resolvable
            // to avoid false positives
            return true;
        }
    }

    /**
     * Try to resolve a method on a class. Uses reflection to check if the
     * class has a method matching the given name. We don't fully parse the
     * ASM descriptor into Java types because that would require recursive
     * class loading; instead we check by name and parameter count as a
     * best-effort heuristic.
     */
    private static boolean canResolveMethod(String ownerInternal, String name, String desc) {
        try {
            String dotName = ownerInternal.replace('/', '.');
            Class<?> cls = Class.forName(dotName, false, FabricModTransformer.class.getClassLoader());
            int paramCount = countParameters(desc);

            if ("<init>".equals(name)) {
                // Check constructors by parameter count
                for (var ctor : cls.getDeclaredConstructors()) {
                    if (ctor.getParameterCount() == paramCount) return true;
                }
                // Also check superclass constructors (inherited)
                Class<?> sup = cls.getSuperclass();
                while (sup != null) {
                    for (var ctor : sup.getDeclaredConstructors()) {
                        if (ctor.getParameterCount() == paramCount) return true;
                    }
                    sup = sup.getSuperclass();
                }
                return false;
            }

            // Check declared methods + inherited methods by name and param count
            // Walk the class hierarchy to catch inherited methods
            Class<?> current = cls;
            while (current != null) {
                for (var m : current.getDeclaredMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                        return true;
                    }
                }
                current = current.getSuperclass();
            }
            // Also check interfaces
            for (var iface : cls.getInterfaces()) {
                for (var m : iface.getMethods()) {
                    if (m.getName().equals(name) && m.getParameterCount() == paramCount) {
                        return true;
                    }
                }
            }
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            // Class not found — we already log this in the class check
            return true; // Don't double-report
        } catch (Exception e) {
            return true; // Avoid false positives
        }
    }

    /**
     * Try to resolve a field on a class by name using reflection.
     */
    private static boolean canResolveField(String ownerInternal, String fieldName) {
        try {
            String dotName = ownerInternal.replace('/', '.');
            Class<?> cls = Class.forName(dotName, false, FabricModTransformer.class.getClassLoader());
            // Walk class hierarchy for the field
            Class<?> current = cls;
            while (current != null) {
                try {
                    current.getDeclaredField(fieldName);
                    return true;
                } catch (NoSuchFieldException e) {
                    current = current.getSuperclass();
                }
            }
            return false;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return true; // Don't double-report if class is missing
        } catch (Exception e) {
            return true; // Avoid false positives
        }
    }

    /**
     * Count the number of parameters in an ASM method descriptor.
     * For example, "(ILjava/lang/String;D)V" has 3 parameters: int, String, double.
     */
    private static int countParameters(String desc) {
        int count = 0;
        int i = 1; // Skip opening '('
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'L') {
                // Object type — skip to ';'
                i = desc.indexOf(';', i) + 1;
                count++;
            } else if (c == '[') {
                // Array — skip dimension markers
                i++;
            } else {
                // Primitive type (B, C, D, F, I, J, S, Z)
                i++;
                count++;
            }
        }
        return count;
    }
}
