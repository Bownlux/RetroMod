/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.aot;

import com.retromod.core.EnvironmentDetector;
import com.retromod.core.RetroModTransformer;
import com.retromod.shim.ShimRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Full AOT Pre-compilation Engine
 * 
 * This is a ONE-TIME heavy compilation that:
 * 1. Scans ALL classes in all mods
 * 2. Analyzes every code path
 * 3. Pre-compiles EVERYTHING that can be AOT compiled
 * 4. Saves the compiled bytecode to a cache
 * 5. Future launches use the cache = MUCH FASTER!
 * 
 * Think of it like:
 * - Normal mode: Compile as you go (some lag during gameplay)
 * - Full AOT mode: Compile everything upfront (slow once, fast forever)
 * 
 * The user triggers this by:
 * - Fabric: Toggle in first-launch dialog
 * - Forge/NeoForge: Button in GUI when adding mods
 * 
 * Progress is shown so the user knows it's working.
 */
public class FullAotCompiler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-FullAOT");
    
    // Cache directory for pre-compiled classes
    private static final String CACHE_DIR = "retromod-cache/full-aot";
    
    // Singleton
    private static FullAotCompiler instance;
    
    private final ShimRegistry shimRegistry;
    private final String targetVersion;
    private final Path cacheDir;
    
    // Progress tracking
    private volatile int totalClasses = 0;
    private volatile int compiledClasses = 0;
    private volatile String currentMod = "";
    private volatile String currentClass = "";
    private volatile boolean isRunning = false;
    private volatile boolean wasCancelled = false;
    
    // Listeners for progress updates
    private final List<ProgressListener> listeners = new CopyOnWriteArrayList<>();
    
    public interface ProgressListener {
        void onProgress(int compiled, int total, String mod, String className);
        void onComplete(int totalCompiled, long timeMs);
        void onError(String mod, String className, String error);
    }
    
    private FullAotCompiler(Path gameDir, String targetVersion) {
        this.shimRegistry = new ShimRegistry();
        this.targetVersion = targetVersion;
        this.cacheDir = gameDir.resolve(CACHE_DIR);
        
        try {
            Files.createDirectories(cacheDir);
        } catch (IOException e) {
            LOGGER.error("Could not create cache directory", e);
        }
    }
    
    public static synchronized FullAotCompiler getInstance(Path gameDir, String targetVersion) {
        if (instance == null) {
            instance = new FullAotCompiler(gameDir, targetVersion);
        }
        return instance;
    }
    
    /**
     * Check if full AOT cache exists for a mod.
     */
    public boolean hasCachedCompilation(String modId) {
        Path modCache = cacheDir.resolve(modId + ".aot");
        return Files.exists(modCache);
    }
    
    /**
     * Get cached compiled class if available.
     */
    public byte[] getCachedClass(String modId, String className) {
        Path classCache = cacheDir.resolve(modId).resolve(className.replace('/', '_') + ".class");
        
        if (Files.exists(classCache)) {
            try {
                return Files.readAllBytes(classCache);
            } catch (IOException e) {
                LOGGER.debug("Could not read cached class: {}", className);
            }
        }
        
        return null;
    }
    
    /**
     * Run full AOT compilation on all mods in the input folder.
     * 
     * @param modsToCompile List of mod JAR paths
     * @return Number of classes compiled
     */
    public CompletableFuture<Integer> runFullCompilation(List<Path> modsToCompile) {
        if (isRunning) {
            return CompletableFuture.completedFuture(0);
        }
        
        isRunning = true;
        wasCancelled = false;
        compiledClasses = 0;
        totalClasses = 0;
        
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            
            try {
                LOGGER.info("═══════════════════════════════════════════════════════════");
                LOGGER.info("  FULL AOT PRE-COMPILATION STARTING");
                LOGGER.info("═══════════════════════════════════════════════════════════");
                LOGGER.info("  Mods to compile: {}", modsToCompile.size());
                LOGGER.info("  This may take several minutes...");
                LOGGER.info("  But future launches will be MUCH faster!");
                LOGGER.info("═══════════════════════════════════════════════════════════");
                
                // Step 1: Count total classes
                LOGGER.info("Counting classes...");
                for (Path modJar : modsToCompile) {
                    totalClasses += countClasses(modJar);
                }
                LOGGER.info("Total classes to compile: {}", totalClasses);
                
                // Step 2: Compile each mod using RetroModTransformer
                com.retromod.core.RetroModTransformer transformer = 
                    com.retromod.core.RetroModTransformer.getInstance();
                
                for (Path modJar : modsToCompile) {
                    if (wasCancelled) {
                        LOGGER.info("Compilation cancelled by user");
                        break;
                    }
                    
                    currentMod = modJar.getFileName().toString();
                    LOGGER.info("Compiling: {}", currentMod);
                    
                    compileAllClassesInMod(modJar, transformer);
                }
                
                long elapsed = System.currentTimeMillis() - startTime;
                
                LOGGER.info("═══════════════════════════════════════════════════════════");
                LOGGER.info("  FULL AOT COMPILATION COMPLETE!");
                LOGGER.info("═══════════════════════════════════════════════════════════");
                LOGGER.info("  Classes compiled: {}", compiledClasses);
                LOGGER.info("  Time: {} seconds", elapsed / 1000);
                LOGGER.info("  Future launches will use cached bytecode!");
                LOGGER.info("═══════════════════════════════════════════════════════════");
                
                // Notify listeners
                for (ProgressListener listener : listeners) {
                    listener.onComplete(compiledClasses, elapsed);
                }
                
                return compiledClasses;
                
            } catch (Exception e) {
                LOGGER.error("Full AOT compilation failed", e);
                return compiledClasses;
            } finally {
                isRunning = false;
            }
        });
    }
    
    /**
     * Count classes in a JAR.
     */
    private int countClasses(Path jarPath) {
        int count = 0;
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && 
                    !entry.getName().startsWith("META-INF/")) {
                    count++;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Could not count classes in: {}", jarPath.getFileName());
        }
        return count;
    }
    
    /**
     * Compile all classes in a mod JAR.
     * OPTIMIZED: Uses parallel processing for faster compilation.
     */
    private void compileAllClassesInMod(Path jarPath, RetroModTransformer transformer) {
        String modId = extractModId(jarPath);
        if (modId == null) {
            modId = jarPath.getFileName().toString().replace(".jar", "");
        }
        
        Path modCacheDir = cacheDir.resolve(modId);
        try {
            Files.createDirectories(modCacheDir);
        } catch (IOException e) {
            LOGGER.error("Could not create mod cache dir", e);
            return;
        }
        
        // Use thread pool for parallel compilation
        int threads = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Collect all class entries first (fast)
            List<JarEntry> classEntries = new ArrayList<>();
            var entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".class") && 
                    !entry.getName().startsWith("META-INF/")) {
                    classEntries.add(entry);
                }
            }
            
            // Process classes in parallel batches
            int batchSize = Math.max(10, classEntries.size() / threads);
            List<Future<?>> futures = new ArrayList<>();
            final Path finalModCacheDir = modCacheDir;
            
            for (int i = 0; i < classEntries.size(); i += batchSize) {
                if (wasCancelled) break;
                
                int start = i;
                int end = Math.min(i + batchSize, classEntries.size());
                List<JarEntry> batch = classEntries.subList(start, end);
                
                futures.add(executor.submit(() -> {
                    for (JarEntry entry : batch) {
                        if (wasCancelled) return;
                        
                        String className = entry.getName()
                            .replace(".class", "")
                            .replace('/', '.');
                        
                        currentClass = className;
                        
                        try {
                            // Read original bytecode with buffered stream
                            byte[] original;
                            try (InputStream is = new BufferedInputStream(jar.getInputStream(entry))) {
                                original = is.readAllBytes();
                            }
                            
                            // Transform it
                            byte[] transformed = transformer.transformClass(original, className);
                            
                            if (transformed != null && transformed != original) {
                                // Save to cache
                                String cacheFileName = className.replace('.', '_') + ".class";
                                Path cacheFile = finalModCacheDir.resolve(cacheFileName);
                                Files.write(cacheFile, transformed);
                                
                                synchronized (this) {
                                    compiledClasses++;
                                }
                            }
                            
                            // Notify listeners (batched - only every 10 classes)
                            if (compiledClasses % 10 == 0) {
                                for (ProgressListener listener : listeners) {
                                    listener.onProgress(compiledClasses, totalClasses, currentMod, className);
                                }
                            }
                            
                        } catch (Exception e) {
                            LOGGER.debug("Could not compile class: {}", className);
                        }
                    }
                }));
            }
            
            // Wait for all batches to complete
            for (Future<?> future : futures) {
                try {
                    future.get();
                } catch (Exception e) {
                    LOGGER.debug("Batch compilation error", e);
                }
            }
            
            // Final progress update
            for (ProgressListener listener : listeners) {
                listener.onProgress(compiledClasses, totalClasses, currentMod, "Complete");
            }
            
            // Mark mod as fully compiled
            Path marker = modCacheDir.resolve(".complete");
            Files.writeString(marker, String.valueOf(System.currentTimeMillis()));
            
        } catch (Exception e) {
            LOGGER.error("Error processing mod: {}", jarPath.getFileName(), e);
        } finally {
            executor.shutdown();
            try {
                executor.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {}
        }
    }
    
    /**
     * Extract mod ID from JAR.
     */
    private String extractModId(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try fabric.mod.json
            ZipEntry fabricEntry = jar.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String content = new String(jar.getInputStream(fabricEntry).readAllBytes());
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
            
            // Try mods.toml
            ZipEntry forgeEntry = jar.getEntry("META-INF/mods.toml");
            if (forgeEntry != null) {
                String content = new String(jar.getInputStream(forgeEntry).readAllBytes());
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
                java.util.regex.Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        } catch (Exception e) {
            // Ignore
        }
        return null;
    }
    
    /**
     * Cancel the running compilation.
     */
    public void cancel() {
        wasCancelled = true;
    }
    
    /**
     * Check if compilation is running.
     */
    public boolean isRunning() {
        return isRunning;
    }
    
    /**
     * Get current progress.
     */
    public int getProgress() {
        if (totalClasses == 0) return 0;
        return (int) ((compiledClasses * 100.0) / totalClasses);
    }
    
    /**
     * Add progress listener.
     */
    public void addProgressListener(ProgressListener listener) {
        listeners.add(listener);
    }
    
    /**
     * Remove progress listener.
     */
    public void removeProgressListener(ProgressListener listener) {
        listeners.remove(listener);
    }
    
    /**
     * Show GUI progress dialog.
     */
    public void showProgressDialog(List<Path> modsToCompile) {
        if (!EnvironmentDetector.canShowGui()) {
            // Just run without GUI on servers
            runFullCompilation(modsToCompile);
            return;
        }
        
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            JDialog dialog = new JDialog((Frame) null, "RetroMod - Full AOT Compilation", true);
            dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
            
            JPanel panel = new JPanel(new BorderLayout(10, 10));
            panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
            
            // Title
            JLabel titleLabel = new JLabel("Pre-compiling mods for maximum performance...");
            titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));
            panel.add(titleLabel, BorderLayout.NORTH);
            
            // Progress area
            JPanel progressPanel = new JPanel(new GridLayout(4, 1, 5, 5));
            
            JProgressBar progressBar = new JProgressBar(0, 100);
            progressBar.setStringPainted(true);
            progressPanel.add(progressBar);
            
            JLabel modLabel = new JLabel("Preparing...");
            progressPanel.add(modLabel);
            
            JLabel classLabel = new JLabel(" ");
            classLabel.setFont(classLabel.getFont().deriveFont(Font.PLAIN, 11f));
            progressPanel.add(classLabel);
            
            JLabel statsLabel = new JLabel("0 / 0 classes compiled");
            progressPanel.add(statsLabel);
            
            panel.add(progressPanel, BorderLayout.CENTER);
            
            // Cancel button
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(e -> {
                cancel();
                dialog.dispose();
            });
            
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            buttonPanel.add(cancelButton);
            panel.add(buttonPanel, BorderLayout.SOUTH);
            
            dialog.add(panel);
            dialog.pack();
            dialog.setSize(450, 200);
            dialog.setLocationRelativeTo(null);
            
            // Add progress listener
            ProgressListener listener = new ProgressListener() {
                @Override
                public void onProgress(int compiled, int total, String mod, String className) {
                    SwingUtilities.invokeLater(() -> {
                        int percent = total > 0 ? (int) ((compiled * 100.0) / total) : 0;
                        progressBar.setValue(percent);
                        progressBar.setString(percent + "%");
                        modLabel.setText("Mod: " + mod);
                        classLabel.setText("Class: " + truncate(className, 50));
                        statsLabel.setText(compiled + " / " + total + " classes compiled");
                    });
                }
                
                @Override
                public void onComplete(int totalCompiled, long timeMs) {
                    SwingUtilities.invokeLater(() -> {
                        dialog.dispose();
                        
                        JOptionPane.showMessageDialog(
                            null,
                            String.format("""
                                Full AOT Compilation Complete!
                                
                                ═══════════════════════════════════════
                                
                                Classes compiled: %d
                                Time: %.1f seconds
                                
                                ═══════════════════════════════════════
                                
                                Future game launches will be MUCH faster!
                                The compiled bytecode is cached and will
                                be reused automatically.
                                """, totalCompiled, timeMs / 1000.0),
                            "Compilation Complete!",
                            JOptionPane.INFORMATION_MESSAGE
                        );
                    });
                }
                
                @Override
                public void onError(String mod, String className, String error) {
                    // Just log, don't interrupt
                    LOGGER.debug("Error compiling {}: {}", className, error);
                }
                
                private String truncate(String s, int max) {
                    if (s.length() <= max) return s;
                    return "..." + s.substring(s.length() - max + 3);
                }
            };
            
            addProgressListener(listener);
            
            // Start compilation in background
            runFullCompilation(modsToCompile).thenRun(() -> {
                removeProgressListener(listener);
            });
            
            // Show dialog (blocks until closed)
            dialog.setVisible(true);
        });
    }
    
    /**
     * Clear the AOT cache.
     */
    public void clearCache() {
        try {
            if (Files.exists(cacheDir)) {
                Files.walk(cacheDir)
                    .sorted((a, b) -> b.toString().length() - a.toString().length())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException ignored) {}
                    });
            }
            Files.createDirectories(cacheDir);
            LOGGER.info("AOT cache cleared");
        } catch (Exception e) {
            LOGGER.error("Could not clear cache", e);
        }
    }
    
    /**
     * Get cache size in bytes.
     */
    public long getCacheSize() {
        try {
            if (!Files.exists(cacheDir)) return 0;
            return Files.walk(cacheDir)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try {
                        return Files.size(p);
                    } catch (IOException e) {
                        return 0;
                    }
                })
                .sum();
        } catch (Exception e) {
            return 0;
        }
    }
}
