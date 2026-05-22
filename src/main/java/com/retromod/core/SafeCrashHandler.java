/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.retromod.gui.InGameScreenFactory;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Graceful crash handler for Retromod.
 * 
 * When a transformed mod causes an error during gameplay:
 * 1. Catches the error before it crashes Minecraft
 * 2. Pauses the game (sets TPS to 0 / freezes tick loop)
 * 3. Saves the world automatically
 * 4. Shows a friendly popup explaining what happened
 * 5. Forces the user to quit (no "continue playing" option)
 * 
 * This prevents players from losing their world due to mod incompatibilities.
 */
public class SafeCrashHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-SafeCrash");
    
    // Singleton
    private static SafeCrashHandler instance;
    
    // Track which class caused an error -> which mod it belongs to
    private final Map<String, String> classToModMap;
    
    // Track errors per mod
    private final Map<String, Integer> modErrorCounts = new ConcurrentHashMap<>();
    
    // Flag to prevent multiple crash dialogs
    private final AtomicBoolean crashHandled = new AtomicBoolean(false);
    
    // Reference to Minecraft server (for world saving)
    private Object minecraftServer = null;
    private Object minecraftClient = null;
    
    // The previous default handler, so we can chain to it
    private final Thread.UncaughtExceptionHandler previousHandler;

    private SafeCrashHandler(Map<String, String> classToModMap) {
        this.classToModMap = classToModMap;

        // Save previous handler so we can chain non-Retromod exceptions to it
        this.previousHandler = Thread.getDefaultUncaughtExceptionHandler();

        // Install our handler that only intercepts Retromod-related exceptions
        Thread.setDefaultUncaughtExceptionHandler(this::handleUncaughtException);
    }

    public static synchronized SafeCrashHandler getInstance() {
        if (instance == null) {
            // Get class->mod mapping from HybridTransformationEngine if available
            Map<String, String> mapping = new ConcurrentHashMap<>();
            try {
                HybridTransformationEngine engine = HybridTransformationEngine.getInstance();
                mapping = engine.getClassToModMap();
            } catch (Exception e) {
                // Fall back to empty mapping
            }
            instance = new SafeCrashHandler(mapping);
        }
        return instance;
    }
    
    /**
     * Register the Minecraft server instance (for world saving).
     */
    public void registerServer(Object server) {
        this.minecraftServer = server;
        LOGGER.debug("Registered Minecraft server for safe crash handling");
    }
    
    /**
     * Register the Minecraft client instance.
     */
    public void registerClient(Object client) {
        this.minecraftClient = client;
        LOGGER.debug("Registered Minecraft client for safe crash handling");
    }
    
    /**
     * Call this when a transformed class throws an error.
     * Returns true if the error was handled (game should stop), false to propagate.
     */
    public boolean handleTransformError(String className, Throwable error) {
        // Determine which mod caused this
        String modId = identifyMod(className, error);
        
        // Track error count
        modErrorCounts.merge(modId, 1, Integer::sum);
        
        // Log the error
        LOGGER.error("Transformed mod '{}' threw an error in class '{}'", modId, className, error);
        
        // If this is a critical error (not just a minor issue), trigger safe crash
        if (isCriticalError(error)) {
            triggerSafeCrash(modId, className, error);
            return true;
        }
        
        return false;
    }
    
    /**
     * Global uncaught exception handler.
     * Only intercepts exceptions from classes we KNOW are Retromod-transformed
     * (i.e. present in classToModMap). All other exceptions are forwarded to the
     * previous handler so Minecraft's own crash handling works normally.
     *
     * NOTE: This handler ALWAYS logs full exception details to both the logger
     * and stderr, and writes a crash-log.txt file. This prevents the "error
     * code 1 with no text" problem where exceptions are swallowed silently.
     */
    private void handleUncaughtException(Thread thread, Throwable error) {
        // ALWAYS log the full exception first — before anything else.
        // This prevents the "crash error 1 with no text" problem.
        LOGGER.error("Uncaught exception on thread '{}': {}", thread.getName(), error.getMessage());
        LOGGER.error("Full stack trace:", error);
        System.err.println("[Retromod] Uncaught exception on thread \"" + thread.getName() + "\": " + error);
        error.printStackTrace(System.err);

        // Write crash log to file so the user always has something to report
        writeCrashLog(thread, error);

        // Now check if it's from a Retromod-transformed class
        String modId = identifyConfirmedModFromStackTrace(error);

        if (modId != null) {
            LOGGER.error("Crash caused by Retromod-transformed mod: '{}'", modId);
            triggerSafeCrash(modId, null, error);
        } else {
            // Not from a Retromod-transformed class - delegate to previous handler
            if (previousHandler != null) {
                previousHandler.uncaughtException(thread, error);
            } else {
                // No previous handler - use default JVM behavior (already logged above)
            }
        }
    }

    /**
     * Write crash details to config/retromod/crash-log.txt so the user always
     * has something to report even if the console window vanishes (e.g., Windows).
     */
    private void writeCrashLog(Thread thread, Throwable error) {
        try {
            java.nio.file.Path crashLogDir = java.nio.file.Path.of("config/retromod");
            java.nio.file.Files.createDirectories(crashLogDir);
            java.nio.file.Path crashLog = crashLogDir.resolve("crash-log.txt");

            StringBuilder sb = new StringBuilder();
            sb.append("=== Retromod Crash Log ===\n");
            sb.append("Time: ").append(java.time.LocalDateTime.now()).append("\n");
            sb.append("Thread: ").append(thread.getName()).append("\n");
            sb.append("Error: ").append(error.getClass().getName())
              .append(": ").append(error.getMessage()).append("\n");
            sb.append("\nStack trace:\n");
            for (StackTraceElement el : error.getStackTrace()) {
                sb.append("  at ").append(el).append("\n");
            }
            if (error.getCause() != null) {
                sb.append("\nCaused by: ").append(error.getCause()).append("\n");
                for (StackTraceElement el : error.getCause().getStackTrace()) {
                    sb.append("  at ").append(el).append("\n");
                }
            }
            sb.append("\nJava: ").append(System.getProperty("java.version")).append("\n");
            sb.append("OS: ").append(System.getProperty("os.name")).append(" ")
              .append(System.getProperty("os.version")).append("\n");
            sb.append("Retromod: 1.0.0-beta.8\n");
            sb.append("\nPlease report this at: https://github.com/Bownlux/Retromod/issues\n");

            java.nio.file.Files.writeString(crashLog, sb.toString());
            LOGGER.info("Crash details written to {}", crashLog.toAbsolutePath());
        } catch (Exception e) {
            // Can't write crash log - at least we already logged to stderr
            LOGGER.warn("Could not write crash log file: {}", e.getMessage());
        }
    }

    /**
     * Only returns a mod ID if the stack trace contains a class that is
     * CONFIRMED to be in our classToModMap (i.e., actually transformed by Retromod).
     */
    private String identifyConfirmedModFromStackTrace(Throwable error) {
        for (StackTraceElement element : error.getStackTrace()) {
            String className = element.getClassName().replace('.', '/');
            if (classToModMap.containsKey(className)) {
                return classToModMap.get(className);
            }
        }
        return null;
    }
    
    /**
     * Identify which mod caused an error from the class name.
     */
    private String identifyMod(String className, Throwable error) {
        // Check direct mapping first
        if (classToModMap.containsKey(className)) {
            return classToModMap.get(className);
        }
        
        // Try to identify from stack trace
        String fromStack = identifyModFromStackTrace(error);
        if (fromStack != null) {
            return fromStack;
        }
        
        // Try to guess from class name
        // e.g., com/example/mymod/SomeClass -> mymod
        String[] parts = className.replace('.', '/').split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        
        return "unknown";
    }
    
    /**
     * Identify mod from stack trace.
     */
    private String identifyModFromStackTrace(Throwable error) {
        for (StackTraceElement element : error.getStackTrace()) {
            String className = element.getClassName().replace('.', '/');
            
            // Check if this class is from a transformed mod
            if (classToModMap.containsKey(className)) {
                return classToModMap.get(className);
            }
            
            // Skip Minecraft and library classes
            if (className.startsWith("net/minecraft/") ||
                className.startsWith("java/") ||
                className.startsWith("com/mojang/") ||
                className.startsWith("org/lwjgl/") ||
                className.startsWith("com/retromod/")) {
                continue;
            }
            
            // This might be a mod class - try to extract mod name
            String[] parts = className.split("/");
            if (parts.length >= 3) {
                return parts[2];
            }
        }
        
        return null;
    }
    
    /**
     * Check if this is a critical error that requires safe crash.
     */
    private boolean isCriticalError(Throwable error) {
        // Always critical
        if (error instanceof OutOfMemoryError ||
            error instanceof StackOverflowError ||
            error instanceof NoClassDefFoundError ||
            error instanceof NoSuchMethodError ||
            error instanceof NoSuchFieldError ||
            error instanceof IncompatibleClassChangeError) {
            return true;
        }
        
        // Check if it's during a critical game phase
        String message = error.getMessage();
        if (message != null) {
            if (message.contains("tick") ||
                message.contains("render") ||
                message.contains("world") ||
                message.contains("entity") ||
                message.contains("block")) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Trigger the safe crash sequence.
     */
    private void triggerSafeCrash(String modId, String className, Throwable error) {
        // Only handle one crash at a time
        if (!crashHandled.compareAndSet(false, true)) {
            return;
        }
        
        LOGGER.error("=======================================================");
        LOGGER.error("  SAFE CRASH TRIGGERED");
        LOGGER.error("  Mod: {}", modId);
        LOGGER.error("  Class: {}", className != null ? className : "unknown");
        LOGGER.error("  Error: {}", error.getMessage());
        LOGGER.error("=======================================================");
        
        // Step 1: Pause the game
        pauseGame();
        
        // Step 2: Save the world
        boolean worldSaved = saveWorld();
        
        // Step 3: Show the crash dialog
        showCrashDialog(modId, error, worldSaved);
    }
    
    /**
     * Pause the game by stopping the tick loop.
     */
    private void pauseGame() {
        LOGGER.info("Pausing game...");
        
        try {
            // Try to pause via Minecraft client
            if (minecraftClient != null) {
                // Try: minecraft.pause() or minecraft.setScreen(new PauseScreen())
                try {
                    Method pauseMethod = minecraftClient.getClass().getMethod("pause");
                    pauseMethod.invoke(minecraftClient);
                    LOGGER.info("Game paused via client");
                } catch (NoSuchMethodException e) {
                    // Try alternative
                    try {
                        Method setScreen = minecraftClient.getClass().getMethod("setScreen", Object.class);
                        setScreen.invoke(minecraftClient, (Object) null);
                    } catch (Exception e2) {
                        LOGGER.debug("Could not pause via setScreen");
                    }
                }
            }
            
            // Also try to pause the server tick
            if (minecraftServer != null) {
                try {
                    // Try to stop the server tick thread
                    Method halt = minecraftServer.getClass().getMethod("halt", boolean.class);
                    halt.invoke(minecraftServer, false); // false = don't wait
                } catch (Exception e) {
                    LOGGER.debug("Could not halt server");
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not pause game: {}", e.getMessage());
        }
    }
    
    /**
     * Save the world.
     */
    private boolean saveWorld() {
        LOGGER.info("Saving world...");
        
        try {
            if (minecraftServer != null) {
                // Try: server.saveAllChunks(false, true, true)
                // or: server.getPlayerList().saveAll()
                // or: server.saveEverything(false, true, true)
                
                String[] methodsToTry = {
                    "saveAllChunks",
                    "saveEverything", 
                    "save"
                };
                
                for (String methodName : methodsToTry) {
                    try {
                        Method saveMethod = findMethod(minecraftServer.getClass(), methodName);
                        if (saveMethod != null) {
                            // Try to invoke with various parameter counts
                            if (saveMethod.getParameterCount() == 0) {
                                saveMethod.invoke(minecraftServer);
                            } else if (saveMethod.getParameterCount() == 3) {
                                saveMethod.invoke(minecraftServer, false, true, true);
                            }
                            LOGGER.info("World saved via {}", methodName);
                            return true;
                        }
                    } catch (Exception e) {
                        LOGGER.debug("Save method {} failed: {}", methodName, e.getMessage());
                    }
                }
            }
            
            LOGGER.warn("Could not save world automatically");
            return false;
            
        } catch (Exception e) {
            LOGGER.error("Error saving world: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Find a method by name (ignoring parameter types).
     */
    private Method findMethod(Class<?> clazz, String name) {
        for (Method m : clazz.getMethods()) {
            if (m.getName().equals(name)) {
                return m;
            }
        }
        for (Method m : clazz.getDeclaredMethods()) {
            if (m.getName().equals(name)) {
                m.setAccessible(true);
                return m;
            }
        }
        return null;
    }
    
    /**
     * Show the crash dialog with options.
     * On servers, logs to console instead.
     */
    private void showCrashDialog(String modId, Throwable error, boolean worldSaved) {
        // Build the message
        StringBuilder message = new StringBuilder();
        message.append("Retromod encountered an error!\n\n");
        
        message.append("═══════════════════════════════════════\n");
        message.append("THE PROBLEM:\n");
        message.append("═══════════════════════════════════════\n\n");
        
        message.append("The mod \"").append(modId).append("\" does not work\n");
        message.append("correctly with Retromod.\n\n");
        
        message.append("Error: ").append(error.getClass().getSimpleName()).append("\n");
        if (error.getMessage() != null) {
            String msg = error.getMessage();
            if (msg.length() > 100) {
                msg = msg.substring(0, 100) + "...";
            }
            message.append("Details: ").append(msg).append("\n");
        }
        message.append("\n");
        
        // World save status
        if (worldSaved) {
            message.append("✓ Your world has been SAVED automatically.\n\n");
        } else {
            message.append("⚠ Could not save world automatically.\n");
            message.append("  (Your recent progress may be lost)\n\n");
        }
        
        message.append("═══════════════════════════════════════\n");
        message.append("WHAT YOU CAN DO:\n");
        message.append("═══════════════════════════════════════\n\n");
        
        message.append("1. UNINSTALL \"").append(modId).append("\"\n");
        message.append("   Remove this mod and continue using Retromod\n");
        message.append("   with your other mods.\n\n");
        
        message.append("2. FIND AN ALTERNATIVE\n");
        message.append("   Look for a similar mod that works with your\n");
        message.append("   Minecraft version, or has been tested with\n");
        message.append("   Retromod.\n\n");
        
        message.append("3. USE THE ORIGINAL VERSION\n");
        message.append("   Play on the Minecraft version that\n");
        message.append("   \"").append(modId).append("\" was designed for.\n\n");
        
        message.append("═══════════════════════════════════════\n");
        
        // Check if we can show GUI
        if (EnvironmentDetector.canShowGui()) {
            showGuiCrashDialog(message.toString());
        } else {
            showConsoleCrashMessage(modId, message.toString());
        }
    }
    
    /**
     * Show crash dialog in-game (client only).
     * Uses InGameScreenFactory to render as a Minecraft screen instead of a Swing popup.
     */
    private void showGuiCrashDialog(String message) {
        try {
            // Try in-game screen first
            InGameScreenFactory.showCrashScreen("Transformed Mod", message, () -> {
                LOGGER.info("User acknowledged crash - exiting Minecraft");
                System.exit(1);
            });
        } catch (Exception e) {
            // Fallback: just log and exit if in-game screen fails
            LOGGER.error("Could not show in-game crash screen: {}", e.getMessage());
            LOGGER.error("Crash details:\n{}", message);
            System.exit(1);
        }

        // Block this thread briefly while screen is showing
        try {
            Thread.sleep(30000); // 30 seconds max — user should click Save & Quit
        } catch (InterruptedException ignored) {
            System.exit(1);
        }
    }
    
    /**
     * Show console crash message (server mode).
     */
    private void showConsoleCrashMessage(String modId, String message) {
        LOGGER.error("");
        LOGGER.error("╔═══════════════════════════════════════════════════════════╗");
        LOGGER.error("║           RETROMOD - MOD COMPATIBILITY ERROR              ║");
        LOGGER.error("╠═══════════════════════════════════════════════════════════╣");
        
        for (String line : message.split("\n")) {
            LOGGER.error("║  {}", padRight(line, 56) + "║");
        }
        
        LOGGER.error("╠═══════════════════════════════════════════════════════════╣");
        LOGGER.error("║  Server will shut down to prevent data corruption.        ║");
        LOGGER.error("║  Remove the mod \"{}\" and restart.{}", modId, padRight("", 56 - 24 - modId.length()) + "║");
        LOGGER.error("╚═══════════════════════════════════════════════════════════╝");
        LOGGER.error("");
        
        // Give time to flush logs
        try {
            Thread.sleep(1000);
        } catch (InterruptedException ignored) {}
        
        // Shutdown server
        LOGGER.error("Shutting down server...");
        System.exit(1);
    }
    
    /**
     * Pad string to specified length.
     */
    private String padRight(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        return s + " ".repeat(n - s.length());
    }
    
    /**
     * Get error count for a mod.
     */
    public int getErrorCount(String modId) {
        return modErrorCounts.getOrDefault(modId, 0);
    }
    
    /**
     * Check if any crashes have been handled.
     */
    public boolean hasCrashOccurred() {
        return crashHandled.get();
    }
}
