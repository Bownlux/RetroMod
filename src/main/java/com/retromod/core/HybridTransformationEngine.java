/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import com.retromod.aot.AotCompiler;
import com.retromod.shim.ShimRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Hybrid AOT/JIT Transformation Engine.
 * 
 * Strategy:
 * 1. On startup, scan mods folder for mods needing transformation
 * 2. Start background AOT compilation of those mods
 * 3. When a class is loaded:
 *    - If AOT cache has it → use cached bytecode (fast!)
 *    - If not yet compiled → JIT transform on the fly
 * 4. Track which mod each class belongs to for performance monitoring
 * 
 * This gives the best of both worlds:
 * - Fast startup (JIT handles early loads)
 * - Fast gameplay (AOT kicks in after background compile)
 * - Per-mod performance tracking
 */
public class HybridTransformationEngine {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Hybrid");
    
    // Singleton
    private static volatile HybridTransformationEngine instance;
    
    // AOT cache directory
    private static final Path AOT_CACHE_DIR = Path.of("config/retromod/aot-cache");
    
    // Components
    private final ShimRegistry shimRegistry;
    private final RetroModTransformer jitTransformer;
    private final ModVersionDetector versionDetector;
    private final MemorySafetyMonitor performanceMonitor;
    
    // Maps class name -> cached AOT bytecode
    private final Map<String, byte[]> aotCache = new ConcurrentHashMap<>();
    
    // Maps class name -> mod ID (for performance tracking)
    private final Map<String, String> classToModMap = new ConcurrentHashMap<>();
    
    // Mods that need transformation
    private final Map<String, ModTransformInfo> modsToTransform = new ConcurrentHashMap<>();
    
    // Background compilation state
    private final ExecutorService backgroundExecutor;
    private final AtomicBoolean aotCompletedFlag = new AtomicBoolean(false);
    private final Set<String> pendingAotClasses = ConcurrentHashMap.newKeySet();
    
    // Statistics
    private final AtomicInteger aotHits = new AtomicInteger(0);
    private final AtomicInteger jitFallbacks = new AtomicInteger(0);
    
    public record ModTransformInfo(
        Path jarPath,
        String modId,
        String modName,
        String sourceVersion,
        Set<String> packages
    ) {}
    
