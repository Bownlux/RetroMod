/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.mapping;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps Fabric intermediary names (class_XXXX, field_XXXX, method_XXXX) to
 * Mojang official names.
 *
 * MC 26.1 removed all code obfuscation, so the runtime uses Mojang's official
 * names directly. Old Fabric mods reference intermediary names in:
 * - Mixin target annotations (@Mixin(targets = "net.minecraft.class_310"))
 * - Mixin refmaps (field_25318 → resources)
 * - Access widener files
 *
 * This mapper loads the composed intermediary→Mojang mapping from a bundled
 * TSV resource file (generated from Fabric intermediary + Mojang ProGuard
 * mappings for 1.21.4).
 *
 * The mapping is version-agnostic for intermediary names — intermediary names
 * are stable across MC versions (that's their whole purpose), so a single
 * mapping from any recent version covers all old mods.
 */
public class IntermediaryToMojangMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Mapping");
    private static final String MAPPING_RESOURCE = "/intermediary-to-mojang.tsv";
    private static final String CLASS_MOVES_RESOURCE = "/mojang-class-moves-26.1.tsv";

    private static volatile IntermediaryToMojangMapper instance;

    private final Map<String, String> classMap = new ConcurrentHashMap<>(9000);
    private final Map<String, String> fieldMap = new ConcurrentHashMap<>(40000);
    private final Map<String, String> methodMap = new ConcurrentHashMap<>(40000);
    /** Old Mojang name → New Mojang name for classes moved in 26.1 */
    private final Map<String, String> classMoves = new ConcurrentHashMap<>(600);

    private IntermediaryToMojangMapper() {
        loadMappings();
        loadClassMoves();
    }

    public static IntermediaryToMojangMapper getInstance() {
        if (instance == null) {
            synchronized (IntermediaryToMojangMapper.class) {
                if (instance == null) {
                    instance = new IntermediaryToMojangMapper();
                }
            }
        }
        return instance;
    }

    private void loadMappings() {
        long start = System.currentTimeMillis();

        try (InputStream is = getClass().getResourceAsStream(MAPPING_RESOURCE)) {
            if (is == null) {
                LOGGER.warn("Intermediary→Mojang mapping file not found: {}", MAPPING_RESOURCE);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.isEmpty()) continue;

                    String[] parts = line.split("\t");
                    if (parts.length < 3) continue;

                    switch (parts[0]) {
                        case "CLASS" -> classMap.put(parts[1], parts[2]);
                        case "FIELD" -> fieldMap.put(parts[1], parts[2]);
                        case "METHOD" -> methodMap.put(parts[1], parts[2]);
                    }
                }
            }

            long elapsed = System.currentTimeMillis() - start;
            LOGGER.info("Loaded intermediary→Mojang mappings: {} classes, {} fields, {} methods ({}ms)",
                classMap.size(), fieldMap.size(), methodMap.size(), elapsed);

        } catch (IOException e) {
            LOGGER.error("Failed to load intermediary→Mojang mappings: {}", e.getMessage());
        }
    }

    private void loadClassMoves() {
        try (InputStream is = getClass().getResourceAsStream(CLASS_MOVES_RESOURCE)) {
            if (is == null) {
                LOGGER.debug("No 26.1 class moves file found: {}", CLASS_MOVES_RESOURCE);
                return;
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.startsWith("#") || line.isEmpty()) continue;
                    String[] parts = line.split("\t");
                    if (parts.length >= 2) {
                        classMoves.put(parts[0], parts[1]);
                    }
                }
            }

            LOGGER.info("Loaded {} class move redirects for 26.1 package reorganization", classMoves.size());
        } catch (IOException e) {
            LOGGER.warn("Failed to load 26.1 class moves: {}", e.getMessage());
        }
    }

    /**
     * Get old Mojang → new Mojang class redirects for 26.1 package moves.
     * These handle classes that were moved to different packages in MC 26.1.
     */
    public Map<String, String> getClassMoves() {
        return Collections.unmodifiableMap(classMoves);
    }

    /**
     * Map an intermediary class name to Mojang official name.
     * Returns the original name if no mapping exists.
     *
     * @param intermediaryClass e.g. "net/minecraft/class_310"
     * @return Mojang name e.g. "net/minecraft/client/Minecraft"
     */
    public String mapClass(String intermediaryClass) {
        return classMap.getOrDefault(intermediaryClass, intermediaryClass);
    }

    /**
     * Map an intermediary field name to Mojang official name.
     *
     * @param intermediaryField e.g. "field_25318"
     * @return Mojang name e.g. "resources"
     */
    public String mapField(String intermediaryField) {
        return fieldMap.getOrDefault(intermediaryField, intermediaryField);
    }

    /**
     * Map an intermediary method name to Mojang official name.
     *
     * @param intermediaryMethod e.g. "method_18858"
     * @return Mojang name e.g. "createTitle"
     */
    public String mapMethod(String intermediaryMethod) {
        return methodMap.getOrDefault(intermediaryMethod, intermediaryMethod);
    }

    // Regex patterns for finding intermediary names efficiently
    // Instead of iterating 86K entries, find class_XXXX/field_XXXX/method_XXXX tokens and look up directly
    private static final Pattern CLASS_PATTERN = Pattern.compile("class_\\d+");
    private static final Pattern FIELD_PATTERN = Pattern.compile("field_\\d+");
    private static final Pattern METHOD_PATTERN = Pattern.compile("method_\\d+");
    // Full qualified class pattern: matches "net/minecraft/class_XXXX" style paths
    private static final Pattern FQ_CLASS_PATTERN = Pattern.compile("[a-z]+(?:/[a-z]+)*/class_\\d+");

    /**
     * Remap all intermediary references in a string.
     * Uses regex to find class_XXXX/field_XXXX/method_XXXX tokens and looks them up
     * in the mapping tables directly — O(n) in string length instead of O(86K) per call.
     */
    public String remapString(String input) {
        if (input == null) return null;

        // First pass: remap fully-qualified class names (net/minecraft/class_XXXX)
        // These must go first so we don't partial-match "class_XXXX" within a path
        String result = replaceByPattern(input, FQ_CLASS_PATTERN, classMap);

        // Second pass: remap standalone class_XXXX references (in dot notation etc.)
        // Only if there are still unresolved class_ references
        if (result.contains("class_")) {
            result = replaceByPattern(result, CLASS_PATTERN, classMap);
        }

        // Third pass: remap field_XXXX
        if (result.contains("field_")) {
            result = replaceByPattern(result, FIELD_PATTERN, fieldMap);
        }

        // Fourth pass: remap method_XXXX
        if (result.contains("method_")) {
            result = replaceByPattern(result, METHOD_PATTERN, methodMap);
        }

        return result;
    }

    /**
     * Remap a descriptor string, replacing all intermediary class references.
     * E.g. "Lnet/minecraft/class_310;" → "Lnet/minecraft/client/Minecraft;"
     */
    public String remapDescriptor(String descriptor) {
        if (descriptor == null) return null;

        if (!descriptor.contains("class_")) return descriptor;

        // Remap fully-qualified class names first
        String result = replaceByPattern(descriptor, FQ_CLASS_PATTERN, classMap);

        // Then standalone class_XXXX
        if (result.contains("class_")) {
            result = replaceByPattern(result, CLASS_PATTERN, classMap);
        }

        return result;
    }

    /**
     * Replace all pattern matches using the given lookup map.
     */
    private String replaceByPattern(String input, Pattern pattern, Map<String, String> lookup) {
        Matcher matcher = pattern.matcher(input);
        StringBuilder sb = new StringBuilder(input.length());
        while (matcher.find()) {
            String match = matcher.group();
            String replacement = lookup.get(match);
            if (replacement != null) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Get the full class mapping table (intermediary → Mojang).
     */
    public Map<String, String> getClassMap() {
        return Collections.unmodifiableMap(classMap);
    }

    /**
     * Register every loaded intermediary→Mojang mapping (classes, methods,
     * fields) plus the 26.1 class moves into the given transformer's redirect
     * tables. Idempotent — safe to call multiple times (subsequent calls
     * overwrite any matching entries with the same value).
     *
     * <p>This is the single entry point that <b>any</b> startup path should
     * invoke to prepare a {@code RetroModTransformer} for Fabric intermediary
     * remapping. Previously this wiring was duplicated inline in
     * {@code RetroModPreLaunch}, which meant the CLI's {@code gaps} and
     * {@code batch} commands couldn't benefit from it — AppleSkin and other
     * Fabric mods had all their intermediary class names show up as
     * "missing" in the gap report. Centralizing here fixes that for every
     * caller.</p>
     *
     * <p>Composition: when an intermediary name {@code class_X} maps to a
     * Mojang name that was subsequently <i>moved</i> in 26.1
     * ({@code OldMojangName → NewMojangName}), we compose both hops so the
     * transformer registers {@code class_X → NewMojangName} directly. This
     * matters because ASM's {@code ClassRemapper} is single-pass — if we
     * registered the intermediate stop, it would never fire the second hop.</p>
     *
     * @param transformer the transformer to populate; must not be null
     * @return number of class redirects registered (for diagnostic logging)
     */
    public static int applyTo(com.retromod.core.RetroModTransformer transformer) {
        if (transformer == null) {
            throw new IllegalArgumentException("transformer must not be null");
        }
        IntermediaryToMojangMapper mapper = getInstance();
        if (!mapper.isLoaded()) {
            LOGGER.debug("IntermediaryToMojangMapper.applyTo called but mappings not loaded");
            return 0;
        }

        Map<String, String> classMoveMap = mapper.getClassMoves();

        int classRedirects = 0;
        int composed = 0;
        for (Map.Entry<String, String> entry : mapper.getClassMap().entrySet()) {
            String intermediary = entry.getKey();
            String mojang = entry.getValue();
            // Compose with class-moves so the single-pass ClassRemapper lands
            // on the *final* 26.1 name, not an intermediate stop.
            String finalName = classMoveMap.getOrDefault(mojang, mojang);
            if (!finalName.equals(mojang)) composed++;
            transformer.registerClassRedirect(intermediary, finalName);
            classRedirects++;
        }
        transformer.registerIntermediaryNameMappings(
                mapper.getMethodMap(), mapper.getFieldMap());

        // Also register class-moves on their own (for mods that already use
        // Mojang names — e.g. mods targeting 1.20+ with GuiGraphics — the
        // intermediary step is skipped but the 26.1 move still applies).
        int classMoves = 0;
        for (Map.Entry<String, String> entry : classMoveMap.entrySet()) {
            transformer.registerClassRedirect(entry.getKey(), entry.getValue());
            classMoves++;
        }

        LOGGER.info("IntermediaryToMojangMapper.applyTo: {} intermediary→Mojang class redirects ({} composed), {} methods, {} fields, {} additional class-moves",
                classRedirects, composed, mapper.getMethodMap().size(),
                mapper.getFieldMap().size(), classMoves);
        return classRedirects;
    }

    /**
     * Get the full field mapping table (intermediary → Mojang).
     */
    public Map<String, String> getFieldMap() {
        return Collections.unmodifiableMap(fieldMap);
    }

    /**
     * Get the full method mapping table (intermediary → Mojang).
     */
    public Map<String, String> getMethodMap() {
        return Collections.unmodifiableMap(methodMap);
    }

    /**
     * Check if mappings are loaded.
     */
    public boolean isLoaded() {
        return !classMap.isEmpty();
    }
}
