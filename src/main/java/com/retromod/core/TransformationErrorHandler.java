/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Handles transformation errors and directs users to report bugs on GitHub.
 *
 * When a mod fails to transform, this class:
 * 1. Logs the detailed error
 * 2. Shows a user-friendly message
 * 3. Provides a link to GitHub Issues for bug reports
 * 4. Generates a bug report template the user can copy
 */
public class TransformationErrorHandler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Error");
    
    // GitHub for bug reports
    public static final String GITHUB_URL = "https://github.com/Bownlux/MC-RetroMod";
    public static final String GITHUB_ISSUES_URL = "https://github.com/Bownlux/MC-RetroMod/issues/new?title=Bug%20Report";
    
    // Track failed mods for summary
    private static final List<FailedMod> failedMods = new ArrayList<>();
    
    public record FailedMod(
        String modName,
        String modId,
        String modLoader,
        String sourceVersion,
        String errorType,
        String errorMessage,
        String stackTrace
    ) {}
    
    /**
     * Handle a transformation error.
     * 
     * @param modPath Path to the mod JAR that failed
     * @param error The exception that occurred
     * @param modId Mod ID if known
     * @param modLoader Mod loader type (fabric, forge, neoforge)
     * @param sourceVersion Source Minecraft version
     */
    public static void handleError(Path modPath, Throwable error, String modId, 
                                   String modLoader, String sourceVersion) {
        
        String modName = modPath.getFileName().toString();
        String errorType = error.getClass().getSimpleName();
        String errorMessage = error.getMessage() != null ? error.getMessage() : "Unknown error";
        String stackTrace = getStackTraceString(error);
        
        // Record the failure
        FailedMod failed = new FailedMod(
            modName, modId, modLoader, sourceVersion, errorType, errorMessage, stackTrace
        );
        failedMods.add(failed);
        
        // Log detailed error
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  TRANSFORMATION FAILED!");
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.error("  Mod: {}", modName);
        LOGGER.error("  Mod ID: {}", modId != null ? modId : "Unknown");
        LOGGER.error("  Loader: {}", modLoader);
        LOGGER.error("  Source Version: {}", sourceVersion);
        LOGGER.error("");
        LOGGER.error("  Error: {} - {}", errorType, errorMessage);
        LOGGER.error("");
        LOGGER.error("  This mod does NOT work with RetroMod.");
        LOGGER.error("");
        LOGGER.error("  PLEASE REPORT THIS BUG:");
        LOGGER.error("  → GitHub: {}", GITHUB_ISSUES_URL);
        LOGGER.error("═══════════════════════════════════════════════════════════");
        LOGGER.debug("Stack trace:", error);
        
        // Show GUI if on client
        if (EnvironmentDetector.canShowGui()) {
            showErrorDialog(failed);
        }
    }
    
    /**
     * Show error dialog with bug report option.
     */
    private static void showErrorDialog(FailedMod failed) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {}
            
            String message = String.format("""
                ❌ Mod Transformation Failed!
                
                ═══════════════════════════════════════
                
                Mod: %s
                Error: %s
                
                ═══════════════════════════════════════
                
                This mod does NOT work with RetroMod.
                
                Please report this bug so we can fix it!
                
                → GitHub: github.com/Bownlux/MC-RetroMod/issues

                Click "Copy Bug Report" to copy a template,
                then paste it when creating a new issue on GitHub.
                
                ═══════════════════════════════════════
                """,
                failed.modName(),
                failed.errorType() + ": " + truncate(failed.errorMessage(), 50)
            );
            
            int choice = JOptionPane.showOptionDialog(
                null,
                message,
                "RetroMod - Transformation Failed",
                JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                new String[]{"Open GitHub Issues", "Copy Bug Report", "OK"},
                "Open GitHub Issues"
            );
            
            if (choice == 0) {
                // Open GitHub Issues
                openGitHub();
            } else if (choice == 1) {
                // Copy bug report to clipboard
                copyBugReport(failed);
            }
        });
    }
    
    /**
     * Open GitHub Issues in browser.
     */
    private static void openGitHub() {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(GITHUB_ISSUES_URL));
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", GITHUB_ISSUES_URL});
            }
        } catch (Exception e) {
            LOGGER.warn("Could not open browser: {}", e.getMessage());
            JOptionPane.showMessageDialog(
                null,
                "Please visit: " + GITHUB_ISSUES_URL,
                "Open GitHub Issues",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
    
    /**
     * Copy bug report template to clipboard.
     */
    private static void copyBugReport(FailedMod failed) {
        String report = generateBugReport(failed);
        
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(report), null);
            
            JOptionPane.showMessageDialog(
                null,
                "Bug report copied to clipboard!\n\nGo to GitHub Issues and paste it in a new issue.",
                "Copied!",
                JOptionPane.INFORMATION_MESSAGE
            );
        } catch (Exception e) {
            // Show the report in a dialog if clipboard fails
            JTextArea textArea = new JTextArea(report);
            textArea.setEditable(false);
            textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            JScrollPane scrollPane = new JScrollPane(textArea);
            scrollPane.setPreferredSize(new Dimension(500, 400));
            
            JOptionPane.showMessageDialog(
                null,
                scrollPane,
                "Copy this bug report:",
                JOptionPane.PLAIN_MESSAGE
            );
        }
    }
    
    /**
     * Generate a bug report template.
     */
    public static String generateBugReport(FailedMod failed) {
        return String.format("""
            # RetroMod Bug Report
            
            ## Mod Information
            - **Mod Name:** %s
            - **Mod ID:** %s
            - **Mod Loader:** %s
            - **Source MC Version:** %s
            - **Target MC Version:** 1.21.1
            
            ## Error
            - **Type:** %s
            - **Message:** %s
            
            ## Stack Trace
            ```
            %s
            ```
            
            ## System Info
            - **RetroMod Version:** 1.0.0
            - **Java Version:** %s
            - **OS:** %s
            
            ## Additional Info
            (Please add any additional information here, such as other mods installed)
            
            ---
            *Generated by RetroMod Error Handler*
            """,
            failed.modName(),
            failed.modId() != null ? failed.modId() : "Unknown",
            failed.modLoader(),
            failed.sourceVersion() != null ? failed.sourceVersion() : "Unknown",
            failed.errorType(),
            failed.errorMessage(),
            truncate(failed.stackTrace(), 2000),
            System.getProperty("java.version"),
            System.getProperty("os.name") + " " + System.getProperty("os.version")
        );
    }
    
    /**
     * Log bug report template to console (for servers).
     */
    public static void logBugReportToConsole(FailedMod failed) {
        String report = generateBugReport(failed);
        
        LOGGER.error("");
        LOGGER.error("╔══════════════════════════════════════════════════════════╗");
        LOGGER.error("║          PLEASE REPORT THIS BUG ON GITHUB!               ║");
        LOGGER.error("╠══════════════════════════════════════════════════════════╣");
        LOGGER.error("║  → github.com/Bownlux/MC-RetroMod/issues                ║");
        LOGGER.error("╠══════════════════════════════════════════════════════════╣");
        LOGGER.error("║  Copy this bug report:                                   ║");
        LOGGER.error("╚══════════════════════════════════════════════════════════╝");
        LOGGER.error("");
        for (String line : report.split("\n")) {
            LOGGER.error("  {}", line);
        }
        LOGGER.error("");
    }
    
    /**
     * Get stack trace as string.
     */
    private static String getStackTraceString(Throwable error) {
        StringBuilder sb = new StringBuilder();
        sb.append(error.toString()).append("\n");
        for (StackTraceElement element : error.getStackTrace()) {
            sb.append("  at ").append(element.toString()).append("\n");
        }
        if (error.getCause() != null) {
            sb.append("Caused by: ").append(error.getCause().toString()).append("\n");
            for (StackTraceElement element : error.getCause().getStackTrace()) {
                sb.append("  at ").append(element.toString()).append("\n");
                if (sb.length() > 3000) break; // Limit size
            }
        }
        return sb.toString();
    }
    
    /**
     * Truncate string to max length.
     */
    private static String truncate(String s, int maxLen) {
        if (s == null) return "null";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen) + "...";
    }
    
    /**
     * Get list of all failed mods.
     */
    public static List<FailedMod> getFailedMods() {
        return new ArrayList<>(failedMods);
    }
    
    /**
     * Check if any mods failed.
     */
    public static boolean hasFailures() {
        return !failedMods.isEmpty();
    }
    
    /**
     * Show summary of all failed mods at end of transformation.
     */
    public static void showFailureSummary() {
        if (failedMods.isEmpty()) return;
        
        LOGGER.error("");
        LOGGER.error("╔══════════════════════════════════════════════════════════╗");
        LOGGER.error("║          {} MOD(S) FAILED TO TRANSFORM                   ║", failedMods.size());
        LOGGER.error("╠══════════════════════════════════════════════════════════╣");
        
        for (FailedMod mod : failedMods) {
            LOGGER.error("║  ✗ {}", mod.modName());
            LOGGER.error("║    Error: {}", mod.errorType());
        }
        
        LOGGER.error("╠══════════════════════════════════════════════════════════╣");
        LOGGER.error("║  Please report these bugs on GitHub so we can fix them!   ║");
        LOGGER.error("║  → github.com/Bownlux/MC-RetroMod/issues                ║");
        LOGGER.error("╚══════════════════════════════════════════════════════════╝");
        LOGGER.error("");
    }
}
