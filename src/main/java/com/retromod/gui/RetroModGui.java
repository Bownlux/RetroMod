/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.gui;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * GUI for RetroMod - allows users to select and transform mods without CLI.
 * 
 * Features:
 * - File picker to select mod JARs (opens Finder on Mac, Explorer on Windows)
 * - Shows transformation progress
 * - "Add More Mods" button for subsequent use
 * - Works on Windows, Mac, and Linux
 * 
 * First launch flow:
 * 1. Shows welcome dialog
 * 2. Opens file picker
 * 3. User selects mod JARs
 * 4. RetroMod transforms them
 * 5. Copies to mods folder
 * 6. Prompts for restart
 */
public class RetroModGui {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-GUI");
    private static final String PREFS_KEY_FIRST_RUN = "retromod_first_run_complete";
    private static final String PREFS_KEY_LAST_DIR = "retromod_last_directory";
    
    private final Path gameDir;
    private final Path modsFolder;
    private final ModCompatibilityChecker checker;
    private final Preferences prefs;
    
    private JFrame mainFrame;
    private JButton addModsButton;
    private boolean transformedAnyMods = false;
    
    public RetroModGui(Path gameDir) {
        this.gameDir = gameDir;
        this.modsFolder = gameDir.resolve("mods");
        this.checker = new ModCompatibilityChecker(gameDir);
        this.prefs = Preferences.userNodeForPackage(RetroModGui.class);
    }
    
    /**
     * Check if this is the first time running RetroMod.
     */
    public boolean isFirstRun() {
        return !prefs.getBoolean(PREFS_KEY_FIRST_RUN, false);
    }
    
    /**
     * Mark first run as complete.
     */
    public void markFirstRunComplete() {
        prefs.putBoolean(PREFS_KEY_FIRST_RUN, true);
    }
    
    /**
     * Show the first-run welcome dialog.
     */
    public void showFirstRunDialog() {
        // Use Swing's thread-safe invocation
        SwingUtilities.invokeLater(() -> {
            try {
                // Set native look and feel
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Fallback to default
            }
            
            int choice = JOptionPane.showOptionDialog(
                null,
                """
                Welcome to RetroMod!
                
                This looks like your first time using RetroMod.
                
                To use old mods with your current Minecraft version,
                you need to select the mod JAR files you want to transform.
                
                Click "Select Mods" to open the file picker and choose
                the mods you want to use.
                
                (The original mods won't be modified - RetroMod creates
                transformed copies in your mods folder.)
                
                ═══════════════════════════════════════════════════
                
                TIP: After selecting mods, you can enable "Full AOT Mode"
                for maximum performance! It takes longer to compile once,
                but makes future launches MUCH faster.
                """,
                "RetroMod - First Time Setup",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.INFORMATION_MESSAGE,
                null,
                new String[]{"Select Mods", "Skip for Now"},
                "Select Mods"
            );
            
            if (choice == 0) {
                // User chose to select mods
                openFilePickerAndTransform();
            }
            
            markFirstRunComplete();
        });
    }
    
    // Track if Full AOT is enabled
    private boolean fullAotEnabled = false;
    
    /**
     * Open the file picker dialog and transform selected mods.
     */
    public void openFilePickerAndTransform() {
        SwingUtilities.invokeLater(() -> {
            JFileChooser fileChooser = new JFileChooser();
            fileChooser.setDialogTitle("Select Mod JARs to Transform");
            fileChooser.setMultiSelectionEnabled(true);
            fileChooser.setFileFilter(new FileNameExtensionFilter("Minecraft Mods (*.jar)", "jar"));
            
            // Add Full AOT checkbox to file chooser
            JCheckBox fullAotCheckbox = new JCheckBox("Enable Full AOT Mode (slower compile, faster gameplay)");
            fullAotCheckbox.setToolTipText("Pre-compiles ALL mod code for maximum performance. Takes longer but game runs faster!");
            fileChooser.setAccessory(createAotPanel(fullAotCheckbox));
            
            // Remember last directory
            String lastDir = prefs.get(PREFS_KEY_LAST_DIR, System.getProperty("user.home"));
            fileChooser.setCurrentDirectory(new File(lastDir));
            
            int result = fileChooser.showOpenDialog(null);
            
            fullAotEnabled = fullAotCheckbox.isSelected();
            
            if (result == JFileChooser.APPROVE_OPTION) {
                File[] selectedFiles = fileChooser.getSelectedFiles();
                
                // Save directory for next time
                if (selectedFiles.length > 0) {
                    prefs.put(PREFS_KEY_LAST_DIR, selectedFiles[0].getParent());
                }
                
                // Transform the selected mods
                transformSelectedMods(selectedFiles);
            }
        });
    }
    
