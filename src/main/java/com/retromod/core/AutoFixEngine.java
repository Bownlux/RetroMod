/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Automated crash analysis and fix system. Scans game/crash logs for known
 * error patterns, applies fixes (redirects, mixin stripping, interface
 * corrections), and marks mods for retransformation on next launch.
 *
 * This replaces the manual "launch -> read log -> fix -> repeat" cycle.
 * The engine runs AFTER initial transformation and does NOT relaunch the game;
 * it prepares fixes so the NEXT launch succeeds.
 */
package com.retromod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Automated crash analysis and fix engine for RetroMod.
 *
 * <h2>Overview</h2>
 * After the initial mod transformation, errors may still occur at runtime due to
 * edge cases that shims and the fuzzy resolver cannot cover. This engine scans
 * game logs and crash reports for known error patterns, then applies targeted
 * fixes to the transformer so that the NEXT launch succeeds.
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Read the game log (latest.log or crash-report)</li>
 *   <li>Match each line against a list of {@link ErrorPattern} instances</li>
 *   <li>For each match, extract error details and compute a fix</li>
 *   <li>Apply the fix to the transformer (register redirect, strip mixin, etc.)</li>
 *   <li>Persist applied fixes to {@code config/retromod/auto-fixes.json}</li>
 *   <li>On next launch, load persisted fixes BEFORE transformation</li>
 * </ol>
 *
 * <h2>Safety</h2>
 * <ul>
 *   <li>Never applies the same fix twice (tracked by fix key)</li>
 *   <li>If a fix was applied but the same error recurs, the fix is blacklisted</li>
 *   <li>All fixes are logged at INFO level for transparency</li>
 *   <li>A maximum of {@value #MAX_FIXES_PER_RUN} fixes per analysis run prevents runaway loops</li>
 * </ul>
 *
 * <p><b>IMPORTANT:</b> This class must NOT reference Fabric/NeoForge loader classes
 * directly because it is also used by the standalone CLI.</p>
 */
public class AutoFixEngine {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-AutoFix");

    /**
     * Where persisted fixes are saved between launches.
     *
     * SECURITY NOTE (MEDIUM): This is a relative path resolved against the working directory.
     * If an attacker replaces config/retromod/ with a symlink pointing to a sensitive
     * directory, writes would follow the symlink. This is mitigated by the fact that:
     * 1. The attacker needs local filesystem access to the game directory
     * 2. The file content is always valid JSON (fix descriptions, not executable code)
     * 3. The game directory is typically user-owned with restricted permissions
     * If stronger protection is needed, add a symlink check before writing.
     */
    private static final Path FIXES_FILE = Path.of("config/retromod/auto-fixes.json");

    /** Safety limit: maximum number of fixes applied in a single analysis run. */
    private static final int MAX_FIXES_PER_RUN = 50;

    /** Gson instance for persisting fixes to JSON. */
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    // ═══════════════════════════════════════════════════════════════════════
    // ERROR PATTERNS — each pattern has a regex, a parser, and a fix action
    // ═══════════════════════════════════════════════════════════════════════

    private final List<ErrorPattern> patterns = new ArrayList<>();

    // ═══════════════════════════════════════════════════════════════════════
    // FIX TRACKING — prevents infinite loops and duplicate fixes
    // ═══════════════════════════════════════════════════════════════════════

    /** All fixes applied during this analysis run. */
    private final List<AppliedFix> appliedFixes = new ArrayList<>();

    /** Set of fix keys that have already been applied (prevents duplicates). */
    private final Set<String> appliedFixKeys = new HashSet<>();

    /**
     * Blacklist: fix keys whose fixes did NOT resolve the error.
     * If a fix was applied but the same error recurs on the next run,
     * the fix key is blacklisted and the fix will not be re-applied.
     */
    private final Set<String> blacklist = new HashSet<>();

    /**
     * Previously persisted fixes loaded from disk. Used to detect recurrence:
     * if a fix from the previous run appears again, it failed and gets blacklisted.
     */
    private final Set<String> previousRunFixKeys = new HashSet<>();

    // ═══════════════════════════════════════════════════════════════════════
    // CONSTRUCTOR — registers all known error patterns
    // ═══════════════════════════════════════════════════════════════════════

    public AutoFixEngine() {
        registerAllPatterns();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Main entry point: scan a log file and apply fixes to the transformer.
     *
     * <p>Reads every line of the log, matches against known error patterns,
     * and applies the corresponding fix. Returns all fixes that were applied.</p>
     *
     * @param logFile     path to the game log or crash report
     * @param transformer the transformer to register fixes on
     * @return list of fixes that were applied (empty if no errors found)
     */
    public List<AppliedFix> analyzeAndFix(Path logFile, RetroModTransformer transformer) {
        if (logFile == null || !Files.exists(logFile)) {
            LOGGER.warn("[AutoFix] Log file not found: {}", logFile);
            return Collections.emptyList();
        }

        // Load previously applied fixes to detect recurrence
        loadPreviousFixes();

        // SECURITY NOTE (MEDIUM): Reads the entire log file into memory. Very large logs
        // (from long game sessions or verbose debug logging) could cause high memory usage.
        // This is acceptable because: (1) game logs are local user files, (2) Minecraft's
        // log rotation typically keeps latest.log under a few MB, (3) crash reports are tiny.
        // If this becomes an issue, consider streaming with BufferedReader and a line limit.
        List<String> logLines;
        try {
            logLines = Files.readAllLines(logFile);
        } catch (IOException e) {
            LOGGER.error("[AutoFix] Could not read log file: {}", e.getMessage());
            return Collections.emptyList();
        }

        LOGGER.info("[AutoFix] Analyzing {} lines from {}", logLines.size(), logFile.getFileName());

        List<AppliedFix> fixes = new ArrayList<>();
        int fixCount = 0;

        for (int i = 0; i < logLines.size() && fixCount < MAX_FIXES_PER_RUN; i++) {
            String line = logLines.get(i);

            // Some errors span multiple lines — gather context
            // Look ahead up to 5 lines for stack trace context
            String context = buildContext(logLines, i, 5);

            for (ErrorPattern pattern : patterns) {
                Matcher matcher = pattern.regex.matcher(line);
                if (matcher.find()) {
                    // Build a fix key to check for duplicates and blacklist
                    String fixKey = pattern.buildFixKey(matcher, line);

                    // Skip if already applied this run or blacklisted
                    if (appliedFixKeys.contains(fixKey)) break;
                    if (blacklist.contains(fixKey)) {
                        LOGGER.debug("[AutoFix] Skipping blacklisted fix: {}", fixKey);
                        break;
                    }

                    // If this fix was applied in a previous run but the error recurred,
                    // it means the fix didn't work — blacklist it
                    if (previousRunFixKeys.contains(fixKey)) {
                        LOGGER.warn("[AutoFix] Fix '{}' was applied previously but error recurred — blacklisting", fixKey);
                        blacklist.add(fixKey);
                        break;
                    }

                    // Apply the fix
                    AppliedFix fix = pattern.apply(matcher, line, context, transformer);
                    if (fix != null) {
                        fixes.add(fix);
                        appliedFixes.add(fix);
                        appliedFixKeys.add(fixKey);
                        fixCount++;
                        LOGGER.info("[AutoFix] Applied: {}", fix);
                    }
                    break; // first matching pattern wins for this line
                }
            }
        }

        // Persist fixes for next launch
        if (!fixes.isEmpty()) {
            persistFixes();
            LOGGER.info("[AutoFix] Applied {} fix(es). Retransform on next launch to take effect.", fixes.size());
        } else {
            LOGGER.info("[AutoFix] No actionable errors found in log.");
        }

        return fixes;
    }

    /**
     * Analyze a log file without applying fixes — just report what would be done.
     * Used by the CLI {@code autofix} command for dry-run / analysis mode.
     *
     * @param logFile path to the log file
     * @return list of suggested fixes (not applied)
     */
    public List<AppliedFix> analyzeOnly(Path logFile) {
        if (logFile == null || !Files.exists(logFile)) {
            LOGGER.warn("[AutoFix] Log file not found: {}", logFile);
            return Collections.emptyList();
        }

        List<String> logLines;
        try {
            logLines = Files.readAllLines(logFile);
        } catch (IOException e) {
            LOGGER.error("[AutoFix] Could not read log file: {}", e.getMessage());
            return Collections.emptyList();
        }

        List<AppliedFix> suggestions = new ArrayList<>();
        Set<String> seenKeys = new HashSet<>();

        for (int i = 0; i < logLines.size(); i++) {
            String line = logLines.get(i);
            String context = buildContext(logLines, i, 5);

            for (ErrorPattern pattern : patterns) {
                Matcher matcher = pattern.regex.matcher(line);
                if (matcher.find()) {
                    String fixKey = pattern.buildFixKey(matcher, line);
                    if (seenKeys.contains(fixKey)) break;
                    seenKeys.add(fixKey);

                    AppliedFix suggestion = pattern.describe(matcher, line, context);
                    if (suggestion != null) {
                        suggestions.add(suggestion);
                    }
                    break;
                }
            }
        }

        return suggestions;
    }

    /**
     * Load previously persisted fixes and apply them to the transformer.
     * Called during startup BEFORE transformation to re-apply fixes from
     * the previous launch cycle.
     *
     * @param transformer the transformer to apply saved fixes to
     * @return number of fixes loaded and applied
     */
    public int loadAndApplySavedFixes(RetroModTransformer transformer) {
        if (!Files.exists(FIXES_FILE)) {
            return 0;
        }

        try {
            String json = Files.readString(FIXES_FILE);
            Type listType = new TypeToken<List<PersistedFix>>() {}.getType();
            List<PersistedFix> saved = GSON.fromJson(json, listType);

            if (saved == null || saved.isEmpty()) {
                return 0;
            }

            int applied = 0;
            for (PersistedFix fix : saved) {
                if (fix.blacklisted) {
                    blacklist.add(fix.fixKey);
                    continue;
                }

                boolean success = applyPersistedFix(fix, transformer);
                if (success) {
                    appliedFixKeys.add(fix.fixKey);
                    previousRunFixKeys.add(fix.fixKey);
                    applied++;
                    LOGGER.info("[AutoFix] Re-applied saved fix: {}", fix.description);
                }
            }

            LOGGER.info("[AutoFix] Loaded {} saved fix(es) from previous run", applied);
            return applied;
        } catch (Exception e) {
            LOGGER.warn("[AutoFix] Could not load saved fixes: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Get all fixes applied during this run.
     */
    public List<AppliedFix> getAppliedFixes() {
        return Collections.unmodifiableList(appliedFixes);
    }

    /**
     * Get the blacklist of fix keys that failed.
     */
    public Set<String> getBlacklist() {
        return Collections.unmodifiableSet(blacklist);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATTERN REGISTRATION — all 20 error patterns
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Register all known error patterns. Each pattern consists of:
     * - A compiled regex that matches an error line in the log
     * - A method to extract error details from the regex match
     * - A method to compute and apply the fix
     */
    private void registerAllPatterns() {

        // ─────────────────────────────────────────────────────────────────
        // Pattern 1: NoSuchMethodError
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NoSuchMethodError: 'void com.example.Foo.oldMethod(int)'"
        // Parses: owner=com.example.Foo, method=oldMethod, desc=(I)V
        // Action: Use FuzzyMethodResolver to find replacement, register redirect
        patterns.add(new ErrorPattern(
            "NoSuchMethodError",
            Pattern.compile("NoSuchMethodError:.*'(\\S+)\\s+(\\S+)\\.(\\w+)\\(([^)]*)\\)'"),
            (matcher, line, context, transformer) -> {
                String returnType = matcher.group(1);    // e.g., "void"
                String ownerDot = matcher.group(2);      // e.g., "com.example.Foo"
                String methodName = matcher.group(3);    // e.g., "oldMethod"
                String paramTypes = matcher.group(4);    // e.g., "int"

                String owner = ownerDot.replace('.', '/');
                String desc = buildDescriptor(paramTypes, returnType);

                // Try pattern heuristics FIRST — faster and more reliable than fuzzy
                PatternHeuristics patterns = transformer.getPatternHeuristics();
                if (patterns != null) {
                    PatternHeuristics.PatternResult patternMatch = patterns.resolveMethod(owner, methodName, desc);
                    if (patternMatch != null && patternMatch.confidence() >= 0.6) {
                        transformer.registerMethodRedirect(
                            owner, methodName, desc,
                            patternMatch.newOwner(), patternMatch.newName(), patternMatch.newDescriptor()
                        );
                        return new AppliedFix(
                            "NoSuchMethodError",
                            ownerDot + "." + methodName + "(" + paramTypes + ")",
                            "Pattern match -> " + patternMatch.newOwner().replace('/', '.') +
                                "." + patternMatch.newName() + " (rule: " + patternMatch.rule() +
                                ", confidence: " + patternMatch.confidence() + ")",
                            "method_redirect:" + owner + "." + methodName + desc
                        );
                    }
                }

                // Check if this is a known bridge method (signature changed, not just renamed).
                // If so, the BridgeAdapterGenerator will handle it at retransform time.
                if (BridgeAdapterGenerator.isKnownBridgeMethod(methodName)) {
                    String oldDesc = BridgeAdapterGenerator.getOldDescriptor(methodName);
                    String newDesc = BridgeAdapterGenerator.getNewDescriptor(methodName);
                    if (oldDesc != null && newDesc != null) {
                        return new AppliedFix(
                            "NoSuchMethodError",
                            ownerDot + "." + methodName + "(" + paramTypes + ")",
                            "Signature changed in 26.1: " + oldDesc + " -> " + newDesc +
                                ". BridgeAdapterGenerator will create a bridge at retransform time.",
                            "bridge_adapter:" + owner + "." + methodName + desc
                        );
                    }
                }

                // Try fuzzy resolver as fallback
                FuzzyMethodResolver fuzzy = transformer.getFuzzyResolver();
                if (fuzzy != null) {
                    FuzzyMethodResolver.MethodInfo resolved = fuzzy.resolveMethod(owner, methodName, desc);
                    if (resolved != null) {
                        transformer.registerMethodRedirect(
                            owner, methodName, desc,
                            resolved.owner(), resolved.name(), resolved.descriptor()
                        );
                        return new AppliedFix(
                            "NoSuchMethodError",
                            ownerDot + "." + methodName + "(" + paramTypes + ")",
                            "Registered redirect -> " + resolved.owner().replace('/', '.') +
                                "." + resolved.name() + resolved.descriptor(),
                            "method_redirect:" + owner + "." + methodName + desc
                        );
                    }
                }
                // No pattern, no bridge, no fuzzy match — log but can't fix
                return new AppliedFix(
                    "NoSuchMethodError",
                    ownerDot + "." + methodName + "(" + paramTypes + ")",
                    "No replacement found — pattern heuristics and fuzzy resolver could not match. Manual shim needed.",
                    "method_redirect:" + owner + "." + methodName + desc
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 2: NoSuchFieldError
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NoSuchFieldError: Class com.example.Foo does not have member field 'int bar'"
        //   OR: "NoSuchFieldError: bar"
        // Parses: owner class, field name, field type
        // Action: Use FuzzyMethodResolver.resolveField(), register field redirect
        patterns.add(new ErrorPattern(
            "NoSuchFieldError",
            Pattern.compile("NoSuchFieldError:.*?(?:Class (\\S+) does not have member field '(\\S+)\\s+(\\w+)'|'(\\S+)\\s+(\\S+)\\.(\\w+)')"),
            (matcher, line, context, transformer) -> {
                String ownerDot, fieldName, fieldType;

                if (matcher.group(1) != null) {
                    // Format: "Class X does not have member field 'Y Z'"
                    ownerDot = matcher.group(1);
                    fieldType = matcher.group(2);
                    fieldName = matcher.group(3);
                } else {
                    // Format: "'type owner.field'"
                    fieldType = matcher.group(4);
                    ownerDot = matcher.group(5);
                    fieldName = matcher.group(6);
                }

                String owner = ownerDot.replace('.', '/');
                String desc = typeNameToDescriptor(fieldType);

                // Try pattern heuristics FIRST — faster and more reliable than fuzzy
                PatternHeuristics patterns = transformer.getPatternHeuristics();
                if (patterns != null) {
                    PatternHeuristics.PatternResult patternMatch = patterns.resolveField(owner, fieldName, desc);
                    if (patternMatch != null && patternMatch.confidence() >= 0.6) {
                        transformer.registerFieldRedirect(
                            owner, fieldName,
                            patternMatch.newOwner(), patternMatch.newName()
                        );
                        return new AppliedFix(
                            "NoSuchFieldError",
                            ownerDot + "." + fieldName,
                            "Pattern match -> " + patternMatch.newOwner().replace('/', '.') +
                                "." + patternMatch.newName() + " (rule: " + patternMatch.rule() + ")",
                            "field_redirect:" + owner + "." + fieldName
                        );
                    }
                }

                // Fallback to fuzzy resolver
                FuzzyMethodResolver fuzzy = transformer.getFuzzyResolver();
                if (fuzzy != null) {
                    FuzzyMethodResolver.FieldInfo resolved = fuzzy.resolveField(owner, fieldName, desc);
                    if (resolved != null) {
                        transformer.registerFieldRedirect(
                            owner, fieldName,
                            resolved.owner(), resolved.name()
                        );
                        return new AppliedFix(
                            "NoSuchFieldError",
                            ownerDot + "." + fieldName,
                            "Registered field redirect -> " + resolved.owner().replace('/', '.') +
                                "." + resolved.name(),
                            "field_redirect:" + owner + "." + fieldName
                        );
                    }
                }
                return new AppliedFix(
                    "NoSuchFieldError",
                    ownerDot + "." + fieldName,
                    "No replacement found — manual field redirect needed.",
                    "field_redirect:" + owner + "." + fieldName
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 3: AbstractMethodError (missing implementation)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Missing implementation of resolved method 'abstract void
        //    net.minecraft.X.extractContents(...)' in class com.example.MyBlock"
        // Parses: class, method, descriptor
        // Action: Log — injecting a no-op requires ASM and is deferred to retransform
        patterns.add(new ErrorPattern(
            "AbstractMethodError",
            Pattern.compile("(?:AbstractMethodError|Missing implementation of resolved method).*?'(?:abstract )?(?:(\\S+)\\s+)?(\\S+)\\.(\\w+)\\(([^)]*)\\)'"),
            (matcher, line, context, transformer) -> {
                String returnType = matcher.group(1) != null ? matcher.group(1) : "void";
                String ownerDot = matcher.group(2);
                String methodName = matcher.group(3);
                String paramTypes = matcher.group(4);

                return new AppliedFix(
                    "AbstractMethodError",
                    ownerDot + "." + methodName + "(" + paramTypes + ")",
                    "Missing abstract method implementation. A no-op stub should be injected " +
                        "via ASM during retransformation. Flag mod class for method injection.",
                    "abstract_method:" + ownerDot.replace('.', '/') + "." + methodName
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 4: VerifyError (bad type on operand stack)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "VerifyError: Bad type on operand stack
        //    Type 'net/minecraft/world/level/Level' is not assignable to 'net/minecraft/server/level/ServerLevel'"
        // Parses: class, method, expected type, actual type
        // Action: If caused by fuzzy match — blacklist. If field redirect — revert.
        patterns.add(new ErrorPattern(
            "VerifyError",
            Pattern.compile("VerifyError.*(?:Bad type on operand stack|Bad return type)"),
            (matcher, line, context, transformer) -> {
                // Look for type mismatch in context lines
                Pattern typePat = Pattern.compile("Type '(\\S+)' .*(?:is not assignable to|not assignable to) '(\\S+)'");
                Matcher typeMatcher = typePat.matcher(context);

                String actualType = "unknown";
                String expectedType = "unknown";
                if (typeMatcher.find()) {
                    actualType = typeMatcher.group(1);
                    expectedType = typeMatcher.group(2);
                }

                return new AppliedFix(
                    "VerifyError",
                    "Type mismatch: " + actualType + " vs " + expectedType,
                    "Bytecode verification failed. If caused by a fuzzy match, the match " +
                        "should be blacklisted. If caused by a field/method redirect, it should " +
                        "be reverted. Check the full stack trace for the offending redirect.",
                    "verify_error:" + actualType + ":" + expectedType
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 5: IncompatibleClassChangeError (class became interface)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "IncompatibleClassChangeError: Method 'X' must be InterfaceMethodref constant"
        //   OR: "Found class X, but interface was expected"
        // Parses: the class that needs to be treated as an interface
        // Action: Add the class to KNOWN_INTERFACES in RetroModTransformer
        //         so INVOKEVIRTUAL is rewritten to INVOKEINTERFACE
        patterns.add(new ErrorPattern(
            "IncompatibleClassChangeError",
            Pattern.compile("IncompatibleClassChangeError.*?(?:must be InterfaceMethodref|interface was expected|Expecting non-static method).*?(?:'(\\S+)'|(\\S+))"),
            (matcher, line, context, transformer) -> {
                // Extract the class name from the error message
                String rawRef = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);

                // The reference may be a full method ref like "net.minecraft.core.Registry.register"
                // We need just the class part
                String ownerDot = rawRef;
                int lastDot = rawRef.lastIndexOf('.');
                if (lastDot > 0) {
                    // Could be a method name after the last dot — check if it starts lowercase
                    String suffix = rawRef.substring(lastDot + 1);
                    if (suffix.length() > 0 && Character.isLowerCase(suffix.charAt(0))) {
                        ownerDot = rawRef.substring(0, lastDot);
                    }
                }

                String owner = ownerDot.replace('.', '/');

                // The real fix for this is adding to KNOWN_INTERFACES. Since that's a
                // static final Set, we can't modify it at runtime. Instead, we log the
                // fix and note that the class should be added to the set in source code.
                // The transformer already handles this check dynamically in visitMethodInsn.
                return new AppliedFix(
                    "IncompatibleClassChangeError",
                    ownerDot + " needs InterfaceMethodref",
                    "Class '" + ownerDot + "' became an interface in newer MC. " +
                        "Add '" + owner + "' to KNOWN_INTERFACES in RetroModTransformer. " +
                        "Alternatively, the transformer's INVOKEVIRTUAL->INVOKEINTERFACE " +
                        "rewrite should handle this at retransform time.",
                    "interface_change:" + owner
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 6: Mixin transformation failed
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Mixin transformation of net.minecraft.client.gui.screens.TitleScreen failed"
        //   OR: "Mixin apply failed ... in mixins.modid.json"
        // Parses: target class, mixin config
        // Action: Strip the mixin from the mod's mixin config JSON
        patterns.add(new ErrorPattern(
            "MixinTransformFailed",
            Pattern.compile("(?:Mixin (?:transformation|apply) (?:of|failed).*?((?:net\\.minecraft|com\\.mojang)\\S+)|Mixin apply.*?failed.*?(\\S+\\.json))"),
            (matcher, line, context, transformer) -> {
                String targetClass = matcher.group(1);
                String mixinConfig = matcher.group(2);

                String description = targetClass != null
                    ? "Mixin targeting " + targetClass + " failed"
                    : "Mixin config " + mixinConfig + " has failures";

                return new AppliedFix(
                    "MixinTransformFailed",
                    description,
                    "The mixin failed to apply, likely due to changed MC internals. " +
                        "Strip the failing mixin from the mod's mixin config JSON during " +
                        "retransformation. The mod may lose some features but won't crash.",
                    "mixin_strip:" + (targetClass != null ? targetClass : mixinConfig)
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 7: InvalidMixinException (super class not found)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Super class 'net.minecraft.X' of com.example.mixin.MyMixin was not found
        //    in the hierarchy of target class 'net.minecraft.Y'"
        // Parses: mixin class
        // Action: Remove the mixin class entry from the mixin config JSON
        patterns.add(new ErrorPattern(
            "InvalidMixinSuperClass",
            Pattern.compile("Super class '(\\S+)' of (\\S+) was not found"),
            (matcher, line, context, transformer) -> {
                String superClass = matcher.group(1);
                String mixinClass = matcher.group(2);

                return new AppliedFix(
                    "InvalidMixinSuperClass",
                    "Mixin " + mixinClass + " has missing super class " + superClass,
                    "Remove mixin class '" + mixinClass + "' from the mod's mixin config JSON. " +
                        "The super class was renamed or removed in this MC version.",
                    "mixin_remove:" + mixinClass
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 8: InvalidAccessorException (@Invoker/@Accessor not found)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "No candidates were found matching methodName(desc) in
        //    net.minecraft.X for com.example.mixin.MyMixin"
        // Parses: mixin class, target method
        // Action: Already handled by required=false, log for debugging
        patterns.add(new ErrorPattern(
            "InvalidAccessorException",
            Pattern.compile("No candidates were found matching (\\w+)\\(([^)]*)\\).*?(?:in (\\S+) for (\\S+)|(\\S+))"),
            (matcher, line, context, transformer) -> {
                String methodName = matcher.group(1);
                String params = matcher.group(2);
                String targetClass = matcher.group(3) != null ? matcher.group(3) : "unknown";
                String mixinClass = matcher.group(4) != null ? matcher.group(4) :
                    (matcher.group(5) != null ? matcher.group(5) : "unknown");

                return new AppliedFix(
                    "InvalidAccessorException",
                    "Accessor " + methodName + "(" + params + ") not found in " + targetClass,
                    "Mixin @Invoker/@Accessor target was renamed or removed. " +
                        "If the accessor is required=true, strip the mixin. " +
                        "If required=false (default for accessors), this is just a warning.",
                    "accessor_missing:" + targetClass + "." + methodName
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 9: InvalidInjectionException (descriptor mismatch)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Invalid descriptor on ... Expected (Lnet/minecraft/X;)V
        //    but found (Lnet/minecraft/Y;)V"
        // Parses: mixin class, expected vs actual descriptor
        // Action: Strip the @Inject method or the whole mixin
        patterns.add(new ErrorPattern(
            "InvalidInjectionException",
            Pattern.compile("Invalid (?:descriptor|injection).*?Expected \\(([^)]*)\\).*?(?:but )?found \\(([^)]*)\\)"),
            (matcher, line, context, transformer) -> {
                String expected = matcher.group(1);
                String actual = matcher.group(2);

                // Try to extract the mixin class from context
                String mixinClass = "unknown";
                Pattern mixinPat = Pattern.compile("in (\\S+Mixin\\S*)");
                Matcher mixinMatcher = mixinPat.matcher(context);
                if (mixinMatcher.find()) {
                    mixinClass = mixinMatcher.group(1);
                }

                return new AppliedFix(
                    "InvalidInjectionException",
                    "Descriptor mismatch in " + mixinClass + ": expected (" + expected + ") vs found (" + actual + ")",
                    "Strip the @Inject method with the mismatched descriptor from the mixin, " +
                        "or strip the entire mixin if it has no other useful injections.",
                    "injection_mismatch:" + mixinClass + ":" + expected
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 10: ClassNotFoundException / NoClassDefFoundError
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NoClassDefFoundError: com/teamresourceful/resourcefulconfig/api/ConfigEntry"
        //   OR: "ClassNotFoundException: com.teamresourceful.resourcefulconfig.api.ConfigEntry"
        // Parses: missing class
        // Action: Log as missing dependency — cannot fix without the library
        patterns.add(new ErrorPattern(
            "ClassNotFound",
            Pattern.compile("(?:NoClassDefFoundError|ClassNotFoundException):\\s*'?(\\S+?)(?:'|$)"),
            (matcher, line, context, transformer) -> {
                String missingClass = matcher.group(1).replace('/', '.');

                // Determine if this is a mod dependency vs MC internal
                boolean isMcClass = missingClass.startsWith("net.minecraft.") ||
                                    missingClass.startsWith("com.mojang.");
                boolean isFabricApi = missingClass.startsWith("net.fabricmc.fabric.api.");

                String action;
                if (isMcClass) {
                    action = "MC class was removed or relocated. Check if a class redirect " +
                        "or polyfill exists for this class. May need a new shim.";
                } else if (isFabricApi) {
                    action = "Fabric API class missing. The mod may need a newer Fabric API, " +
                        "or the API module was removed. Check polyfill providers.";
                } else {
                    action = "Missing mod dependency. User must install the required library " +
                        "mod. RetroMod cannot fix missing third-party dependencies.";
                }

                return new AppliedFix(
                    "ClassNotFound",
                    "Missing class: " + missingClass,
                    action,
                    "class_missing:" + missingClass
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 11: IllegalAccessError (private field/method)
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "IllegalAccessError: tried to access private field net.minecraft.X.Y from class Z"
        //   OR: "IllegalAccessError: class Z tried to access private method 'void X.Y()'"
        // Parses: owner, member name
        // Action: Flag for reflection bridge generation using setAccessible(true)
        patterns.add(new ErrorPattern(
            "IllegalAccessError",
            Pattern.compile("IllegalAccessError:.*?(?:tried to access (?:private|protected) (?:field|method)).*?((?:net\\.minecraft|com\\.mojang)\\S+)\\.(\\w+)"),
            (matcher, line, context, transformer) -> {
                String ownerDot = matcher.group(1);
                String memberName = matcher.group(2);

                return new AppliedFix(
                    "IllegalAccessError",
                    "Cannot access " + ownerDot + "." + memberName,
                    "Field/method became private in newer MC. Generate a reflection bridge " +
                        "that uses setAccessible(true) to access the member. Alternatively, " +
                        "use an AccessWidener or AccessTransformer.",
                    "access_error:" + ownerDot.replace('.', '/') + "." + memberName
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 12: ClassCastException
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "ClassCastException: class net.minecraft.world.level.Level cannot
        //    be cast to class net.minecraft.server.level.ServerLevel"
        // Parses: source type, target type
        // Action: If from constructor redirect CHECKCAST — fix the CHECKCAST type
        patterns.add(new ErrorPattern(
            "ClassCastException",
            Pattern.compile("ClassCastException:.*?(?:class )?(\\S+) cannot be cast to (?:class )?(\\S+)"),
            (matcher, line, context, transformer) -> {
                String sourceType = matcher.group(1).replace('.', '/');
                String targetType = matcher.group(2).replace('.', '/');

                return new AppliedFix(
                    "ClassCastException",
                    sourceType.replace('/', '.') + " cannot be cast to " + targetType.replace('/', '.'),
                    "Type hierarchy changed in newer MC. If this is from a constructor redirect " +
                        "CHECKCAST, fix the cast type. If from a method redirect, the return type " +
                        "changed and the redirect needs updating.",
                    "cast_error:" + sourceType + ":" + targetType
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 13: NegativeArraySizeException in ASM
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NegativeArraySizeException... computeAllFrames"
        //   OR: "NegativeArraySizeException in ClassWriter" (usually in stack trace)
        // Parses: the class being processed
        // Action: Strip the mixin causing frame computation to fail
        patterns.add(new ErrorPattern(
            "NegativeArraySizeException",
            Pattern.compile("NegativeArraySizeException"),
            (matcher, line, context, transformer) -> {
                // Try to find the class being processed from the context
                String targetClass = "unknown";
                Pattern classPat = Pattern.compile("(?:processing|transforming|visiting)\\s+(\\S+)");
                Matcher classMatcher = classPat.matcher(context);
                if (classMatcher.find()) {
                    targetClass = classMatcher.group(1);
                }

                return new AppliedFix(
                    "NegativeArraySizeException",
                    "ASM frame computation failed" + (targetClass.equals("unknown") ? "" : " for " + targetClass),
                    "ASM's computeAllFrames() crashed, likely because a mixin references " +
                        "classes that don't exist in this MC version. Strip the mixin that " +
                        "targets this class, or use COMPUTE_MAXS instead of COMPUTE_FRAMES.",
                    "asm_frames:" + targetClass
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 14: EntryPoint failure
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Could not execute entrypoint stage 'client' due to errors, provided by 'modid'"
        // Parses: mod ID, entrypoint stage
        // Action: Flag the entrypoint for try-catch wrapping if not already wrapped
        patterns.add(new ErrorPattern(
            "EntryPointFailure",
            Pattern.compile("Could not execute entrypoint stage '(\\w+)'.*?provided by '(\\S+)'"),
            (matcher, line, context, transformer) -> {
                String stage = matcher.group(1);
                String modId = matcher.group(2);

                return new AppliedFix(
                    "EntryPointFailure",
                    "Entrypoint '" + stage + "' failed for mod '" + modId + "'",
                    "The mod's entrypoint crashed during initialization. The entrypoint " +
                        "class should be wrapped in try-catch during retransformation. " +
                        "Check the log for the root cause (often a follow-on error).",
                    "entrypoint_fail:" + modId + ":" + stage
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 15: SoundEvent constructor removed
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NoSuchMethodError: 'void net.minecraft.sounds.SoundEvent.<init>(net.minecraft.resources.Identifier)'"
        // Action: Register constructor redirect to SoundEvent.createVariableRangeEvent()
        patterns.add(new ErrorPattern(
            "SoundEventConstructor",
            Pattern.compile("NoSuchMethodError.*SoundEvent\\.<init>\\(.*(?:Identifier|ResourceLocation)"),
            (matcher, line, context, transformer) -> {
                // SoundEvent(Identifier) -> SoundEvent.createVariableRangeEvent(Identifier)
                transformer.registerConstructorRedirect(
                    "net/minecraft/sounds/SoundEvent",
                    "(Lnet/minecraft/resources/Identifier;)V",
                    "net/minecraft/sounds/SoundEvent",
                    "createVariableRangeEvent",
                    "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/sounds/SoundEvent;"
                );

                return new AppliedFix(
                    "SoundEventConstructor",
                    "SoundEvent constructor removed",
                    "Registered constructor redirect: new SoundEvent(Identifier) -> " +
                        "SoundEvent.createVariableRangeEvent(Identifier)",
                    "ctor_redirect:SoundEvent"
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 16: ResourceLocation constructor removed
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NoSuchMethodError: 'void net.minecraft.resources.ResourceLocation.<init>(java.lang.String, java.lang.String)'"
        //   OR: "NoSuchMethodError: ... Identifier.<init>(String)"
        // Action: Register constructor redirect to fromNamespaceAndPath() or parse()
        patterns.add(new ErrorPattern(
            "ResourceLocationConstructor",
            Pattern.compile("NoSuchMethodError.*(?:ResourceLocation|Identifier)\\.<init>\\(.*String"),
            (matcher, line, context, transformer) -> {
                boolean twoArgs = line.contains("String, java.lang.String") ||
                                  line.contains("String,String") ||
                                  line.contains("String, String");

                if (twoArgs) {
                    // ResourceLocation(String, String) -> Identifier.fromNamespaceAndPath(String, String)
                    transformer.registerConstructorRedirect(
                        "net/minecraft/resources/Identifier",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        "net/minecraft/resources/Identifier",
                        "fromNamespaceAndPath",
                        "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
                    );
                    // Also register for the old ResourceLocation name (pre-26.1 remapping)
                    transformer.registerConstructorRedirect(
                        "net/minecraft/resources/ResourceLocation",
                        "(Ljava/lang/String;Ljava/lang/String;)V",
                        "net/minecraft/resources/Identifier",
                        "fromNamespaceAndPath",
                        "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
                    );
                    return new AppliedFix(
                        "ResourceLocationConstructor",
                        "ResourceLocation(String, String) constructor removed",
                        "Registered constructor redirect -> Identifier.fromNamespaceAndPath(String, String)",
                        "ctor_redirect:ResourceLocation:2arg"
                    );
                } else {
                    // ResourceLocation(String) -> Identifier.parse(String)
                    transformer.registerConstructorRedirect(
                        "net/minecraft/resources/Identifier",
                        "(Ljava/lang/String;)V",
                        "net/minecraft/resources/Identifier",
                        "parse",
                        "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
                    );
                    transformer.registerConstructorRedirect(
                        "net/minecraft/resources/ResourceLocation",
                        "(Ljava/lang/String;)V",
                        "net/minecraft/resources/Identifier",
                        "parse",
                        "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
                    );
                    return new AppliedFix(
                        "ResourceLocationConstructor",
                        "ResourceLocation(String) constructor removed",
                        "Registered constructor redirect -> Identifier.parse(String)",
                        "ctor_redirect:ResourceLocation:1arg"
                    );
                }
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 17: Registry.register InterfaceMethodref
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "IncompatibleClassChangeError: ... must be InterfaceMethodref... Registry.register"
        // Action: Confirm Registry is in KNOWN_INTERFACES
        patterns.add(new ErrorPattern(
            "RegistryInterfaceMethodref",
            Pattern.compile("(?:IncompatibleClassChangeError|InterfaceMethodref).*Registry"),
            (matcher, line, context, transformer) -> {
                return new AppliedFix(
                    "RegistryInterfaceMethodref",
                    "Registry.register must use InterfaceMethodref",
                    "Registry became an interface in newer MC. Ensure " +
                        "'net/minecraft/core/Registry' is in KNOWN_INTERFACES. " +
                        "The INVOKEVIRTUAL -> INVOKEINTERFACE rewrite handles this.",
                    "interface_change:net/minecraft/core/Registry"
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 18: Namespace mismatch in accesswidener
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Namespace (intermediary) does not match current runtime namespace (official)"
        // Parses: the namespace issue
        // Action: Flag the mod's access widener for re-remapping
        patterns.add(new ErrorPattern(
            "NamespaceMismatch",
            Pattern.compile("Namespace \\((\\w+)\\) does not match (?:current )?runtime namespace \\((\\w+)\\)"),
            (matcher, line, context, transformer) -> {
                String modNamespace = matcher.group(1);   // e.g., "intermediary"
                String runtimeNamespace = matcher.group(2); // e.g., "official"

                // Try to find the mod name from context
                String modName = "unknown";
                Pattern modPat = Pattern.compile("(?:for|from|in) (?:mod )?'?(\\S+?)'?(?:\\s|$|:)");
                Matcher modMatcher = modPat.matcher(context);
                if (modMatcher.find()) {
                    modName = modMatcher.group(1);
                }

                return new AppliedFix(
                    "NamespaceMismatch",
                    "Access widener uses " + modNamespace + " but runtime expects " + runtimeNamespace,
                    "The mod's access widener or class tweaker uses intermediary names but " +
                        "MC 26.1 uses official names. Re-remap the access widener file during " +
                        "retransformation using IntermediaryToMojangMapper.",
                    "namespace_mismatch:" + modName
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 19: Missing mod dependency
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "Mod 'sodium' is a required dependency of 'iris' but it is not installed"
        //   OR: "Unmet dependency: modid requires fabricapi >= 0.90.0"
        // Parses: missing mod, requiring mod
        // Action: Log — user must install the dependency
        patterns.add(new ErrorPattern(
            "MissingModDependency",
            Pattern.compile("(?:'(\\S+)' is a (?:required )?dependency of '(\\S+)'.*not installed|Unmet dependency.*?(\\S+) requires (\\S+))"),
            (matcher, line, context, transformer) -> {
                String missingMod, requiringMod;
                if (matcher.group(1) != null) {
                    missingMod = matcher.group(1);
                    requiringMod = matcher.group(2);
                } else {
                    requiringMod = matcher.group(3);
                    missingMod = matcher.group(4);
                }

                return new AppliedFix(
                    "MissingModDependency",
                    "Mod '" + requiringMod + "' requires '" + missingMod + "' (not installed)",
                    "The user must install the '" + missingMod + "' mod. RetroMod cannot " +
                        "fix missing third-party mod dependencies. Check Modrinth or CurseForge " +
                        "for a compatible version.",
                    "dep_missing:" + missingMod + ":" + requiringMod
                );
            }
        ));

        // ─────────────────────────────────────────────────────────────────
        // Pattern 20: Util.backgroundExecutor removed
        // ─────────────────────────────────────────────────────────────────
        // Example input:
        //   "NoSuchMethodError: 'java.util.concurrent.ExecutorService
        //    net.minecraft.Util.backgroundExecutor()'"
        // Action: Register redirect to a bridge method
        patterns.add(new ErrorPattern(
            "UtilBackgroundExecutor",
            Pattern.compile("NoSuchMethodError.*Util\\.backgroundExecutor"),
            (matcher, line, context, transformer) -> {
                // Redirect Util.backgroundExecutor() to a shim bridge
                // The bridge creates a simple fixed thread pool as a replacement
                transformer.registerMethodRedirect(
                    "net/minecraft/Util", "backgroundExecutor",
                    "()Ljava/util/concurrent/ExecutorService;",
                    "net/minecraft/Util", "nonCriticalIoPool",
                    "()Ljava/util/concurrent/ExecutorService;"
                );

                return new AppliedFix(
                    "UtilBackgroundExecutor",
                    "Util.backgroundExecutor() removed",
                    "Registered method redirect: Util.backgroundExecutor() -> Util.nonCriticalIoPool(). " +
                        "The nonCriticalIoPool() is the closest equivalent in newer MC.",
                    "method_redirect:Util.backgroundExecutor"
                );
            }
        ));
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PERSISTENCE — save/load fixes between launches
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Save all applied fixes to config/retromod/auto-fixes.json.
     */
    private void persistFixes() {
        try {
            // Ensure config directory exists
            Path configDir = FIXES_FILE.getParent();
            if (configDir != null) {
                Files.createDirectories(configDir);
                // Symlink guard: if config/retromod/ is a symlink into some
                // sensitive location (e.g. ~/.ssh) a pre-planted target would
                // get overwritten with our JSON. We explicitly reject that.
                com.retromod.util.ZipSecurity.validateNotSymlink(configDir);
            }
            // Likewise if the fixes file itself is already a symlink, refuse
            // to follow it. writeString() would otherwise clobber the target.
            if (Files.exists(FIXES_FILE)) {
                com.retromod.util.ZipSecurity.validateNotSymlink(FIXES_FILE);
            }

            List<PersistedFix> toSave = new ArrayList<>();

            // Save applied fixes
            for (AppliedFix fix : appliedFixes) {
                toSave.add(new PersistedFix(
                    fix.fixKey, fix.errorType, fix.description, fix.action, false
                ));
            }

            // Save blacklisted keys
            for (String key : blacklist) {
                toSave.add(new PersistedFix(key, "blacklisted", key, "blacklisted", true));
            }

            String json = GSON.toJson(toSave);
            Files.writeString(FIXES_FILE, json);
            LOGGER.info("[AutoFix] Persisted {} fix(es) to {}", toSave.size(), FIXES_FILE);
        } catch (IOException e) {
            LOGGER.warn("[AutoFix] Could not persist fixes: {}", e.getMessage());
        }
    }

    /**
     * Load previously persisted fixes to detect recurrence.
     */
    private void loadPreviousFixes() {
        if (!Files.exists(FIXES_FILE)) return;

        try {
            String json = Files.readString(FIXES_FILE);
            Type listType = new TypeToken<List<PersistedFix>>() {}.getType();
            List<PersistedFix> saved = GSON.fromJson(json, listType);

            if (saved != null) {
                for (PersistedFix fix : saved) {
                    if (fix.blacklisted) {
                        blacklist.add(fix.fixKey);
                    } else {
                        previousRunFixKeys.add(fix.fixKey);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("[AutoFix] Could not load previous fixes: {}", e.getMessage());
        }
    }

    /**
     * Apply a persisted fix to the transformer. Only handles fix types that
     * directly register redirects; other fix types are informational only.
     */
    private boolean applyPersistedFix(PersistedFix fix, RetroModTransformer transformer) {
        // Only certain fix types can be mechanically re-applied
        if (fix.fixKey == null) return false;

        if (fix.fixKey.startsWith("ctor_redirect:SoundEvent")) {
            transformer.registerConstructorRedirect(
                "net/minecraft/sounds/SoundEvent",
                "(Lnet/minecraft/resources/Identifier;)V",
                "net/minecraft/sounds/SoundEvent",
                "createVariableRangeEvent",
                "(Lnet/minecraft/resources/Identifier;)Lnet/minecraft/sounds/SoundEvent;"
            );
            return true;
        }

        if (fix.fixKey.equals("ctor_redirect:ResourceLocation:1arg")) {
            transformer.registerConstructorRedirect(
                "net/minecraft/resources/Identifier",
                "(Ljava/lang/String;)V",
                "net/minecraft/resources/Identifier",
                "parse",
                "(Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
            );
            return true;
        }

        if (fix.fixKey.equals("ctor_redirect:ResourceLocation:2arg")) {
            transformer.registerConstructorRedirect(
                "net/minecraft/resources/Identifier",
                "(Ljava/lang/String;Ljava/lang/String;)V",
                "net/minecraft/resources/Identifier",
                "fromNamespaceAndPath",
                "(Ljava/lang/String;Ljava/lang/String;)Lnet/minecraft/resources/Identifier;"
            );
            return true;
        }

        if (fix.fixKey.equals("method_redirect:Util.backgroundExecutor")) {
            transformer.registerMethodRedirect(
                "net/minecraft/Util", "backgroundExecutor",
                "()Ljava/util/concurrent/ExecutorService;",
                "net/minecraft/Util", "nonCriticalIoPool",
                "()Ljava/util/concurrent/ExecutorService;"
            );
            return true;
        }

        // Informational fixes (ClassNotFound, MissingModDependency, etc.)
        // are logged but don't register redirects
        LOGGER.debug("[AutoFix] Persisted fix '{}' is informational only, not re-applied", fix.fixKey);
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // UTILITY METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Build a context string from surrounding log lines (for multi-line errors).
     * Includes the current line plus up to {@code lookAhead} lines after it.
     */
    private String buildContext(List<String> lines, int currentIndex, int lookAhead) {
        StringBuilder sb = new StringBuilder();
        int end = Math.min(currentIndex + lookAhead + 1, lines.size());
        for (int i = currentIndex; i < end; i++) {
            sb.append(lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    /**
     * Convert human-readable Java type names to JVM descriptor format.
     *
     * Examples:
     *   "int" -> "(I)V"
     *   "int, java.lang.String" -> "(ILjava/lang/String;)V"
     *   "void" return -> "V"
     *
     * @param paramTypes comma-separated parameter type names
     * @param returnType return type name
     * @return JVM method descriptor string
     */
    static String buildDescriptor(String paramTypes, String returnType) {
        StringBuilder desc = new StringBuilder("(");

        if (paramTypes != null && !paramTypes.trim().isEmpty()) {
            String[] params = paramTypes.split(",");
            for (String param : params) {
                desc.append(typeNameToDescriptor(param.trim()));
            }
        }

        desc.append(")");
        desc.append(typeNameToDescriptor(returnType.trim()));
        return desc.toString();
    }

    /**
     * Convert a single Java type name to its JVM descriptor.
     *
     * Handles primitives (int->I, void->V, etc.), arrays (int[]->I[]),
     * and reference types (com.example.Foo -> Lcom/example/Foo;).
     */
    static String typeNameToDescriptor(String typeName) {
        if (typeName == null || typeName.isEmpty()) return "V";

        // Handle arrays
        int arrayDepth = 0;
        String base = typeName;
        while (base.endsWith("[]")) {
            arrayDepth++;
            base = base.substring(0, base.length() - 2);
        }

        String baseDesc = switch (base) {
            case "void" -> "V";
            case "boolean" -> "Z";
            case "byte" -> "B";
            case "char" -> "C";
            case "short" -> "S";
            case "int" -> "I";
            case "long" -> "J";
            case "float" -> "F";
            case "double" -> "D";
            default -> "L" + base.replace('.', '/') + ";";
        };

        if (arrayDepth > 0) {
            return "[".repeat(arrayDepth) + baseDesc;
        }
        return baseDesc;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA TYPES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Represents a fix that was applied (or suggested) by the auto-fix engine.
     *
     * @param errorType   category of the error (e.g., "NoSuchMethodError")
     * @param description human-readable description of the error
     * @param action      what was done (or should be done) to fix it
     * @param fixKey      unique key for deduplication and blacklisting
     */
    public record AppliedFix(String errorType, String description, String action, String fixKey) {
        @Override
        public String toString() {
            return "[" + errorType + "] " + description + " => " + action;
        }
    }

    /**
     * Persisted fix stored in auto-fixes.json between launches.
     * Uses a class (not record) for Gson deserialization compatibility.
     */
    static class PersistedFix {
        String fixKey;
        String errorType;
        String description;
        String action;
        boolean blacklisted;

        /** No-arg constructor for Gson. */
        PersistedFix() {}

        PersistedFix(String fixKey, String errorType, String description,
                     String action, boolean blacklisted) {
            this.fixKey = fixKey;
            this.errorType = errorType;
            this.description = description;
            this.action = action;
            this.blacklisted = blacklisted;
        }
    }

    /**
     * An error pattern: a compiled regex plus a fix action.
     *
     * <p>Each pattern has:</p>
     * <ul>
     *   <li>{@code name} — human-readable name for logging</li>
     *   <li>{@code regex} — compiled regex that matches the error line</li>
     *   <li>{@code fixAction} — lambda that parses the match and applies a fix</li>
     * </ul>
     */
    static class ErrorPattern {
        final String name;
        final Pattern regex;
        final FixAction fixAction;

        ErrorPattern(String name, Pattern regex, FixAction fixAction) {
            this.name = name;
            this.regex = regex;
            this.fixAction = fixAction;
        }

        /**
         * Build a unique key for this error match, used for deduplication
         * and blacklisting. Defaults to name + matched text hash.
         *
         * NOTE (LOW): Using hashCode() has collision potential — two different
         * error lines could hash to the same fix key, causing a legitimate fix
         * to be skipped. In practice this is unlikely given the pattern-specific
         * name prefix, but for maximum correctness consider using the matched
         * text directly (truncated to a reasonable length) instead of its hash.
         */
        String buildFixKey(Matcher matcher, String line) {
            // Use the full match as part of the key for uniqueness
            return name + ":" + matcher.group(0).hashCode();
        }

        /**
         * Apply the fix and return a description of what was done.
         *
         * @param matcher     the regex matcher with captured groups
         * @param line        the full log line that matched
         * @param context     surrounding lines for multi-line errors
         * @param transformer the transformer to register fixes on
         * @return the applied fix, or null if no fix could be computed
         */
        AppliedFix apply(Matcher matcher, String line, String context,
                         RetroModTransformer transformer) {
            try {
                AppliedFix fix = fixAction.apply(matcher, line, context, transformer);
                if (fix != null) {
                    return fix;
                }
            } catch (Exception e) {
                LOGGER.warn("[AutoFix] Error applying fix for pattern '{}': {}", name, e.getMessage());
            }
            return null;
        }

        /**
         * Describe what fix would be applied without actually applying it.
         * Used for dry-run / CLI analysis mode.
         */
        AppliedFix describe(Matcher matcher, String line, String context) {
            try {
                // Call the fix action with a null transformer — patterns that
                // register redirects will produce an AppliedFix but the redirect
                // won't actually be registered since transformer is null.
                // Patterns that require the transformer will return a descriptive fix.
                return fixAction.apply(matcher, line, context, null);
            } catch (NullPointerException e) {
                // Expected for patterns that call transformer methods
                return new AppliedFix(name, line.trim(), "Fix requires transformer (would apply at runtime)", name);
            } catch (Exception e) {
                return new AppliedFix(name, line.trim(), "Analysis error: " + e.getMessage(), name);
            }
        }
    }

    /**
     * Functional interface for the fix action lambda.
     */
    @FunctionalInterface
    interface FixAction {
        /**
         * Parse the error match and apply a fix.
         *
         * @param matcher     regex matcher with captured groups
         * @param line        full log line
         * @param context     surrounding lines (current + next 5)
         * @param transformer the transformer (may be null in dry-run mode)
         * @return description of the fix, or null if no fix could be applied
         */
        AppliedFix apply(Matcher matcher, String line, String context,
                         RetroModTransformer transformer);
    }
}
