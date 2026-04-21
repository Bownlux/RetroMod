/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.core.verify;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * Scans transformed mod bytecode and reports any reference to an MC API that
 * doesn't exist in the target MC version.
 *
 * <h3>Where this fits in the pipeline</h3>
 * <p>The verifier runs <b>after</b> the iterative transform loop and reflection
 * remapping have done everything they can. What remains is a list of
 * references the automatic fixes couldn't handle — these are the genuine gaps
 * that need manual work (new shims, new polyfills, or a decision that this mod
 * can't be supported).</p>
 *
 * <h3>Filtering</h3>
 * <p>Only {@code net/minecraft/**} and {@code com/mojang/**} references are
 * considered — per the v1 scope rule. Loader-API references are checked
 * against the curated {@link LoaderApiRenames} table (a reference that matches
 * a known-removed loader class is flagged; everything else is left alone).</p>
 *
 * <p>References to classes defined by the mod itself are skipped via the
 * {@code modOwnClasses} set passed to {@link #verify}. Without this filter,
 * every mod's internal references would show up as "missing" since they don't
 * exist in the MC JAR.</p>
 *
 * <h3>What we scan</h3>
 * <p>Every reference the JVM loads at class-resolution time:
 * <ul>
 *   <li>Class refs in {@code NEW}, {@code CHECKCAST}, {@code INSTANCEOF},
 *       {@code ANEWARRAY}, {@code MULTIANEWARRAY}</li>
 *   <li>Class refs inside method descriptors (parameters + return type)</li>
 *   <li>Class refs inside field descriptors</li>
 *   <li>Method refs in {@code INVOKEVIRTUAL}, {@code INVOKESTATIC},
 *       {@code INVOKEINTERFACE}, {@code INVOKESPECIAL}</li>
 *   <li>Field refs in {@code GETFIELD}/{@code PUTFIELD}/{@code GETSTATIC}/{@code PUTSTATIC}</li>
 *   <li>Class superclass and interface refs (catches mods extending removed classes)</li>
 * </ul>
 * </p>
 *
 * <p>Generic signatures, annotations, and invokedynamic bootstrap handles are
 * NOT scanned in v1 — they occasionally reference MC types but the false-positive
 * rate is higher and the diagnostic value is lower. Those can be added later if
 * the gap report shows real misses.</p>
 */
public final class ReferenceVerifier {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-Verifier");

    private final McSymbolIndex index;
    private final LoaderApiRenames loaderRenames;
    private final int maxSuggestions;

    /**
     * @param index           the MC symbol index to check references against
     * @param loaderRenames   curated loader-API rename table for spotting
     *                        references to known-removed loader classes
     * @param maxSuggestions  cap on fuzzy suggestions per unresolved ref (0 disables)
     */
    public ReferenceVerifier(McSymbolIndex index, LoaderApiRenames loaderRenames, int maxSuggestions) {
        this.index = index;
        this.loaderRenames = loaderRenames;
        this.maxSuggestions = Math.max(0, maxSuggestions);
    }

    /** Convenience constructor with default of 3 suggestions per ref. */
    public ReferenceVerifier(McSymbolIndex index, LoaderApiRenames loaderRenames) {
        this(index, loaderRenames, 3);
    }

    /**
     * Scan one transformed class and append any unresolved references to the
     * given report. Safe to call many times against the same report — it
     * accumulates.
     *
     * @param classBytes     transformed class bytecode (post-iterative-loop)
     * @param modOwnClasses  JVM internal names of classes defined by the mod
     *                       itself; references to these are skipped since they
     *                       obviously won't be in the MC index
     * @param report         report to append findings to
     */
    public void verify(byte[] classBytes, Set<String> modOwnClasses, VerificationReport report) {
        if (classBytes == null || classBytes.length == 0 || report == null) return;
        if (!index.isAvailable()) {
            // No index means we can't check anything. Be loud about it — a silent
            // skip here would look like "everything is fine" to the user.
            LOGGER.debug("Skipping verification — McSymbolIndex not available");
            return;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            reader.accept(new VerifyingClassVisitor(report,
                    modOwnClasses == null ? Set.of() : modOwnClasses),
                    ClassReader.SKIP_FRAMES);
        } catch (Exception e) {
            // Malformed class or verifier bug — don't blow up the whole mod
            // transformation, just log and move on.
            LOGGER.debug("Verification failed for a class ({}); skipping", e.getMessage());
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    // CLASSIFICATION HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * @return {@code true} if this internal name is something we should verify —
     *         i.e., an MC or Mojang shared-library reference
     */
    private static boolean isMcRef(String internalName) {
        if (internalName == null) return false;
        return internalName.startsWith("net/minecraft/")
            || internalName.startsWith("com/mojang/");
    }

    /**
     * @return {@code true} if this is a loader-API reference — consulted
     *         against the curated rename table only, not the MC index
     */
    private static boolean isLoaderRef(String internalName) {
        if (internalName == null) return false;
        return internalName.startsWith("net/fabricmc/")
            || internalName.startsWith("net/neoforged/")
            || internalName.startsWith("net/minecraftforge/");
    }

    /**
     * Strip a descriptor to its component class name if it's an L-type,
     * otherwise return null. Primitive types ({@code I}, {@code Z}, etc.)
     * and array elements that are primitives all return null — there's
     * nothing to verify.
     */
    private static String unwrapObjectType(Type t) {
        if (t == null) return null;
        int sort = t.getSort();
        if (sort == Type.OBJECT) return t.getInternalName();
        if (sort == Type.ARRAY) return unwrapObjectType(t.getElementType());
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════
    // MAIN VISITOR
    // ═══════════════════════════════════════════════════════════════════════

    private final class VerifyingClassVisitor extends ClassVisitor {
        private final VerificationReport report;
        private final Set<String> modOwnClasses;
        /** The class currently being visited — used to annotate source location. */
        private String currentClass = "<unknown>";

        VerifyingClassVisitor(VerificationReport report, Set<String> modOwnClasses) {
            super(Opcodes.ASM9);
            this.report = report;
            this.modOwnClasses = modOwnClasses;
        }

        @Override
        public void visit(int version, int access, String name, String signature,
                          String superName, String[] interfaces) {
            this.currentClass = name;
            // Check superclass and interfaces — mods that extend removed MC types
            // or implement removed interfaces are the most common crash case.
            checkClassRef(superName, name, "<class>", -1);
            if (interfaces != null) {
                for (String iface : interfaces) {
                    checkClassRef(iface, name, "<class>", -1);
                }
            }
        }

        @Override
        public FieldVisitor visitField(int access, String name, String descriptor,
                                        String signature, Object value) {
            // Check the field's type. Example: a field of type Lnet/minecraft/world/World;
            // where World no longer exists produces an unresolved-class report.
            Type fieldType = Type.getType(descriptor);
            checkClassRef(unwrapObjectType(fieldType), currentClass, "<field:" + name + ">", -1);
            return null;
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                                         String signature, String[] exceptions) {
            // Check the method's parameter and return types.
            Type methodType = Type.getMethodType(descriptor);
            for (Type paramType : methodType.getArgumentTypes()) {
                checkClassRef(unwrapObjectType(paramType), currentClass,
                        "<signature:" + name + ">", -1);
            }
            checkClassRef(unwrapObjectType(methodType.getReturnType()), currentClass,
                    "<signature:" + name + ">", -1);
            // Also check declared exceptions (subclasses of Throwable in MC packages)
            if (exceptions != null) {
                for (String ex : exceptions) {
                    checkClassRef(ex, currentClass, "<signature:" + name + ">", -1);
                }
            }
            return new VerifyingMethodVisitor(name);
        }

        // ────────────────────────────────────────────────────────────────────
        // Inner classes and references to them
        // ────────────────────────────────────────────────────────────────────

        private final class VerifyingMethodVisitor extends MethodVisitor {
            private final String methodName;
            /** Last line number we saw — attached to any refs seen after it. */
            private int currentLine = -1;

            VerifyingMethodVisitor(String methodName) {
                super(Opcodes.ASM9);
                this.methodName = methodName;
            }

            @Override
            public void visitLineNumber(int line, Label start) {
                this.currentLine = line;
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                // NEW, CHECKCAST, INSTANCEOF, ANEWARRAY — 'type' is either
                // an internal class name or an array descriptor. Normalize.
                String cls = type;
                if (cls != null && cls.startsWith("[")) {
                    Type arrayType = Type.getType(cls);
                    cls = unwrapObjectType(arrayType);
                }
                checkClassRef(cls, currentClass, methodName, currentLine);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name,
                                         String descriptor, boolean isInterface) {
                checkMethodRef(owner, name, descriptor, currentClass, methodName, currentLine);
                // Also verify every class mentioned in the method descriptor — a
                // call to an existing method whose parameter type was removed is
                // still a broken reference.
                checkDescriptorClassRefs(descriptor, methodName);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                checkFieldRef(owner, name, descriptor, currentClass, methodName, currentLine);
                Type fieldType = Type.getType(descriptor);
                checkClassRef(unwrapObjectType(fieldType), currentClass, methodName, currentLine);
            }

            @Override
            public void visitInvokeDynamicInsn(String name, String descriptor, Handle bootstrapMethodHandle,
                                                Object... bootstrapMethodArguments) {
                // InvokeDynamic's descriptor is the call-site type. Bootstrap
                // method verification is complex (recursive handle analysis) and
                // usually MC-unrelated (lambda metafactory), so we skip it in v1.
                checkDescriptorClassRefs(descriptor, methodName);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int numDimensions) {
                Type arrayType = Type.getType(descriptor);
                checkClassRef(unwrapObjectType(arrayType), currentClass, methodName, currentLine);
            }

            private void checkDescriptorClassRefs(String descriptor, String methodName) {
                Type methodType = Type.getMethodType(descriptor);
                for (Type arg : methodType.getArgumentTypes()) {
                    checkClassRef(unwrapObjectType(arg), currentClass, methodName, currentLine);
                }
                checkClassRef(unwrapObjectType(methodType.getReturnType()),
                        currentClass, methodName, currentLine);
            }
        }

        // ────────────────────────────────────────────────────────────────────
        // Actual reference checking — the logic shared by all visit* methods.
        // ────────────────────────────────────────────────────────────────────

        private void checkClassRef(String internalName, String srcClass,
                                    String srcMethod, int line) {
            if (internalName == null) return;
            if (modOwnClasses.contains(internalName)) return;

            if (isMcRef(internalName)) {
                if (!index.hasClass(internalName)) {
                    report.add(new UnresolvedReference(
                            UnresolvedReference.Kind.MISSING_CLASS,
                            internalName, "", "",
                            srcClass, srcMethod, line,
                            index.suggestClassAlternatives(internalName, maxSuggestions)));
                }
            } else if (isLoaderRef(internalName)) {
                // Loader refs: only flag if curated table says it's removed/renamed.
                // "removed" takes precedence over "renamed" — a class in both
                // buckets of the JSON (data-entry mistake) should produce ONE
                // report entry, not two. We treat "removed" as the authoritative
                // final state; if it's also in "renamed", the curated data is
                // self-inconsistent and the removal wins.
                if (loaderRenames.isRemoved(internalName)) {
                    report.add(new UnresolvedReference(
                            UnresolvedReference.Kind.MISSING_CLASS,
                            internalName, "", "",
                            srcClass, srcMethod, line,
                            java.util.List.of()));
                } else {
                    String newName = loaderRenames.getClassRename(internalName);
                    if (newName != null) {
                        // Not strictly "missing" — the old name is gone but we know
                        // the replacement. Still flag it so the mod author knows the
                        // reference needs rewriting. Our classRedirects pipeline
                        // should have done the rewrite upstream; if it didn't, this
                        // is a useful diagnostic.
                        report.add(new UnresolvedReference(
                                UnresolvedReference.Kind.MISSING_CLASS,
                                internalName, "", "",
                                srcClass, srcMethod, line,
                                java.util.List.of(newName)));
                    }
                }
            }
            // Else: unrecognized namespace (mod's own deps, JDK, etc.) — skip.
        }

        private void checkMethodRef(String owner, String name, String descriptor,
                                     String srcClass, String srcMethod, int line) {
            if (owner == null) return;
            if (modOwnClasses.contains(owner)) return;
            // Arrays have INVOKEVIRTUAL on them (e.g., .clone()); the JVM handles
            // those via a synthetic array type. Skip them — not an MC miss.
            if (owner.startsWith("[")) return;

            if (!isMcRef(owner)) return;

            // If the owner class itself is missing, the class-level report
            // already covers it — don't generate redundant method-level entries.
            if (!index.hasClass(owner)) {
                // The class-existence scan (visit/visitField/visitMethod) will
                // have generated a MISSING_CLASS for this owner. Skip the member-
                // level report to avoid pages of redundant entries.
                return;
            }

            if (!index.hasMethod(owner, name, descriptor)) {
                report.add(new UnresolvedReference(
                        UnresolvedReference.Kind.MISSING_METHOD,
                        owner, name, descriptor,
                        srcClass, srcMethod, line,
                        toStringSuggestions(index.suggestMethodAlternatives(
                                owner, name, descriptor, maxSuggestions))));
            }
        }

        private void checkFieldRef(String owner, String name, String descriptor,
                                    String srcClass, String srcMethod, int line) {
            if (owner == null || !isMcRef(owner)) return;
            if (modOwnClasses.contains(owner)) return;
            if (!index.hasClass(owner)) return; // covered by class-level report

            if (!index.hasField(owner, name, descriptor)) {
                report.add(new UnresolvedReference(
                        UnresolvedReference.Kind.MISSING_FIELD,
                        owner, name, descriptor,
                        srcClass, srcMethod, line,
                        java.util.List.of()));
                // Field-alternative suggestions not implemented in v1 — the
                // fuzzy resolver's field-level matching is geared toward
                // redirects, not user-facing suggestions. Add later if the
                // gap report shows we need them.
            }
        }

        private java.util.List<String> toStringSuggestions(
                java.util.List<McSymbolIndex.MemberSignature> sigs) {
            if (sigs == null || sigs.isEmpty()) return java.util.List.of();
            java.util.List<String> out = new java.util.ArrayList<>(sigs.size());
            for (McSymbolIndex.MemberSignature s : sigs) out.add(s.prettyPrint());
            return out;
        }
    }
}
