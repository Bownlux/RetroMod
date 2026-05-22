/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core;

/**
 * Loader-agnostic holder for the runtime-detected target MC version.
 *
 * <p>Used to live as a {@code public static String} on {@link Retromod},
 * but {@code Retromod implements net.fabricmc.api.ModInitializer} — a Fabric-only
 * supertype. Reading {@code Retromod.TARGET_MC_VERSION} from {@code RetromodForge}
 * (or {@code RetromodNeoForge}) on a Forge runtime triggered class linkage of
 * {@code Retromod}, which then needed {@code ModInitializer} on the classpath,
 * which doesn't exist there. Forge then crashed with:
 *
 * <pre>
 *   java.lang.NoClassDefFoundError: net/fabricmc/api/ModInitializer
 *     at RetromodForge.initializeHybridEngine
 * </pre>
 *
 * <p>Putting the constant on a class with no Fabric / Forge / NeoForge supertype
 * makes it safe to read from any loader's entry point.
 *
 * <p>The value is mutable because it's set by the loader-specific entry points
 * after they auto-detect the running MC version. Default is a sensible fallback
 * for the case where detection fails.
 */
public final class RetromodVersion {

    /**
     * The MC version Retromod is translating mods <i>to</i>. Auto-detected
     * from the running mod loader by whichever entry point boots first
     * ({@link Retromod#onInitialize()} on Fabric, {@code RetromodForge.<init>()}
     * on Forge, {@code RetromodNeoForge.<init>()} on NeoForge).
     */
    public static volatile String TARGET_MC_VERSION = "1.21.4";

    private RetromodVersion() {}

    // ── MC version math ─────────────────────────────────────────────────────
    // Loader-agnostic and free of any Fabric/Forge/NeoForge supertype, so any
    // entry point can call these. They used to live on RetromodPreLaunch, but
    // that class `implements PreLaunchEntrypoint` (Fabric-only) — so
    // RetromodNeoForge/RetromodForge calling them dragged in PreLaunchEntrypoint
    // and crashed with NoClassDefFoundError on NeoForge/Forge even with no mods
    // (issue #40). Same lesson as TARGET_MC_VERSION above.

    /**
     * Whether the host MC version is 26.1+ (the first unobfuscated release, where
     * Fabric switched from intermediary to Mojang names). Gates the
     * intermediary→Mojang remap and 26.1 class moves: applying those on a pre-26.1
     * host rewrites a mod's working references to names that don't exist yet.
     * Unparseable/unknown → {@code true} (preserve published 26.1 behavior).
     */
    public static boolean isUnobfuscatedTarget(String hostVersion) {
        if (hostVersion == null) return true;
        try {
            java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(\\d+)").matcher(hostVersion.trim());
            if (!m.find()) return true;
            return Integer.parseInt(m.group(1)) >= 26;
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Whether MC version {@code a} is strictly newer than {@code b}. Used to skip
     * version shims whose target is newer than the host. Unparseable → {@code false}
     * (don't skip) so we never over-exclude.
     */
    public static boolean mcVersionExceeds(String a, String b) {
        try {
            return compareMcVersions(a, b) > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Numeric per-component MC version compare ({@code 1.21.8} vs {@code 26.1} → negative). */
    public static int compareMcVersions(String a, String b) {
        int[] pa = parseMcVersion(a);
        int[] pb = parseMcVersion(b);
        int n = Math.max(pa.length, pb.length);
        for (int i = 0; i < n; i++) {
            int x = i < pa.length ? pa[i] : 0;
            int y = i < pb.length ? pb[i] : 0;
            if (x != y) return Integer.compare(x, y);
        }
        return 0;
    }

    private static int[] parseMcVersion(String v) {
        if (v == null) return new int[0];
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)*)").matcher(v.trim());
        if (!m.find()) return new int[0];
        String[] parts = m.group(1).split("\\.");
        int[] out = new int[parts.length];
        for (int i = 0; i < parts.length; i++) out[i] = Integer.parseInt(parts[i]);
        return out;
    }
}
