/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill.minecraft.mixin;

import com.retromod.core.RetroModTransformer;
import com.retromod.polyfill.PolyfillProvider;

/**
 * Polyfill for removed Minecraft classes that are commonly used as mixin targets.
 *
 * When mods have mixins targeting classes that were removed in newer MC versions,
 * the mixin framework fails during validation because the target class doesn't exist.
 * This polyfill provides stub classes so the mixin system can find them.
 *
 * Known removed classes:
 * - class_5500 (ChatOptionsScreen) - removed/merged, broke No Chat Reports
 */
public class MixinTargetPolyfill implements PolyfillProvider {

    @Override
    public String getName() {
        return "Mixin Target Stubs";
    }

    @Override
    public String getCategory() {
        return "mixin_targets";
    }

    @Override
    public String[] getRemovedClasses() {
        return new String[]{
            "net/minecraft/class_5500"   // ChatOptionsScreen
        };
    }

    @Override
    public String[] getPolyfillClasses() {
        return new String[]{
            "com.retromod.polyfill.minecraft.mixin.embedded.ChatOptionsScreenStub"
        };
    }

    @Override
    public void registerPolyfills(RetroModTransformer transformer) {
        // Redirect the removed class to our stub
        transformer.registerClassRedirect(
            "net/minecraft/class_5500",
            "com/retromod/polyfill/minecraft/mixin/embedded/ChatOptionsScreenStub"
        );

        for (String cls : getPolyfillClasses()) {
            transformer.registerEmbeddedShim(cls);
        }
    }
}
