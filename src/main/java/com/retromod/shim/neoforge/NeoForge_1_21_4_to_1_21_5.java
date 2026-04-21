/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.21.4 to 1.21.5 shim - Tool item class flattening.
 * Mojang removed dedicated tool item subclasses (SwordItem, PickaxeItem, etc.)
 * and merged their functionality into the base Item class via data components.
 * ArmorItem was similarly folded into Item.
 */
public class NeoForge_1_21_4_to_1_21_5 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.4 to 1.21.5"; }
    @Override public String getSourceVersion() { return "1.21.4"; }
    @Override public String getTargetVersion() { return "1.21.5"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Tool item subclasses removed in 1.21.5 — merged into base Item class
        transformer.registerClassRedirect(
            "net/minecraft/world/item/SwordItem",
            "net/minecraft/world/item/Item"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/item/PickaxeItem",
            "net/minecraft/world/item/Item"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/item/AxeItem",
            "net/minecraft/world/item/Item"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/item/ShovelItem",
            "net/minecraft/world/item/Item"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/item/HoeItem",
            "net/minecraft/world/item/Item"
        );
        // DiggerItem base class also removed
        transformer.registerClassRedirect(
            "net/minecraft/world/item/DiggerItem",
            "net/minecraft/world/item/Item"
        );
        // ArmorItem merged into Item
        transformer.registerClassRedirect(
            "net/minecraft/world/item/ArmorItem",
            "net/minecraft/world/item/Item"
        );
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
