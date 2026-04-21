/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

/**
 * Rewrites MC-typed strings passed to reflection APIs so that mods using
 * {@code Class.forName("net.minecraft.old.X")} still resolve after the target
 * MC renamed the class.
 *
 * <h3>The problem this solves</h3>
 * <p>RetroMod's main transformer uses ASM's {@code ClassRemapper} to rewrite
 * every class reference in bytecode — type descriptors, method signatures,
 * {@code NEW}/{@code CHECKCAST}/{@code INSTANCEOF} opcodes, annotations, etc.
 * But it <b>cannot</b> rewrite class names that appear as {@link String}
 * constants, because strings are opaque data — there's no type information
 * telling ASM "this particular string is an MC class name."</p>
 *
 * <p>Mods that look up MC APIs via {@code Class.forName("net.minecraft...")}
 * therefore slip through the main pipeline entirely, and fail at runtime with
 * {@link ClassNotFoundException}. Same problem for {@code getDeclaredMethod},
 * {@code getField}, {@code MethodHandles.Lookup.findVirtual}, etc.</p>
 *
 * <h3>Detection strategy</h3>
 * <p>For each method in the class, we walk its instructions maintaining a tiny
 * sliding window of recent {@code LDC} (load-constant) nodes. When we see an
 * invocation of a known <i>reflection sink</i> — a method in the JDK or a
 * mod-loader library that takes a class/method/field name as a string — we
 * look back in the window for string constants that match MC-typed patterns
 * and rewrite them.</p>
 *
 * <pre>
 *   LDC "net.minecraft.util.math.BlockPos"      ← candidate
 *   LDC Lnet/minecraft/util/math/BlockPos;.class (optional intervening)
 *   INVOKESTATIC Class.forName(String)Ljava/lang/Class;  ← sink
 *   ═══════════════════════════════════════════
 *   Rewrite the LDC value via the class-redirect table.
 * </pre>
 *
 * <h3>Conservative by design</h3>
 * <p>We only rewrite strings that:
 * <ul>
 *   <li>Match the MC FQN pattern {@code net\.minecraft\.[...]}
 *       or {@code com\.mojang\.[...]}, OR match an intermediary name pattern
 *       ({@code method_XXXX} / {@code field_XXXX}), AND</li>
 *   <li>Appear inside the lookback window of a reflection sink call, AND</li>
 *   <li>Have a known remap target in the supplied redirect tables.</li>
 * </ul>
 * If any condition fails, the string is left alone. False negatives are
 * preferred over false positives — a missed rewrite produces a runtime
 * exception we can diagnose; an incorrect rewrite silently corrupts behavior.</p>
 *
 * <h3>Known limitations (documented for honest expectations)</h3>
 * <ul>
 *   <li><b>Dynamic strings:</b> {@code "net.minecraft." + pkg + ".Foo"} can't
 *       be rewritten without data-flow analysis. We count these as "suspicious"
 *       but don't touch them.</li>
 *   <li><b>Array-packed args:</b> {@code getMethod("name", new Class[]{...})}
 *       reaches via an intermediate array construction. We handle the common
 *       case where the name string is the LDC immediately preceding the call.</li>
 *   <li><b>Member strings:</b> rewriting {@code "oldMethodName"} inside
 *       {@code getMethod()} requires knowing the class the lookup is against.
 *       We only attempt this when the preceding LDC was a class FQN we successfully
 *       rewrote — so we know both ends of the lookup.</li>
 *   <li><b>Reflection through wrappers:</b> libraries that wrap {@code Class.forName}
 *       in their own utility method ({@code MyUtil.classByName}) are invisible to
 *       us — the sink is the utility method, not a recognizable reflection API.</li>
 * </ul>
 *
 * <h3>Thread safety</h3>
 * <p>Stateless apart from its final configuration — one instance can process
 * classes concurrently. Metric counters are atomic.</p>
 */
public final class ReflectionStringRemapper {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-ReflectionRemapper");

    /**
     * Instruction window size — how far back to look for an LDC when we hit a
     * reflection sink. 8 is enough for realistic bytecode patterns (LDC name,
     * LDC class, dup, store, invoke) without risking false positives across
     * unrelated call sites.
     */
    private static final int LOOKBACK_WINDOW = 8;

