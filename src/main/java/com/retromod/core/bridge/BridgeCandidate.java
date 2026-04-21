/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.bridge;

/**
 * Describes one method in a mod class that is a candidate for bridge synthesis.
 *
 * <p>A bridge candidate means: "the mod class declares a method that overrides
 * a parent-class method whose name was renamed in the target MC version. Without
 * a bridge, the mod's override becomes orphaned — the parent no longer has the
 * old method name, so the JVM dispatches to the parent's renamed version
 * instead of the mod's override."</p>
 *
 * <p>The fix is to synthesize a new method in the mod class with the renamed
 * name, delegating to the original. Virtual dispatch then hits the mod's
 * synthesized method, which calls the original implementation.</p>
 *
 * <h3>Field meanings</h3>
 * <ul>
 *   <li>{@code methodName} / {@code methodDescriptor} — the mod's declared method.
 *       Its body is preserved as-is.</li>
 *   <li>{@code access} — access flags from the declared method ({@code ACC_PUBLIC},
 *       {@code ACC_PROTECTED}, etc.). Reused for the bridge.</li>
 *   <li>{@code bridgeName} — the new name the bridge method should have. Comes
 *       from the method-redirect table.</li>
 * </ul>
 *
 * <h3>What's NOT captured in v1</h3>
 * <p>Only descriptor-preserving renames are modelled. Cases where the descriptor
 * itself changes (e.g., parameter added) would require argument-conversion
 * logic in the bridge body — out of scope for v1 to keep the risk of VerifyError
 * low. See the v1 scope notes in {@code design-3-4.md}.</p>
 */
public record BridgeCandidate(
        int access,
        String methodName,
        String methodDescriptor,
        String bridgeName
) {
}
