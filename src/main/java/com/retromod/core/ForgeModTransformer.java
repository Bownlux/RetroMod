/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. MIT License.
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
 * Transforms Forge and NeoForge mods to work on newer Minecraft versions.
 *
 * Same approach as FabricModTransformer but handles mods.toml and
 * neoforge.mods.toml metadata instead of fabric.mod.json.
 *
 * Steps:
 * 1. Extract mod JAR to temp directory
 * 2. Transform all class files (bytecode)
 * 3. Update mods.toml / neoforge.mods.toml version range
 * 4. Repackage as new JAR
 * 5. Copy to mods folder
 */
public class ForgeModTransformer {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-ForgeTransform");

    /** Maximum size for a single ZIP entry (50 MB) to prevent zip bomb attacks. */
    private static final long MAX_ENTRY_SIZE = 50 * 1024 * 1024;
    /** Maximum total extracted size (500 MB) to prevent zip bomb attacks. */
    private static final long MAX_TOTAL_SIZE = 500 * 1024 * 1024;

    /**
     * Forge/NeoForge mod IDs for APIs that RetroMod provides compatibility shims for.
     * When a mod declares a dependency with a restrictive versionRange in mods.toml,
     * the mod loader will block the mod from loading. Since RetroMod transforms the
     * bytecode, we also relax these constraints.
     */
    private static final Set<String> SHIMMED_API_MOD_IDS = Set.of(
        // Tech / content mod APIs
        "mekanism", "mekanismapi",
        "ae2", "appliedenergistics2",
        "botania", "botania_api",
        "create",
        "thermal", "thermal_foundation", "thermal_expansion", "cofh_core",

        // Equipment
        "curios", "curiosapi",
        "baubles",

        // Recipe viewers
        "jei", "just_enough_items",
        "nei",

        // Tooltips / overlays
        "jade", "waila", "wthit",

        // Config libraries
        "cloth_config", "cloth-config",

        // Animation / model
        "geckolib", "geckolib3", "geckolib4",

        // Cross-platform
        "architectury",

        // Guide
        "patchouli",

        // Utility
        "autoreglib"
    );

    private final String targetMcVersion;
    private final RetroModTransformer bytecodeTransformer;

    public ForgeModTransformer(String targetMcVersion) {
        this.targetMcVersion = targetMcVersion;
        this.bytecodeTransformer = RetroModTransformer.getInstance();
    }