    /**
     * Transform the selected mod files.
     */
    private void transformSelectedMods(File[] modFiles) {
        if (modFiles == null || modFiles.length == 0) {
            return;
        }
        
        // Show progress dialog
        JDialog progressDialog = new JDialog((Frame) null, "RetroMod - Transforming Mods", true);
        JProgressBar progressBar = new JProgressBar(0, modFiles.length);
        JLabel statusLabel = new JLabel("Preparing...");
        JTextArea logArea = new JTextArea(10, 50);
        logArea.setEditable(false);
        
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(progressBar, BorderLayout.CENTER);
        panel.add(new JScrollPane(logArea), BorderLayout.SOUTH);
        
        progressDialog.add(panel);
        progressDialog.pack();
        progressDialog.setLocationRelativeTo(null);
        
        // Run transformation in background thread
        new Thread(() -> {
            List<String> successfulMods = new ArrayList<>();
            List<String> failedMods = new ArrayList<>();
            List<String> skippedMods = new ArrayList<>();
            List<Path> transformedModPaths = new ArrayList<>(); // For Full AOT
            
            try {
                Files.createDirectories(modsFolder);
            } catch (Exception e) {
                LOGGER.error("Could not create mods folder", e);
            }
            
            for (int i = 0; i < modFiles.length; i++) {
                File modFile = modFiles[i];
                final int index = i;
                
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Checking: " + modFile.getName());
                    progressBar.setValue(index);
                });
                
                try {
                    // Step 1: Check Modrinth for native version
                    var modrinthResult = com.retromod.core.ModrinthVersionChecker
                        .checkForNativeVersion(modFile.toPath(), "1.21.1");
                    
                    if (modrinthResult.found()) {
                        // Found native version! Ask user what to do
                        boolean skip = com.retromod.core.ModrinthVersionChecker
                            .offerNativeVersion(modrinthResult, modFile.getName());
                        
                        if (skip) {
                            skippedMods.add(modFile.getName());
                            final String msg = "⏭ " + modFile.getName() + " (skipped - native version available on Modrinth)";
                            SwingUtilities.invokeLater(() -> logArea.append(msg + "\n"));
                            continue;
                        }
                    }
                    
                    SwingUtilities.invokeLater(() -> {
                        statusLabel.setText("Transforming: " + modFile.getName());
                    });
                    
                    // Step 2: Analyze the mod
                    var analysis = checker.analyzeJar(modFile.toPath());
                    
                    String logMessage;
                    Path transformedPath = null;
                    if (analysis != null) {
                        // Needs transformation
                        Path result = checker.transformAndInstall(modFile.toPath());
                        logMessage = "✓ " + modFile.getName() + " → " + result.getFileName();
                        successfulMods.add(modFile.getName());
                        transformedModPaths.add(result); // Track for Full AOT
                        transformedAnyMods = true;
                    } else {
                        // Already compatible, just copy
                        Path dest = modsFolder.resolve(modFile.getName());
                        Files.copy(modFile.toPath(), dest, 
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        logMessage = "✓ " + modFile.getName() + " (already compatible, copied)";
                        successfulMods.add(modFile.getName());
                        transformedModPaths.add(dest); // Track for Full AOT
                    }
                    
                    final String msg = logMessage;
                    SwingUtilities.invokeLater(() -> {
                        logArea.append(msg + "\n");
                    });
                    
                } catch (Exception e) {
                    LOGGER.error("Failed to transform {}: {}", modFile.getName(), e.getMessage());
                    failedMods.add(modFile.getName());
                    
                    SwingUtilities.invokeLater(() -> {
                        logArea.append("✗ " + modFile.getName() + " - FAILED: " + e.getMessage() + "\n");
                    });
                }
            }
            
            // Done - show result
            SwingUtilities.invokeLater(() -> {
                progressBar.setValue(modFiles.length);
                statusLabel.setText("Complete!");
                
                progressDialog.dispose();
                
                // Show completion message
                StringBuilder message = new StringBuilder();
                message.append("Transformation complete!\n\n");
                message.append("Successful: ").append(successfulMods.size()).append(" mod(s)\n");
                if (!skippedMods.isEmpty()) {
                    message.append("Skipped (native available): ").append(skippedMods.size()).append(" mod(s)\n");
                }
                if (!failedMods.isEmpty()) {
                    message.append("Failed: ").append(failedMods.size()).append(" mod(s)\n");
                }
                message.append("\nMods installed to: ").append(modsFolder).append("\n\n");
                
                if (fullAotEnabled && !transformedModPaths.isEmpty()) {
                    message.append("Full AOT Mode is enabled - next step will pre-compile for max performance.\n\n");
                }
                
                if (transformedAnyMods) {
                    message.append("Please RESTART the game for changes to take effect.");
                }
                
                JOptionPane.showMessageDialog(
                    null,
                    message.toString(),
                    "RetroMod - Complete",
                    JOptionPane.INFORMATION_MESSAGE
                );
                
                // Run Full AOT compilation if enabled
                if (fullAotEnabled && !transformedModPaths.isEmpty()) {
                    runFullAotCompilation(transformedModPaths);
                }
            });
            
        }).start();
        