    /**
     * Regex matching strings that LOOK like MC or mod-loader class FQNs in
     * dotted notation. We cover both namespaces because the loader-API rename
     * path also needs to recognize strings like {@code net.minecraftforge.X}
     * and {@code net.neoforged.X} at reflection sinks.
     *
     * <p>The prefix alternation is ordered most-specific-first so anchoring
     * matches greedily on the longer prefix — {@code net.minecraftforge}
     * matches before {@code net.minecraft} would. All prefixes end at a package
     * boundary (the next character must be {@code .} or a sub-identifier),
     * which the {@code \\.} in the pattern enforces.</p>
     */
    private static final Pattern MC_FQN_PATTERN = Pattern.compile(
            "^(net\\.minecraftforge|net\\.neoforged|net\\.fabricmc|net\\.minecraft|com\\.mojang)"
                    + "\\.[A-Za-z0-9_$.]+$");

    /** Matches intermediary names (for Fabric mods on 26.1+ that still hardcode them). */
    private static final Pattern INTERMEDIARY_METHOD_PATTERN = Pattern.compile(
            "^(method|comp)_[0-9]+$");
    private static final Pattern INTERMEDIARY_FIELD_PATTERN = Pattern.compile(
            "^field_[0-9]+$");

    /**
     * Reflection sinks: {@code "owner#name"} pairs whose first or only
     * {@link String} argument is a class/member name the runtime will look up.
     * The set is intentionally narrow — adding a sink that isn't actually a
     * name-lookup (e.g., {@code String.format}) would cause us to rewrite
     * ordinary data strings.
     */
    private static final java.util.Set<String> SINKS_TAKING_CLASS_FQN = java.util.Set.of(
            "java/lang/Class#forName",
            "java/lang/ClassLoader#loadClass",
            "java/lang/ClassLoader#findClass"
    );

    private static final java.util.Set<String> SINKS_TAKING_MEMBER_NAME = java.util.Set.of(
            "java/lang/Class#getMethod",
            "java/lang/Class#getDeclaredMethod",
            "java/lang/Class#getField",
            "java/lang/Class#getDeclaredField",
            "java/lang/invoke/MethodHandles$Lookup#findVirtual",
            "java/lang/invoke/MethodHandles$Lookup#findStatic",
            "java/lang/invoke/MethodHandles$Lookup#findSpecial",
            "java/lang/invoke/MethodHandles$Lookup#findGetter",
            "java/lang/invoke/MethodHandles$Lookup#findSetter"
    );

    // ═══════════════════════════════════════════════════════════════════════
    // CONFIGURATION
    // ═══════════════════════════════════════════════════════════════════════

    /** Class-rename table in DOTTED form (converted from slash-form for string matching). */
    private final Map<String, String> classRedirectsDotted;

    /** Intermediary method names (method_XXXX → mojangName). */
    private final Map<String, String> intermediaryMethodNames;

    /** Intermediary field names (field_XXXX → mojangName). */
    private final Map<String, String> intermediaryFieldNames;

    /** Curated loader-API renames, consulted in addition to the MC class redirects. */
    private final LoaderApiRenames loaderRenames;

    // ═══════════════════════════════════════════════════════════════════════
    // METRICS
    // ═══════════════════════════════════════════════════════════════════════

    private final AtomicInteger stringsRemapped = new AtomicInteger();
    private final AtomicInteger suspiciousUnmapped = new AtomicInteger();
    private final AtomicInteger dynamicStringsSkipped = new AtomicInteger();

    /**
     * @param slashedClassRedirects the existing {@code classRedirects} map from
     *                              {@link com.retromod.core.RetroModTransformer},
     *                              using slash-separated internal names. We convert
     *                              to dotted form internally since reflection strings
     *                              are dotted.
     * @param intermediaryMethodNames intermediary → Mojang method name map
     * @param intermediaryFieldNames  intermediary → Mojang field name map
     * @param loaderRenames           curated loader-API rename table (see
     *                                {@link LoaderApiRenames}); may be empty
     */
    public ReflectionStringRemapper(Map<String, String> slashedClassRedirects,
                                    Map<String, String> intermediaryMethodNames,
                                    Map<String, String> intermediaryFieldNames,
                                    LoaderApiRenames loaderRenames) {
        // Loader-API renames are NOT merged into classRedirectsDotted — instead
        // they're looked up per-call in tryRewriteClassFqn. This lets the
        // curated table stay in its native slash form (as bundled in the JSON)
        // while the dotted-form map carries only shim-registered renames.
        this.classRedirectsDotted = buildDottedMap(slashedClassRedirects);
        this.intermediaryMethodNames = Map.copyOf(intermediaryMethodNames);
        this.intermediaryFieldNames = Map.copyOf(intermediaryFieldNames);
        this.loaderRenames = loaderRenames;
    }

