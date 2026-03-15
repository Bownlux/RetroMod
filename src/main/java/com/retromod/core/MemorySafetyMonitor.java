/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Performance monitor for RetroMod.
 * 
 * Tracks:
 * - Memory usage
 * - CPU load (transformation time)
 * - TPS (ticks per second) if available
 * - Per-mod resource usage (to identify heavy mods)
 * 
 * Shows friendly errors suggesting which mods to remove.
 */
public class MemorySafetyMonitor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Performance");
    
    // Singleton instance
    private static MemorySafetyMonitor instance;
    
    // Thresholds
    private static final double MEMORY_CRITICAL_PERCENT = 0.90;
    private static final double MEMORY_WARNING_PERCENT = 0.80;
    private static final double CPU_CRITICAL_PERCENT = 0.95;
    private static final double CPU_WARNING_PERCENT = 0.80;
    private static final int TPS_CRITICAL = 5;
    private static final int TPS_WARNING = 10;
    private static final int NORMAL_TPS = 20;
    
    // System monitoring
    private final MemoryMXBean memoryBean;
    private final OperatingSystemMXBean osBean;
    
    // Per-mod tracking
    private final Map<String, ModPerformanceData> modPerformance = new ConcurrentHashMap<>();
    
    // Current state
    private final AtomicInteger activeTransforms = new AtomicInteger(0);
    private final AtomicLong totalTransformTimeNs = new AtomicLong(0);
    private final AtomicInteger totalTransformed = new AtomicInteger(0);
    
    // TPS tracking
    private volatile int currentTps = NORMAL_TPS;
    private long lastTickTime = System.currentTimeMillis();
    private int tickCount = 0;
    
    // State flags
    private volatile boolean shutdownRequested = false;
    private volatile boolean warningShown = false;
    private volatile PerformanceIssue lastIssue = null;
    
    public enum PerformanceIssue {
        NONE,
        MEMORY_WARNING,
        MEMORY_CRITICAL,
        CPU_WARNING,
        CPU_CRITICAL,
        TPS_WARNING,
        TPS_CRITICAL
    }
    
    /**
     * Tracks performance data for a single mod.
     */
    public static class ModPerformanceData {
        public final String modId;
        public final String modName;
        public long totalTransformTimeNs = 0;
        public int classesTransformed = 0;
        public long memoryUsedBytes = 0;
        public int transformErrors = 0;
        
        public ModPerformanceData(String modId, String modName) {
            this.modId = modId;
            this.modName = modName != null ? modName : modId;
        }
        
        public double getAverageTransformTimeMs() {
            if (classesTransformed == 0) return 0;
            return (totalTransformTimeNs / 1_000_000.0) / classesTransformed;
        }
        
        public long getTotalTransformTimeMs() {
            return totalTransformTimeNs / 1_000_000;
        }
    }
    
    private MemorySafetyMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.osBean = ManagementFactory.getOperatingSystemMXBean();
        
        // Start background monitoring thread
        Thread monitor = new Thread(this::monitorLoop, "RetroMod-PerformanceMonitor");
        monitor.setDaemon(true);
        monitor.start();
    }
    
    public static synchronized MemorySafetyMonitor getInstance() {
        if (instance == null) {
            instance = new MemorySafetyMonitor();
        }
        return instance;
    }
    
    /**
     * Called by Minecraft's tick loop to track TPS.
     */
    public void onServerTick() {
        tickCount++;
        long now = System.currentTimeMillis();
        long elapsed = now - lastTickTime;
        
        if (elapsed >= 1000) {
            currentTps = (int) (tickCount * 1000.0 / elapsed);
            tickCount = 0;
            lastTickTime = now;
            
            // Check for TPS issues
            if (currentTps <= TPS_CRITICAL && !warningShown) {
                lastIssue = PerformanceIssue.TPS_CRITICAL;
                showPerformanceError();
            } else if (currentTps <= TPS_WARNING && !warningShown) {
                lastIssue = PerformanceIssue.TPS_WARNING;
            }
        }
    }
    
    /**
     * Call before starting to transform a class.
     */
    public TransformContext beginTransform(String className, String modId) {
        if (shutdownRequested) {
            return null;
        }
        
        activeTransforms.incrementAndGet();
        
        // Check current performance
        PerformanceIssue issue = checkPerformance();
        if (issue == PerformanceIssue.MEMORY_CRITICAL || 
            issue == PerformanceIssue.CPU_CRITICAL ||
            issue == PerformanceIssue.TPS_CRITICAL) {
            
            activeTransforms.decrementAndGet();
            if (!warningShown) {
                lastIssue = issue;
                showPerformanceError();
            }
            return null;
        }
        
        return new TransformContext(className, modId, System.nanoTime());
    }
    
    /**
     * Call after finishing transformation.
     */
    public void endTransform(TransformContext ctx, boolean success, long memoryUsed) {
        if (ctx == null) return;
        
        activeTransforms.decrementAndGet();
        long elapsed = System.nanoTime() - ctx.startTimeNs;
        totalTransformTimeNs.addAndGet(elapsed);
        
        if (success) {
            totalTransformed.incrementAndGet();
        }
        
        // Track per-mod data
        if (ctx.modId != null) {
            ModPerformanceData data = modPerformance.computeIfAbsent(
                ctx.modId, 
                id -> new ModPerformanceData(id, ctx.modId)
            );
            data.totalTransformTimeNs += elapsed;
            data.classesTransformed++;
            data.memoryUsedBytes += memoryUsed;
            if (!success) {
                data.transformErrors++;
            }
        }
    }
    
    /**
     * Check current system performance.
     */
    public PerformanceIssue checkPerformance() {
        // Check memory
        double memPercent = getMemoryUsagePercent();
        if (memPercent > MEMORY_CRITICAL_PERCENT) {
            return PerformanceIssue.MEMORY_CRITICAL;
        }
        if (memPercent > MEMORY_WARNING_PERCENT) {
            return PerformanceIssue.MEMORY_WARNING;
        }
        
        // Check CPU
        double cpuPercent = getCpuUsagePercent();
        if (cpuPercent > CPU_CRITICAL_PERCENT) {
            return PerformanceIssue.CPU_CRITICAL;
        }
        if (cpuPercent > CPU_WARNING_PERCENT) {
            return PerformanceIssue.CPU_WARNING;
        }
        
        // Check TPS
        if (currentTps <= TPS_CRITICAL) {
            return PerformanceIssue.TPS_CRITICAL;
        }
        if (currentTps <= TPS_WARNING) {
            return PerformanceIssue.TPS_WARNING;
        }
        
        return PerformanceIssue.NONE;
    }
    
    /**
     * Get memory usage as percentage (0.0 to 1.0).
     */
    public double getMemoryUsagePercent() {
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        return (double) heap.getUsed() / heap.getMax();
    }
    
    /**
     * Get CPU usage as percentage (0.0 to 1.0).
     */
    public double getCpuUsagePercent() {
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double load = sunBean.getProcessCpuLoad();
            return load >= 0 ? load : 0.5; // Default to 50% if unavailable
        }
        return osBean.getSystemLoadAverage() / osBean.getAvailableProcessors();
    }
    
    /**
     * Get current TPS.
     */
    public int getCurrentTps() {
        return currentTps;
    }
    
    /**
     * Get the heaviest mods by transform time.
     */
    public List<ModPerformanceData> getHeaviestMods(int limit) {
        return modPerformance.values().stream()
            .sorted((a, b) -> Long.compare(b.totalTransformTimeNs, a.totalTransformTimeNs))
            .limit(limit)
            .toList();
    }
    
    /**
     * Background monitoring loop.
     */
    private void monitorLoop() {
        while (!shutdownRequested) {
            try {
                Thread.sleep(500); // Check every 500ms
                
                PerformanceIssue issue = checkPerformance();
                
                if (issue != PerformanceIssue.NONE && !warningShown) {
                    // Log performance status periodically
                    LOGGER.debug("Performance: Memory={}%, CPU={}%, TPS={}",
                        String.format("%.1f", getMemoryUsagePercent() * 100),
                        String.format("%.1f", getCpuUsagePercent() * 100),
                        currentTps);
                }
                
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.debug("Monitor error: {}", e.getMessage());
            }
        }
    }
    
    /**
     * Show performance error with specific cause and heavy mod suggestions.
     * On servers, logs to console instead of showing GUI.
     */
    private void showPerformanceError() {
        warningShown = true;
        
        // Build the message
        StringBuilder message = new StringBuilder();
        message.append("RetroMod Performance Issue Detected!\n\n");
        
        // Explain the specific problem
        switch (lastIssue) {
            case MEMORY_CRITICAL, MEMORY_WARNING -> {
                message.append("Problem: OUT OF MEMORY\n");
                message.append(String.format("Memory usage: %.0f%%\n\n", getMemoryUsagePercent() * 100));
                message.append("Your computer doesn't have enough RAM for all these mods.\n\n");
            }
            case CPU_CRITICAL, CPU_WARNING -> {
                message.append("Problem: CPU OVERLOADED\n");
                message.append(String.format("CPU usage: %.0f%%\n\n", getCpuUsagePercent() * 100));
                message.append("Your processor is struggling to transform all these mods.\n\n");
            }
            case TPS_CRITICAL, TPS_WARNING -> {
                message.append("Problem: GAME LAGGING (Low TPS)\n");
                message.append(String.format("Current TPS: %d (should be 20)\n\n", currentTps));
                message.append("The game is running slowly because of mod transformation.\n\n");
            }
            default -> {
                message.append("Problem: Performance degradation detected.\n\n");
            }
        }
        
        // Show heaviest mods
        List<ModPerformanceData> heavyMods = getHeaviestMods(5);
        if (!heavyMods.isEmpty()) {
            message.append("═══════════════════════════════════════\n");
            message.append("HEAVIEST MODS (consider removing these):\n");
            message.append("═══════════════════════════════════════\n\n");
            
            for (int i = 0; i < heavyMods.size(); i++) {
                ModPerformanceData mod = heavyMods.get(i);
                message.append(String.format("%d. %s\n", i + 1, mod.modName));
                message.append(String.format("   • Transform time: %dms\n", mod.getTotalTransformTimeMs()));
                message.append(String.format("   • Classes: %d\n", mod.classesTransformed));
                if (mod.memoryUsedBytes > 0) {
                    message.append(String.format("   • Memory: %.1fMB\n", mod.memoryUsedBytes / 1024.0 / 1024.0));
                }
                message.append("\n");
            }
        }
        
        // Solutions
        message.append("═══════════════════════════════════════\n");
        message.append("SOLUTIONS:\n");
        message.append("═══════════════════════════════════════\n\n");
        
        if (lastIssue == PerformanceIssue.MEMORY_CRITICAL || 
            lastIssue == PerformanceIssue.MEMORY_WARNING) {
            message.append("1. Allocate more RAM:\n");
            if (EnvironmentDetector.isDedicatedServer()) {
                message.append("   • Edit start.sh/start.bat\n");
                message.append("   • Change -Xmx2G to -Xmx4G or higher\n\n");
            } else {
                message.append("   • Open Minecraft Launcher\n");
                message.append("   • Installations → Edit → More Options\n");
                message.append("   • Change -Xmx2G to -Xmx4G or higher\n\n");
            }
        }
        
        message.append("2. Remove heavy mods listed above\n");
        message.append("   (especially cosmetic mods like 3D skin layers)\n\n");
        
        message.append("3. Close other applications\n");
        if (EnvironmentDetector.isDedicatedServer()) {
            message.append("   (other processes using CPU/RAM)\n");
        } else {
            message.append("   (browsers, Discord, etc.)\n");
        }
        
        boolean isCritical = lastIssue == PerformanceIssue.MEMORY_CRITICAL ||
                             lastIssue == PerformanceIssue.CPU_CRITICAL ||
                             lastIssue == PerformanceIssue.TPS_CRITICAL;
        
        // Check if we can show GUI
        if (EnvironmentDetector.canShowGui()) {
            // Show GUI dialog
            showGuiPerformanceError(message.toString(), isCritical);
        } else {
            // Server mode - log to console
            showConsolePerformanceError(message.toString(), isCritical);
        }
    }
    
    /**
     * Show GUI performance error dialog (client only).
     */
    private void showGuiPerformanceError(String message, boolean isCritical) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            if (isCritical) {
                int choice = JOptionPane.showOptionDialog(
                    null,
                    message,
                    "RetroMod - Performance Critical",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.ERROR_MESSAGE,
                    null,
                    new String[]{"Exit Game", "Try to Continue"},
                    "Exit Game"
                );
                
                if (choice == 0) {
                    LOGGER.error("User chose to exit due to performance issues");
                    System.exit(1);
                } else {
                    LOGGER.warn("User chose to continue despite performance issues");
                    shutdownRequested = false;
                    warningShown = false;
                }
            } else {
                JOptionPane.showMessageDialog(
                    null,
                    message,
                    "RetroMod - Performance Warning",
                    JOptionPane.WARNING_MESSAGE
                );
                warningShown = false;
            }
        });
    }
    
    /**
     * Show console performance error (server mode).
     */
    private void showConsolePerformanceError(String message, boolean isCritical) {
        if (isCritical) {
            LOGGER.error("═══════════════════════════════════════════════════════════");
            LOGGER.error("  RETROMOD CRITICAL PERFORMANCE ERROR");
            LOGGER.error("═══════════════════════════════════════════════════════════");
            for (String line : message.split("\n")) {
                LOGGER.error("  {}", line);
            }
            LOGGER.error("═══════════════════════════════════════════════════════════");
            LOGGER.error("  Server will continue but may be unstable.");
            LOGGER.error("  Consider stopping the server and removing heavy mods.");
            LOGGER.error("═══════════════════════════════════════════════════════════");
        } else {
            LOGGER.warn("═══════════════════════════════════════════════════════════");
            LOGGER.warn("  RETROMOD PERFORMANCE WARNING");
            LOGGER.warn("═══════════════════════════════════════════════════════════");
            for (String line : message.split("\n")) {
                LOGGER.warn("  {}", line);
            }
            LOGGER.warn("═══════════════════════════════════════════════════════════");
        }
        
        // Don't exit on server - just warn
        warningShown = false;
    }
    
    /**
     * Force garbage collection.
     */
    public void requestGarbageCollection() {
        double before = getMemoryUsagePercent();
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}
        double after = getMemoryUsagePercent();
        LOGGER.info("GC: {}% -> {}%", String.format("%.1f", before * 100), String.format("%.1f", after * 100));
    }
    
    /**
     * Context object for tracking a single transformation.
     */
    public static class TransformContext {
        public final String className;
        public final String modId;
        public final long startTimeNs;
        
        public TransformContext(String className, String modId, long startTimeNs) {
            this.className = className;
            this.modId = modId;
            this.startTimeNs = startTimeNs;
        }
    }
}
