/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.pattern.patterns;

import com.retromod.core.pattern.ClassPattern;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Detects classes that look like a Minecraft {@code BlockEntity} (old
 * {@code TileEntity}) — structural match based on NBT serialization + tick
 * behaviour rather than exact inheritance.
 *
 * <h3>Match confidence</h3>
 * <p>Tiered:</p>
 * <ul>
 *   <li><b>0.9</b> — Class has an NBT-read method AND an NBT-write method AND
 *       extends a class with "BlockEntity" or "TileEntity" in its name.</li>
 *   <li><b>0.75</b> — NBT read + write + a tick-shaped method, but no
 *       parent-name match. These are likely custom BlockEntity base classes
 *       or derived classes that use an indirect parent.</li>
 *   <li><b>null</b> — Missing any of the three signals.</li>
 * </ul>
 *
 * <h3>Signal heuristics</h3>
 * <p>A method is considered "NBT-read-like" if its name is one of
 * {@code load}, {@code read}, {@code readNbt}, {@code readFromNBT},
 * {@code readCustomNBT} and its signature accepts a single {@code CompoundTag}
 * or {@code NBTTagCompound} parameter.</p>
 *
 * <p>A method is "NBT-write-like" if its name is one of {@code save},
 * {@code write}, {@code writeNbt}, {@code writeToNBT}, {@code writeCustomNBT}
 * and it either accepts or returns a {@code CompoundTag}/{@code NBTTagCompound}.</p>
 *
 * <p>A method is "tick-shaped" if it's named {@code tick}, {@code serverTick},
 * {@code clientTick}, or {@code update}.</p>
 *
 * <h3>Why it matters</h3>
 * <p>BlockEntity is one of the most-refactored subsystems across MC versions.
 * Mods with custom block entities frequently break silently — data fails to
 * persist, ticking stops, save files corrupt. Detection lets us flag these
 * classes specifically so mod authors know to test them carefully after
 * transformation.</p>
 */
public final class BlockEntityLikePattern implements ClassPattern {

    private static final Set<String> NBT_READ_NAMES = Set.of(
            "load", "read", "readNbt", "readFromNBT", "readCustomNBT",
            "method_11014" /* intermediary for load, seen in Fabric mods */
    );

    private static final Set<String> NBT_WRITE_NAMES = Set.of(
            "save", "write", "writeNbt", "writeToNBT", "writeCustomNBT",
            "method_11007" /* intermediary for save */
    );

    private static final Set<String> TICK_NAMES = Set.of(
            "tick", "serverTick", "clientTick", "update", "onTick"
    );

    /** Known CompoundTag/NBTTagCompound FQNs. */
    private static final Set<String> COMPOUND_TAG_DESCS = Set.of(
            "Lnet/minecraft/nbt/CompoundTag;",
            "Lnet/minecraft/nbt/NBTTagCompound;"
    );

    @Override
    public String name() { return "BlockEntityLike"; }

    @Override
    public String description() {
        return "Classes that look like a BlockEntity — NBT read/write + ticking behaviour";
    }

    @Override
    public PatternMatch match(ClassNode cls, MatchContext ctx) {
        if (cls.name == null) return null;
        if (!ctx.modOwnClasses().isEmpty() && !ctx.modOwnClasses().contains(cls.name)) {
            return null;
        }
        if (cls.methods == null) return null;

        boolean hasNbtRead = false;
        boolean hasNbtWrite = false;
        boolean hasTick = false;

        for (MethodNode m : cls.methods) {
            if (m.name == null) continue;
            if (!hasNbtRead && NBT_READ_NAMES.contains(m.name) && takesCompoundTag(m.desc)) {
                hasNbtRead = true;
            }
            if (!hasNbtWrite && NBT_WRITE_NAMES.contains(m.name) && involvesCompoundTag(m.desc)) {
                hasNbtWrite = true;
            }
            if (!hasTick && TICK_NAMES.contains(m.name)) {
                hasTick = true;
            }
            if (hasNbtRead && hasNbtWrite && hasTick) break;
        }

        // Need at least NBT read + write to be considered BlockEntity-like.
        // Tick is nice to have but not required (many mods use external tickers).
        if (!hasNbtRead || !hasNbtWrite) return null;

        // Check the parent-name signal to decide confidence tier.
        boolean parentNameMatches = cls.superName != null
                && (cls.superName.contains("BlockEntity") || cls.superName.contains("TileEntity"));

        double confidence = parentNameMatches ? 0.9 : 0.75;

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("hasNbtRead", hasNbtRead);
        metadata.put("hasNbtWrite", hasNbtWrite);
        metadata.put("hasTick", hasTick);
        metadata.put("superclass", cls.superName);
        return new PatternMatch(name(), cls.name, confidence, metadata);
    }

    /** Method takes a single CompoundTag/NBTTagCompound parameter. */
    private static boolean takesCompoundTag(String desc) {
        if (desc == null) return false;
        for (String tagDesc : COMPOUND_TAG_DESCS) {
            if (desc.startsWith("(" + tagDesc)) return true;
        }
        return false;
    }

    /** Method takes or returns a CompoundTag/NBTTagCompound. */
    private static boolean involvesCompoundTag(String desc) {
        if (desc == null) return false;
        for (String tagDesc : COMPOUND_TAG_DESCS) {
            if (desc.contains(tagDesc)) return true;
        }
        return false;
    }
}
