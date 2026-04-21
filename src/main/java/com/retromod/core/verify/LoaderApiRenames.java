/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Curated rename table for high-impact mod-loader API changes that the
 * auto-indexed {@link McSymbolIndex} can't cover (because we don't have a
 * comprehensive loader-JAR index at transform time).
 *
 * <h3>Why hand-curated, not auto-indexed?</h3>
 * <p>The MC JAR is a single artifact per version — trivial to index. Loader
 * APIs are different: Fabric API is split into ~40 modules, each versioned
 * independently, and mod developers depend on an arbitrary subset. Building a
 * full symbol index would require knowing exactly which loader-API artifacts
 * correspond to the target MC version, which we don't have at CLI time.</p>
 *
 * <p>Instead, we maintain a small hand-curated table focused on the changes
 * that actually break mods in practice. The v1 scope rule: include a rename
 * only if it's used by &gt;10-20% of mods in the translatable ecosystem. If
 * the gap report later shows something we missed is actually common, we add
 * it. If something we included turns out to not matter, we drop it.</p>
 *
 * <h3>Data format</h3>
 * <p>The JSON file at {@code /retromod/loader-api-renames.json} has one section
 * per loader ({@code "fabric"}, {@code "neoforge"}, {@code "forge"}), each with:
 * <ul>
 *   <li>{@code renamed_classes} — map of old internal-name → new internal-name</li>
 *   <li>{@code renamed_methods} — map of {@code "owner#name desc"} → {@code "newOwner#newName newDesc"}</li>
 *   <li>{@code removed_classes} — list of internal-names that were deleted outright
 *       (no replacement available; the reference is just broken)</li>
 * </ul>
 * </p>
 *
 * <h3>Thread safety</h3>
 * <p>Instances are immutable after construction. Safe to share across threads.</p>
 */
public final class LoaderApiRenames {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-LoaderApiRenames");

    private static final String RESOURCE_PATH = "/retromod/loader-api-renames.json";

    /**
     * Known loader keys in the JSON file. Matches the enum values of
     * {@code com.retromod.api.platform.Loader} conceptually, but kept as strings
     * here so this class has zero dependency on the rest of RetroMod.
     */
    private static final String[] LOADER_KEYS = {"fabric", "neoforge", "forge"};

    /** Singleton loaded lazily on first use (guarded by double-checked locking). */
    private static volatile LoaderApiRenames instance;

    /** Internal-name → internal-name, across all loaders, deduplicated. */
    private final Map<String, String> renamedClasses;

    /** Fully-qualified member key → fully-qualified member key. */
    private final Map<String, String> renamedMethods;

    /** Set of internal-names known to be removed outright. */
    private final Set<String> removedClasses;

    /** Empty, always-returns-false instance used when the JSON fails to load. */
    private static final LoaderApiRenames EMPTY = new LoaderApiRenames(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptySet());

    private LoaderApiRenames(Map<String, String> renamedClasses,
                             Map<String, String> renamedMethods,
                             Set<String> removedClasses) {
        this.renamedClasses = renamedClasses;
        this.renamedMethods = renamedMethods;
        this.removedClasses = removedClasses;
    }

    /**
     * Returns the process-wide loader rename table. Loads from the bundled
     * JSON resource on first call and caches the result. If the resource
     * cannot be loaded, returns an empty table (every lookup returns null /
     * empty) rather than throwing — a misconfigured data file shouldn't
     * break transformation entirely.
     */
    public static LoaderApiRenames getInstance() {
        LoaderApiRenames local = instance;
        if (local != null) return local;
        synchronized (LoaderApiRenames.class) {
            if (instance != null) return instance;
            instance = loadFromResource();
            return instance;
        }
    }

    /**
     * Check whether {@code oldInternalName} is a known loader-API class that
     * was renamed to a new location.
     *
     * @return the new internal name if known, else {@code null}
     */
    public String getClassRename(String oldInternalName) {
        return renamedClasses.get(oldInternalName);
    }

    /**
     * Check whether the given member was renamed.
     *
     * @param owner      JVM internal class name of the old location
     * @param name       old method name
     * @param descriptor old method descriptor
     * @return a formatted key {@code "newOwner#newName newDesc"} if renamed,
     *         else {@code null}. (Callers parse this key to extract components.)
     */
    public String getMethodRename(String owner, String name, String descriptor) {
        return renamedMethods.get(memberKey(owner, name, descriptor));
    }

