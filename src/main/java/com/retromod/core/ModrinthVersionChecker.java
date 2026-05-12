/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;

/**
 * Checks Modrinth for native versions of mods.
 * 
 * Before transforming a mod, checks if there's already a version
 * that works natively with the target Minecraft version.
 * 
 * If found, offers the user a link to download it instead of transforming.
 */
public class ModrinthVersionChecker {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Modrinth");
    
    private static final String MODRINTH_API = "https://api.modrinth.com/v2";
    private static final String USER_AGENT = "RetroMod/1.0.0 (bownlux)";
    
    // Cache results to avoid repeated API calls
    private static final java.util.Map<String, ModrinthResult> cache = new java.util.concurrent.ConcurrentHashMap<>();
    
    public record ModrinthResult(
        boolean found,
        String projectId,
        String projectSlug,
        String projectName,
        String versionId,
        String versionNumber,
        String downloadUrl,
        String pageUrl
    ) {
        public static ModrinthResult notFound() {
            return new ModrinthResult(false, null, null, null, null, null, null, null);
        }
    }
    
    /**
     * Check if a mod has a native version for the target Minecraft version.
     *
     * <p><strong>Network policy:</strong> this method is gated on the
     * {@code check_for_native_versions} config flag, which defaults to
     * {@code false}. Without the flag set, the method returns
     * {@link ModrinthResult#notFound()} immediately without any HTTP
     * activity. RetroMod doesn't make outbound network calls just because
     * a user opened a settings menu — the user has to flip the flag in
     * {@code config/retromod/config.json} (or via the in-game settings
     * screen) to opt in.
     *
     * @param modJarPath Path to the mod JAR
     * @param targetMcVersion Target Minecraft version (e.g., "1.21.1")
     * @return ModrinthResult with info about native version, or notFound()
     */
    public static ModrinthResult checkForNativeVersion(Path modJarPath, String targetMcVersion) {
        if (!isUserOptedIn()) {
            return ModrinthResult.notFound();
        }
        try {
            // Extract mod info from JAR
            ModInfo info = extractModInfo(modJarPath);
            if (info == null || info.modId == null) {
                return ModrinthResult.notFound();
            }

            // Check cache
            String cacheKey = info.modId + ":" + targetMcVersion;
            if (cache.containsKey(cacheKey)) {
                return cache.get(cacheKey);
            }

            // Search Modrinth by mod ID or name
            ModrinthResult result = searchModrinth(info, targetMcVersion);
            cache.put(cacheKey, result);

            return result;

        } catch (Exception e) {
            LOGGER.debug("Error checking Modrinth: {}", e.getMessage());
            return ModrinthResult.notFound();
        }
    }

    /**
     * Returns {@code true} only if the user has explicitly opted into
     * Modrinth lookups by setting {@code check_for_native_versions=true}
     * in {@code config/retromod/config.json}. JVM system property
     * {@code -Dretromod.checkForNativeVersions=true} also works as a
     * short-circuit (handy for testing and for users who manage their
     * server via flags rather than configs).
     *
     * <p>Returns {@code false} (and produces no network traffic) by
     * default — RetroMod's standing rule is that any outbound network
     * call has to be explicitly enabled by the user.
     */
    private static boolean isUserOptedIn() {
        // System property short-circuit
        if (Boolean.getBoolean("retromod.checkForNativeVersions")) {
            return true;
        }
        // Read the config file directly — keep this cheap so we don't
        // hold any singleton state, and avoid a circular dependency with
        // RetroMod.java (which we don't want this class to drag in).
        try {
            Path cfg = Path.of("config/retromod/config.json");
            if (!java.nio.file.Files.exists(cfg)) return false;
            String json = java.nio.file.Files.readString(cfg);
            var obj = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            return obj.has("check_for_native_versions")
                && obj.get("check_for_native_versions").getAsBoolean();
        } catch (Exception e) {
            // Bad/missing config → treat as opted out (safe default).
            return false;
        }
    }
    
