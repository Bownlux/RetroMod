/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.core;

import com.retromod.embedder.ModVersionInfo;
import com.google.gson.*;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.jar.*;
import java.util.zip.*;

/**
 * Detects what Minecraft version a mod was built for by analyzing its metadata.
 */
public class ModVersionDetector {
    
    private static final Gson GSON = new GsonBuilder().create();
    
    /**
     * Analyze a mod JAR and extract version information.
     */
    public ModVersionInfo detectVersion(Path modJarPath) throws IOException {
        try (JarFile jar = new JarFile(modJarPath.toFile())) {
            
            // Try Fabric first (fabric.mod.json)
            JarEntry fabricEntry = jar.getJarEntry("fabric.mod.json");
            if (fabricEntry != null) {
                return parseFabricMod(jar, fabricEntry);
            }
            
            // Try Forge (mods.toml or mcmod.info)
            JarEntry forgeEntry = jar.getJarEntry("META-INF/mods.toml");
            if (forgeEntry != null) {
                return parseForgeMod(jar, forgeEntry);
            }
            
            // Try legacy Forge (mcmod.info)
            JarEntry legacyForgeEntry = jar.getJarEntry("mcmod.info");
            if (legacyForgeEntry != null) {
                return parseLegacyForgeMod(jar, legacyForgeEntry);
            }
            
            // Try NeoForge (neoforge.mods.toml)
            JarEntry neoEntry = jar.getJarEntry("META-INF/neoforge.mods.toml");
            if (neoEntry != null) {
                return parseNeoForgeMod(jar, neoEntry);
            }
            
            return null;
        }
    }
    
