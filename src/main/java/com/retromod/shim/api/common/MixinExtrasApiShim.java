/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Old MixinExtras -> Bundled MixinExtras Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * MixinExtras API compatibility shim.
 *
 * MixinExtras by LlamaLad7 is a companion library to SpongePowered Mixin
 * that adds powerful injection annotations like @WrapOperation, @ModifyExpressionValue,
 * @WrapWithCondition, and @Local. It rapidly became essential for mod development.
 *
 * History:
 * - v0.0.1 - v0.1.x: Standalone library, mods JiJ'd (Jar-in-Jar) their own copy
 * - v0.2.x: Major refactors, some annotation renames
 * - v0.3.x+: Bundled with Fabric Loader 0.15+ and NeoForge/Forge
 *
 * The problem: Many older mods bundle old versions of MixinExtras (0.0.x - 0.1.x)
 * in their JARs. These old versions have different package paths and annotation
 * names that conflict with the bundled version in modern loaders.
 *
 * Key mappings:
 * - com.llamalad7.mixinextras.injector.wrapoperation -> same package (mostly stable)
 * - Old @WrapMethod -> renamed/restructured in 0.2+
 * - com.llamalad7.mixinextras.sugar.Local -> stable but internals changed
 * - com.llamalad7.mixinextras.expression -> added in 0.3+ (no old equivalent)
 * - MixinExtrasBootstrap.init() -> no-op (handled by loader now)
 */
public class MixinExtrasApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "MixinExtras Legacy -> Bundled MixinExtras Compatibility";
    }

    @Override
    public String getSourceVersion() {
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        return "common"; // Affects both Fabric and Forge/NeoForge
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // BOOTSTRAP INITIALIZATION (most common issue)
        // ============================================================

        // Old: MixinExtrasBootstrap.init() - mods called this in their Mixin plugin
        // Older mods explicitly initialized MixinExtras in their IMixinConfigPlugin
        // New: No-op, modern loaders initialize MixinExtras automatically
        transformer.registerMethodRedirect(
            "com/llamalad7/mixinextras/MixinExtrasBootstrap",
            "init",
            "()V",
            "com/retromod/shim/api/common/embedded/MixinExtrasShim",
            "noopInit",
            "()V"
        );

        // Some mods used the older package path before restructuring
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/bootstrap/MixinExtrasBootstrap",
            "com/llamalad7/mixinextras/MixinExtrasBootstrap"
        );

        // ============================================================
        // INJECTOR ANNOTATION CLASSES (0.0.x -> 0.2+ changes)
        // ============================================================

        // @WrapOperation - the most popular annotation, mostly stable
        // Old package in some very early builds was slightly different
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/injector/WrapOperation",
            "com/llamalad7/mixinextras/injector/wrapoperation/WrapOperation"
        );

        // Old: Operation interface (used as the wrapper callback parameter)
        // In early versions this was in a different sub-package
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/injector/Operation",
            "com/llamalad7/mixinextras/injector/wrapoperation/Operation"
        );

        // ============================================================
        // @ModifyExpressionValue CHANGES
        // ============================================================

        // Old: @ModifyExpressionValue was in injector package directly (0.0.x)
        // New: moved to injector.v2 sub-package in some intermediate versions,
        // then stabilized back in injector package
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/injector/v2/ModifyExpressionValue",
            "com/llamalad7/mixinextras/injector/ModifyExpressionValue"
        );

        // ============================================================
        // @WrapWithCondition CHANGES
        // ============================================================

        // Old: @WrapWithCondition in early package structure
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/injector/WrapWithCondition",
            "com/llamalad7/mixinextras/injector/wrapmethod/WrapWithCondition"
        );

        // ============================================================
        // @ModifyReturnValue CHANGES
        // ============================================================

        // Old: @ModifyReturnValue was added in 0.1.x, stable package
        // Ensure old package references resolve
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/injector/v2/ModifyReturnValue",
            "com/llamalad7/mixinextras/injector/ModifyReturnValue"
        );

        // ============================================================
        // @ModifyReceiver CHANGES
        // ============================================================

        // Old: @ModifyReceiver early sub-package
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/injector/v2/ModifyReceiver",
            "com/llamalad7/mixinextras/injector/ModifyReceiver"
        );

        // ============================================================
        // @Local SUGAR ANNOTATION
        // ============================================================

        // Old: @Local in sugar package (mostly stable, but internal resolver changed)
        // The annotation itself stayed the same but the backing LocalRef/LocalIntRef etc changed
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/Local",
            "com/llamalad7/mixinextras/sugar/Local"
        );

        // Old: LocalRef<T> interface (0.1.x naming)
        // Some early versions used different generic wrapper names
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/ref/LocalRef",
            "com/llamalad7/mixinextras/sugar/ref/LocalRef"
        );

        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/ref/LocalIntRef",
            "com/llamalad7/mixinextras/sugar/ref/LocalIntRef"
        );

        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/ref/LocalFloatRef",
            "com/llamalad7/mixinextras/sugar/ref/LocalFloatRef"
        );

        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/ref/LocalDoubleRef",
            "com/llamalad7/mixinextras/sugar/ref/LocalDoubleRef"
        );

        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/ref/LocalBooleanRef",
            "com/llamalad7/mixinextras/sugar/ref/LocalBooleanRef"
        );

        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/ref/LocalLongRef",
            "com/llamalad7/mixinextras/sugar/ref/LocalLongRef"
        );

        // ============================================================
        // @Share ANNOTATION
        // ============================================================

        // Old: @Share for sharing values between handlers (added in 0.1.x)
        // Package stayed stable but internal sharing mechanism changed
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/sugar/Share",
            "com/llamalad7/mixinextras/sugar/Share"
        );

        // ============================================================
        // MIXIN CONFIG PLUGIN INTEGRATION
        // ============================================================

        // Old: MixinExtrasPlugin - mods referenced this in their mixin config
        // as the plugin class to trigger MixinExtras initialization
        // New: No longer needed, but must resolve to avoid ClassNotFoundException
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/MixinExtrasPlugin",
            "com/retromod/shim/api/common/embedded/MixinExtrasShim$PluginCompat"
        );

        // ============================================================
        // SERVICE / INTERNAL RESOLUTION
        // ============================================================

        // Old: MixinExtras service internals that mods should not have referenced
        // but sometimes did for advanced use cases
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/service/MixinExtrasService",
            "com/retromod/shim/api/common/embedded/MixinExtrasShim$ServiceCompat"
        );

        // Old: MixinExtrasVersion - version checking class
        // New: redirect to shim that always returns compatible version
        transformer.registerClassRedirect(
            "com/llamalad7/mixinextras/MixinExtrasVersion",
            "com/retromod/shim/api/common/embedded/MixinExtrasShim$VersionCompat"
        );

        // Old: MixinExtrasVersion.getVersion()
        // New: return current bundled version string
        transformer.registerMethodRedirect(
            "com/llamalad7/mixinextras/MixinExtrasVersion",
            "getVersion",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/common/embedded/MixinExtrasShim",
            "getVersion",
            "()Ljava/lang/String;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.MixinExtrasShim"
        };
    }
}
