/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Polyfill stub for net.minecraft.world.gen.feature.StructureFeature.
 *
 * This class was removed in Minecraft 1.18.2 as part of the world
 * generation overhaul. Structure generation was moved to
 * net.minecraft.world.gen.structure.Structure.
 * The stub prevents ClassNotFoundException for legacy mod code.
 */
package net.minecraft.world.gen.feature;

/**
 * Minimal stub for the removed StructureFeature abstract class.
 *
 * In versions prior to 1.18.2, StructureFeature was the base class
 * for all structure generators (villages, strongholds, etc.).
 * This stub provides only the class identity to satisfy type references.
 */
public abstract class StructureFeature {
    // Intentionally minimal - exists only to satisfy class loading
}
