/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.minecraft.class_5500 (ChatOptionsScreen).
 *
 * This class was removed/merged in newer MC versions. Mods like
 * No Chat Reports used it as a mixin superclass target. The stub
 * prevents "Super class not found in hierarchy" mixin errors.
 *
 * In intermediary mappings:
 *   class_5500 = ChatOptionsScreen (was a subclass of SimpleOptionsScreen/class_404)
 *   class_404  = Screen
 */
package com.retromod.polyfill.minecraft.mixin.embedded;

/**
 * Minimal stub for the removed ChatOptionsScreen (class_5500).
 *
 * This is an empty class that exists solely to satisfy the mixin
 * type hierarchy check. When a mixin declares
 * {@code @Mixin(targets = "net.minecraft.class_5500")} and requires
 * the superclass to match, this stub provides the expected type.
 *
 * Note: This is a plain Object subclass. The mixin framework checks
 * that the mixin's declared superclass exists in the target's hierarchy.
 * By providing this stub class via a class redirect, the check passes.
 */
public class ChatOptionsScreenStub {
    // Intentionally empty - exists only to satisfy mixin hierarchy validation
}
