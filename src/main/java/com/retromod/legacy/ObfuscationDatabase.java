/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * OBFUSCATION DATABASE
 * 
 * Minecraft uses different obfuscation mappings across versions:
 * - MCP mappings (1.8-1.12)
 * - MCPConfig mappings (1.13+)
 * - Yarn mappings (Fabric)
 * - Mojang official mappings (1.14.4+)
 * - Intermediary mappings (Fabric)
 * - SRG mappings (Forge)
 * 
 * This database handles translation between all these mapping systems.
 */
package com.retromod.legacy;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * Database of obfuscation mappings across Minecraft versions.
 */
public class ObfuscationDatabase {
    
    /**
     * Mapping types we support.
     */
    public enum MappingType {
        OBFUSCATED,     // Raw obfuscated names (a, b, c, etc.)
        SRG,            // Searge names (func_12345_a)
        MCP,            // MCP human-readable names
        INTERMEDIARY,   // Fabric intermediary (method_12345)
        YARN,           // Fabric Yarn human-readable
        MOJANG          // Mojang official mappings
    }
    
    // Class mappings: source -> target
    private final Map<String, Map<String, String>> classMappings = new HashMap<>();
    
    // Method mappings: owner.name.desc -> target name
    private final Map<String, Map<String, String>> methodMappings = new HashMap<>();
    
    // Field mappings: owner.name -> target name
    private final Map<String, Map<String, String>> fieldMappings = new HashMap<>();
    
    // Version-specific mapping files loaded
    private final Set<String> loadedVersions = new HashSet<>();
    
    public ObfuscationDatabase() {
        // Initialize with built-in mappings
        initializeBuiltInMappings();
    }
    
