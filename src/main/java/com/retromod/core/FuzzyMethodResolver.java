/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Fuzzy method/field resolver — last-resort fallback for unresolved references.
 * Indexes the target MC JAR and scores candidates to find probable matches.
 */
package com.retromod.core;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * Fuzzy matching resolver for unresolved method and field references.
 *
 * <h2>Purpose</h2>
 * When a mod references a method or field that has no hardcoded redirect in
 * RetroModTransformer, this resolver scans the target Minecraft JAR to find
 * probable matches using a weighted scoring algorithm. This covers edge cases
 * where methods were renamed, signatures slightly changed, or fields moved
 * between minor MC versions without explicit shim coverage.
 *
 * <h2>Usage</h2>
 * This is a FALLBACK only. Hardcoded redirects registered by version shims
 * always take priority. The fuzzy resolver only fires when all explicit
 * lookups fail. It will NEVER override a hardcoded redirect.
 *
 * <h2>Scoring algorithm (methods)</h2>
 * Each candidate method in the target class is scored against the unresolved
 * reference on four axes:
 * <ol>
 *   <li><b>Class match (0-40 pts):</b> Exact owner match scores highest.
 *       Superclasses/interfaces of the owner also count.</li>
 *   <li><b>Name similarity (0-30 pts):</b> Exact match, Levenshtein distance,
 *       substring containment, and common prefix/suffix.</li>
 *   <li><b>Parameter count (0-15 pts):</b> Same count scores highest.
 *       Off-by-one (common for parameter injection) still scores.</li>
 *   <li><b>Parameter types (0-15 pts):</b> Exact type match of all parameters
 *       scores highest. Partial matches (>50%) also score.</li>
 * </ol>
 * Maximum possible score: 100 points.
 *
 * <h2>Confidence thresholds</h2>
 * <ul>
 *   <li>70+ = Apply automatically and log at INFO level</li>
 *   <li>50-69 = Log warning but do NOT apply (developer should add explicit redirect)</li>
 *   <li>&lt;50 = Silently ignored</li>
 * </ul>
 *
 * <h2>Thread safety</h2>
 * The resolve cache uses {@link ConcurrentHashMap}. The index maps are populated
 * once during {@link #indexJar(Path)} and are read-only thereafter. The
 * {@code indexed} flag is volatile to ensure visibility across threads.
 *
 * <p><b>IMPORTANT:</b> This class must NOT reference {@code RetroMod} directly
 * (which implements ModInitializer) because it is also used by the standalone
 * CLI where Fabric classes are not on the classpath.</p>
 */
public class FuzzyMethodResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Fuzzy");

    // ═══════════════════════════════════════════════════════════════════════
    // SCORING CONSTANTS — weights for each axis of the matching algorithm
    // ═══════════════════════════════════════════════════════════════════════

    /** Points awarded when the candidate method is on the exact same class. */
    private static final int SCORE_EXACT_CLASS = 40;
    /** Points awarded when the candidate method is on a superclass/interface. */
    private static final int SCORE_RELATED_CLASS = 25;

    /** Points for exact method name match. */
    private static final int SCORE_EXACT_NAME = 30;
    /** Points when Levenshtein distance is <= 3. */
    private static final int SCORE_LEVENSHTEIN_CLOSE = 20;
    /** Points when old name is a substring of the candidate (or vice versa). */
    private static final int SCORE_SUBSTRING_MATCH = 15;
    /** Points for common prefix or suffix (at least 4 chars). */
    private static final int SCORE_PREFIX_SUFFIX = 5;

    /** Points when parameter count matches exactly. */
    private static final int SCORE_PARAM_COUNT_EXACT = 15;
    /** Points when parameter count is off by exactly 1. */
    private static final int SCORE_PARAM_COUNT_OFF_BY_ONE = 5;

    /** Points when ALL parameter types match exactly. */
    private static final int SCORE_ALL_PARAMS_MATCH = 15;
    /** Points when >50% of parameter types match. */
    private static final int SCORE_MOST_PARAMS_MATCH = 8;

    /** Minimum score to auto-apply the redirect. */
    private static final int THRESHOLD_AUTO_APPLY = 70;
    /** Minimum score to log a warning (but not apply). */
    private static final int THRESHOLD_LOG_WARNING = 50;

    // ═══════════════════════════════════════════════════════════════════════
    // INDEX STRUCTURES — populated once by indexJar(), read-only thereafter
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Index of all methods in the target MC JAR.
     * Key: JVM internal class name (e.g., "net/minecraft/world/level/Level")
     * Value: list of (methodName, descriptor) tuples for that class
     */
    private final Map<String, List<MethodInfo>> methodIndex = new HashMap<>();

    /**
     * Index of all fields in the target MC JAR.
     * Key: JVM internal class name
     * Value: list of (fieldName, descriptor) tuples for that class
     */
    private final Map<String, List<FieldInfo>> fieldIndex = new HashMap<>();

    /**
     * Superclass/interface relationships for inheritance-aware matching.
     * Key: JVM internal class name
     * Value: list of parent classes and implemented interfaces
     */
    private final Map<String, List<String>> classHierarchy = new HashMap<>();

    // ═══════════════════════════════════════════════════════════════════════
    // RESOLVE CACHES — avoid repeated scoring for the same unresolved ref
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Cache of resolved method matches.
     * Key: "owner.name.desc" (the unresolved reference)
     * Value: resolved MethodInfo, or EMPTY_METHOD_INFO sentinel for "no match"
     *
     * Using ConcurrentHashMap for thread-safe access during class loading.
     */
    private final Map<String, MethodInfo> methodResolveCache = new ConcurrentHashMap<>();

    /**
     * Cache of resolved field matches.
     * Key: "owner.name.desc" (the unresolved reference)
     * Value: resolved FieldInfo, or EMPTY_FIELD_INFO sentinel for "no match"
     */
    private final Map<String, FieldInfo> fieldResolveCache = new ConcurrentHashMap<>();

    /** Sentinel value indicating "we looked and found nothing" — avoids re-scanning. */
    private static final MethodInfo EMPTY_METHOD_INFO = new MethodInfo("", "", "", -1);
    /** Sentinel value for "no field match found". */
    private static final FieldInfo EMPTY_FIELD_INFO = new FieldInfo("", "", "", -1);

    /** Whether the JAR has been indexed. Volatile for cross-thread visibility. */
    private volatile boolean indexed = false;

    /** Total number of classes indexed (for logging). */
    private volatile int indexedClassCount = 0;

    /** Total number of methods indexed (for logging). */
    private volatile int indexedMethodCount = 0;

    /** Total number of fields indexed (for logging). */
    private volatile int indexedFieldCount = 0;

    // ═══════════════════════════════════════════════════════════════════════
    // DATA RECORDS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * A method found in the target MC JAR.
     *
     * @param owner      JVM internal name of the class that declares this method
     * @param name       method name
     * @param descriptor JVM method descriptor (e.g., "(IIF)V")
     * @param score      match score when used as a fuzzy result (-1 when in the index)
     */
    public record MethodInfo(String owner, String name, String descriptor, int score) {}

    /**
     * A field found in the target MC JAR.
     *
     * @param owner      JVM internal name of the class that declares this field
     * @param name       field name
     * @param descriptor JVM field descriptor (e.g., "Ljava/lang/String;")
     * @param score      match score when used as a fuzzy result (-1 when in the index)
     */
    public record FieldInfo(String owner, String name, String descriptor, int score) {}

    // ═══════════════════════════════════════════════════════════════════════
    // INDEXING — scans the target MC JAR to build lookup structures
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Index all classes, methods, and fields in the given MC JAR.
     * This must be called once during startup before any resolve calls.
     *
     * <p>Only indexes {@code net/minecraft/} classes and their inner classes.
     * Skips non-class entries (assets, data, etc.) for performance.</p>
     *
     * @param mcJar path to the Minecraft client JAR
     * @throws IOException if the JAR cannot be read
     */
    public void indexJar(Path mcJar) throws IOException {
        if (indexed) {
            LOGGER.debug("JAR already indexed, skipping");
            return;
        }

        if (mcJar == null || !Files.exists(mcJar)) {
            LOGGER.warn("MC JAR not found at {}, fuzzy resolver disabled", mcJar);
            return;
        }

        LOGGER.info("Indexing MC JAR for fuzzy resolution: {}", mcJar.getFileName());
        long startTime = System.currentTimeMillis();

        int classes = 0;
        int methods = 0;
        int fields = 0;

        try (JarFile jar = new JarFile(mcJar.toFile())) {
            Enumeration<JarEntry> entries = jar.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // Only index .class files in the net/minecraft package tree
                // and com/mojang (Mojang's shared libraries like DFU, brigadier)
                if (!entryName.endsWith(".class")) continue;
                if (!entryName.startsWith("net/minecraft/") &&
                    !entryName.startsWith("com/mojang/")) {
                    continue;
                }

                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] classBytes = is.readAllBytes();
                    ClassReader reader = new ClassReader(classBytes);

                    // Extract class name, superclass, interfaces
                    String className = reader.getClassName();
                    String superName = reader.getSuperName();
                    String[] interfaces = reader.getInterfaces();

                    // Build hierarchy entry
                    List<String> parents = new ArrayList<>();
                    if (superName != null && !superName.equals("java/lang/Object")) {
                        parents.add(superName);
                    }
                    if (interfaces != null) {
                        Collections.addAll(parents, interfaces);
                    }
                    classHierarchy.put(className, parents);

                    // Collect methods and fields with a lightweight visitor
                    // (SKIP_CODE + SKIP_DEBUG for speed — we only need signatures)
                    List<MethodInfo> classMethods = new ArrayList<>();
                    List<FieldInfo> classFields = new ArrayList<>();

                    reader.accept(new ClassVisitor(Opcodes.ASM9) {
                        @Override
                        public MethodVisitor visitMethod(int access, String name,
                                String descriptor, String signature, String[] exceptions) {
                            // Skip constructors and class initializers — they are not
                            // candidates for fuzzy matching (use constructor redirects instead)
                            if (!"<init>".equals(name) && !"<clinit>".equals(name)) {
                                classMethods.add(new MethodInfo(className, name, descriptor, -1));
                            }
                            return null; // don't visit method body
                        }

                        @Override
                        public FieldVisitor visitField(int access, String name,
                                String descriptor, String signature, Object value) {
                            classFields.add(new FieldInfo(className, name, descriptor, -1));
                            return null;
                        }
                    }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

                    if (!classMethods.isEmpty()) {
                        methodIndex.put(className, classMethods);
                        methods += classMethods.size();
                    }
                    if (!classFields.isEmpty()) {
                        fieldIndex.put(className, classFields);
                        fields += classFields.size();
                    }
                    classes++;

                } catch (Exception e) {
                    // Skip unparseable classes (shouldn't happen with valid MC JARs)
                    LOGGER.debug("Could not index class {}: {}", entryName, e.getMessage());
                }
            }
        }

        indexedClassCount = classes;
        indexedMethodCount = methods;
        indexedFieldCount = fields;
        indexed = true;

        long elapsed = System.currentTimeMillis() - startTime;
        LOGGER.info("Indexed {} classes, {} methods, {} fields from MC JAR in {}ms",
                classes, methods, fields, elapsed);
    }

    /**
     * Check whether the resolver has been initialized with a JAR index.
     */
    public boolean isIndexed() {
        return indexed;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // METHOD RESOLUTION — fuzzy match an unresolved method call
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Attempt to resolve an unresolved method reference by fuzzy matching
     * against methods in the target MC JAR.
     *
     * <p>Returns the best match if its score is >= THRESHOLD_AUTO_APPLY (70).
     * Logs a warning (but returns null) for scores in the 50-69 range.
     * Returns null silently for scores < 50.</p>
     *
     * @param owner      JVM internal name of the class the method was called on
     * @param name       the method name that wasn't found
     * @param descriptor the JVM method descriptor of the call
     * @return the best matching method, or null if no confident match was found
     */
    public MethodInfo resolveMethod(String owner, String name, String descriptor) {
        if (!indexed) return null;

        // Check cache first
        String cacheKey = owner + "." + name + "." + descriptor;
        MethodInfo cached = methodResolveCache.get(cacheKey);
        if (cached != null) {
            // Return null for the "no match" sentinel
            return cached == EMPTY_METHOD_INFO ? null : cached;
        }

        // Gather candidate methods from the owner class and its parents
        List<MethodInfo> candidates = gatherMethodCandidates(owner);
        if (candidates.isEmpty()) {
            methodResolveCache.put(cacheKey, EMPTY_METHOD_INFO);
            return null;
        }

        // Score each candidate and find the best match
        MethodInfo bestMatch = null;
        int bestScore = 0;

        // Parse the unresolved descriptor for parameter comparison
        List<String> unresolvedParams = parseParameterTypes(descriptor);
        String unresolvedReturn = parseReturnType(descriptor);

        for (MethodInfo candidate : candidates) {
            int score = scoreMethodMatch(
                    owner, name, descriptor, unresolvedParams, unresolvedReturn,
                    candidate
            );

            if (score > bestScore) {
                bestScore = score;
                bestMatch = new MethodInfo(
                        candidate.owner(), candidate.name(),
                        candidate.descriptor(), score
                );
            }
        }

        // Apply threshold logic
        if (bestMatch != null && bestScore >= THRESHOLD_AUTO_APPLY) {
            LOGGER.info("[RetroMod-Fuzzy] Resolved {}.{}{} -> {}.{}{} (confidence: {}%)",
                    owner, name, descriptor,
                    bestMatch.owner(), bestMatch.name(), bestMatch.descriptor(),
                    bestScore);
            methodResolveCache.put(cacheKey, bestMatch);
            return bestMatch;
        } else if (bestMatch != null && bestScore >= THRESHOLD_LOG_WARNING) {
            LOGGER.warn("[RetroMod-Fuzzy] Possible match for {}.{}{} -> {}.{}{} " +
                    "(confidence: {}% — below auto-apply threshold of {}%)",
                    owner, name, descriptor,
                    bestMatch.owner(), bestMatch.name(), bestMatch.descriptor(),
                    bestScore, THRESHOLD_AUTO_APPLY);
        } else if (bestMatch != null) {
            LOGGER.debug("[RetroMod-Fuzzy] Low confidence match for {}.{}{} -> {}.{}{} ({}%)",
                    owner, name, descriptor,
                    bestMatch.owner(), bestMatch.name(), bestMatch.descriptor(),
                    bestScore);
        }

        // Cache the "no match" result to avoid re-scoring
        methodResolveCache.put(cacheKey, EMPTY_METHOD_INFO);
        return null;
    }

    /**
     * Gather all candidate methods from the given class and its parents.
     * Walks up the class hierarchy (superclasses + interfaces) to find
     * methods that might be inherited matches.
     */
    private List<MethodInfo> gatherMethodCandidates(String owner) {
        List<MethodInfo> candidates = new ArrayList<>();

        // Methods directly on the owner class
        List<MethodInfo> ownerMethods = methodIndex.get(owner);
        if (ownerMethods != null) {
            candidates.addAll(ownerMethods);
        }

        // Walk up the hierarchy: superclasses and interfaces
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        visited.add(owner);

        List<String> parents = classHierarchy.get(owner);
        if (parents != null) {
            for (String parent : parents) {
                if (visited.add(parent)) {
                    queue.add(parent);
                }
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<MethodInfo> parentMethods = methodIndex.get(current);
            if (parentMethods != null) {
                candidates.addAll(parentMethods);
            }
            // Continue walking up
            List<String> grandparents = classHierarchy.get(current);
            if (grandparents != null) {
                for (String gp : grandparents) {
                    if (visited.add(gp)) {
                        queue.add(gp);
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Score how well a candidate method matches the unresolved reference.
     * Returns a score from 0 to 100.
     *
     * @param unresolvedOwner  the class the unresolved method was called on
     * @param unresolvedName   the unresolved method name
     * @param unresolvedDesc   the full unresolved method descriptor
     * @param unresolvedParams parsed parameter types from the descriptor
     * @param unresolvedReturn parsed return type from the descriptor
     * @param candidate        the candidate method to score
     * @return score from 0 (no match) to 100 (perfect match)
     */
    private int scoreMethodMatch(
            String unresolvedOwner, String unresolvedName, String unresolvedDesc,
            List<String> unresolvedParams, String unresolvedReturn,
            MethodInfo candidate) {

        int score = 0;

        // === AXIS 1: Class match (0-40 points) ===
        if (candidate.owner().equals(unresolvedOwner)) {
            // Exact class match — highest confidence
            score += SCORE_EXACT_CLASS;
        } else if (isRelatedClass(unresolvedOwner, candidate.owner())) {
            // Candidate is on a superclass or interface of the owner
            score += SCORE_RELATED_CLASS;
        } else {
            // Candidate is on a completely unrelated class — very unlikely match.
            // Don't add any class points, which effectively caps the score at 60
            // (name + params), making it very hard to reach auto-apply threshold.
        }

        // === AXIS 2: Name similarity (0-30 points) ===
        String candidateName = candidate.name();
        if (candidateName.equals(unresolvedName)) {
            // Exact name match — this is the strongest signal
            score += SCORE_EXACT_NAME;
        } else {
            // Try Levenshtein distance (catches typos and minor renames)
            int editDistance = levenshteinDistance(unresolvedName, candidateName);
            if (editDistance <= 3) {
                score += SCORE_LEVENSHTEIN_CLOSE;
            }
            // Try substring containment (e.g., "setBlock" contains "Block")
            else if (candidateName.toLowerCase().contains(unresolvedName.toLowerCase()) ||
                     unresolvedName.toLowerCase().contains(candidateName.toLowerCase())) {
                score += SCORE_SUBSTRING_MATCH;
            }
            // Try common prefix/suffix of at least 4 characters
            else {
                int commonPrefix = commonPrefixLength(unresolvedName, candidateName);
                int commonSuffix = commonSuffixLength(unresolvedName, candidateName);
                if (commonPrefix >= 4 || commonSuffix >= 4) {
                    score += SCORE_PREFIX_SUFFIX;
                }
            }
        }

        // === AXIS 3: Parameter count (0-15 points) ===
        List<String> candidateParams = parseParameterTypes(candidate.descriptor());
        int paramCountDiff = Math.abs(unresolvedParams.size() - candidateParams.size());
        if (paramCountDiff == 0) {
            score += SCORE_PARAM_COUNT_EXACT;
        } else if (paramCountDiff == 1) {
            // Off by one — common when a parameter is added/removed between versions
            score += SCORE_PARAM_COUNT_OFF_BY_ONE;
        }
        // Off by 2+ — no points

        // === AXIS 4: Parameter type matching (0-15 points) ===
        if (!unresolvedParams.isEmpty() && !candidateParams.isEmpty()) {
            int matchCount = countMatchingParams(unresolvedParams, candidateParams);
            int minSize = Math.min(unresolvedParams.size(), candidateParams.size());
            if (matchCount == minSize && paramCountDiff == 0) {
                // All parameter types match exactly
                score += SCORE_ALL_PARAMS_MATCH;
            } else if (minSize > 0 && matchCount > minSize / 2) {
                // More than half the parameters match
                score += SCORE_MOST_PARAMS_MATCH;
            }
        } else if (unresolvedParams.isEmpty() && candidateParams.isEmpty()) {
            // Both are no-arg methods — full type match
            score += SCORE_ALL_PARAMS_MATCH;
        }

        // === RETURN TYPE COMPATIBILITY CHECK ===
        // If the return type category changes (primitive vs object, or different
        // primitive type), this is almost certainly a wrong match. Penalize heavily.
        String candidateReturn = candidate.descriptor().substring(
            candidate.descriptor().lastIndexOf(')') + 1);
        if (!unresolvedReturn.equals(candidateReturn)) {
            boolean unresolvedPrimitive = !unresolvedReturn.startsWith("L") && !unresolvedReturn.startsWith("[");
            boolean candidatePrimitive = !candidateReturn.startsWith("L") && !candidateReturn.startsWith("[");

            if (unresolvedPrimitive != candidatePrimitive) {
                // One returns primitive, other returns object — incompatible stack types
                // This would cause VerifyError, so reject entirely
                return 0;
            }
            if (unresolvedPrimitive && candidatePrimitive && !unresolvedReturn.equals(candidateReturn)) {
                // Different primitive types (e.g., long vs int) — reject
                return 0;
            }
            // Both return objects but different types — small penalty (might be valid)
            score -= 5;
        }

        return score;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIELD RESOLUTION — fuzzy match an unresolved field access
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Attempt to resolve an unresolved field reference by fuzzy matching.
     *
     * <p>Checks two scenarios:</p>
     * <ol>
     *   <li>Field was renamed: same type, different name, same class</li>
     *   <li>Field moved to a superclass or related class</li>
     * </ol>
     *
     * @param owner      JVM internal name of the class the field was accessed on
     * @param name       the field name that wasn't found
     * @param descriptor the JVM field descriptor (type)
     * @return the best matching field, or null if no confident match was found
     */
    public FieldInfo resolveField(String owner, String name, String descriptor) {
        if (!indexed) return null;

        // Check cache first
        String cacheKey = owner + "." + name + "." + descriptor;
        FieldInfo cached = fieldResolveCache.get(cacheKey);
        if (cached != null) {
            return cached == EMPTY_FIELD_INFO ? null : cached;
        }

        // Gather candidate fields from the owner class and its parents
        List<FieldInfo> candidates = gatherFieldCandidates(owner);
        if (candidates.isEmpty()) {
            fieldResolveCache.put(cacheKey, EMPTY_FIELD_INFO);
            return null;
        }

        // Score each candidate
        FieldInfo bestMatch = null;
        int bestScore = 0;

        for (FieldInfo candidate : candidates) {
            int score = scoreFieldMatch(owner, name, descriptor, candidate);

            if (score > bestScore) {
                bestScore = score;
                bestMatch = new FieldInfo(
                        candidate.owner(), candidate.name(),
                        candidate.descriptor(), score
                );
            }
        }

        // Apply threshold logic (same thresholds as methods)
        if (bestMatch != null && bestScore >= THRESHOLD_AUTO_APPLY) {
            LOGGER.info("[RetroMod-Fuzzy] Resolved field {}.{} {} -> {}.{} {} (confidence: {}%)",
                    owner, name, descriptor,
                    bestMatch.owner(), bestMatch.name(), bestMatch.descriptor(),
                    bestScore);
            fieldResolveCache.put(cacheKey, bestMatch);
            return bestMatch;
        } else if (bestMatch != null && bestScore >= THRESHOLD_LOG_WARNING) {
            LOGGER.warn("[RetroMod-Fuzzy] Possible field match for {}.{} {} -> {}.{} {} " +
                    "(confidence: {}% — below auto-apply threshold of {}%)",
                    owner, name, descriptor,
                    bestMatch.owner(), bestMatch.name(), bestMatch.descriptor(),
                    bestScore, THRESHOLD_AUTO_APPLY);
        }

        fieldResolveCache.put(cacheKey, EMPTY_FIELD_INFO);
        return null;
    }

    /**
     * Gather candidate fields from the owner class and its class hierarchy.
     */
    private List<FieldInfo> gatherFieldCandidates(String owner) {
        List<FieldInfo> candidates = new ArrayList<>();

        // Fields directly on the owner class
        List<FieldInfo> ownerFields = fieldIndex.get(owner);
        if (ownerFields != null) {
            candidates.addAll(ownerFields);
        }

        // Walk up the hierarchy
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        visited.add(owner);

        List<String> parents = classHierarchy.get(owner);
        if (parents != null) {
            for (String parent : parents) {
                if (visited.add(parent)) {
                    queue.add(parent);
                }
            }
        }

        while (!queue.isEmpty()) {
            String current = queue.poll();
            List<FieldInfo> parentFields = fieldIndex.get(current);
            if (parentFields != null) {
                candidates.addAll(parentFields);
            }
            List<String> grandparents = classHierarchy.get(current);
            if (grandparents != null) {
                for (String gp : grandparents) {
                    if (visited.add(gp)) {
                        queue.add(gp);
                    }
                }
            }
        }

        return candidates;
    }

    /**
     * Score how well a candidate field matches the unresolved field reference.
     * Uses the same 100-point scale as method matching:
     * - Class match: 0-40 points
     * - Name similarity: 0-30 points
     * - Type match: 0-30 points (replaces param count + param types axes)
     *
     * @param unresolvedOwner the class the field was accessed on
     * @param unresolvedName  the field name that wasn't found
     * @param unresolvedDesc  the JVM field descriptor (type)
     * @param candidate       the candidate field to score
     * @return score from 0 to 100
     */
    private int scoreFieldMatch(String unresolvedOwner, String unresolvedName,
            String unresolvedDesc, FieldInfo candidate) {

        int score = 0;

        // === Class match (0-40 points) ===
        if (candidate.owner().equals(unresolvedOwner)) {
            score += SCORE_EXACT_CLASS;
        } else if (isRelatedClass(unresolvedOwner, candidate.owner())) {
            score += SCORE_RELATED_CLASS;
        }

        // === Name similarity (0-30 points) ===
        if (candidate.name().equals(unresolvedName)) {
            score += SCORE_EXACT_NAME;
        } else {
            int editDistance = levenshteinDistance(unresolvedName, candidate.name());
            if (editDistance <= 3) {
                score += SCORE_LEVENSHTEIN_CLOSE;
            } else if (candidate.name().toLowerCase().contains(unresolvedName.toLowerCase()) ||
                       unresolvedName.toLowerCase().contains(candidate.name().toLowerCase())) {
                score += SCORE_SUBSTRING_MATCH;
            } else {
                int commonPrefix = commonPrefixLength(unresolvedName, candidate.name());
                int commonSuffix = commonSuffixLength(unresolvedName, candidate.name());
                if (commonPrefix >= 4 || commonSuffix >= 4) {
                    score += SCORE_PREFIX_SUFFIX;
                }
            }
        }

        // === Type match (0-30 points) ===
        // For fields, the descriptor IS the type. Exact type match is strong evidence.
        if (candidate.descriptor().equals(unresolvedDesc)) {
            // Same type — very strong signal (field renamed but type unchanged)
            score += 30;
        } else {
            // Different type — check if types are related (e.g., same base class)
            String unresolvedType = extractTypeFromDescriptor(unresolvedDesc);
            String candidateType = extractTypeFromDescriptor(candidate.descriptor());
            if (unresolvedType != null && candidateType != null &&
                isRelatedClass(unresolvedType, candidateType)) {
                score += 15;
            }
        }

        return score;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS — string comparison, descriptor parsing, hierarchy
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if two classes are related via the indexed class hierarchy.
     * Returns true if classB is a superclass or interface of classA
     * (or vice versa), walking up to 5 levels deep.
     */
    private boolean isRelatedClass(String classA, String classB) {
        // Direct relationship check (A extends/implements B)
        if (isAncestor(classA, classB, 5)) return true;
        // Reverse check (B extends/implements A)
        if (isAncestor(classB, classA, 5)) return true;
        return false;
    }

    /**
     * Walk the class hierarchy upward from {@code child} to check if
     * {@code ancestor} appears anywhere. Bounded by maxDepth to avoid
     * excessive traversal on deep hierarchies.
     */
    private boolean isAncestor(String child, String ancestor, int maxDepth) {
        if (maxDepth <= 0) return false;

        List<String> parents = classHierarchy.get(child);
        if (parents == null) return false;

        for (String parent : parents) {
            if (parent.equals(ancestor)) return true;
            if (isAncestor(parent, ancestor, maxDepth - 1)) return true;
        }
        return false;
    }

    /**
     * Compute the Levenshtein (edit) distance between two strings.
     * Used for fuzzy name matching — a distance of 1-3 suggests a rename.
     *
     * Uses the standard dynamic programming approach with O(min(m,n)) space.
     */
    static int levenshteinDistance(String a, String b) {
        if (a.equals(b)) return 0;
        if (a.isEmpty()) return b.length();
        if (b.isEmpty()) return a.length();

        // Ensure a is the shorter string for space optimization
        if (a.length() > b.length()) {
            String tmp = a;
            a = b;
            b = tmp;
        }

        int[] prev = new int[a.length() + 1];
        int[] curr = new int[a.length() + 1];

        // Initialize base case: distance from empty string
        for (int i = 0; i <= a.length(); i++) {
            prev[i] = i;
        }

        for (int j = 1; j <= b.length(); j++) {
            curr[0] = j;
            for (int i = 1; i <= a.length(); i++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                curr[i] = Math.min(
                        Math.min(curr[i - 1] + 1, prev[i] + 1),
                        prev[i - 1] + cost
                );
            }
            // Swap arrays
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }

        return prev[a.length()];
    }

    /**
     * Count how many characters at the start of both strings match.
     */
    private int commonPrefixLength(String a, String b) {
        int len = Math.min(a.length(), b.length());
        int count = 0;
        for (int i = 0; i < len; i++) {
            if (a.charAt(i) == b.charAt(i)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Count how many characters at the end of both strings match.
     */
    private int commonSuffixLength(String a, String b) {
        int lenA = a.length();
        int lenB = b.length();
        int len = Math.min(lenA, lenB);
        int count = 0;
        for (int i = 1; i <= len; i++) {
            if (a.charAt(lenA - i) == b.charAt(lenB - i)) {
                count++;
            } else {
                break;
            }
        }
        return count;
    }

    /**
     * Parse parameter types from a JVM method descriptor.
     * Example: "(ILjava/lang/String;F)V" -> ["I", "Ljava/lang/String;", "F"]
     */
    static List<String> parseParameterTypes(String descriptor) {
        List<String> params = new ArrayList<>();
        int i = 1; // skip the opening '('
        while (i < descriptor.length() && descriptor.charAt(i) != ')') {
            int start = i;
            char c = descriptor.charAt(i);
            switch (c) {
                case 'L' -> {
                    // Object type: read until ';'
                    int end = descriptor.indexOf(';', i);
                    if (end < 0) break; // malformed
                    params.add(descriptor.substring(start, end + 1));
                    i = end + 1;
                }
                case '[' -> {
                    // Array type: skip all '[' then parse the element type
                    while (i < descriptor.length() && descriptor.charAt(i) == '[') i++;
                    if (i < descriptor.length() && descriptor.charAt(i) == 'L') {
                        int end = descriptor.indexOf(';', i);
                        if (end < 0) break;
                        params.add(descriptor.substring(start, end + 1));
                        i = end + 1;
                    } else if (i < descriptor.length()) {
                        params.add(descriptor.substring(start, i + 1));
                        i++;
                    }
                }
                default -> {
                    // Primitive type: single character
                    params.add(String.valueOf(c));
                    i++;
                }
            }
        }
        return params;
    }

    /**
     * Parse the return type from a JVM method descriptor.
     * Example: "(IF)Ljava/lang/String;" -> "Ljava/lang/String;"
     */
    private String parseReturnType(String descriptor) {
        int closeParenIdx = descriptor.indexOf(')');
        if (closeParenIdx < 0 || closeParenIdx + 1 >= descriptor.length()) {
            return "V";
        }
        return descriptor.substring(closeParenIdx + 1);
    }

    /**
     * Count how many parameter types match between two lists (ordered comparison).
     * Compares position by position up to the length of the shorter list.
     */
    private int countMatchingParams(List<String> paramsA, List<String> paramsB) {
        int count = 0;
        int minLen = Math.min(paramsA.size(), paramsB.size());
        for (int i = 0; i < minLen; i++) {
            if (paramsA.get(i).equals(paramsB.get(i))) {
                count++;
            }
        }
        return count;
    }

    /**
     * Extract the class name from a JVM field descriptor.
     * "Ljava/lang/String;" -> "java/lang/String"
     * "I" -> null (primitive, no class to extract)
     */
    private String extractTypeFromDescriptor(String descriptor) {
        if (descriptor == null || descriptor.isEmpty()) return null;
        if (descriptor.startsWith("L") && descriptor.endsWith(";")) {
            return descriptor.substring(1, descriptor.length() - 1);
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MC JAR DETECTION — find the Minecraft JAR on the classpath or disk
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Attempt to find the Minecraft client JAR from the JVM classpath.
     * During Fabric Loader startup, the MC JAR is always on the classpath.
     *
     * <p>Searches for paths containing "minecraft" and ending in ".jar".
     * Handles both Prism Launcher and vanilla launcher layouts.</p>
     *
     * @return path to the MC JAR, or null if not found
     */
    public static Path findMcJarFromClasspath() {
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isEmpty()) {
            return null;
        }

        String separator = System.getProperty("path.separator", ":");
        String[] entries = classpath.split(separator);

        for (String entry : entries) {
            String lower = entry.toLowerCase();
            // Look for the Minecraft client JAR
            // Patterns:
            //   minecraft-VERSION-client.jar (Prism/MultiMC)
            //   VERSION.jar in .minecraft/versions/VERSION/ (vanilla)
            //   minecraft-client-VERSION.jar (some launchers)
            if (lower.contains("minecraft") && lower.endsWith(".jar")) {
                Path jarPath = Path.of(entry);
                if (Files.exists(jarPath)) {
                    return jarPath;
                }
            }
        }

        return null;
    }

    /**
     * Attempt to find the Minecraft JAR for a specific version from known
     * launcher locations on disk.
     *
     * @param mcVersion the Minecraft version to find (e.g., "26.1")
     * @return path to the MC JAR, or null if not found
     */
    public static Path findMcJarByVersion(String mcVersion) {
        if (mcVersion == null || mcVersion.isEmpty()) return null;

        String home = System.getProperty("user.home");
        String os = System.getProperty("os.name", "").toLowerCase();

        List<Path> candidates = new ArrayList<>();

        if (os.contains("mac")) {
            // macOS paths
            // Prism Launcher
            candidates.add(Path.of(home, "Library/Application Support/PrismLauncher/libraries",
                    "com/mojang/minecraft", mcVersion, "minecraft-" + mcVersion + "-client.jar"));
            // Vanilla launcher
            candidates.add(Path.of(home, "Library/Application Support/minecraft/versions",
                    mcVersion, mcVersion + ".jar"));
        } else if (os.contains("win")) {
            // Windows paths
            String appdata = System.getenv("APPDATA");
            if (appdata == null) appdata = home;
            candidates.add(Path.of(appdata, "PrismLauncher/libraries",
                    "com/mojang/minecraft", mcVersion, "minecraft-" + mcVersion + "-client.jar"));
            candidates.add(Path.of(appdata, ".minecraft/versions",
                    mcVersion, mcVersion + ".jar"));
        } else {
            // Linux paths
            candidates.add(Path.of(home, ".local/share/PrismLauncher/libraries",
                    "com/mojang/minecraft", mcVersion, "minecraft-" + mcVersion + "-client.jar"));
            candidates.add(Path.of(home, ".minecraft/versions",
                    mcVersion, mcVersion + ".jar"));
        }

        for (Path path : candidates) {
            if (Files.exists(path)) {
                return path;
            }
        }

        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DIAGNOSTICS — cache stats and index info
    // ═══════════════════════════════════════════════════════════════════════

    /** Get the number of cached method resolution results. */
    public int getMethodCacheSize() {
        return methodResolveCache.size();
    }

    /** Get the number of cached field resolution results. */
    public int getFieldCacheSize() {
        return fieldResolveCache.size();
    }

    /** Get the total number of indexed classes. */
    public int getIndexedClassCount() {
        return indexedClassCount;
    }

    /** Get the total number of indexed methods. */
    public int getIndexedMethodCount() {
        return indexedMethodCount;
    }

    /** Get the total number of indexed fields. */
    public int getIndexedFieldCount() {
        return indexedFieldCount;
    }

    /**
     * Clear both resolve caches. Useful after the JAR index is rebuilt
     * or when redirect maps change significantly.
     */
    public void clearCaches() {
        methodResolveCache.clear();
        fieldResolveCache.clear();
        LOGGER.debug("Cleared fuzzy resolver caches");
    }
}
