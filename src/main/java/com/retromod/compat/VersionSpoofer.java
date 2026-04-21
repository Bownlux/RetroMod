/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.compat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Runtime version spoofer — lies about installed mod versions when a calling
 * mod does its own version check in code.
 *
 * <h3>The problem this solves</h3>
 * <p>RetroMod already relaxes {@code "depends"} entries in mod metadata so
 * Fabric Loader stops blocking mods at load time. That fixes metadata-level
 * incompatibilities. But some mods (REI, old tech mods, anything with a
 * "strict compatibility" check) also run code at init that asks Fabric Loader
 * for the installed version of a dependency and compares it against a
 * hard-coded version range. When the installed version is newer than the
 * range, the mod pops up a "your X is unsupported" error and refuses to work.</p>
 *
 * <p>Example from REI 14 on Cloth Config 15:</p>
 * <pre>
 * String cloth = FabricLoader.getInstance()
 *     .getModContainer("cloth-config").get()
 *     .getMetadata().getVersion().getFriendlyString();
 * // "15.0.140" — newer than REI expects
 * if (!acceptedRange.matches(cloth)) {
 *     showCompatibilityPopup("Cloth Config " + cloth + " unsupported!");
 * }
 * </pre>
 *
 * <h3>How the spoofer works</h3>
 * <p>RetroMod's bytecode transformer redirects every call to
 * {@code FabricLoader.getModContainer(modId)} inside transformed mods to
 * {@link #getModContainer(Object, String)}. We intercept the call, look up
 * {@code modId} in a curated spoof table, and either:</p>
 * <ul>
 *   <li>Return the real {@link net.fabricmc.loader.api.ModContainer} unchanged
 *       (the common case — most calls don't need lying),</li>
 *   <li>Or return a {@link Proxy}-wrapped {@code ModContainer} whose
 *       {@code getMetadata().getVersion().getFriendlyString()} chain returns
 *       the spoofed version string instead of the real one.</li>
 * </ul>
 *
 * <p>Only the version-reporting chain is spoofed. Every other method on the
 * container/metadata/version passes through to the real implementation, so
 * mods that inspect other fields (authors, description, dependencies) see
 * real data.</p>
 *
 * <h3>Which mods get spoofed?</h3>
 * <p>Curated list in {@code /retromod/version-spoofs.json}. Each entry says
 * "when any caller asks for the version of mod X, tell them Y." The spoofed
 * version is chosen to satisfy the widest practical version-range constraint
 * for that mod family — e.g. Cloth Config spoofs to {@code "13.999.999"},
 * which satisfies both {@code ">=13.0.0 &lt;14.0.0"} (REI 14's check) and
 * most other checks that expect "cloth-config 13-something."</p>
 *
 * <h3>Reflection-only implementation</h3>
 * <p>This class deliberately uses {@code Object} parameter types instead of
 * importing {@code net.fabricmc.loader.api.FabricLoader} / {@code ModContainer}
 * / {@code ModMetadata} / {@code Version}. RetroMod ships as a standalone JAR
 * that also runs in the CLI, where Fabric Loader is not on the classpath.
 * Using reflection keeps the core JAR loadable in those environments; the
 * spoofer simply no-ops if Fabric types aren't present.</p>
 */
public final class VersionSpoofer {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-VersionSpoofer");

    private static final String SPOOF_RESOURCE = "/retromod/version-spoofs.json";

    /** Default: ON. Disable via {@code -Dretromod.spoofVersions=false} for debugging. */
    private static final boolean SPOOF_ENABLED =
            Boolean.parseBoolean(System.getProperty("retromod.spoofVersions", "true"));

    /** {@code modId → spoofedVersionString}. Populated lazily on first use. */
    private static volatile Map<String, String> spoofTable;

    /** How many times we've returned a spoofed container (for diagnostics). */
    private static final java.util.concurrent.atomic.AtomicInteger spoofsApplied =
            new java.util.concurrent.atomic.AtomicInteger();

    private VersionSpoofer() {}

    // ═══════════════════════════════════════════════════════════════════════
    // REDIRECT TARGETS
    // ═══════════════════════════════════════════════════════════════════════
    //
    // These methods are called by transformed mod bytecode. The redirect from
    // FabricLoader.getModContainer(String) to getModContainer(Object, String)
    // is registered in VersionSpoofShim at transformer init time.

    /**
     * Replacement for {@code FabricLoader.getModContainer(String)}. Called
     * from transformed mod bytecode.
     *
     * <p>The {@code fabricLoader} parameter is the receiver object (a real
     * {@code net.fabricmc.loader.api.FabricLoader} instance); we take it as
     * {@code Object} so this class compiles without a Fabric dep.</p>
     *
     * @return {@code Optional<ModContainer>} — real or proxy-wrapped
     */
    public static Optional<?> getModContainer(Object fabricLoader, String modId) {
        // Invoke the real method reflectively. Errors fall back to Optional.empty()
        // — the original Fabric behaviour for missing mods — so a reflection
        // failure never breaks the caller harder than the real method would.
        Optional<?> real;
        try {
            Method m = fabricLoader.getClass().getMethod("getModContainer", String.class);
            Object result = m.invoke(fabricLoader, modId);
            real = (result instanceof Optional<?>) ? (Optional<?>) result : Optional.empty();
        } catch (Throwable t) {
            LOGGER.debug("Spoofer passthrough failed for {}: {}", modId, t.getMessage());
            return Optional.empty();
        }

        if (!SPOOF_ENABLED) return real;

        String spoofedVersion = getSpoofTable().get(modId);
        if (spoofedVersion == null) return real;         // no spoof needed
        if (!real.isPresent()) return real;               // mod not installed — can't fake it

        try {
            Object wrapped = wrapContainer(real.get(), spoofedVersion);
            spoofsApplied.incrementAndGet();
            return Optional.of(wrapped);
        } catch (Throwable t) {
            // If proxy construction fails for any reason, fall back to the real
            // container. Spoofing is best-effort diagnostic help — never block
            // the mod from loading just because the proxy setup went sideways.
            LOGGER.debug("Could not build spoof proxy for {} ({}): {}",
                    modId, spoofedVersion, t.getMessage());
            return real;
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PROXY CONSTRUCTION
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Wrap a real {@code ModContainer} so its {@code getMetadata()} returns a
     * spoofing proxy. All other methods (parent, roots, findPath, etc.) pass
     * through unchanged.
     */
    private static Object wrapContainer(Object realContainer, String spoofedVersion)
            throws ClassNotFoundException {
        Class<?> containerIface = Class.forName("net.fabricmc.loader.api.ModContainer");
        return Proxy.newProxyInstance(
                containerIface.getClassLoader(),
                new Class<?>[]{containerIface},
                new ContainerHandler(realContainer, spoofedVersion));
    }

    /**
     * Wrap a real {@code ModMetadata} so its {@code getVersion()} returns a
     * spoofing proxy. Every other accessor (id, name, authors, dependencies,
     * …) returns the real value so mods that care about other metadata
     * fields see accurate data.
     */
    private static Object wrapMetadata(Object realMetadata, String spoofedVersion)
            throws ClassNotFoundException {
        Class<?> metaIface = Class.forName("net.fabricmc.loader.api.metadata.ModMetadata");
        return Proxy.newProxyInstance(
                metaIface.getClassLoader(),
                new Class<?>[]{metaIface},
                new MetadataHandler(realMetadata, spoofedVersion));
    }

    /**
     * Wrap a real {@code Version} object so its text accessors return the
     * spoofed string. We intercept:
     * <ul>
     *   <li>{@code getFriendlyString()} — the human-readable form
     *       (most common version-check code path)</li>
     *   <li>{@code toString()}</li>
     *   <li>{@code compareTo(Version)} — returns 0 so any SemVer range check
     *       that uses compareTo thinks we match the expected version</li>
     * </ul>
     */
    private static Object wrapVersion(Object realVersion, String spoofedVersion)
            throws ClassNotFoundException {
        Class<?> versionIface = Class.forName("net.fabricmc.loader.api.Version");
        return Proxy.newProxyInstance(
                versionIface.getClassLoader(),
                new Class<?>[]{versionIface},
                new VersionHandler(realVersion, spoofedVersion));
    }

    // Invocation handlers kept as static nested classes (not lambdas) so their
    // instance state — the real target + the spoof string — is always captured
    // by reference, never by value-at-capture-time.

    private static final class ContainerHandler implements InvocationHandler {
        private final Object real;
        private final String spoofVersion;
        ContainerHandler(Object real, String spoofVersion) {
            this.real = real; this.spoofVersion = spoofVersion;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getMetadata".equals(method.getName()) && method.getParameterCount() == 0) {
                try {
                    Object meta = method.invoke(real);
                    return meta == null ? null : wrapMetadata(meta, spoofVersion);
                } catch (Throwable t) {
                    return method.invoke(real, args);
                }
            }
            return method.invoke(real, args);
        }
    }

    private static final class MetadataHandler implements InvocationHandler {
        private final Object real;
        private final String spoofVersion;
        MetadataHandler(Object real, String spoofVersion) {
            this.real = real; this.spoofVersion = spoofVersion;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if ("getVersion".equals(method.getName()) && method.getParameterCount() == 0) {
                try {
                    Object ver = method.invoke(real);
                    return ver == null ? null : wrapVersion(ver, spoofVersion);
                } catch (Throwable t) {
                    return method.invoke(real, args);
                }
            }
            return method.invoke(real, args);
        }
    }

    private static final class VersionHandler implements InvocationHandler {
        private final Object real;
        private final String spoofVersion;
        VersionHandler(Object real, String spoofVersion) {
            this.real = real; this.spoofVersion = spoofVersion;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (("getFriendlyString".equals(name) || "toString".equals(name))
                    && method.getParameterCount() == 0) {
                return spoofVersion;
            }
            // compareTo(Version) — neutralize by claiming equal. Any SemVer
            // range check that boils down to "compare and compare" will see
            // "match" and accept us.
            if ("compareTo".equals(name) && method.getParameterCount() == 1) {
                return 0;
            }
            // hashCode / equals — pass through so proxy identity behaves.
            return method.invoke(real, args);
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // DATA-DRIVEN RULES
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Lazy-load the spoof table from the bundled JSON resource. If the
     * resource is missing or malformed, the spoofer operates in "passthrough-
     * only" mode — all calls return the real container.
     */
    private static Map<String, String> getSpoofTable() {
        Map<String, String> local = spoofTable;
        if (local != null) return local;
        synchronized (VersionSpoofer.class) {
            if (spoofTable != null) return spoofTable;
            spoofTable = Collections.unmodifiableMap(loadTable());
            LOGGER.info("VersionSpoofer loaded {} spoof rule(s) from {}",
                    spoofTable.size(), SPOOF_RESOURCE);
            return spoofTable;
        }
    }

    private static Map<String, String> loadTable() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = VersionSpoofer.class.getResourceAsStream(SPOOF_RESOURCE)) {
            if (in == null) {
                LOGGER.warn("{} not found — spoofer will passthrough every call", SPOOF_RESOURCE);
                return out;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                // Top-level schema: { "spoofs": { "cloth-config": "13.999.999", ... } }
                if (root.has("spoofs")) {
                    JsonObject spoofs = root.getAsJsonObject("spoofs");
                    for (Map.Entry<String, JsonElement> e : spoofs.entrySet()) {
                        out.put(e.getKey(), e.getValue().getAsString());
                    }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("Could not load spoof table: {}", e.getMessage());
        }
        return out;
    }

    /**
     * Number of times a spoofed container has been returned (for diagnostics).
     */
    public static int getSpoofsApplied() {
        return spoofsApplied.get();
    }

    /**
     * Test-only hook — reset the cached table so unit tests can re-populate
     * with a controlled rule set.
     */
    static void resetForTesting() {
        spoofTable = null;
        spoofsApplied.set(0);
    }

    /**
     * Test-only hook — install a specific rule map, bypassing the JSON load.
     */
    static void setSpoofTableForTesting(Map<String, String> table) {
        spoofTable = Collections.unmodifiableMap(new HashMap<>(table));
    }
}
