/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Pattern-based heuristics for resolving unknown API changes between MC versions.
 *
 * These rules encode deterministic naming conventions discovered by analyzing
 * Minecraft version transitions. They are NOT guesses — each rule represents a
 * documented, repeatable API change pattern (e.g., "all render* methods in the
 * GUI package became extract* methods in 26.1").
 *
 * This sits between the hardcoded shim redirect tables and the fuzzy resolver:
 *   1. Shim redirect tables (exact match — highest confidence)
 *   2. PatternHeuristics (pattern-based — medium-high confidence)
 *   3. FuzzyMethodResolver (similarity search — lower confidence)
 *
 * Pattern rules are checked BEFORE fuzzy resolution because they are faster
 * (simple string comparisons vs. JAR scanning) and more reliable (deterministic
 * rules vs. probabilistic matching).
 */
package com.retromod.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Applies known naming pattern transformations to resolve method, class, and field
 * references that are not covered by hardcoded shim redirect tables.
 *
 * <h2>Design</h2>
 * Rules are stored in fixed-size arrays (not growable lists) because the rule set
 * is fully defined at construction time and never modified afterward. Each rule is
 * a functional interface that returns a {@link PatternResult} on match or null on
 * miss. Rules are checked in registration order; the first match wins.
 *
 * <h2>Confidence Scores</h2>
 * Each rule carries a confidence score from 0.0 to 1.0:
 * <ul>
 *   <li><b>0.95-0.99:</b> Exact naming convention shifts verified across multiple versions</li>
 *   <li><b>0.85-0.90:</b> Broad patterns with known exceptions (e.g., NoCull suffix removal)</li>
 *   <li><b>0.60-0.70:</b> Heuristic guesses that work most of the time (e.g., getX to x accessor)</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe. Rule arrays are immutable after construction, and
 * match counters use {@link AtomicInteger} for lock-free concurrent updates.
 *
 * <p><b>IMPORTANT:</b> This class must NOT reference {@code RetroMod} directly
 * because the transformer is also used by the standalone CLI where Fabric classes
 * are not on the classpath.</p>
 */
public class PatternHeuristics {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Patterns");

    // ═══════════════════════════════════════════════════════════════════════
    // PATTERN RESULT — immutable record returned when a rule matches
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Result of a successful pattern match.
     *
     * @param newOwner    the redirected class (JVM internal name with '/')
     * @param newName     the redirected method/field name (null for class-only matches)
     * @param newDescriptor the redirected descriptor (null for class-only matches)
     * @param confidence  how reliable this match is (0.0-1.0)
     * @param rule        short identifier for the rule that matched (for logging)
     * @param explanation human-readable explanation of why this redirect exists
     */
    public record PatternResult(
        String newOwner,
        String newName,
        String newDescriptor,
        double confidence,
        String rule,
        String explanation
    ) {}

    // ═══════════════════════════════════════════════════════════════════════
    // RULE STORAGE — immutable arrays populated at construction time
    // ═══════════════════════════════════════════════════════════════════════

    // Using arrays instead of Lists because the rule set is fixed after construction.
    // This eliminates ArrayList overhead and iterator allocation on every resolve call.
    private final PatternRule[] methodRules;
    private final ClassPatternRule[] classRules;
    private final PatternRule[] fieldRules;

    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICS — atomic counters for thread-safe concurrent access
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Safely rename GuiGraphics→GuiGraphicsExtractor without double-applying.
     * Plain String.replace("GuiGraphics","GuiGraphicsExtractor") will match the
     * prefix of an already-renamed "GuiGraphicsExtractor", producing the broken
     * "GuiGraphicsExtractorExtractor". This method avoids that.
     */
    private static String renameGuiGraphics(String s) {
        if (s == null || !s.contains("GuiGraphics")) return s;
        // Already renamed — don't touch
        if (s.contains("GuiGraphicsExtractor")) return s;
        return s.replace("GuiGraphics", "GuiGraphicsExtractor");
    }

    private final AtomicInteger methodMatches = new AtomicInteger(0);
    private final AtomicInteger classMatches = new AtomicInteger(0);
    private final AtomicInteger fieldMatches = new AtomicInteger(0);
    private final AtomicInteger totalAttempts = new AtomicInteger(0);

    /**
     * Constructs a new PatternHeuristics engine with all known rules pre-registered.
     * Rules are built once and stored in immutable arrays for fast iteration.
     */
    public PatternHeuristics() {
        // Build rules into temporary lists, then freeze into arrays
        List<PatternRule> methods = new ArrayList<>();
        List<ClassPatternRule> classes = new ArrayList<>();
        List<PatternRule> fields = new ArrayList<>();

        registerMethodPatterns(methods);
        registerClassPatterns(classes);
        registerFieldPatterns(fields);

        this.methodRules = methods.toArray(new PatternRule[0]);
        this.classRules = classes.toArray(new ClassPatternRule[0]);
        this.fieldRules = fields.toArray(new PatternRule[0]);

        LOGGER.debug("Loaded {} method rules, {} class rules, {} field rules",
            methodRules.length, classRules.length, fieldRules.length);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Try to resolve an unknown method reference using pattern heuristics.
     *
     * <p>Rules are checked in registration order. The first matching rule wins.
     * If no rule matches, returns null — the caller should fall through to
     * {@link FuzzyMethodResolver} as a last resort.</p>
     *
     * @param owner      the class containing the method (JVM internal name)
     * @param name       the method name
     * @param descriptor the method descriptor (e.g., "(DDI)Z")
     * @return the pattern match result, or null if no pattern applies
     */
    public PatternResult resolveMethod(String owner, String name, String descriptor) {
        totalAttempts.incrementAndGet();
        for (PatternRule rule : methodRules) {
            PatternResult result = rule.tryMatch(owner, name, descriptor);
            if (result != null) {
                methodMatches.incrementAndGet();
                LOGGER.debug("Pattern match: {}.{}{} -> {}.{}{} (rule: {}, confidence: {})",
                    owner, name, descriptor,
                    result.newOwner(), result.newName(), result.newDescriptor(),
                    result.rule(), result.confidence());
                return result;
            }
        }
        return null;
    }

    /**
     * Try to resolve an unknown class reference using pattern heuristics.
     *
     * <p>Handles package relocations (e.g., GeckoLib moving from software.bernie to
     * com.geckolib) and class renames (e.g., GuiGraphics to GuiGraphicsExtractor).</p>
     *
     * @param className the class to resolve (JVM internal name with '/')
     * @return the pattern match result, or null if no pattern applies
     */
    public PatternResult resolveClass(String className) {
        totalAttempts.incrementAndGet();
        for (ClassPatternRule rule : classRules) {
            PatternResult result = rule.tryMatch(className);
            if (result != null) {
                classMatches.incrementAndGet();
                LOGGER.debug("Class pattern match: {} -> {} (rule: {})",
                    className, result.newOwner(), result.rule());
                return result;
            }
        }
        return null;
    }

    /**
     * Try to resolve an unknown field reference using pattern heuristics.
     *
     * @param owner      the class containing the field (JVM internal name)
     * @param name       the field name
     * @param descriptor the field descriptor (e.g., "J" for long)
     * @return the pattern match result, or null if no pattern applies
     */
    public PatternResult resolveField(String owner, String name, String descriptor) {
        totalAttempts.incrementAndGet();
        for (PatternRule rule : fieldRules) {
            PatternResult result = rule.tryMatch(owner, name, descriptor);
            if (result != null) {
                fieldMatches.incrementAndGet();
                LOGGER.debug("Field pattern match: {}.{} -> {}.{} (rule: {})",
                    owner, name, result.newOwner(), result.newName(), result.rule());
                return result;
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATISTICS ACCESSORS
    // ═══════════════════════════════════════════════════════════════════════

    /** Number of successful method pattern matches. */
    public int getMethodMatches() { return methodMatches.get(); }
    /** Number of successful class pattern matches. */
    public int getClassMatches() { return classMatches.get(); }
    /** Number of successful field pattern matches. */
    public int getFieldMatches() { return fieldMatches.get(); }
    /** Total number of resolve attempts (methods + classes + fields). */
    public int getTotalAttempts() { return totalAttempts.get(); }
    /** Total number of successful matches across all categories. */
    public int getTotalMatches() { return methodMatches.get() + classMatches.get() + fieldMatches.get(); }

    // ═══════════════════════════════════════════════════════════════════════
    // METHOD PATTERN RULES
    // Each rule encodes a specific MC version API change pattern.
    // ═══════════════════════════════════════════════════════════════════════

    private void registerMethodPatterns(List<PatternRule> rules) {

        // ── 26.1 Render Verb Migration ─────────────────────────────────
        // MC 26.1 redesigned the rendering pipeline to use an "extract-then-submit"
        // model. All render*() methods in GUI classes became extract*RenderState()
        // methods, and GuiGraphics was renamed to GuiGraphicsExtractor.

        // Screen.render(GuiGraphics,...) -> Screen.extractRenderState(GuiGraphicsExtractor,...)
        rules.add((owner, name, desc) -> {
            if (name.equals("render") && isScreenClass(owner) && desc.contains("GuiGraphics")) {
                return new PatternResult(owner, "extractRenderState",
                    renameGuiGraphics(desc),
                    0.95, "render->extractRenderState",
                    "26.1 renamed Screen.render to extractRenderState");
            }
            return null;
        });

        // renderWidget(GuiGraphics,...) -> extractWidgetRenderState(GuiGraphicsExtractor,...)
        rules.add((owner, name, desc) -> {
            if (name.equals("renderWidget") && desc.contains("GuiGraphics")) {
                return new PatternResult(owner, "extractWidgetRenderState",
                    renameGuiGraphics(desc),
                    0.95, "renderWidget->extractWidgetRenderState",
                    "26.1 renamed widget rendering to extract pattern");
            }
            return null;
        });

        // Generic render*() -> extract*() pattern for GUI classes.
        // This catches inventory rendering, entity rendering, tooltip rendering, etc.
        // Lower confidence (0.7) because not ALL render* methods follow this pattern —
        // only those in the gui/ package hierarchy.
        rules.add((owner, name, desc) -> {
            if (name.startsWith("render") && name.length() > 6
                && Character.isUpperCase(name.charAt(6))
                && owner.startsWith("net/minecraft/client/gui/")) {
                String suffix = name.substring(6); // e.g., "Tooltip", "Background"
                String newName = "extract" + suffix;
                return new PatternResult(owner, newName,
                    renameGuiGraphics(desc),
                    0.7, "render*->extract*",
                    "26.1 render verb migration pattern");
            }
            return null;
        });

        // ── GuiGraphicsExtractor Method Renames ────────────────────────
        // When GuiGraphics became GuiGraphicsExtractor, several drawing methods
        // were also simplified to shorter names.

        // drawString -> text
        rules.add((owner, name, desc) -> {
            if (name.equals("drawString") && isGuiGraphics(owner)) {
                return new PatternResult(
                    renameGuiGraphics(owner),
                    "text", desc, 0.95, "drawString->text",
                    "26.1 renamed GuiGraphics.drawString to text()");
            }
            return null;
        });

        // drawCenteredString -> centeredText
        rules.add((owner, name, desc) -> {
            if (name.equals("drawCenteredString") && isGuiGraphics(owner)) {
                return new PatternResult(
                    renameGuiGraphics(owner),
                    "centeredText", desc, 0.95, "drawCenteredString->centeredText",
                    "26.1 renamed drawCenteredString to centeredText()");
            }
            return null;
        });

        // renderOutline -> outline
        rules.add((owner, name, desc) -> {
            if (name.equals("renderOutline") && isGuiGraphics(owner)) {
                return new PatternResult(
                    renameGuiGraphics(owner),
                    "outline", desc, 0.95, "renderOutline->outline",
                    "26.1 renamed renderOutline to outline()");
            }
            return null;
        });

        // renderItem -> item
        rules.add((owner, name, desc) -> {
            if (name.equals("renderItem") && isGuiGraphics(owner)) {
                return new PatternResult(
                    renameGuiGraphics(owner),
                    "item", desc, 0.95, "renderItem->item",
                    "26.1 simplified renderItem to item()");
            }
            return null;
        });

        // ── Level/World API Renames ────────────────────────────────────
        // MC has progressively renamed "World" concepts to "Level", and 26.1
        // renamed specific time-related methods.

        // getDayTime() -> getOverworldClockTime()
        // Only matches on Level classes with the exact ()J descriptor (returns long)
        rules.add((owner, name, desc) -> {
            if (name.equals("getDayTime") && desc.equals("()J")
                && (owner.contains("Level") || owner.contains("level"))) {
                return new PatternResult(owner, "getOverworldClockTime", desc,
                    0.95, "getDayTime->getOverworldClockTime",
                    "26.1 renamed Level.getDayTime()");
            }
            return null;
        });

        // ── Player API Changes ─────────────────────────────────────────
        // displayClientMessage(Component, boolean) was split into two separate methods:
        // sendSystemMessage(Component) for chat messages and sendOverlayMessage(Component)
        // for action bar messages. We default to sendSystemMessage since that's the
        // more common usage. Confidence is 0.8 because the overlay case exists.
        rules.add((owner, name, desc) -> {
            if (name.equals("displayClientMessage") && owner.contains("player/Player")) {
                return new PatternResult(owner, "sendSystemMessage",
                    "(Lnet/minecraft/network/chat/Component;)V",
                    0.8, "displayClientMessage->sendSystemMessage",
                    "26.1 split displayClientMessage into sendSystemMessage/sendOverlayMessage");
            }
            return null;
        });

        // ── RenderType Changes ─────────────────────────────────────────
        // 26.1 made no-cull the default for all render types, so the *NoCull
        // suffix was removed (e.g., entityCutoutNoCull -> entityCutout).
        // The RenderType class also moved to a rendertype/ subpackage.
        rules.add((owner, name, desc) -> {
            if (name.endsWith("NoCull") && owner.contains("RenderType")) {
                String newName = name.substring(0, name.length() - 6);
                return new PatternResult(
                    owner.replace("client/renderer/RenderType", "client/renderer/rendertype/RenderType"),
                    newName, desc.replace("client/renderer/RenderType", "client/renderer/rendertype/RenderType"),
                    0.85, "*NoCull->remove suffix",
                    "26.1 made no-cull the default for render types");
            }
            return null;
        });

        // ── Fabric API Renames ─────────────────────────────────────────
        // The keybinding API module was renamed to keymapping to match MC's
        // internal terminology shift from "key bindings" to "key mappings".

        // registerKeyBinding -> registerKeyMapping
        rules.add((owner, name, desc) -> {
            if (name.equals("registerKeyBinding") && owner.contains("keybinding")) {
                return new PatternResult(
                    owner.replace("keybinding", "keymapping")
                         .replace("KeyBindingHelper", "KeyMappingHelper"),
                    "registerKeyMapping", desc, 0.95,
                    "registerKeyBinding->registerKeyMapping",
                    "26.1 Fabric API renamed keybinding to keymapping");
            }
            return null;
        });

        // ── Generic Getter -> Record Accessor Pattern ───────────────────
        // MC 26.1 converted some Blaze3D utility classes to records, which
        // means getX() methods become just x() (record accessor pattern).
        // Lower confidence (0.6) because only specific classes were converted;
        // this rule is a catch-all for the Blaze3D package.
        rules.add((owner, name, desc) -> {
            if (name.startsWith("get") && name.length() > 3
                && Character.isUpperCase(name.charAt(3))
                && desc.startsWith("()") && !desc.equals("()V")
                && owner.startsWith("net/minecraft/")
                && owner.contains("blaze3d")) {
                // getWindow -> window, getHandle -> handle, etc.
                String fieldName = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                return new PatternResult(owner, fieldName, desc,
                    0.6, "getX->x (record accessor)",
                    "26.1 may use record-style accessors for Blaze3D classes");
            }
            return null;
        });

        // ── Networking: S2C -> Clientbound, C2S -> Serverbound ──────────
        // Fabric API 26.1 standardized networking method names to use the
        // Mojang naming convention (Clientbound/Serverbound) instead of the
        // Fabric-specific S2C/C2S abbreviations.
        rules.add((owner, name, desc) -> {
            if (owner.contains("networking") && name.contains("S2C")) {
                return new PatternResult(owner, name.replace("S2C", "Clientbound"), desc,
                    0.9, "S2C->Clientbound",
                    "26.1 Fabric API renamed S2C to Clientbound");
            }
            if (owner.contains("networking") && name.contains("C2S")) {
                return new PatternResult(owner, name.replace("C2S", "Serverbound"), desc,
                    0.9, "C2S->Serverbound",
                    "26.1 Fabric API renamed C2S to Serverbound");
            }
            return null;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CLASS PATTERN RULES
    // Handle package relocations and class renames between MC versions.
    // ═══════════════════════════════════════════════════════════════════════

    private void registerClassPatterns(List<ClassPatternRule> rules) {

        // GeckoLib 5.5 package rename: software.bernie.geckolib -> com.geckolib
        // This is a third-party library, not MC itself, but many mods depend on it.
        rules.add(className -> {
            if (className.startsWith("software/bernie/geckolib")) {
                return new PatternResult(
                    className.replace("software/bernie/geckolib", "com/geckolib"),
                    null, null, 0.95, "GeckoLib package rename",
                    "GeckoLib 5.5 moved from software.bernie.geckolib to com.geckolib");
            }
            return null;
        });

        // RenderType/RenderTypes moved to rendertype/ subpackage in 26.1
        rules.add(className -> {
            if (className.equals("net/minecraft/client/renderer/RenderType")
                || className.equals("net/minecraft/client/renderer/RenderTypes")) {
                return new PatternResult(
                    className.replace("client/renderer/Render", "client/renderer/rendertype/Render"),
                    null, null, 0.95, "RenderType package move",
                    "26.1 moved RenderType to rendertype subpackage");
            }
            return null;
        });

        // GuiGraphics -> GuiGraphicsExtractor (core 26.1 rendering rename)
        // Very high confidence (0.99) because this is a documented, exact rename.
        rules.add(className -> {
            if (className.equals("net/minecraft/client/gui/GuiGraphics")) {
                return new PatternResult(
                    "net/minecraft/client/gui/GuiGraphicsExtractor",
                    null, null, 0.99, "GuiGraphics->GuiGraphicsExtractor",
                    "26.1 renamed GuiGraphics to GuiGraphicsExtractor");
            }
            return null;
        });

        // PlayerFaceRenderer -> PlayerFaceExtractor (follows the extract pattern)
        rules.add(className -> {
            if (className.equals("net/minecraft/client/gui/components/PlayerFaceRenderer")) {
                return new PatternResult(
                    "net/minecraft/client/gui/components/PlayerFaceExtractor",
                    null, null, 0.99, "PlayerFaceRenderer->PlayerFaceExtractor",
                    "26.1 renamed PlayerFaceRenderer to PlayerFaceExtractor");
            }
            return null;
        });

        // Fabric keybinding -> keymapping package rename
        rules.add(className -> {
            if (className.contains("fabricmc/fabric/api/client/keybinding")) {
                return new PatternResult(
                    className.replace("keybinding", "keymapping")
                             .replace("KeyBindingHelper", "KeyMappingHelper"),
                    null, null, 0.95, "keybinding->keymapping",
                    "26.1 Fabric API renamed keybinding package to keymapping");
            }
            return null;
        });

        // Fabric WorldRenderEvents -> LevelRenderEvents
        // Part of the World->Level terminology migration in Fabric API.
        rules.add(className -> {
            if (className.contains("rendering/v1/world/WorldRenderEvents")) {
                return new PatternResult(
                    className.replace("world/WorldRenderEvents", "level/LevelRenderEvents"),
                    null, null, 0.95, "WorldRenderEvents->LevelRenderEvents",
                    "26.1 Fabric API moved world events to level package");
            }
            return null;
        });

        // Generic World -> Level pattern in Fabric API class names.
        // Lower confidence (0.7) because not all World-containing class names
        // were renamed — some are kept for backwards compatibility.
        rules.add(className -> {
            if (className.contains("fabricmc/fabric/") && className.contains("World")
                && !className.contains("Level")) {
                return new PatternResult(
                    className.replace("World", "Level"),
                    null, null, 0.7, "World->Level (Fabric API)",
                    "26.1 Fabric API renamed World to Level in many classes");
            }
            return null;
        });

        // LivingEntityFeatureRendererRegistrationCallback -> LivingEntityRenderLayerRegistrationCallback
        // Exact rename with very high confidence.
        rules.add(className -> {
            if (className.contains("LivingEntityFeatureRendererRegistrationCallback")) {
                return new PatternResult(
                    className.replace("LivingEntityFeatureRendererRegistrationCallback",
                                     "LivingEntityRenderLayerRegistrationCallback"),
                    null, null, 0.99, "LivingEntityFeatureRendererRegistrationCallback rename",
                    "26.1 Fabric API renamed the callback");
            }
            return null;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // FIELD PATTERN RULES
    // Handle field renames between MC versions.
    // ═══════════════════════════════════════════════════════════════════════

    private void registerFieldPatterns(List<PatternRule> rules) {

        // Window.window -> Window.handle
        // The GLFW window handle field was renamed for clarity.
        rules.add((owner, name, desc) -> {
            if (name.equals("window") && owner.contains("blaze3d/platform/Window")) {
                return new PatternResult(owner, "handle", desc, 0.95,
                    "Window.window->handle",
                    "26.1 renamed Window.window field to handle");
            }
            return null;
        });

        // KeyMapping.boundKey -> KeyMapping.key
        // Simplified field name.
        rules.add((owner, name, desc) -> {
            if (name.equals("boundKey") && owner.contains("KeyMapping")) {
                return new PatternResult(owner, "key", desc, 0.95,
                    "KeyMapping.boundKey->key",
                    "26.1 renamed KeyMapping.boundKey to key");
            }
            return null;
        });

        // EntityType.BOAT -> EntityType.OAK_BOAT
        // 26.1 split the generic BOAT entity type into per-wood-type variants.
        // We default to OAK_BOAT as the most common/default wood type.
        rules.add((owner, name, desc) -> {
            if (name.equals("BOAT") && owner.contains("EntityType")) {
                return new PatternResult(owner, "OAK_BOAT", desc, 0.85,
                    "BOAT->OAK_BOAT",
                    "26.1 split EntityType.BOAT into per-wood types");
            }
            return null;
        });

        // EntityType.CHEST_BOAT -> EntityType.OAK_CHEST_BOAT
        // Same per-wood-type split as above.
        rules.add((owner, name, desc) -> {
            if (name.equals("CHEST_BOAT") && owner.contains("EntityType")) {
                return new PatternResult(owner, "OAK_CHEST_BOAT", desc, 0.85,
                    "CHEST_BOAT->OAK_CHEST_BOAT",
                    "26.1 split EntityType.CHEST_BOAT into per-wood types");
            }
            return null;
        });
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if a class is a Screen subclass based on its package or name.
     * Used to scope the render->extractRenderState rule to GUI screen classes.
     */
    private static boolean isScreenClass(String owner) {
        return owner.contains("gui/screens/") || owner.endsWith("Screen");
    }

    /**
     * Check if a class is GuiGraphics (pre-26.1 name) or GuiGraphicsExtractor (26.1 name).
     * Used to scope drawing method renames to the correct class.
     */
    private static boolean isGuiGraphics(String owner) {
        return owner.contains("GuiGraphics");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PATTERN RULE INTERFACES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Functional interface for method and field pattern rules.
     * Returns a {@link PatternResult} if the rule matches, or null if it does not.
     */
    @FunctionalInterface
    interface PatternRule {
        PatternResult tryMatch(String owner, String name, String descriptor);
    }

    /**
     * Functional interface for class pattern rules.
     * Returns a {@link PatternResult} if the rule matches, or null if it does not.
     */
    @FunctionalInterface
    interface ClassPatternRule {
        PatternResult tryMatch(String className);
    }
}
