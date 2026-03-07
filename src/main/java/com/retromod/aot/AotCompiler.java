/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import com.retromod.core.*;
import com.retromod.embedder.*;
import com.retromod.shim.ShimRegistry;
import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Ahead-of-Time (AOT) Compiler for RetroMod.
 * 
 * Instead of transforming bytecode at runtime (JIT), this compiler:
 * 1. Pre-transforms all classes in a mod JAR
 * 2. Embeds required shims for removed APIs
 * 3. Saves the transformed JAR to a cache or inside the original
 * 4. Falls back to JIT only for obfuscated code that can't be analyzed
 * 
 * Benefits:
 * - Faster subsequent launches (no runtime transformation)
 * - Can validate transformations before running
 * - Can detect compatibility issues early
 * - Transformed mods can be distributed
 */
public class AotCompiler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-aot");
    
    // Cache directory for pre-compiled mods
    private static final Path AOT_CACHE_DIR = Path.of("config/retromod/aot-cache");
    
    // Manifest key indicating this JAR was AOT compiled
    private static final String AOT_MANIFEST_KEY = "RetroMod-AOT-Version";
    
    // Current AOT compiler version (bump when shims change)
    private static final String AOT_VERSION = "1.0.0-beta.1";
    
    private final ShimRegistry shimRegistry;
    private final RetroModTransformer transformer;
    private final ModVersionDetector versionDetector;
    private final ApiEmbedder apiEmbedder;
    private final String targetMcVersion;
    
    // Statistics
    private int classesTransformed = 0;
    private int classesSkipped = 0;
    private int classesObfuscated = 0;
    
    public AotCompiler(ShimRegistry shimRegistry, String targetMcVersion) {
        this.shimRegistry = shimRegistry;
        this.transformer = RetroModTransformer.getInstance();
        this.versionDetector = new ModVersionDetector();
        this.apiEmbedder = new ApiEmbedder();
        this.targetMcVersion = targetMcVersion;
        
        try {
            Files.createDirectories(AOT_CACHE_DIR);
        } catch (IOException e) {
            LOGGER.warn("Could not create AOT cache directory", e);
        }
    }
    
    /**
     * Process a mod JAR using AOT compilation.
     * 
     * @param modJar Path to the mod JAR
     * @return Path to the AOT-compiled JAR (may be cached version)
     */
    public Path compileModAot(Path modJar) throws IOException {
        LOGGER.info("AOT compiling: {}", modJar.getFileName());
        
        // Check if already AOT compiled
        Path cachedJar = getCachedJar(modJar);
        if (cachedJar != null && isValidCache(cachedJar, modJar)) {
            LOGGER.info("Using cached AOT compilation for: {}", modJar.getFileName());
            return cachedJar;
        }
        
        // Analyze the mod
        ModVersionInfo modInfo = versionDetector.detectVersion(modJar);
        if (modInfo == null) {
            LOGGER.warn("Could not analyze mod: {}", modJar.getFileName());
            return modJar;  // Return original
        }
        
        // Check if transformation is needed
        if (!modInfo.needsTransformation(targetMcVersion)) {
            LOGGER.info("Mod {} is already compatible, skipping AOT", modInfo.modId());
            return modJar;
        }
        
        // Create backup before transformation
        backupOriginalMod(modJar);
        
        // Find shim chain
        List<VersionShim> shimChain = shimRegistry.findShimChain(
            modInfo.modLoaderType(),
            modInfo.targetMcVersion(),
            targetMcVersion
        );
        
        if (shimChain.isEmpty()) {
            LOGGER.warn("No shim chain available for {} ({} -> {})", 
                modInfo.modId(), modInfo.targetMcVersion(), targetMcVersion);
            return modJar;
        }
        
        // Register all shims in chain
        for (VersionShim shim : shimChain) {
            LOGGER.debug("Applying shim: {}", shim.getShimName());
            shim.registerRedirects(transformer);
        }
        
        // Perform AOT compilation
        Path outputJar = AOT_CACHE_DIR.resolve(
            modJar.getFileName().toString().replace(".jar", "-aot.jar")
        );
        
        compileJar(modJar, outputJar, modInfo);
        
        LOGGER.info("AOT compilation complete: {} classes transformed, {} skipped, {} obfuscated (JIT fallback)",
            classesTransformed, classesSkipped, classesObfuscated);
        
        return outputJar;
    }
    
    /**
     * Backup original mod JAR before transformation.
     * Backups are stored in mods/retromod-backups/
     */
    private void backupOriginalMod(Path modJar) {
        try {
            Path backupFolder = modJar.getParent().resolve("retromod-backups");
            Files.createDirectories(backupFolder);
            
            String fileName = modJar.getFileName().toString();
            Path backupPath = backupFolder.resolve(fileName);
            
            // Don't overwrite existing backups
            if (!Files.exists(backupPath)) {
                Files.copy(modJar, backupPath);
                LOGGER.info("Created backup: {}", backupPath);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not create backup for: {}", modJar.getFileName(), e);
        }
    }
    
    /**
     * Compile all mods in a folder using AOT.
     * Returns immediately, compilation happens in background.
     */
    public CompletableFuture<List<Path>> compileAllModsAsync(Path modsFolder) {
        return CompletableFuture.supplyAsync(() -> {
            List<Path> compiled = new ArrayList<>();
            
            try {
                File[] modFiles = modsFolder.toFile().listFiles(
                    (dir, name) -> name.endsWith(".jar") && !name.contains("-aot")
                );
                
                if (modFiles == null) return compiled;
                
                for (File modFile : modFiles) {
                    try {
                        Path result = compileModAot(modFile.toPath());
                        compiled.add(result);
                    } catch (Exception e) {
                        LOGGER.error("Failed to AOT compile: {}", modFile.getName(), e);
                        compiled.add(modFile.toPath());  // Use original
                    }
                }
            } catch (Exception e) {
                LOGGER.error("AOT compilation batch failed", e);
            }
            
            return compiled;
        });
    }
    
    /**
     * Compile all mods synchronously (blocks until complete).
     * Shows progress for UI feedback.
     */
    public List<AotResult> compileAllModsSync(Path modsFolder, ProgressCallback callback) {
        List<AotResult> results = new ArrayList<>();
        
        File[] modFiles = modsFolder.toFile().listFiles(
            (dir, name) -> name.endsWith(".jar") && !name.contains("-aot")
        );
        
        if (modFiles == null || modFiles.length == 0) {
            return results;
        }
        
        int total = modFiles.length;
        int current = 0;
        
        for (File modFile : modFiles) {
            current++;
            
            if (callback != null) {
                callback.onProgress(current, total, modFile.getName());
            }
            
            try {
                long startTime = System.currentTimeMillis();
                Path result = compileModAot(modFile.toPath());
                long duration = System.currentTimeMillis() - startTime;
                
                results.add(new AotResult(
                    modFile.toPath(),
                    result,
                    AotResult.Status.SUCCESS,
                    duration,
                    classesTransformed,
                    classesObfuscated
                ));
                
                // Reset counters
                classesTransformed = 0;
                classesSkipped = 0;
                classesObfuscated = 0;
                
            } catch (Exception e) {
                results.add(new AotResult(
                    modFile.toPath(),
                    modFile.toPath(),
                    AotResult.Status.FAILED,
                    0,
                    0,
                    0
                ));
                LOGGER.error("AOT compilation failed: {}", modFile.getName(), e);
            }
        }
        
        return results;
    }
    
    /**
     * Perform the actual JAR compilation.
     */
    private void compileJar(Path inputJar, Path outputJar, ModVersionInfo modInfo) throws IOException {
        // Collect all classes and analyze them first
        Map<String, byte[]> transformedClasses = new LinkedHashMap<>();
        Map<String, byte[]> originalResources = new LinkedHashMap<>();
        Set<String> obfuscatedClasses = new HashSet<>();
        
        try (JarFile jar = new JarFile(inputJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                
                if (entry.isDirectory()) continue;
                
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] data = is.readAllBytes();
                    
                    if (entry.getName().endsWith(".class")) {
                        // Analyze and potentially transform class
                        String className = entry.getName().replace(".class", "");
                        
                        if (shouldTransformClass(className, modInfo)) {
                            if (isObfuscated(data)) {
                                // Mark for JIT fallback
                                obfuscatedClasses.add(className);
                                transformedClasses.put(entry.getName(), data);
                                classesObfuscated++;
                            } else {
                                // AOT transform
                                byte[] transformed = transformClassAot(data, className);
                                transformedClasses.put(entry.getName(), transformed);
                                classesTransformed++;
                            }
                        } else {
                            transformedClasses.put(entry.getName(), data);
                            classesSkipped++;
                        }
                    } else {
                        originalResources.put(entry.getName(), data);
                    }
                }
            }
        }
        
        // Collect embedded shims
        Map<String, byte[]> embeddedShims = collectEmbeddedShims(modInfo);
        
        // Write output JAR
        try (JarOutputStream jos = new JarOutputStream(
                new BufferedOutputStream(new FileOutputStream(outputJar.toFile())))) {
            
            // Write manifest with AOT marker
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue(AOT_MANIFEST_KEY, AOT_VERSION);
            manifest.getMainAttributes().putValue("RetroMod-Source-Version", modInfo.targetMcVersion());
            manifest.getMainAttributes().putValue("RetroMod-Target-Version", targetMcVersion);
            manifest.getMainAttributes().putValue("RetroMod-Compiled-Time", String.valueOf(System.currentTimeMillis()));
            manifest.getMainAttributes().putValue("RetroMod-Source-Hash", computeHash(inputJar));
            
            // Add obfuscated class list for JIT fallback
            if (!obfuscatedClasses.isEmpty()) {
                manifest.getMainAttributes().putValue("RetroMod-JIT-Classes", 
                    String.join(",", obfuscatedClasses));
            }
            
            jos.putNextEntry(new JarEntry("META-INF/MANIFEST.MF"));
            manifest.write(jos);
            jos.closeEntry();
            
            // Write transformed classes
            for (Map.Entry<String, byte[]> entry : transformedClasses.entrySet()) {
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
            
            // Write original resources
            for (Map.Entry<String, byte[]> entry : originalResources.entrySet()) {
                if (entry.getKey().equals("META-INF/MANIFEST.MF")) continue;  // Already wrote
                jos.putNextEntry(new JarEntry(entry.getKey()));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
            
            // Write embedded shims
            for (Map.Entry<String, byte[]> entry : embeddedShims.entrySet()) {
                jos.putNextEntry(new JarEntry("retromod_embedded/" + entry.getKey() + ".class"));
                jos.write(entry.getValue());
                jos.closeEntry();
            }
            
            // Write AOT metadata
            writeAotMetadata(jos, modInfo, obfuscatedClasses);
        }
    }
    
    /**
     * Compile a class using hybrid AOT/JIT approach.
     * Falls back to partial JIT only for specific code regions that can't be analyzed.
     */
    private byte[] transformClassAot(byte[] classBytes, String className) {
        try {
            // Use hybrid compiler for smart partial AOT
            HybridCompiler hybridCompiler = new HybridCompiler(transformer);
            HybridCompiler.HybridCompilationResult result = hybridCompiler.compileClass(classBytes, className);
            
            // Update stats
            if (result.methodsJitOnly() > 0) {
                classesObfuscated++;  // Track classes that needed any JIT
            }
            
            // Log compilation mode breakdown
            if (result.methodsPartialAot() > 0 || result.methodsJitOnly() > 0) {
                RetroMod.LOGGER.debug("Class {} compiled: {} full AOT, {} partial, {} JIT-only methods",
                    className, result.methodsFullyAot(), result.methodsPartialAot(), result.methodsJitOnly());
            }
            
            return result.bytecode();
            
        } catch (Exception e) {
            // Fallback to simple transformation
            RetroMod.LOGGER.warn("Hybrid compilation failed for {}, using simple transform", className);
            return transformClassSimple(classBytes, className);
        }
    }
    
    /**
     * Simple transformation fallback - transforms entire class without analysis.
     */
    private byte[] transformClassSimple(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        
        // Full transformation pass
        ClassVisitor visitor = new AotClassVisitor(Opcodes.ASM9, writer, className);
        reader.accept(visitor, ClassReader.EXPAND_FRAMES);
        
        return writer.toByteArray();
    }
    
    /**
     * ASM ClassVisitor for AOT transformation.
     * More thorough than JIT - analyzes and rewrites everything.
     */
    private class AotClassVisitor extends ClassVisitor {
        private final String className;
        
        public AotClassVisitor(int api, ClassVisitor classVisitor, String className) {
            super(api, classVisitor);
            this.className = className;
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            return new AotMethodVisitor(api, mv);
        }
        
        @Override
        public void visit(int version, int access, String name, String signature,
                String superName, String[] interfaces) {
            // Handle class-level redirects (superclass changes, interface changes)
            String newSuper = transformer.getClassRedirects().getOrDefault(superName, superName);
            
            String[] newInterfaces = interfaces;
            if (interfaces != null) {
                newInterfaces = new String[interfaces.length];
                for (int i = 0; i < interfaces.length; i++) {
                    newInterfaces[i] = transformer.getClassRedirects()
                        .getOrDefault(interfaces[i], interfaces[i]);
                }
            }
            
            super.visit(version, access, name, signature, newSuper, newInterfaces);
        }
    }
    
    /**
     * ASM MethodVisitor for AOT transformation.
     */
    private class AotMethodVisitor extends MethodVisitor {
        
        public AotMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, 
                String descriptor, boolean isInterface) {
            
            // Check for method redirect
            var key = new RetroModTransformer.MethodKey(owner, name, descriptor);
            var target = transformer.getMethodRedirects().get(key);
            
            if (target != null) {
                // Redirect the call
                super.visitMethodInsn(opcode, target.owner(), target.name(), 
                    target.desc(), isInterface);
            } else {
                // Check for class redirect
                String newOwner = transformer.getClassRedirects().getOrDefault(owner, owner);
                super.visitMethodInsn(opcode, newOwner, name, descriptor, isInterface);
            }
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            String newOwner = transformer.getClassRedirects().getOrDefault(owner, owner);
            super.visitFieldInsn(opcode, newOwner, name, descriptor);
        }
        
        @Override
        public void visitTypeInsn(int opcode, String type) {
            String newType = transformer.getClassRedirects().getOrDefault(type, type);
            super.visitTypeInsn(opcode, newType);
        }
        
        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type type) {
                if (type.getSort() == Type.OBJECT) {
                    String newName = transformer.getClassRedirects()
                        .getOrDefault(type.getInternalName(), type.getInternalName());
                    super.visitLdcInsn(Type.getObjectType(newName));
                    return;
                }
            }
            super.visitLdcInsn(value);
        }
        
        @Override
        public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
            // Handle array type redirects
            String newDesc = redirectDescriptor(descriptor);
            super.visitMultiANewArrayInsn(newDesc, numDimensions);
        }
        
        private String redirectDescriptor(String descriptor) {
            // Simple descriptor redirection
            for (var entry : transformer.getClassRedirects().entrySet()) {
                descriptor = descriptor.replace(
                    "L" + entry.getKey() + ";",
                    "L" + entry.getValue() + ";"
                );
            }
            return descriptor;
        }
    }
    
    /**
     * Check if a class appears to be obfuscated.
     * Obfuscated classes need JIT fallback because we can't predict
     * all code paths statically.
     */
    private boolean isObfuscated(byte[] classBytes) {
        try {
            ClassReader reader = new ClassReader(classBytes);
            String className = reader.getClassName();
            
            // Check for common obfuscation patterns
            
            // 1. Very short class names (a, b, aa, ab, etc.)
            String simpleName = className.substring(className.lastIndexOf('/') + 1);
            if (simpleName.length() <= 2 && simpleName.matches("[a-z]+")) {
                return true;
            }
            
            // 2. Classes in default package or obfuscator packages
            if (!className.contains("/")) {
                return true;  // Default package
            }
            
            // 3. Check method names
            final boolean[] hasObfuscatedMethods = {false};
            reader.accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor,
                        String signature, String[] exceptions) {
                    // Short method names that aren't constructors or common names
                    if (name.length() <= 2 && !name.equals("<init>") && !name.equals("<clinit>")) {
                        hasObfuscatedMethods[0] = true;
                    }
                    return null;
                }
            }, ClassReader.SKIP_CODE);
            
            return hasObfuscatedMethods[0];
            
        } catch (Exception e) {
            // If we can't analyze it, assume not obfuscated
            return false;
        }
    }
    
    /**
     * Check if a class should be transformed based on its package.
     */
    private boolean shouldTransformClass(String className, ModVersionInfo modInfo) {
        // Don't transform Minecraft classes
        if (className.startsWith("net/minecraft/") || 
            className.startsWith("com/mojang/")) {
            return false;
        }
        
        // Don't transform mod loader classes
        if (className.startsWith("net/fabricmc/") ||
            className.startsWith("net/minecraftforge/") ||
            className.startsWith("net/neoforged/")) {
            return false;
        }
        
        // Transform if in mod's packages
        for (String pkg : modInfo.modPackages()) {
            if (className.startsWith(pkg.replace('.', '/'))) {
                return true;
            }
        }
        
        // Transform if it's likely mod code
        return true;
    }
    
    /**
     * Collect all shim classes that need to be embedded.
     */
    private Map<String, byte[]> collectEmbeddedShims(ModVersionInfo modInfo) throws IOException {
        Map<String, byte[]> shims = new HashMap<>();
        
        // Get shim chain
        List<VersionShim> shimChain = shimRegistry.findShimChain(
            modInfo.modLoaderType(),
            modInfo.targetMcVersion(),
            targetMcVersion
        );
        
        // Collect shim classes from each shim in chain
        for (VersionShim shim : shimChain) {
            for (String shimClass : shim.getShimClasses()) {
                try {
                    String resourcePath = shimClass.replace('.', '/') + ".class";
                    InputStream is = getClass().getClassLoader().getResourceAsStream(resourcePath);
                    if (is != null) {
                        shims.put(shimClass.replace('.', '/'), is.readAllBytes());
                        is.close();
                    }
                } catch (Exception e) {
                    LOGGER.warn("Could not load shim class: {}", shimClass);
                }
            }
        }
        
        return shims;
    }
    
    /**
     * Write AOT metadata file to the JAR.
     */
    private void writeAotMetadata(JarOutputStream jos, ModVersionInfo modInfo,
            Set<String> obfuscatedClasses) throws IOException {
        
        StringBuilder sb = new StringBuilder();
        sb.append("# RetroMod AOT Compilation Metadata\n");
        sb.append("aot_version=").append(AOT_VERSION).append("\n");
        sb.append("source_mc_version=").append(modInfo.targetMcVersion()).append("\n");
        sb.append("target_mc_version=").append(targetMcVersion).append("\n");
        sb.append("mod_id=").append(modInfo.modId()).append("\n");
        sb.append("mod_loader=").append(modInfo.modLoaderType()).append("\n");
        sb.append("compiled_time=").append(System.currentTimeMillis()).append("\n");
        sb.append("classes_transformed=").append(classesTransformed).append("\n");
        sb.append("classes_jit_fallback=").append(obfuscatedClasses.size()).append("\n");
        
        if (!obfuscatedClasses.isEmpty()) {
            sb.append("\n# Classes requiring JIT transformation (obfuscated)\n");
            for (String cls : obfuscatedClasses) {
                sb.append("jit_class=").append(cls).append("\n");
            }
        }
        
        jos.putNextEntry(new JarEntry("retromod_aot.properties"));
        jos.write(sb.toString().getBytes());
        jos.closeEntry();
    }
    
    /**
     * Get cached JAR path if it exists.
     */
    private Path getCachedJar(Path originalJar) {
        Path cached = AOT_CACHE_DIR.resolve(
            originalJar.getFileName().toString().replace(".jar", "-aot.jar")
        );
        return Files.exists(cached) ? cached : null;
    }
    
    /**
     * Check if cached JAR is still valid.
     */
    private boolean isValidCache(Path cachedJar, Path originalJar) {
        try (JarFile jar = new JarFile(cachedJar.toFile())) {
            Manifest manifest = jar.getManifest();
            if (manifest == null) return false;
            
            // Check AOT version
            String aotVersion = manifest.getMainAttributes().getValue(AOT_MANIFEST_KEY);
            if (!AOT_VERSION.equals(aotVersion)) return false;
            
            // Check source hash
            String cachedHash = manifest.getMainAttributes().getValue("RetroMod-Source-Hash");
            String currentHash = computeHash(originalJar);
            
            return cachedHash != null && cachedHash.equals(currentHash);
            
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Compute SHA-256 hash of a file.
     */
    private String computeHash(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(file);
            byte[] hash = digest.digest(bytes);
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
            
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * Clear the AOT cache.
     */
    public void clearCache() throws IOException {
        if (Files.exists(AOT_CACHE_DIR)) {
            Files.walk(AOT_CACHE_DIR)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        LOGGER.warn("Could not delete: {}", path);
                    }
                });
        }
        Files.createDirectories(AOT_CACHE_DIR);
    }
    
    // --- Callback interfaces ---
    
    @FunctionalInterface
    public interface ProgressCallback {
        void onProgress(int current, int total, String currentFile);
    }
    
    /**
     * Result of AOT compilation for a single mod.
     */
    public record AotResult(
        Path originalJar,
        Path compiledJar,
        Status status,
        long compilationTimeMs,
        int classesTransformed,
        int classesJitFallback
    ) {
        public enum Status {
            SUCCESS,
            CACHED,
            SKIPPED,
            FAILED
        }
    }
}
