/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.polyfill;

import com.retromod.core.RetroModTransformer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Registry that discovers and manages polyfill providers.
 *
 * Loads PolyfillProvider implementations via ServiceLoader,
 * checks config for enabled categories, and registers their
 * redirects with the transformer.
 */
public class PolyfillRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Polyfills");

    private final List<PolyfillProvider> providers = new ArrayList<>();
    private final Set<String> enabledCategories = new HashSet<>();
    private boolean polyfillsEnabled = true;

    public PolyfillRegistry() {
        // All categories enabled by default
        enabledCategories.add("fabric_api");
        enabledCategories.add("rendering");
        enabledCategories.add("entity");
        enabledCategories.add("mixin_targets");
        enabledCategories.add("minecraft_vanilla");
        enabledCategories.add("forge");
        enabledCategories.add("neoforge");
        enabledCategories.add("thirdparty");
    }

    /**
     * Set whether polyfills are globally enabled.
     */
    public void setEnabled(boolean enabled) {
        this.polyfillsEnabled = enabled;
    }

    /**
     * Enable or disable a specific category.
     */
    public void setCategoryEnabled(String category, boolean enabled) {
        if (enabled) {
            enabledCategories.add(category);
        } else {
            enabledCategories.remove(category);
        }
    }

    /**
     * Load all polyfill providers via ServiceLoader and register
     * enabled ones with the transformer.
     */
    public void loadAndRegister(RetroModTransformer transformer) {
        if (!polyfillsEnabled) {
            LOGGER.info("Polyfills are disabled in config");
            return;
        }

        // Discover providers via ServiceLoader
        ServiceLoader<PolyfillProvider> loader = ServiceLoader.load(PolyfillProvider.class);

        int registered = 0;
        int skipped = 0;

        for (PolyfillProvider provider : loader) {
            providers.add(provider);

            if (enabledCategories.contains(provider.getCategory())) {
                try {
                    provider.registerPolyfills(transformer);
                    registered++;

                    String[] removed = provider.getRemovedClasses();
                    LOGGER.info("Polyfill loaded: {} (category: {}, {} removed classes bridged)",
                            provider.getName(), provider.getCategory(), removed.length);
                } catch (Exception e) {
                    LOGGER.warn("Failed to register polyfill: {}", provider.getName(), e);
                }
            } else {
                skipped++;
                LOGGER.debug("Polyfill skipped (category disabled): {}", provider.getName());
            }
        }

        LOGGER.info("Polyfill system: {} registered, {} skipped", registered, skipped);
    }

    /**
     * @return All discovered providers (both enabled and disabled)
     */
    public List<PolyfillProvider> getProviders() {
        return Collections.unmodifiableList(providers);
    }

    /**
     * @return Set of enabled category names
     */
    public Set<String> getEnabledCategories() {
        return Collections.unmodifiableSet(enabledCategories);
    }
}