        progressDialog.setVisible(true);
    }
    
    /**
     * Show the "Add More Mods" button overlay.
     * This creates a small floating button the user can click.
     */
    public void showAddModsButton() {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception e) {
                // Ignore
            }
            
            // Create a small undecorated window for the button
            mainFrame = new JFrame();
            mainFrame.setUndecorated(true);
            mainFrame.setAlwaysOnTop(true);
            mainFrame.setType(Window.Type.UTILITY);
            
            addModsButton = new JButton("+ Add Mods");
            addModsButton.setToolTipText("Click to add more mods to transform");
            addModsButton.addActionListener(e -> openFilePickerAndTransform());
            
            // Style the button
            addModsButton.setBackground(new Color(88, 101, 242)); // Discord-ish blue
            addModsButton.setForeground(Color.WHITE);
            addModsButton.setFocusPainted(false);
            addModsButton.setBorderPainted(false);
            addModsButton.setFont(new Font("SansSerif", Font.BOLD, 12));
            addModsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            
            mainFrame.add(addModsButton);
            mainFrame.pack();
            
            // Position in bottom-right corner
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            mainFrame.setLocation(
                screenSize.width - mainFrame.getWidth() - 20,
                screenSize.height - mainFrame.getHeight() - 60
            );
            
            // Make draggable
            final Point[] dragPoint = {null};
            addModsButton.addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) {
                        // Right-click to hide
                        mainFrame.setVisible(false);
                    } else {
                        dragPoint[0] = e.getPoint();
                    }
                }
            });
            addModsButton.addMouseMotionListener(new MouseMotionAdapter() {
                @Override
                public void mouseDragged(MouseEvent e) {
                    if (dragPoint[0] != null) {
                        Point location = mainFrame.getLocation();
                        mainFrame.setLocation(
                            location.x + e.getX() - dragPoint[0].x,
                            location.y + e.getY() - dragPoint[0].y
                        );
                    }
                }
            });
            
            mainFrame.setVisible(true);
        });
    }
    
    /**
     * Hide the add mods button.
     */
    public void hideAddModsButton() {
        if (mainFrame != null) {
            SwingUtilities.invokeLater(() -> mainFrame.dispose());
        }
    }
    
    /**
     * Check if any mods were transformed.
     */
    public boolean didTransformMods() {
        return transformedAnyMods;
    }
    
    /**
     * Create the Full AOT options panel for file chooser.
     */
    private JPanel createAotPanel(JCheckBox checkbox) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder("Performance Options"));
        
        checkbox.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(checkbox);
        
        JLabel infoLabel = new JLabel("<html><small>" +
            "Full AOT Mode pre-compiles ALL mod<br>" +
            "code for maximum performance.<br><br>" +
            "• Takes longer to compile (once)<br>" +
            "• Game runs MUCH faster after<br>" +
            "• Results are cached for future use" +
            "</small></html>");
        infoLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(Box.createVerticalStrut(10));
        panel.add(infoLabel);
        
        return panel;
    }
    
    /**
     * Run Full AOT compilation on transformed mods.
     */
    private void runFullAotCompilation(List<Path> modPaths) {
        if (!fullAotEnabled || modPaths.isEmpty()) {
            return;
        }
        
        try {
            com.retromod.aot.FullAotCompiler compiler = 
                com.retromod.aot.FullAotCompiler.getInstance(gameDir, "1.21.1");
            
            // Show progress dialog (blocks until complete)
            compiler.showProgressDialog(modPaths);
            
        } catch (Exception e) {
            LOGGER.error("Full AOT compilation failed", e);
            JOptionPane.showMessageDialog(
                null,
                "Full AOT compilation failed: " + e.getMessage() + "\n\n" +
                "The mods are still installed and will work,\n" +
                "but without the full performance boost.",
                "AOT Compilation Error",
                JOptionPane.WARNING_MESSAGE
            );
        }
    }
    
    /**
     * Static helper to run the GUI from mod initialization.
     */
    public static void runFirstTimeSetupIfNeeded(Path gameDir) {
        RetroModGui gui = new RetroModGui(gameDir);
        
        if (gui.isFirstRun()) {
            LOGGER.info("First run detected - showing setup dialog");
            gui.showFirstRunDialog();
        }
    }
    
    /**
     * Static helper to show the add mods button.
     */
    public static void showFloatingButton(Path gameDir) {
        RetroModGui gui = new RetroModGui(gameDir);
        gui.showAddModsButton();
    }
}
