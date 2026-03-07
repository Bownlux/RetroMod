/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.19.3 to run on 1.19.4.
 * Handles the DamageSource rework to use DamageType registry and recipe API changes.
 */
public class Fabric_1_19_3_to_1_19_4 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.19.3 to 1.19.4"; }
    @Override public String getSourceVersion() { return "1.19.3"; }
    @Override public String getTargetVersion() { return "1.19.4"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // DamageSource reworked to use DamageType registry
        transformer.registerMethodRedirect(
            "net/minecraft/entity/damage/DamageSource", "<init>",
            "(Ljava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "create",
            "(Ljava/lang/String;)Lnet/minecraft/entity/damage/DamageSource;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "GENERIC",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "GENERIC"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "FALL",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "FALL"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "DROWN",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "DROWN"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/entity/damage/DamageSource", "IN_FIRE",
            "com/retromod/shim/fabric/embedded/DamageSourceShim", "IN_FIRE"
        );
        // Recipe changes
        transformer.registerMethodRedirect(
            "net/minecraft/recipe/RecipeManager", "getFirstMatch",
            "(Lnet/minecraft/recipe/RecipeType;Lnet/minecraft/inventory/Inventory;Lnet/minecraft/world/World;)Ljava/util/Optional;",
            "com/retromod/shim/fabric/embedded/RecipeShim", "getFirstMatch",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/Optional;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