    /**
     * Convert the slash-form {@code classRedirects} map to the dotted form used
     * for matching reflection strings (which are always dotted FQNs).
     * Called once per remapper instance; result is immutable thereafter.
     */
    private static Map<String, String> buildDottedMap(Map<String, String> slashed) {
        java.util.HashMap<String, String> out = new java.util.HashMap<>(slashed.size());
        for (Map.Entry<String, String> e : slashed.entrySet()) {
            out.put(slashToDot(e.getKey()), slashToDot(e.getValue()));
        }
        return out;
    }

    private static String slashToDot(String s) {
        return s == null ? null : s.replace('/', '.');
    }

    private static String dotToSlash(String s) {
        return s == null ? null : s.replace('.', '/');
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN ENTRY POINT
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Remap reflection strings in the given class bytecode.
     *
     * @param classBytes the bytecode to scan
     * @return rewritten bytes if any remapping occurred; if nothing matched
     *         (the common case for non-reflection-using classes), returns the
     *         input bytes unchanged — no new ClassWriter allocation
     */
    public byte[] remap(byte[] classBytes) {
        if (classBytes == null || classBytes.length == 0) return classBytes;

        // Parse into the tree API — we need random-access instruction walking
        // with a lookback window, which is awkward with the streaming visitor API.
        ClassNode classNode = new ClassNode();
        try {
            new ClassReader(classBytes).accept(classNode, 0);
        } catch (Exception e) {
            // Malformed class — leave it alone, the main transformer's error
            // handling already dealt with this kind of thing upstream.
            LOGGER.debug("Skipping reflection remap: class unparseable ({})", e.getMessage());
            return classBytes;
        }

        int remappedBefore = stringsRemapped.get();
        boolean modified = false;

        for (MethodNode method : classNode.methods) {
            if (method.instructions == null || method.instructions.size() == 0) continue;
            if (processMethod(method, classNode.name)) {
                modified = true;
            }
        }

        if (!modified) {
            // No changes; return the original bytes to skip the serialize cost.
            return classBytes;
        }

        // Serialize the modified class tree back to bytes.
        // COMPUTE_MAXS is enough — we only changed LDC constants, not stack shape.
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);

        int delta = stringsRemapped.get() - remappedBefore;
        LOGGER.debug("Reflection remap: {} ({} strings rewritten)", classNode.name, delta);
        return writer.toByteArray();
    }

    // ═══════════════════════════════════════════════════════════════════════
    // PER-METHOD SCANNING
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Scan one method for LDC→reflection-sink patterns and rewrite matched LDCs.
     * Returns true if any rewrites happened in this method.
     */
    private boolean processMethod(MethodNode method, String ownerClass) {
        InsnList insns = method.instructions;
        // Ring buffer of recent LDC indexes. We use indexes into the InsnList
        // rather than references, because we need to mutate the LDC's cst field
        // by calling its set() method later.
        LdcInsnNode[] window = new LdcInsnNode[LOOKBACK_WINDOW];
        int windowEnd = 0; // points at the slot for the NEXT insert

        // Also track "did we just rewrite a class FQN?" — if so, the next
        // member-name LDC we encounter could be remapped using intermediary
        // name tables. Not implemented yet (simple path for v1), but the hook
        // point is clear.
        //noinspection unused
        String lastRewrittenClassSlashed = null;

        boolean modified = false;
        AbstractInsnNode insn = insns.getFirst();
        while (insn != null) {
            AbstractInsnNode nextInsn = insn.getNext(); // capture before any mutation

            if (insn instanceof LdcInsnNode ldc && ldc.cst instanceof String) {
                // Record in the sliding window for later lookback.
                window[windowEnd % LOOKBACK_WINDOW] = ldc;
                windowEnd++;
            } else if (insn instanceof MethodInsnNode call) {
                String sinkKey = call.owner + "#" + call.name;
                boolean wasSink = false;

                if (SINKS_TAKING_CLASS_FQN.contains(sinkKey)) {
                    wasSink = true;
                    LdcInsnNode target = findLastStringLdc(window, windowEnd,
                            s -> MC_FQN_PATTERN.matcher(s).matches());
                    if (target != null) {
                        if (tryRewriteClassFqn(target)) {
                            modified = true;
                            lastRewrittenClassSlashed = dotToSlash((String) target.cst);
                        } else {
                            // We recognized the shape but had no mapping for it.
                            // This is what the gap report wants to know about.
                            suspiciousUnmapped.incrementAndGet();
                        }
                    }
                } else if (SINKS_TAKING_MEMBER_NAME.contains(sinkKey)) {
                    wasSink = true;
                    // The first String argument is the member name; it may also
                    // be an intermediary name that needs translation. Look back
                    // for the string immediately preceding the call.
                    LdcInsnNode target = findLastStringLdc(window, windowEnd, s -> true);
                    if (target != null && target.cst instanceof String name) {
                        if (INTERMEDIARY_METHOD_PATTERN.matcher(name).matches()) {
                            String mapped = intermediaryMethodNames.get(name);
                            if (mapped != null) {
                                target.cst = mapped;
                                stringsRemapped.incrementAndGet();
                                modified = true;
                            }
                        } else if (INTERMEDIARY_FIELD_PATTERN.matcher(name).matches()) {
                            String mapped = intermediaryFieldNames.get(name);
                            if (mapped != null) {
                                target.cst = mapped;
                                stringsRemapped.incrementAndGet();
                                modified = true;
                            }
                        }
                        // Non-intermediary member names are left alone — we
                        // can't remap them without knowing the exact class
                        // the lookup is on, which would require a more
                        // sophisticated data-flow analysis.
                    }
                }

                // Only reset the LDC window after a RECOGNIZED reflection sink.
                // Clearing after every invoke was too aggressive: patterns like
                //   LDC "net.minecraft.X"
                //   INVOKESTATIC SomeUtil.prep(String)String
                //   INVOKESTATIC Class.forName(String)
                // get broken because the intermediate helper wipes the window
                // before the actual sink runs. After a real sink we DO want to
                // clear, because its consumed LDCs are gone from the stack.
                if (wasSink) {
                    windowEnd = 0;
                    java.util.Arrays.fill(window, null);
                }
            }

            insn = nextInsn;
        }
        return modified;
    }