    /**
     * Transform a Forge or NeoForge mod JAR.
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

        LOGGER.info("Checking Forge/NeoForge mod: {}", originalName);

        // Check if mod is already for native version
        String modMcVersion = extractMinecraftVersion(sourceJar);
        if (targetMcVersion.equals(modMcVersion)) {
            LOGGER.info("  {} is already for MC {} - no transformation needed", originalName, targetMcVersion);
            Path directCopy = outputDir.resolve(originalName);
            Files.copy(sourceJar, directCopy, StandardCopyOption.REPLACE_EXISTING);
            return directCopy;
        }

        LOGGER.info("Transforming Forge/NeoForge mod: {} -> {}", originalName, outputName);

        // Create temp directory for extraction
        Path tempDir = Files.createTempDirectory("retromod-forge-");

        try {
            // Step 1: Extract JAR
            extractJar(sourceJar, tempDir);

            // Step 2: Transform bytecode
            int classesTransformed = transformClasses(tempDir);
            LOGGER.info("Transformed {} class files", classesTransformed);

            // Step 2.5: Make mixin configs non-fatal
            makeMixinConfigsNonFatal(tempDir);

            // Step 2.6: Handle access wideners/classtweakers (cross-loader mods)
            stripAccessWideners(tempDir);

            // Step 3: Update mods.toml / neoforge.mods.toml
            updateModsToml(tempDir, "META-INF/mods.toml");
            updateModsToml(tempDir, "META-INF/neoforge.mods.toml");

            // Step 4: Repackage
            repackageJar(tempDir, outputJar);

            LOGGER.info("Created transformed mod: {}", outputJar.getFileName());

            // Verify transforms if enabled in config
            if (TransformVerifier.isEnabled()) {
                TransformVerifier.verifyAndReport(outputJar, originalName, targetMcVersion);
            }

            return outputJar;

        } catch (Exception e) {
            LOGGER.error("Failed to transform {}: {}", originalName, e.getMessage());
            return null;
        } finally {
            // Clean up temp directory
            try (var walk = Files.walk(tempDir)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                    try { Files.delete(p); } catch (Exception ignored) {}
                });
            }
        }
    }

    /**
     * Extract minecraft version from a Forge/NeoForge mod JAR.
     */
    private String extractMinecraftVersion(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try neoforge.mods.toml first
            ZipEntry entry = jar.getEntry("META-INF/neoforge.mods.toml");
            if (entry == null) entry = jar.getEntry("META-INF/mods.toml");

            if (entry != null) {
                String content = new String(jar.getInputStream(entry).readAllBytes());
                // Match versionRange = "[1.20.6]" or "[1.20.6,)" etc
                Pattern p = Pattern.compile("versionRange\\s*=\\s*\"\\[([0-9.]+)");
                Matcher m = p.matcher(content);
                // Skip the first match (usually forge/neoforge version), find minecraft
                while (m.find()) {
                    String version = m.group(1);
                    if (version.startsWith("1.") || version.matches("\\d{2,}\\..*")) {
                        return version;
                    }
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }

    /**
     * Extract JAR to directory with zip-bomb protection based on ACTUAL bytes
     * read, not the (attacker-controlled) declared entry size. See the
     * equivalent method in {@link FabricModTransformer#extractJar} for the
     * full rationale.
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
                    Files.createDirectories(outputPath.getParent());
                    long writtenBytes;
                    try (InputStream is = jar.getInputStream(entry)) {
                        writtenBytes = copyBounded(is, outputPath, MAX_ENTRY_SIZE, entry.getName());
                    }
                    totalSize += writtenBytes;
                    if (totalSize > MAX_TOTAL_SIZE) {
                        throw new IOException("ZIP total extracted size exceeds limit ("
                            + MAX_TOTAL_SIZE + " bytes) — possible zip bomb (decompressed "
                            + totalSize + " bytes so far)");
                    }
                }
            }
        }
    }

    /** @see FabricModTransformer#copyBounded */
    private static long copyBounded(InputStream is, Path target, long maxBytes,
                                     String entryNameForError) throws IOException {
        long written = 0;
        byte[] buf = new byte[8192];
        try (var out = java.nio.file.Files.newOutputStream(target,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING,
                java.nio.file.StandardOpenOption.WRITE)) {
            int n;
            while ((n = is.read(buf)) > 0) {
                written += n;
                if (written > maxBytes) {
                    throw new IOException("ZIP entry too large: " + entryNameForError
                        + " (exceeded " + maxBytes + " bytes while reading, "
                        + "possible zip bomb — declared size in central directory "
                        + "may be falsified)");
                }
                out.write(buf, 0, n);
            }
        }
        return written;
    }

    private int transformClasses(Path dir) throws IOException {
        final java.util.List<Path> classFiles;
        try (var stream = Files.walk(dir)) {
            classFiles = stream
                .filter(p -> p.toString().endsWith(".class"))
                .filter(p -> !p.toString().contains("META-INF"))
                .toList();
        }

        // Parallel per-class transformation — same rationale as
        // FabricModTransformer.transformClasses. Classes transform
        // independently; the bytecodeTransformer's redirect tables are
        // thread-safe (ConcurrentHashMap reads). See RetroModExecutors
        // for pool-sizing rationale.
        final java.util.concurrent.atomic.AtomicInteger counter =
                new java.util.concurrent.atomic.AtomicInteger();

        com.retromod.core.parallel.RetroModExecutors.parallelForEach(classFiles, classFile -> {
            try {
                byte[] original = Files.readAllBytes(classFile);
                String className = dir.relativize(classFile).toString()
                    .replace(".class", "")
                    .replace(File.separator, "/");

                byte[] transformed = bytecodeTransformer.transformClass(original, className);
                boolean wroteFirst = false;
                if (transformed != null && transformed != original) {
                    Files.write(classFile, transformed);
                    counter.incrementAndGet();
                    wroteFirst = true;
                }

                // Post-processing: inject missing abstract methods for Button subclasses.
                byte[] current = (transformed != null && transformed != original) ? transformed : original;
                byte[] patched = injectMissingAbstractMethods(current, className);
                if (patched != null && patched != current) {
                    Files.write(classFile, patched);
                    if (!wroteFirst) counter.incrementAndGet();
                }
            } catch (Exception e) {
                // Include exception details so per-class transform failures are
                // actually diagnosable (previously only the filename was logged).
                LOGGER.warn("Could not transform class: {} ({}: {})",
                        classFile.getFileName(), e.getClass().getSimpleName(), e.getMessage());
            }
        });

        return counter.get();
    }

    /**
     * Update mods.toml or neoforge.mods.toml to target the correct MC version.
     */
    private void updateModsToml(Path dir, String tomlPath) throws IOException {
        Path tomlFile = dir.resolve(tomlPath);
        if (!Files.exists(tomlFile)) return;

        String content = Files.readString(tomlFile);
        String original = content;

        // Update minecraft versionRange
        // Matches: versionRange = "[1.20.6]" or "[1.20.6,)" or "[1.20,1.21)"
        // Only update the minecraft dependency, not the forge/neoforge one
        // Strategy: find [[dependencies.xxx]] blocks and update the minecraft one
        content = updateMinecraftVersionRange(content);

        // Add RetroMod marker if not present
        if (!content.contains("retromod_transformed")) {
            content = content + "\n# Transformed by RetroMod (original version modified)\n";
        }

        if (!content.equals(original)) {
            Files.writeString(tomlFile, content);
            LOGGER.info("Updated {}: minecraft versionRange -> [{}]", tomlPath, targetMcVersion);
        }
    }

    /**
     * Update the minecraft versionRange in TOML content.
     * Finds the [[dependencies.xxx]] block with modId = "minecraft" and
     * updates its versionRange to the target version.
     *
     * Also relaxes version ranges for third-party APIs that RetroMod has shims for.
     * Without this, the mod loader would block the mod at startup even though
     * RetroMod has already transformed the bytecode to work with newer API versions.
     */
    private String updateMinecraftVersionRange(String toml) {
        // TOML structure:
        //   [[dependencies.modname]]
        //   modId = "minecraft"
        //   mandatory = true
        //   versionRange = "[1.20.6]"
        //
        //   [[dependencies.modname]]
        //   modId = "cloth_config"
        //   mandatory = true
        //   versionRange = "[6.0,7.0)"

        StringBuilder result = new StringBuilder();
        String[] lines = toml.split("\n");
        String currentDepModId = null;

        for (String line : lines) {
            String trimmed = line.trim();

            // Detect start of a new dependency block
            if (trimmed.startsWith("[[dependencies")) {
                currentDepModId = null;
            }

            // Detect modId = "xxx"
            Pattern modIdPattern = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
            Matcher modIdMatcher = modIdPattern.matcher(trimmed);
            if (modIdMatcher.matches()) {
                currentDepModId = modIdMatcher.group(1);
            }

            // If we're in a dependency block, check if we should update versionRange
            if (currentDepModId != null && trimmed.startsWith("versionRange")) {
                if ("minecraft".equals(currentDepModId)) {
                    // Minecraft dependency: set to target version
                    result.append("versionRange = \"[").append(targetMcVersion).append(",)\"\n");
                    LOGGER.info("  Updated minecraft versionRange -> [{},...)", targetMcVersion);
                    currentDepModId = null;
                } else if (SHIMMED_API_MOD_IDS.contains(currentDepModId)) {
                    // Shimmed API dependency: relax to accept any version
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed API dependency: {} -> [0,...) (RetroMod has shims)", currentDepModId);
                    currentDepModId = null;
                } else if ("forge".equals(currentDepModId) || "neoforge".equals(currentDepModId)) {
                    // Forge/NeoForge loader: also relax
                    result.append("versionRange = \"[0,)\"\n");
                    currentDepModId = null;
                } else if (targetMcVersion.startsWith("26.")) {
                    // For 26.1+, relax ALL non-core dependencies since most
                    // old mod versions won't be available
                    result.append("versionRange = \"[0,)\"\n");
                    LOGGER.info("  Relaxed dependency: {} -> [0,...) (26.1+ compat)", currentDepModId);
                    currentDepModId = null;
                } else {
                    // Unknown dependency: keep as-is
                    result.append(line).append("\n");
                }
            } else if (currentDepModId != null &&
                       !("minecraft".equals(currentDepModId) || "neoforge".equals(currentDepModId) || "forge".equals(currentDepModId))
                       && trimmed.startsWith("mandatory")) {
                // Make non-core dependencies non-mandatory
                result.append("mandatory = false\n");
            } else if (currentDepModId != null &&
                       !("minecraft".equals(currentDepModId) || "neoforge".equals(currentDepModId) || "forge".equals(currentDepModId))
                       && trimmed.matches("type\\s*=\\s*\"required\".*")) {
                // For NeoForge format: change type="required" to type="optional"
                result.append(line.replaceFirst("\"required\"", "\"optional\"")).append("\n");
            } else {
                result.append(line).append("\n");
            }
        }

        return result.toString();
    }

    /**
     * Inject missing abstract methods for classes that extend Button or AbstractButton.
     *
     * In MC 26.1, AbstractButton gained a new abstract method:
     *   extractContents(GuiGraphicsExtractor, int, int, float)V
     *
     * Old mod widgets that extend Button don't implement this method, causing
     * AbstractMethodError when the game tries to call it.
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
     * Scan the extracted mod directory for mixin config files and make them non-fatal.
     * Sets "required": false and "injectors": {"defaultRequire": 0} so that
     * @Accessor/@Invoker failures on removed fields/methods don't crash the game.
     */
    private void makeMixinConfigsNonFatal(Path dir) {
        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String entryName = dir.relativize(file).toString()
                    .replace(File.separator, "/");
                if (isMixinConfigFile(entryName)) {
                    try {
                        String json = Files.readString(file);
                        String transformed = makeMixinConfigNonFatal(json);
                        if (!transformed.equals(json)) {
                            Files.writeString(file, transformed, java.nio.charset.StandardCharsets.UTF_8);
                        }
                    } catch (Exception e) {
                        LOGGER.warn("Failed to process mixin config {}: {}", entryName, e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to scan for mixin configs: {}", e.getMessage());
        }
    }

    /**
     * Make a mixin config JSON non-fatal by setting "required": false and
     * "injectors": {"defaultRequire": 0}. This prevents crashes from @Accessor/@Invoker
     * targeting fields/methods that were removed in newer MC versions.
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
     * Check if a file path looks like a mixin config file.
     */
    private boolean isMixinConfigFile(String path) {
        String name = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
        if (name.endsWith(".mixins.json")) return true;       // modid.mixins.json
        if (name.endsWith("mixin.json")) return true;         // modid.mixin.json
        if (name.startsWith("mixins.") && name.endsWith(".json")) return true; // mixins.modid.json
        if (path.contains("mixins/") && name.endsWith(".json") && name.contains("mixin")) return true;
        return false;
    }

    /**
     * Strip or neutralize access widener and class tweaker files from cross-loader mods.
     * Some mods include both Fabric (.accesswidener) and Forge (META-INF/accesstransformer.cfg)
     * files. The access widener format is not understood by Forge/NeoForge and can cause issues.
     * Detection is case-insensitive to handle variations like .AccessWidener or .ACCESSWIDENER.
     */
    private void stripAccessWideners(Path dir) {
        try (var stream = Files.walk(dir)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                String name = file.getFileName().toString();
                String nameLower = name.toLowerCase(java.util.Locale.ROOT);
                if (nameLower.endsWith(".accesswidener") || nameLower.endsWith(".classtweaker")) {
                    // Access wideners are Fabric-only; remove from Forge/NeoForge JARs
                    // to prevent confusion. The access transformer (META-INF/accesstransformer.cfg)
                    // is the Forge equivalent and is left intact.
                    LOGGER.info("Removing Fabric access widener from Forge mod: {}", name);
                    Files.delete(file);
                }
            }
        } catch (IOException e) {
            LOGGER.debug("Failed to scan for access wideners: {}", e.getMessage());
        }
    }

    private void repackageJar(Path sourceDir, Path outputJar) throws IOException {
        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(Files.newOutputStream(outputJar)))) {

            try (var walk = Files.walk(sourceDir)) {
                for (Path file : walk.filter(Files::isRegularFile).toList()) {
                    String entryName = sourceDir.relativize(file).toString()
                        .replace(File.separator, "/");

                    JarEntry entry = new JarEntry(entryName);
                    jos.putNextEntry(entry);
                    Files.copy(file, jos);
                    jos.closeEntry();
                }
            }
        }
    }
}