    /**
     * Initialize built-in mappings for common classes that changed names.
     */
    private void initializeBuiltInMappings() {
        // These are the most commonly-needed mappings embedded directly
        
        // ─────────────────────────────────────────────────────────────────────
        // YARN ↔ MOJANG CLASS MAPPINGS
        // ─────────────────────────────────────────────────────────────────────
        
        addClassMapping("yarn", "mojang", 
            "net/minecraft/class_1", "net/minecraft/world/level/Level");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_2", "net/minecraft/world/entity/Entity");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_1657", "net/minecraft/world/entity/player/Player");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_1792", "net/minecraft/world/item/Item");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_1747", "net/minecraft/world/item/ItemStack");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_2248", "net/minecraft/world/level/block/Block");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_2680", "net/minecraft/world/level/block/state/BlockState");
        addClassMapping("yarn", "mojang",
            "net/minecraft/class_2586", "net/minecraft/world/level/block/entity/BlockEntity");
        
        // ─────────────────────────────────────────────────────────────────────
        // COMMON METHOD RENAMES ACROSS VERSIONS
        // ─────────────────────────────────────────────────────────────────────
        
        // Entity.getWorld() → Entity.getEntityWorld() (1.21.9+)
        addMethodMapping("1.21.8", "1.21.9",
            "net/minecraft/entity/Entity", "getWorld", "()Lnet/minecraft/world/World;",
            "getEntityWorld");
        
        // World.getBlockState() variations
        addMethodMapping("1.12", "1.13",
            "net/minecraft/world/World", "getBlockState", "(Lnet/minecraft/util/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
            "getBlockState");
            
        // ItemStack.getCount() vs ItemStack.stackSize
        addMethodMapping("1.10", "1.11",
            "net/minecraft/item/ItemStack", "stackSize", "I",
            "getCount");
            
        // ─────────────────────────────────────────────────────────────────────
        // SRG (FORGE) TO YARN (FABRIC) METHOD MAPPINGS
        // ─────────────────────────────────────────────────────────────────────
        
        // These allow cross-loader compatibility
        addMethodMapping("srg", "yarn",
            "net/minecraft/entity/Entity", "func_70005_c_", "()Ljava/lang/String;",
            "getName");
        addMethodMapping("srg", "yarn",
            "net/minecraft/entity/Entity", "func_70011_f", "(DDD)D",
            "distanceTo");
        addMethodMapping("srg", "yarn",
            "net/minecraft/world/World", "func_180501_a", "(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;I)Z",
            "setBlockState");
    }
    
    /**
     * Add a class mapping between two mapping systems.
     */
    public void addClassMapping(String sourceMapping, String targetMapping,
            String sourceClass, String targetClass) {
        String key = sourceMapping + "->" + targetMapping;
        classMappings.computeIfAbsent(key, k -> new HashMap<>())
                    .put(sourceClass, targetClass);
    }
    
    /**
     * Add a method mapping between versions.
     */
    public void addMethodMapping(String sourceVersion, String targetVersion,
            String owner, String name, String desc, String newName) {
        String key = sourceVersion + "->" + targetVersion;
        String methodKey = owner + "." + name + "." + desc;
        methodMappings.computeIfAbsent(key, k -> new HashMap<>())
                     .put(methodKey, newName);
    }
    
    /**
     * Add a field mapping between versions.
     */
    public void addFieldMapping(String sourceVersion, String targetVersion,
            String owner, String name, String newName) {
        String key = sourceVersion + "->" + targetVersion;
        String fieldKey = owner + "." + name;
        fieldMappings.computeIfAbsent(key, k -> new HashMap<>())
                    .put(fieldKey, newName);
    }
    
    /**
     * Map a class name from source to target version/mapping.
     */
    public String mapClass(String sourceVersion, String targetVersion, String className) {
        String key = sourceVersion + "->" + targetVersion;
        Map<String, String> mappings = classMappings.get(key);
        if (mappings != null) {
            String mapped = mappings.get(className);
            if (mapped != null) return mapped;
        }
        return className;
    }
    
    /**
     * Map a method name from source to target version/mapping.
     */
    public String mapMethod(String sourceVersion, String targetVersion,
            String owner, String name, String desc) {
        String key = sourceVersion + "->" + targetVersion;
        Map<String, String> mappings = methodMappings.get(key);
        if (mappings != null) {
            String methodKey = owner + "." + name + "." + desc;
            String mapped = mappings.get(methodKey);
            if (mapped != null) return mapped;
        }
        return name;
    }
    
    /**
     * Map a field name from source to target version/mapping.
     */
    public String mapField(String sourceVersion, String targetVersion,
            String owner, String name) {
        String key = sourceVersion + "->" + targetVersion;
        Map<String, String> mappings = fieldMappings.get(key);
        if (mappings != null) {
            String fieldKey = owner + "." + name;
            String mapped = mappings.get(fieldKey);
            if (mapped != null) return mapped;
        }
        return name;
    }
    
    /**
     * Load mappings from a file.
     * Supports TSRG, TSRG2, TINY, and PROGUARD formats.
     */
    public void loadMappings(InputStream mappingStream, String format, 
            String sourceMapping, String targetMapping) throws IOException {
        
        switch (format.toLowerCase()) {
            case "tsrg", "tsrg2" -> loadTsrgMappings(mappingStream, sourceMapping, targetMapping);
            case "tiny", "tiny2" -> loadTinyMappings(mappingStream, sourceMapping, targetMapping);
            case "proguard" -> loadProguardMappings(mappingStream, sourceMapping, targetMapping);
            default -> throw new IllegalArgumentException("Unknown mapping format: " + format);
        }
    }
    
    private void loadTsrgMappings(InputStream stream, String source, String target) 
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String currentClass = null;
        String line;
        
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            if (!line.startsWith("\t") && !line.startsWith(" ")) {
                // Class mapping
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    currentClass = parts[0];
                    addClassMapping(source, target, parts[0], parts[1]);
                }
            } else if (currentClass != null) {
                // Member mapping
                line = line.trim();
                String[] parts = line.split(" ");
                
                if (parts.length >= 2) {
                    if (parts[0].contains("(")) {
                        // Method: name desc newName
                        int descStart = parts[0].indexOf('(');
                        String name = parts[0].substring(0, descStart);
                        String desc = parts[0].substring(descStart);
                        addMethodMapping(source, target, currentClass, name, desc, parts[1]);
                    } else {
                        // Field: name newName
                        addFieldMapping(source, target, currentClass, parts[0], parts[1]);
                    }
                }
            }
        }
    }
    
    private void loadTinyMappings(InputStream stream, String source, String target) 
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String line;
        String[] namespaces = null;
        int sourceIdx = -1;
        int targetIdx = -1;
        String currentClass = null;
        
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            String[] parts = line.split("\t");
            
            if (parts[0].equals("tiny") || parts[0].equals("v1")) {
                // Header line
                namespaces = Arrays.copyOfRange(parts, 3, parts.length);
                for (int i = 0; i < namespaces.length; i++) {
                    if (namespaces[i].equals(source)) sourceIdx = i;
                    if (namespaces[i].equals(target)) targetIdx = i;
                }
            } else if (parts[0].equals("c") || parts[0].equals("CLASS")) {
                // Class mapping
                if (sourceIdx >= 0 && targetIdx >= 0 && parts.length > targetIdx + 1) {
                    currentClass = parts[sourceIdx + 1];
                    addClassMapping(source, target, parts[sourceIdx + 1], parts[targetIdx + 1]);
                }
            } else if ((parts[0].equals("m") || parts[0].equals("METHOD")) && currentClass != null) {
                // Method mapping
                if (parts.length > targetIdx + 2) {
                    String desc = parts[1];
                    addMethodMapping(source, target, currentClass, 
                        parts[sourceIdx + 2], desc, parts[targetIdx + 2]);
                }
            } else if ((parts[0].equals("f") || parts[0].equals("FIELD")) && currentClass != null) {
                // Field mapping
                if (parts.length > targetIdx + 2) {
                    addFieldMapping(source, target, currentClass,
                        parts[sourceIdx + 2], parts[targetIdx + 2]);
                }
            }
        }
    }
    
    private void loadProguardMappings(InputStream stream, String source, String target) 
            throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        String currentClass = null;
        String line;
        
        Pattern classPattern = Pattern.compile("^(\\S+) -> (\\S+):$");
        Pattern memberPattern = Pattern.compile("^\\s+(?:\\d+:\\d+:)?(\\S+) (\\S+)\\((.*?)\\) -> (\\S+)$");
        Pattern fieldPattern = Pattern.compile("^\\s+(\\S+) (\\S+) -> (\\S+)$");
        
        while ((line = reader.readLine()) != null) {
            if (line.isEmpty() || line.startsWith("#")) continue;
            
            Matcher classMatcher = classPattern.matcher(line);
            if (classMatcher.matches()) {
                currentClass = classMatcher.group(1).replace('.', '/');
                String obfClass = classMatcher.group(2).replace('.', '/');
                // ProGuard maps FROM readable TO obfuscated, but we want reverse
                addClassMapping(source, target, obfClass, currentClass);
                continue;
            }
            
            if (currentClass == null) continue;
            
            Matcher methodMatcher = memberPattern.matcher(line);
            if (methodMatcher.matches()) {
                String returnType = methodMatcher.group(1).replace('.', '/');
                String methodName = methodMatcher.group(2);
                String params = methodMatcher.group(3);
                String obfName = methodMatcher.group(4);
                
                // Build descriptor
                String desc = buildDescriptor(params, returnType);
                addMethodMapping(source, target, currentClass, obfName, desc, methodName);
                continue;
            }
            
            Matcher fieldMatcher = fieldPattern.matcher(line);
            if (fieldMatcher.matches()) {
                String fieldName = fieldMatcher.group(2);
                String obfName = fieldMatcher.group(3);
                addFieldMapping(source, target, currentClass, obfName, fieldName);
            }
        }
    }
    
    private String buildDescriptor(String params, String returnType) {
        StringBuilder desc = new StringBuilder("(");
        if (!params.isEmpty()) {
            for (String param : params.split(",")) {
                desc.append(typeToDescriptor(param.trim()));
            }
        }
        desc.append(")");
        desc.append(typeToDescriptor(returnType));
        return desc.toString();
    }
    
    private String typeToDescriptor(String type) {
        return switch (type) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> {
                if (type.endsWith("[]")) {
                    yield "[" + typeToDescriptor(type.substring(0, type.length() - 2));
                }
                yield "L" + type.replace('.', '/') + ";";
            }
        };
    }
}