    /**
     * @return {@code true} if the class was deleted outright with no
     *         replacement (callers should flag this as a broken reference
     *         that cannot be auto-fixed)
     */
    public boolean isRemoved(String internalName) {
        return removedClasses.contains(internalName);
    }

    /** Size of the rename table — for diagnostics/logging only. */
    public int size() {
        return renamedClasses.size() + renamedMethods.size() + removedClasses.size();
    }

    /**
     * Build the canonical member-key format used for method rename lookups.
     * Package-private for tests.
     */
    static String memberKey(String owner, String name, String descriptor) {
        return owner + "#" + name + " " + descriptor;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // LOADING
    // ═══════════════════════════════════════════════════════════════════════

    private static LoaderApiRenames loadFromResource() {
        try (InputStream in = LoaderApiRenames.class.getResourceAsStream(RESOURCE_PATH)) {
            if (in == null) {
                LOGGER.warn("Loader API rename table not found at {} — loader-API checks disabled",
                        RESOURCE_PATH);
                return EMPTY;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
                return buildFromJson(root);
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to read loader API rename table: {}", e.getMessage());
            return EMPTY;
        } catch (Exception e) {
            // Malformed JSON, unexpected structure — don't break the transformer
            LOGGER.warn("Loader API rename table is malformed: {}", e.getMessage());
            return EMPTY;
        }
    }

    private static LoaderApiRenames buildFromJson(JsonObject root) {
        Map<String, String> classes = new HashMap<>();
        Map<String, String> methods = new HashMap<>();
        Set<String> removed = new HashSet<>();

        // Per-loader try-catch so a malformed section for one loader doesn't
        // wipe out valid data from the others. One broken entry in the Forge
        // section shouldn't also invalidate NeoForge renames — the biggest
        // surface area.
        for (String loader : LOADER_KEYS) {
            if (!root.has(loader)) continue;
            try {
                JsonObject loaderSection = root.getAsJsonObject(loader);

                // renamed_classes: { "old/internal/Name": "new/internal/Name" }
                if (loaderSection.has("renamed_classes")) {
                    JsonObject classMap = loaderSection.getAsJsonObject("renamed_classes");
                    for (Map.Entry<String, ?> e : classMap.entrySet()) {
                        String oldName = e.getKey();
                        String newName = classMap.get(oldName).getAsString();
                        // If two loaders disagree on a rename, first-wins. Shouldn't
                        // happen with the curated data, but deterministic is better
                        // than "last-wins" which depends on key iteration order.
                        classes.putIfAbsent(oldName, newName);
                    }
                }

                // renamed_methods: { "owner#name desc": "newOwner#newName newDesc" }
                if (loaderSection.has("renamed_methods")) {
                    JsonObject methodMap = loaderSection.getAsJsonObject("renamed_methods");
                    for (Map.Entry<String, ?> e : methodMap.entrySet()) {
                        methods.putIfAbsent(e.getKey(), methodMap.get(e.getKey()).getAsString());
                    }
                }

                // removed_classes: [ "internal/Name", ... ]
                if (loaderSection.has("removed_classes")) {
                    loaderSection.getAsJsonArray("removed_classes")
                            .forEach(el -> removed.add(el.getAsString()));
                }
            } catch (Exception sectionError) {
                // Log and continue — malformed data for one loader must not
                // silently kill all loader-rename coverage.
                LOGGER.warn("Loader-API rename section '{}' is malformed, skipping it: {}",
                        loader, sectionError.getMessage());
            }
        }

        LOGGER.info("Loaded loader-API rename table: {} classes, {} methods, {} removed",
                classes.size(), methods.size(), removed.size());

        return new LoaderApiRenames(
                Collections.unmodifiableMap(classes),
                Collections.unmodifiableMap(methods),
                Collections.unmodifiableSet(removed));
    }

    /**
     * Test-only: build an instance from explicit maps, bypassing the JSON load.
     * Useful for unit tests that want a controlled rename table.
     */
    public static LoaderApiRenames forTesting(Map<String, String> renamedClasses,
                                              Map<String, String> renamedMethods,
                                              Set<String> removedClasses) {
        return new LoaderApiRenames(
                Map.copyOf(Objects.requireNonNullElse(renamedClasses, Map.of())),
                Map.copyOf(Objects.requireNonNullElse(renamedMethods, Map.of())),
                Set.copyOf(Objects.requireNonNullElse(removedClasses, Set.of())));
    }
}