    /**
     * Parse Fabric mod metadata.
     */
    private ModVersionInfo parseFabricMod(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry);
             Reader reader = new InputStreamReader(is)) {
            
            JsonObject json = GSON.fromJson(reader, JsonObject.class);
            
            String modId = json.has("id") ? json.get("id").getAsString() : "unknown";
            String modVersion = json.has("version") ? json.get("version").getAsString() : "unknown";
            
            // Get target MC version from depends
            String targetMcVersion = extractFabricMcVersion(json);
            
            // Get Fabric API version if present
            String fabricApiVersion = extractFabricApiVersion(json);
            
            // Find mod's packages by scanning classes
            Set<String> packages = scanModPackages(jar, modId);
            
            // Check for uses of removed APIs
            Set<String> apiDeps = scanApiDependencies(jar);
            boolean usesRemoved = checkForRemovedApis(apiDeps);
            
            return new ModVersionInfo(
                modId,
                modVersion,
                targetMcVersion,
                "fabric",
                fabricApiVersion,
                packages,
                apiDeps,
                usesRemoved
            );
        }
    }
    
    /**
     * Extract MC version from Fabric depends.
     */
    private String extractFabricMcVersion(JsonObject json) {
        if (!json.has("depends")) return null;
        
        JsonObject depends = json.getAsJsonObject("depends");
        
        // Check for minecraft dependency
        if (depends.has("minecraft")) {
            JsonElement mc = depends.get("minecraft");
            if (mc.isJsonPrimitive()) {
                return parseFabricVersionRange(mc.getAsString());
            } else if (mc.isJsonArray()) {
                // Take first version in array
                JsonArray arr = mc.getAsJsonArray();
                if (!arr.isEmpty()) {
                    return parseFabricVersionRange(arr.get(0).getAsString());
                }
            }
        }
        
        return null;
    }
    
    /**
     * Parse Fabric version range to extract target version.
     * Examples: ">=1.21", "~1.21.1", "1.21.x", ">=1.21 <1.22"
     */
    private String parseFabricVersionRange(String range) {
        // Remove operators and extract version number
        String version = range
            .replaceAll("[>=<~^]", "")
            .replaceAll("x", "0")
            .trim();
        
        // If it's a range, take the lower bound
        if (version.contains(" ")) {
            version = version.split(" ")[0];
        }
        
        return version;
    }
    
    private String extractFabricApiVersion(JsonObject json) {
        if (!json.has("depends")) return null;
        
        JsonObject depends = json.getAsJsonObject("depends");
        
        if (depends.has("fabricloader")) {
            JsonElement loader = depends.get("fabricloader");
            if (loader.isJsonPrimitive()) {
                return parseFabricVersionRange(loader.getAsString());
            }
        }
        
        // Also check for fabric-api
        if (depends.has("fabric-api")) {
            JsonElement api = depends.get("fabric-api");
            if (api.isJsonPrimitive()) {
                return parseFabricVersionRange(api.getAsString());
            }
        }
        
        return null;
    }
    
    /**
     * Parse Forge mods.toml format.
     */
    private ModVersionInfo parseForgeMod(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry);
             BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
            
            Map<String, String> toml = parseSimpleToml(reader);
            
            String modId = toml.getOrDefault("modId", "unknown");
            String modVersion = toml.getOrDefault("version", "unknown");
            String mcVersion = toml.get("minecraft");  // From dependencies
            String forgeVersion = toml.get("forge");
            
            Set<String> packages = scanModPackages(jar, modId);
            Set<String> apiDeps = scanApiDependencies(jar);
            
            return new ModVersionInfo(
                modId,
                modVersion,
                mcVersion,
                "forge",
                forgeVersion,
                packages,
                apiDeps,
                checkForRemovedApis(apiDeps)
            );
        }
    }
    
    /**
     * Parse legacy mcmod.info JSON format.
     */
    private ModVersionInfo parseLegacyForgeMod(JarFile jar, JarEntry entry) throws IOException {
        try (InputStream is = jar.getInputStream(entry);
             Reader reader = new InputStreamReader(is)) {
            
            JsonArray array = GSON.fromJson(reader, JsonArray.class);
            if (array.isEmpty()) return null;
            
            JsonObject first = array.get(0).getAsJsonObject();
            
            String modId = first.has("modid") ? first.get("modid").getAsString() : "unknown";
            String modVersion = first.has("version") ? first.get("version").getAsString() : "unknown";
            String mcVersion = first.has("mcversion") ? first.get("mcversion").getAsString() : null;
            
            Set<String> packages = scanModPackages(jar, modId);
            Set<String> apiDeps = scanApiDependencies(jar);
            
            return new ModVersionInfo(
                modId,
                modVersion,
                mcVersion,
                "forge",
                null,
                packages,
                apiDeps,
                checkForRemovedApis(apiDeps)
            );
        }
    }
    
    /**
     * Parse NeoForge mod metadata.
     */
    private ModVersionInfo parseNeoForgeMod(JarFile jar, JarEntry entry) throws IOException {
        // NeoForge uses similar TOML format to Forge
        return parseForgeMod(jar, entry);  // Reuse Forge parser
    }
    
    /**
     * Simple TOML parser for mod metadata.
     */
    private Map<String, String> parseSimpleToml(BufferedReader reader) throws IOException {
        Map<String, String> result = new HashMap<>();
        String line;
        String currentSection = "";
        boolean inMultiLineString = false;
        StringBuilder multiLineValue = new StringBuilder();
        String multiLineKey = "";

        while ((line = reader.readLine()) != null) {
            // Handle multi-line strings (triple-quoted '''...''')
            if (inMultiLineString) {
                if (line.contains("'''")) {
                    multiLineValue.append(line, 0, line.indexOf("'''"));
                    result.put(multiLineKey, multiLineValue.toString().trim());
                    if (!currentSection.isEmpty()) {
                        result.put(currentSection + "." + multiLineKey, multiLineValue.toString().trim());
                    }
                    inMultiLineString = false;
                } else {
                    multiLineValue.append(line).append("\n");
                }
                continue;
            }

            line = line.trim();

            // Skip comments and empty lines
            if (line.isEmpty() || line.startsWith("#")) continue;

            // Array-of-tables header [[section]] - extract section name properly
            if (line.startsWith("[[") && line.endsWith("]]")) {
                currentSection = line.substring(2, line.length() - 2).trim();
                continue;
            }

            // Table header [section]
            if (line.startsWith("[") && line.endsWith("]")) {
                currentSection = line.substring(1, line.length() - 1).trim();
                continue;
            }

            // Key-value pair
            if (line.contains("=")) {
                String[] parts = line.split("=", 2);
                String key = parts[0].trim();
                String value = parts[1].trim();

                // Check for multi-line string start
                if (value.equals("'''") || value.startsWith("'''")) {
                    inMultiLineString = true;
                    multiLineKey = key;
                    multiLineValue = new StringBuilder();
                    // Content after opening ''' on same line
                    String after = value.substring(3);
                    if (after.contains("'''")) {
                        // Single-line triple-quoted: '''content'''
                        result.put(key, after.substring(0, after.indexOf("'''")).trim());
                        if (!currentSection.isEmpty()) {
                            result.put(currentSection + "." + key, after.substring(0, after.indexOf("'''")).trim());
                        }
                        inMultiLineString = false;
                    } else {
                        multiLineValue.append(after).append("\n");
                    }
                    continue;
                }

                // Strip quotes and brackets from value
                value = value
                    .replaceAll("^\"|\"$", "")
                    .replaceAll("^'|'$", "")
                    .replaceAll("^\\[|\\]$", ""); // Strip version range brackets like [1.21,)

                result.put(key, value);

                // Also store with section prefix
                if (!currentSection.isEmpty()) {
                    result.put(currentSection + "." + key, value);
                }
            }
        }

        return result;
    }
    
    /**
     * Scan a mod JAR to find all packages containing mod classes.
     */
    private Set<String> scanModPackages(JarFile jar, String modId) {
        Set<String> packages = new HashSet<>();
        
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            String name = entry.getName();
            
            if (name.endsWith(".class") && !name.startsWith("META-INF")) {
                // Extract package from class path
                int lastSlash = name.lastIndexOf('/');
                if (lastSlash > 0) {
                    packages.add(name.substring(0, lastSlash + 1));
                }
            }
        }
        
        // Filter to likely mod packages (exclude common libraries)
        packages.removeIf(pkg -> 
            pkg.startsWith("org/apache/") ||
            pkg.startsWith("com/google/") ||
            pkg.startsWith("org/slf4j/") ||
            pkg.startsWith("kotlin/") ||
            pkg.startsWith("org/objectweb/")
        );
        
        return packages;
    }
    
    /**
     * Scan for API dependencies in mod bytecode.
     */
    private Set<String> scanApiDependencies(JarFile jar) {
        Set<String> apis = new HashSet<>();
        
        Enumeration<JarEntry> entries = jar.entries();
        while (entries.hasMoreElements()) {
            JarEntry entry = entries.nextElement();
            
            if (entry.getName().endsWith(".class")) {
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] bytes = is.readAllBytes();
                    // Quick scan for API package references in constant pool
                    String content = new String(bytes, "ISO-8859-1");
                    
                    if (content.contains("net/fabricmc/")) {
                        apis.add("fabric-api");
                    }
                    if (content.contains("net/minecraftforge/")) {
                        apis.add("forge");
                    }
                    if (content.contains("net/neoforged/")) {
                        apis.add("neoforge");
                    }
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }
        }
        
        return apis;
    }
    
    /**
     * Check if any API dependencies use removed functionality.
     */
    private boolean checkForRemovedApis(Set<String> apiDeps) {
        // This would be populated from a database of removed APIs
        // For now, return false - real implementation would check
        // against RemovedApiRegistry
        return false;
    }
}