    private HybridTransformationEngine() {
        this.shimRegistry = new ShimRegistry();
        this.jitTransformer = RetroModTransformer.getInstance();
        this.versionDetector = new ModVersionDetector();
        this.performanceMonitor = MemorySafetyMonitor.getInstance();
        
        // Create cache directory
        try {
            Files.createDirectories(AOT_CACHE_DIR);
        } catch (IOException e) {
            LOGGER.warn("Could not create AOT cache directory", e);
        }
        
        // Background executor for AOT compilation
        this.backgroundExecutor = Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
            r -> {
                Thread t = new Thread(r, "RetroMod-AOT-Background");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY); // Low priority - don't compete with game
                return t;
            }
        );
    }
    
    public static synchronized HybridTransformationEngine getInstance() {
        if (instance == null) {
            instance = new HybridTransformationEngine();
        }
        return instance;
    }
    
    /**
     * Initialize the engine - scan mods and start background AOT.
     */
    public void initialize(Path modsFolder, String targetVersion) {
        LOGGER.info("Initializing Hybrid AOT/JIT engine...");
        
        // Step 1: Load existing AOT cache
        loadAotCache();
        
        // Step 2: Scan mods folder for mods needing transformation
        scanModsFolder(modsFolder, targetVersion);
        
        // Step 3: Initialize the safe crash handler (shares our class->mod mapping)
        initializeCrashHandler();
        
        // Step 4: Start background AOT compilation
        if (!modsToTransform.isEmpty()) {
            startBackgroundAotCompilation(targetVersion);
        } else {
            aotCompletedFlag.set(true);
            LOGGER.info("No mods need transformation - AOT skipped");
        }
        
        LOGGER.info("Hybrid engine ready: {} mods queued for AOT, {} classes pre-cached",
            modsToTransform.size(), aotCache.size());
    }
    
    /**
     * Initialize the safe crash handler with our class->mod mapping.
     */
    private void initializeCrashHandler() {
        try {
            SafeCrashHandler.getInstance();
            LOGGER.info("Safe crash handler initialized");
        } catch (Exception e) {
            LOGGER.warn("Could not initialize safe crash handler: {}", e.getMessage());
        }
    }
    
    /**
     * Get the class-to-mod mapping (used by SafeCrashHandler).
     */
    public Map<String, String> getClassToModMap() {
        return classToModMap;
    }
    
    /**
     * Transform a class - uses AOT if available, falls back to JIT.
     */
    public byte[] transform(String className, byte[] originalBytes, String modId) {
        // Track which mod this class belongs to
        if (modId != null) {
            classToModMap.put(className, modId);
        } else {
            // Try to determine mod from class name
            modId = guessModFromClass(className);
        }
        
        // Start performance tracking
        var ctx = performanceMonitor.beginTransform(className, modId);
        if (ctx == null) {
            // Performance monitor says skip - return original bytes so the class still loads
            return originalBytes;
        }

        long memBefore = Runtime.getRuntime().freeMemory();
        byte[] result = null;

        try {
            // Check AOT cache first
            if (aotCache.containsKey(className)) {
                result = aotCache.get(className);
                aotHits.incrementAndGet();
                LOGGER.trace("AOT cache hit: {}", className);
                return result;
            }

            // Check if AOT is still compiling this class
            if (pendingAotClasses.contains(className)) {
                // Wait a bit for AOT to finish, or fall through to JIT
                try {
                    Thread.sleep(50); // Brief wait
                    if (aotCache.containsKey(className)) {
                        result = aotCache.get(className);
                        aotHits.incrementAndGet();
                        return result;
                    }
                } catch (InterruptedException ignored) {}
            }

            // Fall back to JIT transformation
            jitFallbacks.incrementAndGet();
            LOGGER.trace("JIT fallback: {}", className);
            result = jitTransformer.transformClass(originalBytes, className);

            // Cache the JIT result for future use
            if (result != null) {
                aotCache.put(className, result);
            }

            return result != null ? result : originalBytes;

        } catch (OutOfMemoryError e) {
            LOGGER.error("OOM during transform: {}", className);
            performanceMonitor.requestGarbageCollection();
            return originalBytes;
        } finally {
            long memUsed = memBefore - Runtime.getRuntime().freeMemory();
            performanceMonitor.endTransform(ctx, result != null, Math.max(0, memUsed));
        }
    }
    
    /**
     * Load existing AOT cache from disk.
     */
    private void loadAotCache() {
        try {
            if (!Files.exists(AOT_CACHE_DIR)) return;
            
            try (var stream = Files.walk(AOT_CACHE_DIR)) {
                stream.filter(p -> p.toString().endsWith(".class"))
                    .forEach(p -> {
                        try {
                            String className = AOT_CACHE_DIR.relativize(p).toString()
                                .replace(".class", "")
                                .replace(p.getFileSystem().getSeparator(), "/");
                            byte[] bytes = Files.readAllBytes(p);
                            aotCache.put(className, bytes);
                        } catch (IOException e) {
                            LOGGER.debug("Could not load cached class: {}", p);
                        }
                    });
            }
            
            LOGGER.info("Loaded {} classes from AOT cache", aotCache.size());
            
        } catch (IOException e) {
            LOGGER.warn("Could not load AOT cache: {}", e.getMessage());
        }
    }
    
    /**
     * Scan mods folder for mods that need transformation.
     */
    private void scanModsFolder(Path modsFolder, String targetVersion) {
        if (!Files.exists(modsFolder)) return;
        
        try {
            Files.list(modsFolder)
                .filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> !p.getFileName().toString().contains("-retromod"))
                .forEach(jarPath -> {
                    try {
                        var info = versionDetector.detectVersion(jarPath);
                        if (info != null && info.needsTransformation(targetVersion)) {
                            // Extract packages from the JAR
                            Set<String> packages = extractPackages(jarPath);
                            
                            ModTransformInfo modInfo = new ModTransformInfo(
                                jarPath,
                                info.modId(),
                                info.modId(), // Use modId as name for now
                                info.targetMcVersion(),
                                packages
                            );
                            
                            modsToTransform.put(info.modId(), modInfo);
                            
                            // Register packages for JIT transformer
                            for (String pkg : packages) {
                                jitTransformer.addTransformablePackage(pkg);
                            }
                            
                            // Map classes to mod
                            for (String pkg : packages) {
                                // Will be refined as classes are actually loaded
                            }
                            
                            LOGGER.info("Found mod to transform: {} ({} -> {})",
                                info.modId(), info.targetMcVersion(), targetVersion);
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Could not analyze mod: {}", jarPath.getFileName());
                    }
                });
        } catch (IOException e) {
            LOGGER.error("Error scanning mods folder", e);
        }
    }
    
    /**
     * Extract package prefixes from a JAR file.
     */
    private Set<String> extractPackages(Path jarPath) {
        Set<String> packages = new HashSet<>();
        
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            jar.stream()
                .map(JarEntry::getName)
                .filter(name -> name.endsWith(".class"))
                .filter(name -> !name.startsWith("META-INF/"))
                .forEach(name -> {
                    // Get package from class name
                    int lastSlash = name.lastIndexOf('/');
                    if (lastSlash > 0) {
                        String pkg = name.substring(0, lastSlash + 1);
                        packages.add(pkg);
                    }
                });
        } catch (IOException e) {
            LOGGER.debug("Could not extract packages from: {}", jarPath);
        }
        
        return packages;
    }
    
    /**
     * Start background AOT compilation of all queued mods.
     */
    private void startBackgroundAotCompilation(String targetVersion) {
        LOGGER.info("Starting background AOT compilation of {} mods...", modsToTransform.size());
        
        // Queue each mod for background compilation
        for (ModTransformInfo mod : modsToTransform.values()) {
            backgroundExecutor.submit(() -> {
                try {
                    compileModAot(mod, targetVersion);
                } catch (Exception e) {
                    LOGGER.warn("Background AOT failed for {}: {}", mod.modId(), e.getMessage());
                }
            });
        }
        
        // Submit a final task to mark completion
        backgroundExecutor.submit(() -> {
            aotCompletedFlag.set(true);
            LOGGER.info("Background AOT compilation complete! Stats: {} AOT hits, {} JIT fallbacks",
                aotHits.get(), jitFallbacks.get());
        });
    }
    
    /**
     * AOT compile a single mod.
     */
    private void compileModAot(ModTransformInfo mod, String targetVersion) throws IOException {
        LOGGER.info("AOT compiling: {} ({})", mod.modId(), mod.jarPath().getFileName());
        
        try (JarFile jar = new JarFile(mod.jarPath().toFile())) {
            // Find all classes in the mod
            List<JarEntry> classEntries = jar.stream()
                .filter(e -> e.getName().endsWith(".class"))
                .filter(e -> !e.getName().startsWith("META-INF/"))
                .toList();
            
            int compiled = 0;
            for (JarEntry entry : classEntries) {
                String className = entry.getName().replace(".class", "");
                
                // Skip if already in cache
                if (aotCache.containsKey(className)) {
                    continue;
                }
                
                // Mark as pending
                pendingAotClasses.add(className);
                
                try {
                    // Read original bytecode
                    byte[] original = jar.getInputStream(entry).readAllBytes();
                    
                    // Transform
                    byte[] transformed = jitTransformer.transformClass(original, className);
                    
                    if (transformed != null) {
                        // Cache in memory
                        aotCache.put(className, transformed);
                        
                        // Save to disk cache
                        saveToCache(className, transformed);
                        
                        // Map to mod
                        classToModMap.put(className, mod.modId());
                        
                        compiled++;
                    }
                } finally {
                    pendingAotClasses.remove(className);
                }
                
                // Yield to avoid blocking game
                Thread.yield();
            }
            
            LOGGER.info("AOT compiled {} classes for {}", compiled, mod.modId());
        }
    }
    
    /**
     * Save transformed class to disk cache.
     */
    private void saveToCache(String className, byte[] bytes) {
        try {
            Path cachePath = AOT_CACHE_DIR.resolve(className + ".class");
            Files.createDirectories(cachePath.getParent());
            Files.write(cachePath, bytes);
        } catch (IOException e) {
            LOGGER.debug("Could not cache class: {}", className);
        }
    }
    
    /**
     * Try to determine which mod a class belongs to.
     */
    private String guessModFromClass(String className) {
        // Check direct mapping
        if (classToModMap.containsKey(className)) {
            return classToModMap.get(className);
        }
        
        // Try to match by package prefix
        for (ModTransformInfo mod : modsToTransform.values()) {
            for (String pkg : mod.packages()) {
                if (className.startsWith(pkg)) {
                    classToModMap.put(className, mod.modId());
                    return mod.modId();
                }
            }
        }
        
        return "unknown";
    }
    
    /**
     * Check if AOT compilation is complete.
     */
    public boolean isAotComplete() {
        return aotCompletedFlag.get();
    }
    
    /**
     * Get AOT cache hit rate.
     */
    public double getAotHitRate() {
        int total = aotHits.get() + jitFallbacks.get();
        if (total == 0) return 0;
        return (double) aotHits.get() / total;
    }
    
    /**
     * Get statistics summary.
     */
    public String getStats() {
        return String.format(
            "AOT: %d hits, JIT: %d fallbacks (%.1f%% hit rate), Cache: %d classes",
            aotHits.get(), jitFallbacks.get(), getAotHitRate() * 100, aotCache.size()
        );
    }
    
    /**
     * Shutdown the background executor.
     */
    public void shutdown() {
        backgroundExecutor.shutdown();
    }
}
