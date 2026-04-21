/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Shim that registers the runtime version-check redirect.
 *
 * <p>Every call in transformed mod bytecode to
 * {@code FabricLoader.getModContainer(String)} is rewritten to
 * {@link com.retromod.compat.VersionSpoofer#getModContainer(Object, String)}.
 * The spoofer intercepts version-reporting calls on the returned container
 * so dependent mods that ask "what version of Cloth Config is installed?"
 * see the spoofed version from {@code /retromod/version-spoofs.json}
 * instead of the real (potentially newer) installed version.</p>
 *
 * <h3>Why this is a VersionShim rather than inline init</h3>
 * <p>RetroMod's VersionShim SPI fires once, during transformer setup, on
 * every loader/environment. Registering the redirect there means every mod
 * transformed by RetroMod — whether via Fabric pre-launch, NeoForge
 * constructor, CLI batch, or Java Agent — gets the same redirect applied
 * uniformly. Making it a shim also keeps the wiring file-local (no sprawl
 * into RetroMod.onInitialize or its loader-specific siblings).</p>
 *
 * <h3>What this shim does NOT do</h3>
 * <p>It doesn't register the spoof rules themselves — those live in the
 * bundled JSON resource that {@link com.retromod.compat.VersionSpoofer}
 * loads on first use. Editing the rules doesn't require touching this
 * shim.</p>
 */
public final class VersionSpoofShim implements VersionShim {

    @Override
    public String getShimName() {
        return "Runtime Version Spoofer";
    }

    @Override
    public String getSourceVersion() {
        // Loader-API shims aren't tied to a source MC version — they apply
        // wherever mods run. "*" signals "always active" to the chain finder.
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        // "common" — applies under every loader since FabricLoader.getModContainer
        // is only called by Fabric-adapted mod code. Under NeoForge/Forge the
        // redirect simply never fires because the method isn't called.
        return "common";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Redirect FabricLoader.getModContainer(String) → VersionSpoofer.getModContainer(FabricLoader, String).
        //
        // devirtualize=true tells the transformer to:
        //   1. Change INVOKEINTERFACE/INVOKEVIRTUAL to INVOKESTATIC
        //   2. Leave the receiver on the stack so it becomes the first
        //      argument to the static method.
        //
        // Net effect: the old call
        //     INVOKEINTERFACE FabricLoader.getModContainer(String) : Optional
        // becomes
        //     INVOKESTATIC VersionSpoofer.getModContainer(FabricLoader, String) : Optional
        // — semantically identical except our static method gets a chance to
        // wrap the result with a version-spoofing proxy before returning it.
        transformer.registerMethodRedirect(
                "net/fabricmc/loader/api/FabricLoader",
                "getModContainer",
                "(Ljava/lang/String;)Ljava/util/Optional;",
                "com/retromod/compat/VersionSpoofer",
                "getModContainer",
                "(Ljava/lang/Object;Ljava/lang/String;)Ljava/util/Optional;",
                true);
    }
}
