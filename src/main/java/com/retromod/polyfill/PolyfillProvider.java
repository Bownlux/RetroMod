/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill;

import com.retromod.core.RetroModTransformer;

/**
 * Interface for polyfill providers that re-implement removed APIs.
 *
 * Unlike VersionShims (which redirect existing calls), polyfills provide
 * actual class implementations for APIs that were completely removed.
 * This allows old mods to find classes/methods that no longer exist
 * in the current Minecraft version.
 *
 * Polyfills are discovered via ServiceLoader and can be toggled per-category
 * in config/retromod/config.json.
 */
public interface PolyfillProvider {

    /**
     * @return Human-readable name (e.g., "Fabric TinyMapping API")
     */
    String getName();

    /**
     * @return Category for config toggling (e.g., "fabric_api", "rendering", "mixin_targets")
     */
    String getCategory();

    /**
     * @return Internal names of the removed classes this polyfill replaces
     */
    String[] getRemovedClasses();

    /**
     * @return Fully qualified names of embedded polyfill classes shipped in the JAR
     */
    String[] getPolyfillClasses();

    /**
     * Register class/method/field redirects and superclass redirects
     * to wire up the polyfill.
     *
     * @param transformer The transformer to register redirects with
     */
    void registerPolyfills(RetroModTransformer transformer);
}