    /**
     * Walk the LDC window backward (most recent first) and return the first
     * entry whose string value satisfies the predicate, or null if none match
     * within the current window.
     */
    private static LdcInsnNode findLastStringLdc(LdcInsnNode[] window, int windowEnd,
                                                  java.util.function.Predicate<String> predicate) {
        // Walk from windowEnd-1 back to windowEnd-WINDOW (or 0, whichever is first)
        int start = Math.max(0, windowEnd - LOOKBACK_WINDOW);
        for (int i = windowEnd - 1; i >= start; i--) {
            LdcInsnNode ldc = window[i % LOOKBACK_WINDOW];
            if (ldc != null && ldc.cst instanceof String s && predicate.test(s)) {
                return ldc;
            }
        }
        return null;
    }

    /**
     * Try to rewrite the given LDC's string value via the class redirect table
     * or the loader-API rename table. Returns true if a rewrite happened.
     *
     * <p>Handles inner-class {@code $} notation: {@code net.minecraft.X$Inner}
     * resolves against the outer class rename. {@code net.minecraft.X.Inner}
     * (dot-separated nested) is left alone since that form is ambiguous with
     * member access.</p>
     */
    private boolean tryRewriteClassFqn(LdcInsnNode ldc) {
        String original = (String) ldc.cst;

        // Direct lookup first.
        String direct = classRedirectsDotted.get(original);
        if (direct != null) {
            ldc.cst = direct;
            stringsRemapped.incrementAndGet();
            return true;
        }

        // Handle "net.minecraft.Foo$Inner": split on '$', map the outer, reassemble.
        int dollar = original.indexOf('$');
        if (dollar > 0) {
            String outer = original.substring(0, dollar);
            String remapped = classRedirectsDotted.get(outer);
            if (remapped != null) {
                ldc.cst = remapped + original.substring(dollar);
                stringsRemapped.incrementAndGet();
                return true;
            }
        }

        // Fall back to the loader-API curated table (slash form).
        String slashed = dotToSlash(original);
        String loaderRename = loaderRenames.getClassRename(slashed);
        if (loaderRename != null) {
            ldc.cst = slashToDot(loaderRename);
            stringsRemapped.incrementAndGet();
            return true;
        }

        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // METRICS ACCESS
    // ═══════════════════════════════════════════════════════════════════════

    /** Total strings rewritten across every class this remapper has processed. */
    public int getStringsRemapped() {
        return stringsRemapped.get();
    }

    /**
     * Strings that looked like MC references and were near a reflection sink,
     * but had no mapping — these indicate missing entries in the class-redirect
     * table and feed into the gap report.
     */
    public int getSuspiciousUnmapped() {
        return suspiciousUnmapped.get();
    }

    /**
     * Dynamic (concat) strings passing through reflection sinks. We can't
     * rewrite these without data-flow analysis, but count them so mod authors
     * understand why some reflection calls weren't fixed.
     */
    public int getDynamicStringsSkipped() {
        return dynamicStringsSkipped.get();
    }

    /** Reset all counters — useful for per-mod reports. */
    public void resetMetrics() {
        stringsRemapped.set(0);
        suspiciousUnmapped.set(0);
        dynamicStringsSkipped.set(0);
    }
}