    /**
     * Extract mod info from JAR file.
     */
    private static ModInfo extractModInfo(Path jarPath) {
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            // Try fabric.mod.json
            ZipEntry fabricEntry = jar.getEntry("fabric.mod.json");
            if (fabricEntry != null) {
                String content = new String(jar.getInputStream(fabricEntry).readAllBytes());
                return parseModJson(content, "fabric");
            }
            
            // Try mods.toml (Forge/NeoForge)
            ZipEntry forgeEntry = jar.getEntry("META-INF/mods.toml");
            if (forgeEntry == null) {
                forgeEntry = jar.getEntry("META-INF/neoforge.mods.toml");
            }
            if (forgeEntry != null) {
                String content = new String(jar.getInputStream(forgeEntry).readAllBytes());
                return parseModsToml(content);
            }
            
            // Try mcmod.info (old Forge)
            ZipEntry oldForgeEntry = jar.getEntry("mcmod.info");
            if (oldForgeEntry != null) {
                String content = new String(jar.getInputStream(oldForgeEntry).readAllBytes());
                return parseMcmodInfo(content);
            }
            
        } catch (Exception e) {
            LOGGER.debug("Could not extract mod info from: {}", jarPath.getFileName());
        }
        
        return null;
    }
    
    private static ModInfo parseModJson(String json, String loader) {
        // Simple regex parsing to avoid Gson dependency
        String modId = extractJsonField(json, "id");
        String modName = extractJsonField(json, "name");
        return new ModInfo(modId, modName, loader);
    }
    
    private static ModInfo parseModsToml(String toml) {
        // Parse TOML for modId
        Pattern idPattern = Pattern.compile("modId\\s*=\\s*\"([^\"]+)\"");
        Pattern namePattern = Pattern.compile("displayName\\s*=\\s*\"([^\"]+)\"");
        
        Matcher idMatcher = idPattern.matcher(toml);
        Matcher nameMatcher = namePattern.matcher(toml);
        
        String modId = idMatcher.find() ? idMatcher.group(1) : null;
        String modName = nameMatcher.find() ? nameMatcher.group(1) : null;
        
        return new ModInfo(modId, modName, "forge");
    }
    
    private static ModInfo parseMcmodInfo(String json) {
        // Old format is a JSON array
        String modId = extractJsonField(json, "modid");
        String modName = extractJsonField(json, "name");
        return new ModInfo(modId, modName, "forge");
    }
    
    private static String extractJsonField(String json, String field) {
        Pattern pattern = Pattern.compile("\"" + field + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        return matcher.find() ? matcher.group(1) : null;
    }
    
    /**
     * Search Modrinth for a mod.
     */
    private static ModrinthResult searchModrinth(ModInfo info, String targetMcVersion) {
        try {
            // First try to find project by slug (mod ID)
            String projectData = fetchUrl(MODRINTH_API + "/project/" + info.modId);
            
            if (projectData == null && info.modName != null) {
                // Try searching by name
                String searchQuery = URLEncoder.encode(info.modName, StandardCharsets.UTF_8);
                String searchData = fetchUrl(MODRINTH_API + "/search?query=" + searchQuery + "&limit=1");
                
                if (searchData != null) {
                    // Extract first result's slug
                    String slug = extractJsonField(searchData, "slug");
                    if (slug != null) {
                        projectData = fetchUrl(MODRINTH_API + "/project/" + slug);
                    }
                }
            }
            
            if (projectData == null) {
                return ModrinthResult.notFound();
            }
            
            // Extract project info
            String projectId = extractJsonField(projectData, "id");
            String projectSlug = extractJsonField(projectData, "slug");
            String projectName = extractJsonField(projectData, "title");
            
            if (projectId == null) {
                return ModrinthResult.notFound();
            }
            
            // Check for versions that support target MC version
            String versionsUrl = MODRINTH_API + "/project/" + projectId + "/version?game_versions=[\"" + targetMcVersion + "\"]";
            String versionsData = fetchUrl(versionsUrl);
            
            if (versionsData == null || versionsData.equals("[]")) {
                // No versions for this MC version
                return ModrinthResult.notFound();
            }
            
            // Extract first (latest) version info
            String versionId = extractJsonField(versionsData, "id");
            String versionNumber = extractJsonField(versionsData, "version_number");
            
            // Build URLs
            String pageUrl = "https://modrinth.com/mod/" + projectSlug;
            String downloadUrl = "https://modrinth.com/mod/" + projectSlug + "/version/" + versionId;
            
            LOGGER.info("Found native version of {} for {}: {}", projectName, targetMcVersion, versionNumber);
            
            return new ModrinthResult(
                true,
                projectId,
                projectSlug,
                projectName,
                versionId,
                versionNumber,
                downloadUrl,
                pageUrl
            );
            
        } catch (Exception e) {
            LOGGER.debug("Modrinth search failed: {}", e.getMessage());
            return ModrinthResult.notFound();
        }
    }
    
    /**
     * Fetch URL content.
     */
    private static String fetchUrl(String urlString) {
        try {
            URL url = URI.create(urlString).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() != 200) {
                return null;
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream()))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Show dialog offering to download native version.
     * Returns true if user chooses to skip transformation.
     */
    public static boolean offerNativeVersion(ModrinthResult result, String modFileName) {
        if (!result.found() || !EnvironmentDetector.canShowGui()) {
            return false;
        }
        
        String message = String.format("""
            Good news! There's a newer version of this mod
            that works with your Minecraft version natively!
            
            ═══════════════════════════════════════
            
            Mod: %s
            File: %s
            
            Native version available: %s
            
            ═══════════════════════════════════════
            
            You can download the native version from Modrinth
            instead of using RetroMod to transform it.
            
            Native versions usually work better than transformed ones!
            
            What would you like to do?
            """,
            result.projectName(),
            modFileName,
            result.versionNumber()
        );
        
        int choice = JOptionPane.showOptionDialog(
            null,
            message,
            "RetroMod - Native Version Available!",
            JOptionPane.YES_NO_CANCEL_OPTION,
            JOptionPane.INFORMATION_MESSAGE,
            null,
            new String[]{"Open Modrinth", "Transform Anyway", "Skip This Mod"},
            "Open Modrinth"
        );
        
        if (choice == 0) {
            // Open Modrinth page
            openBrowser(result.pageUrl());
            return true; // Skip transformation
        } else if (choice == 2) {
            // Skip
            return true;
        }
        
        // Transform anyway
        return false;
    }
    
    /**
     * Open URL in default browser.
     */
    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI.create(url));
            } else {
                // Try xdg-open on Linux
                Runtime.getRuntime().exec(new String[]{"xdg-open", url});
            }
        } catch (Exception e) {
            LOGGER.warn("Could not open browser: {}", e.getMessage());
            // Show URL in dialog
            JOptionPane.showMessageDialog(
                null,
                "Please open this URL in your browser:\n\n" + url,
                "Open Modrinth",
                JOptionPane.INFORMATION_MESSAGE
            );
        }
    }
    
    /**
     * Log native version info to console (for server mode).
     */
    public static void logNativeVersionAvailable(ModrinthResult result, String modFileName) {
        if (!result.found()) return;
        
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        LOGGER.warn("  NATIVE VERSION AVAILABLE!");
        LOGGER.warn("═══════════════════════════════════════════════════════════");
        LOGGER.warn("  Mod: {}", result.projectName());
        LOGGER.warn("  File: {}", modFileName);
        LOGGER.warn("  Native version: {}", result.versionNumber());
        LOGGER.warn("");
        LOGGER.warn("  Download from: {}", result.pageUrl());
        LOGGER.warn("");
        LOGGER.warn("  Native versions work better than transformed ones!");
        LOGGER.warn("═══════════════════════════════════════════════════════════");
    }
    
    private record ModInfo(String modId, String modName, String loader) {}
}
