/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Bridge Adapter Generator — handles method SIGNATURE changes (not just renames).
 *
 * When Minecraft changes a method's parameter types between versions, old mods have
 * method bodies that use the old parameter types. Simply renaming doesn't help because
 * the JVM signature verification will fail. This generator solves that by:
 *
 *   1. Renaming the original method: mouseClicked -> mouseClicked$bridge_old
 *   2. Generating a new bridge method with the 26.1 signature that unpacks event
 *      objects into primitives and calls the renamed original
 *   3. Redirecting super.mouseClicked(old params) to wrap primitives into an event
 *      and call super.mouseClicked(new params)
 *
 * Example transformation:
 *   Old mod code:  mouseClicked(double x, double y, int button) { ... }
 *   After bridge:  mouseClicked$bridge_old(double x, double y, int button) { ... }
 *   New bridge:    mouseClicked(MouseButtonEvent e, boolean b) {
 *                      return mouseClicked$bridge_old(e.x(), e.y(), e.button());
 *                  }
 *
 * This approach preserves the mod's original logic intact while making it compatible
 * with the new 26.1 event-based input API.
 */
package com.retromod.core;

import org.objectweb.asm.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Generates bytecode bridge methods for method signature changes between MC versions.
 *
 * <h2>Integration</h2>
 * This is integrated into {@link RetroModTransformer}'s class visitor pipeline:
 * <ul>
 *   <li>During {@code visitMethod()}: call {@link #checkAndRename} to detect methods
 *       with old signatures. If a match is found, the method gets renamed to
 *       {@code methodName$bridge_old} so the bridge can take the original name.</li>
 *   <li>During {@code visitEnd()}: call {@link #generateBridges} to emit the bridge
 *       methods that forward from the new signature to the renamed original.</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is NOT thread-safe. Each instance is used for a single class transformation
 * and must be {@link #reset()} before reuse. The static {@link #BRIDGES} list and
 * static lookup methods are safe for concurrent use.
 *
 * <h2>Bounded Collections</h2>
 * The {@link #pendingBridges} list is bounded by the size of {@link #BRIDGES} (currently 6).
 * A class cannot have more pending bridges than there are known bridge definitions.
 *
 * <p><b>IMPORTANT:</b> This class must NOT reference {@code RetroMod} directly
 * because the transformer is also used by the standalone CLI where Fabric classes
 * are not on the classpath.</p>
 */
public class BridgeAdapterGenerator {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Bridge");

    // ═══════════════════════════════════════════════════════════════════════
    // MC 26.1 EVENT CLASS CONSTANTS
    // These are the new event types that replaced primitive parameters in
    // the 26.1 input event API overhaul.
    // ═══════════════════════════════════════════════════════════════════════

    /** Mouse button event — wraps x/y coordinates + button id. */
    private static final String MOUSE_EVENT = "net/minecraft/client/input/MouseButtonEvent";
    /** Keyboard event — wraps key code + scan code + modifiers. */
    private static final String KEY_EVENT = "net/minecraft/client/input/KeyEvent";
    /** Character typed event — wraps Unicode codepoint. */
    private static final String CHAR_EVENT = "net/minecraft/client/input/CharacterEvent";

    // ═══════════════════════════════════════════════════════════════════════
    // BRIDGE DEFINITIONS — all known signature changes for 26.1
    // This list is immutable and safe for concurrent access.
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * A signature bridge definition describing how to convert between old and new
     * method signatures.
     *
     * @param methodName    the method name (same in old and new versions)
     * @param oldDescriptor the old JVM descriptor (e.g., "(DDI)Z")
     * @param newDescriptor the new JVM descriptor using event objects
     * @param bridgeOldName the name to give the renamed original method
     */
    public record BridgeDef(
        String methodName,
        String oldDescriptor,
        String newDescriptor,
        String bridgeOldName
    ) {}

    /**
     * All known signature bridges for MC 26.1's input event API overhaul.
     * Immutable list — safe for concurrent reads from multiple transformer threads.
     */
    private static final List<BridgeDef> BRIDGES = List.of(
        // mouseClicked(double x, double y, int button) -> mouseClicked(MouseButtonEvent, boolean)
        new BridgeDef("mouseClicked",
            "(DDI)Z",
            "(L" + MOUSE_EVENT + ";Z)Z",
            "mouseClicked$bridge_old"),

        // mouseReleased(double x, double y, int button) -> mouseReleased(MouseButtonEvent)
        new BridgeDef("mouseReleased",
            "(DDI)Z",
            "(L" + MOUSE_EVENT + ";)Z",
            "mouseReleased$bridge_old"),

        // mouseDragged(double x, double y, int button, double dx, double dy)
        //   -> mouseDragged(MouseButtonEvent, double dx, double dy)
        new BridgeDef("mouseDragged",
            "(DDIDD)Z",
            "(L" + MOUSE_EVENT + ";DD)Z",
            "mouseDragged$bridge_old"),

        // keyPressed(int key, int scancode, int modifiers) -> keyPressed(KeyEvent)
        new BridgeDef("keyPressed",
            "(III)Z",
            "(L" + KEY_EVENT + ";)Z",
            "keyPressed$bridge_old"),

        // keyReleased(int key, int scancode, int modifiers) -> keyReleased(KeyEvent)
        new BridgeDef("keyReleased",
            "(III)Z",
            "(L" + KEY_EVENT + ";)Z",
            "keyReleased$bridge_old"),

        // charTyped(char codepoint, int modifiers) -> charTyped(CharacterEvent)
        new BridgeDef("charTyped",
            "(CI)Z",
            "(L" + CHAR_EVENT + ";)Z",
            "charTyped$bridge_old")
    );

    /**
     * Pre-computed set of method names that have bridge definitions.
     * Used for O(1) lookups in {@link #isKnownBridgeMethod}.
     */
    private static final Set<String> BRIDGE_METHOD_NAMES;
    static {
        Set<String> names = new HashSet<>();
        for (BridgeDef b : BRIDGES) {
            names.add(b.methodName());
        }
        BRIDGE_METHOD_NAMES = Collections.unmodifiableSet(names);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // INSTANCE STATE — per-class transformation state (NOT thread-safe)
    // pendingBridges is bounded by BRIDGES.size() (currently 6)
    // ═══════════════════════════════════════════════════════════════════════

    /** Bridges that need to be generated for the current class. */
    private final List<BridgeDef> pendingBridges = new ArrayList<>(BRIDGES.size());

    /** The class currently being transformed. Set during checkAndRename. */
    private String currentClassName;

    public BridgeAdapterGenerator() {}

    // ═══════════════════════════════════════════════════════════════════════
    // PUBLIC API — called from RetroModTransformer's visitor pipeline
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if a method definition has an old signature that needs bridging.
     * If it does, the method should be renamed to the bridge old name so the
     * bridge method can take the original name with the new signature.
     *
     * <p>Called from {@code RetroModClassVisitor.visitMethod()} for each method
     * in the class being transformed.</p>
     *
     * @param className  the class being transformed (JVM internal name)
     * @param methodName the method name
     * @param descriptor the method descriptor
     * @return the bridge old name (e.g., "mouseClicked$bridge_old") if this method
     *         needs bridging, or null if the method signature is fine as-is
     */
    public String checkAndRename(String className, String methodName, String descriptor) {
        this.currentClassName = className;

        // First check: exact descriptor match against known old descriptors
        for (BridgeDef bridge : BRIDGES) {
            if (bridge.methodName().equals(methodName) && bridge.oldDescriptor().equals(descriptor)) {
                pendingBridges.add(bridge);
                LOGGER.debug("Bridge needed: {}.{}{} -> renamed to {}",
                    className, methodName, descriptor, bridge.bridgeOldName());
                return bridge.bridgeOldName();
            }
        }

        // Second check: the old descriptor might have been partially remapped by
        // ClassRemapper (e.g., intermediary class names in the descriptor replaced
        // with Mojang names). Check pattern-based matching as fallback.
        for (BridgeDef bridge : BRIDGES) {
            if (bridge.methodName().equals(methodName) && descriptorMatchesOldPattern(bridge, descriptor)) {
                pendingBridges.add(bridge);
                LOGGER.debug("Bridge needed (remapped desc): {}.{}{} -> {}",
                    className, methodName, descriptor, bridge.bridgeOldName());
                return bridge.bridgeOldName();
            }
        }
        return null;
    }

    /**
     * Generate all pending bridge methods.
     * Called from {@code RetroModClassVisitor.visitEnd()} after all original methods
     * have been visited (and potentially renamed).
     *
     * <p>Each bridge method:</p>
     * <ol>
     *   <li>Has the new 26.1 signature (public, non-abstract)</li>
     *   <li>Unpacks event object fields into primitive parameters</li>
     *   <li>Calls the renamed original method with the primitive parameters</li>
     *   <li>Returns the result</li>
     * </ol>
     *
     * @param cv the ClassVisitor to emit bridge methods into
     */
    public void generateBridges(ClassVisitor cv) {
        for (BridgeDef bridge : pendingBridges) {
            generateBridgeMethod(cv, bridge);
        }
        if (!pendingBridges.isEmpty()) {
            LOGGER.info("Generated {} bridge adapter(s) for {}", pendingBridges.size(), currentClassName);
        }
    }

    /**
     * Check if any bridges are pending for the current class.
     * Used to decide whether visitEnd needs to call generateBridges.
     */
    public boolean hasPendingBridges() {
        return !pendingBridges.isEmpty();
    }

    /**
     * Reset for a new class transformation.
     * Must be called before transforming each new class to clear state from the previous one.
     */
    public void reset() {
        pendingBridges.clear();
        currentClassName = null;
    }

    /**
     * Get the set of method names that have pending bridges for the current class.
     * Used for super call redirection — when the mod calls super.mouseClicked(old params),
     * the transformer needs to know that mouseClicked has a bridge so it can redirect
     * the super call to use the new signature.
     *
     * @return unmodifiable set of method names with pending bridges
     */
    public Set<String> getBridgedMethodNames() {
        Set<String> names = new HashSet<>(pendingBridges.size());
        for (BridgeDef bridge : pendingBridges) {
            names.add(bridge.methodName());
        }
        return Collections.unmodifiableSet(names);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // STATIC LOOKUP API — safe for concurrent access
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if a method name has a known bridge definition (any of the 6 input methods).
     * This is a fast O(1) lookup used to quickly filter candidate methods.
     *
     * @param methodName the method name to check
     * @return true if a bridge definition exists for this method name
     */
    public static boolean isKnownBridgeMethod(String methodName) {
        return BRIDGE_METHOD_NAMES.contains(methodName);
    }

    /**
     * Get the old (pre-26.1) descriptor for a bridge method.
     * Used by AutoFixEngine to detect signature mismatches.
     *
     * @param methodName the method name to look up
     * @return the old descriptor, or null if no bridge exists for this method
     */
    public static String getOldDescriptor(String methodName) {
        for (BridgeDef b : BRIDGES) {
            if (b.methodName().equals(methodName)) {
                return b.oldDescriptor();
            }
        }
        return null;
    }

    /**
     * Get the new (26.1) descriptor for a bridge method.
     * Used by AutoFixEngine to register the correct redirect.
     *
     * @param methodName the method name to look up
     * @return the new descriptor, or null if no bridge exists for this method
     */
    public static String getNewDescriptor(String methodName) {
        for (BridgeDef b : BRIDGES) {
            if (b.methodName().equals(methodName)) {
                return b.newDescriptor();
            }
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PRIVATE — descriptor matching and bridge method generation
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Check if a descriptor matches the "old" pattern even after partial remapping.
     * ClassRemapper may have changed class names in the descriptor but not the
     * overall structure. We check the primitive parameter types to determine
     * if this is still the old-style signature.
     */
    private boolean descriptorMatchesOldPattern(BridgeDef bridge, String descriptor) {
        return switch (bridge.methodName()) {
            case "mouseClicked", "mouseReleased" -> descriptor.equals("(DDI)Z");
            case "mouseDragged" -> descriptor.equals("(DDIDD)Z");
            case "keyPressed", "keyReleased" ->
                descriptor.equals("(III)Z") && !descriptor.contains("KeyEvent");
            case "charTyped" ->
                descriptor.equals("(CI)Z") || descriptor.equals("(II)Z");
            default -> false;
        };
    }

    /**
     * Generate a single bridge method that unpacks new event params into old primitive params.
     * The bridge method has the new 26.1 signature and delegates to the renamed original.
     */
    private void generateBridgeMethod(ClassVisitor cv, BridgeDef bridge) {
        MethodVisitor mv = cv.visitMethod(
            Opcodes.ACC_PUBLIC,
            bridge.methodName(),
            bridge.newDescriptor(),
            null, null);
        mv.visitCode();

        switch (bridge.methodName()) {
            case "mouseClicked" -> generateMouseClickedBridge(mv, bridge);
            case "mouseReleased" -> generateMouseReleasedBridge(mv, bridge);
            case "mouseDragged" -> generateMouseDraggedBridge(mv, bridge);
            case "keyPressed", "keyReleased" -> generateKeyBridge(mv, bridge);
            case "charTyped" -> generateCharTypedBridge(mv, bridge);
        }

        mv.visitEnd();
    }

    /**
     * Bridge: mouseClicked(MouseButtonEvent event, boolean consumed)
     *       -> mouseClicked$bridge_old(event.x(), event.y(), event.button())
     *
     * Stack layout: [this, event, consumed] -> [this, double, double, int]
     * The consumed boolean is dropped (old API didn't have it).
     */
    private void generateMouseClickedBridge(MethodVisitor mv, BridgeDef bridge) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);  // this

        mv.visitVarInsn(Opcodes.ALOAD, 1);  // event
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "x", "()D", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);  // event
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "y", "()D", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);  // event
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "button", "()I", false);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName,
            bridge.bridgeOldName(), "(DDI)Z", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(6, 3);
    }

    /**
     * Bridge: mouseReleased(MouseButtonEvent event)
     *       -> mouseReleased$bridge_old(event.x(), event.y(), event.button())
     *
     * Stack layout: [this, event] -> [this, double, double, int]
     */
    private void generateMouseReleasedBridge(MethodVisitor mv, BridgeDef bridge) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "x", "()D", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "y", "()D", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "button", "()I", false);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName,
            bridge.bridgeOldName(), "(DDI)Z", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(6, 2);
    }

    /**
     * Bridge: mouseDragged(MouseButtonEvent event, double dragX, double dragY)
     *       -> mouseDragged$bridge_old(event.x(), event.y(), event.button(), dragX, dragY)
     *
     * Stack layout: [this, event, dragX, dragY] -> [this, double, double, int, double, double]
     * The dragX/dragY parameters are passed through unchanged.
     */
    private void generateMouseDraggedBridge(MethodVisitor mv, BridgeDef bridge) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "x", "()D", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "y", "()D", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, MOUSE_EVENT, "button", "()I", false);

        mv.visitVarInsn(Opcodes.DLOAD, 2);  // dragX (pass-through)
        mv.visitVarInsn(Opcodes.DLOAD, 4);  // dragY (pass-through)

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName,
            bridge.bridgeOldName(), "(DDIDD)Z", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(10, 6);
    }

    /**
     * Bridge: keyPressed/keyReleased(KeyEvent event)
     *       -> keyPressed$bridge_old(event.key(), event.scancode(), event.modifiers())
     *
     * Stack layout: [this, event] -> [this, int, int, int]
     * Used for both keyPressed and keyReleased since they have identical signatures.
     */
    private void generateKeyBridge(MethodVisitor mv, BridgeDef bridge) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KEY_EVENT, "key", "()I", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KEY_EVENT, "scancode", "()I", false);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, KEY_EVENT, "modifiers", "()I", false);

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName,
            bridge.bridgeOldName(), "(III)Z", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(4, 2);
    }

    /**
     * Bridge: charTyped(CharacterEvent event)
     *       -> charTyped$bridge_old(event.codepoint() as char, 0)
     *
     * Stack layout: [this, event] -> [this, char, int]
     * The modifiers parameter (second int in old API) is dropped in 26.1,
     * so we pass 0 as a placeholder. I2C narrows the int codepoint to char.
     */
    private void generateCharTypedBridge(MethodVisitor mv, BridgeDef bridge) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, CHAR_EVENT, "codepoint", "()I", false);
        mv.visitInsn(Opcodes.I2C);  // int codepoint -> char (narrow conversion)

        mv.visitInsn(Opcodes.ICONST_0);  // modifiers = 0 (dropped in 26.1)

        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, currentClassName,
            bridge.bridgeOldName(), "(CI)Z", false);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(3, 2);
    }
}
