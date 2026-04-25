/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.jar.JarFile;

/**
 * Mod-author opt-out mechanism.
 *
 * <h3>What this is</h3>
 * <p>RetroMod transforms mod bytecode at load time. The end user has every
 * right to do that with their own copy of a mod, and there's no statutory
 * obligation that requires us to ask first — RetroMod sits in the same legal
 * frame as Forge, Fabric, Mixin, and OptiFine, none of which ask either.</p>
 *
 * <p>That said, some mod authors have legitimate reasons to prefer their mod
 * NOT be transformed by RetroMod:</p>
 * <ul>
 *   <li>Paid / Patreon mods where transform-introduced bugs would unfairly
 *       reflect on the author</li>
 *   <li>Mods with explicit "no modification" clauses in their license or
 *       README</li>
 *   <li>Mods built around precise behavior that RetroMod's general-purpose
 *       shimming might break in subtle ways</li>
 * </ul>
 *
 * <p>For those authors, RetroMod honors a single, simple opt-out signal: a
 * marker file at <code>META-INF/retromod-opt-out</code> inside the mod's JAR.
 * If present, RetroMod skips transformation entirely for that JAR — the JAR
 * passes through untouched to {@code mods/}.</p>
 *
 * <h3>How a mod author opts out</h3>
 * <p>Add an empty file at {@code src/main/resources/META-INF/retromod-opt-out}
 * to the mod's source tree. The build will package it. That's the whole
 * mechanism — no version coordination, no API to track, no API surface to
 * maintain.</p>
 *
 * <h3>How a user can override</h3>
 * <p>Some users may want to force-transform a mod whose author opted out
 * (e.g., to make an abandoned mod work on a newer MC). The override is
 * {@code -Dretromod.honorOptOut=false} on the JVM command line. Default is
 * {@code true} (opt-out is honored).</p>
 *
 * <h3>Implementation choices</h3>
 * <p>The marker file lives at {@code META-INF/}, parallel to {@code mods.toml}
 * and {@code fabric.mod.json}, so it's discoverable next to other
 * mod-loader metadata. The file is empty — only its presence matters. We
 * deliberately do NOT read its contents because content gives us a way to
 * mis-parse and accidentally NOT honor the opt-out.</p>
 *
 * <p>An alternative would have been a JSON key inside fabric.mod.json /
 * mods.toml. We rejected that because it requires per-loader handling and
 * the parser surface is bigger; a single sentinel file is foolproof.</p>
 */
public final class OptOutCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-OptOut");

    /** The well-known marker path inside a mod JAR. Empty file; presence is the signal. */
    public static final String OPT_OUT_MARKER = "META-INF/retromod-opt-out";

    /**
     * Whether the user has chosen to ignore opt-out markers. Default false.
     * Setting {@code -Dretromod.honorOptOut=false} flips this to true ("ignore
     * markers"), which lets the user force-transform a mod whose author
     * requested opt-out.
     */
    private static final boolean IGNORE_OPT_OUT =
            !Boolean.parseBoolean(System.getProperty("retromod.honorOptOut", "true"));

    private OptOutCheck() {}

    /**
     * @return {@code true} if the JAR contains the opt-out marker AND the user
     *         hasn't disabled marker-honoring. Caller should skip transformation
     *         when this returns true.
     */
    public static boolean isOptedOut(Path modJar) {
        if (IGNORE_OPT_OUT) return false;
        if (modJar == null) return false;
        try (JarFile jar = new JarFile(modJar.toFile())) {
            return jar.getEntry(OPT_OUT_MARKER) != null;
        } catch (IOException e) {
            // Can't open the JAR — let the regular transform path try (and
            // probably fail with a clearer error than we'd produce here).
            LOGGER.debug("Could not read {} to check opt-out: {}", modJar, e.getMessage());
            return false;
        }
    }

    /**
     * Convenience: log a consistent message when a JAR is being skipped due
     * to opt-out. Call this from each entry point so the user sees the same
     * line regardless of which transformer noticed.
     */
    public static void logSkipped(Path modJar) {
        LOGGER.info("Skipping {} — mod author opted out via {} marker. " +
                "Override with -Dretromod.honorOptOut=false if you want to force transform.",
                modJar.getFileName(), OPT_OUT_MARKER);
    }
}
