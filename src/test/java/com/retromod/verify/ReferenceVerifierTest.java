/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.verify;

import com.retromod.core.verify.LoaderApiRenames;
import com.retromod.core.verify.McSymbolIndex;
import com.retromod.core.verify.ReferenceVerifier;
import com.retromod.core.verify.UnresolvedReference;
import com.retromod.core.verify.VerificationReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReferenceVerifier}. Uses a stub {@link McSymbolIndex} so the
 * tests are fast and don't need a real Minecraft JAR on disk.
 */
class ReferenceVerifierTest {

    @Test
    @DisplayName("Method call to a missing MC class reports MISSING_CLASS")
    void missingClassIsReported() {
        // Stub index: only the "new" class exists. The "old" class doesn't.
        StubIndex index = new StubIndex();
        index.classes.add("net/minecraft/core/BlockPos"); // new
        // Note: net/minecraft/util/math/BlockPos intentionally NOT added

        ReferenceVerifier verifier = new ReferenceVerifier(index, emptyLoaderRenames(), 3);
        byte[] modClass = classThatReferences("net/minecraft/util/math/BlockPos", "getX", "()I");

        VerificationReport report = new VerificationReport("testmod", "26.1", 1);
        verifier.verify(modClass, Set.of("test/Mod"), report);

        assertFalse(report.isClean(), "Expected unresolved refs");
        assertEquals(1, report.missingClasses().size(),
                "Should report one missing class (getBlockPos's owner)");
        UnresolvedReference ref = report.missingClasses().get(0);
        assertEquals("net/minecraft/util/math/BlockPos", ref.owner());
        assertEquals(UnresolvedReference.Kind.MISSING_CLASS, ref.kind());
        // Suggestion should be the similarly-named class that DOES exist
        assertTrue(ref.suggestions().contains("net/minecraft/core/BlockPos"),
                "Suggestion should point at the moved class");
    }

    @Test
    @DisplayName("Method missing on an existing class reports MISSING_METHOD")
    void missingMethodIsReported() {
        StubIndex index = new StubIndex();
        // Class exists but the specific method doesn't
        index.classes.add("net/minecraft/world/level/Level");
        // No entry in methodSignatures for Level#getBlockState → method is missing

        ReferenceVerifier verifier = new ReferenceVerifier(index, emptyLoaderRenames(), 3);
        byte[] modClass = classThatReferences(
                "net/minecraft/world/level/Level",
                "getBlockState",
                "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;");

        VerificationReport report = new VerificationReport("testmod", "26.1", 1);
        verifier.verify(modClass, Set.of("test/Mod"), report);

        // The referenced parameter type (BlockPos) also isn't in our stub, which
        // would generate extra MISSING_CLASS reports. We specifically assert on
        // the method miss, not the total count.
        assertTrue(report.missingMethods().stream()
                        .anyMatch(r -> r.name().equals("getBlockState")
                                    && r.owner().equals("net/minecraft/world/level/Level")),
                "Expected MISSING_METHOD for Level#getBlockState");
    }

    @Test
    @DisplayName("Mod's own internal classes are not flagged as missing")
    void modOwnClassesAreFiltered() {
        StubIndex index = new StubIndex();
        // Note: no MC refs at all. The mod only references its OWN class.

        ReferenceVerifier verifier = new ReferenceVerifier(index, emptyLoaderRenames(), 3);
        byte[] modClass = classThatReferences("com/example/mymod/Helper", "doIt", "()V");

        VerificationReport report = new VerificationReport("mymod", "26.1", 1);
        Set<String> modOwnClasses = new HashSet<>(Arrays.asList(
                "com/example/mymod/MyClass",
                "com/example/mymod/Helper"));
        verifier.verify(modClass, modOwnClasses, report);

        assertTrue(report.isClean(),
                "Mod-internal references must not be reported as missing — they're the mod's own code");
    }

    @Test
    @DisplayName("Non-MC references (JDK, mod dependencies) are ignored")
    void nonMcReferencesIgnored() {
        StubIndex index = new StubIndex();
        ReferenceVerifier verifier = new ReferenceVerifier(index, emptyLoaderRenames(), 3);

        byte[] modClass = classThatReferences("java/util/ArrayList", "size", "()I");

        VerificationReport report = new VerificationReport("mymod", "26.1", 1);
        verifier.verify(modClass, Set.of("test/Mod"), report);

        assertTrue(report.isClean(),
                "JDK refs must not appear in the gap report");
    }

    @Test
    @DisplayName("Loader-API removed class is reported")
    void loaderApiRemovedClassIsReported() {
        StubIndex index = new StubIndex();
        LoaderApiRenames renames = LoaderApiRenames.forTesting(
                Map.of(),
                Map.of(),
                Set.of("net/minecraftforge/common/capabilities/CapabilityManager"));

        ReferenceVerifier verifier = new ReferenceVerifier(index, renames, 3);
        byte[] modClass = classThatReferences(
                "net/minecraftforge/common/capabilities/CapabilityManager",
                "get", "()V");

        VerificationReport report = new VerificationReport("mymod", "26.1", 1);
        verifier.verify(modClass, Set.of("test/Mod"), report);

        assertFalse(report.isClean(),
                "Known-removed loader class should be flagged");
        assertTrue(report.missingClasses().stream()
                        .anyMatch(r -> r.owner().equals(
                                "net/minecraftforge/common/capabilities/CapabilityManager")),
                "Expected the removed CapabilityManager to appear in MISSING_CLASSES");
    }

    @Test
    @DisplayName("Verifier is a no-op when the index has no data")
    void noOpOnEmptyIndex() {
        StubIndex index = new StubIndex();
        index.available = false; // simulate "MC JAR not available"
        ReferenceVerifier verifier = new ReferenceVerifier(index, emptyLoaderRenames(), 3);

        byte[] modClass = classThatReferences(
                "net/minecraft/util/math/BlockPos", "getX", "()I");

        VerificationReport report = new VerificationReport("mymod", "26.1", 1);
        verifier.verify(modClass, Set.of("test/Mod"), report);

        assertTrue(report.isClean(),
                "Unindexed verifier should report nothing rather than false-positives");
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private static LoaderApiRenames emptyLoaderRenames() {
        return LoaderApiRenames.forTesting(Map.of(), Map.of(), Set.of());
    }

    /**
     * Generate a class that has one method invoking
     * {@code invokevirtual owner.name descriptor}. Simplest possible shape —
     * gives the verifier a single INVOKEVIRTUAL to check.
     */
    private static byte[] classThatReferences(String owner, String name, String desc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/Mod",
                null, "java/lang/Object", null);

        // Static method taking an Object and calling owner.name(desc) on it.
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "exec", "(L" + owner + ";)V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, owner, name, desc, false);
        // Pop whatever the call returned (if anything)
        if (desc.endsWith(")V")) {
            // no return value
        } else if (desc.endsWith(")J") || desc.endsWith(")D")) {
            mv.visitInsn(Opcodes.POP2);
        } else {
            mv.visitInsn(Opcodes.POP);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Hand-built stub index for tests. {@code classes} and {@code methods} are
     * populated by tests to simulate a minimal MC API surface.
     */
    private static class StubIndex implements McSymbolIndex {
        boolean available = true;
        final Set<String> classes = new HashSet<>();
        final Set<String> methodSignatures = new HashSet<>(); // "owner#name desc"
        final Set<String> fieldSignatures = new HashSet<>();

        @Override public boolean isAvailable() { return available; }
        @Override public boolean hasClass(String internalName) {
            return classes.contains(internalName);
        }
        @Override public boolean hasMethod(String owner, String name, String descriptor) {
            return methodSignatures.contains(owner + "#" + name + " " + descriptor);
        }
        @Override public boolean hasField(String owner, String name, String descriptor) {
            return fieldSignatures.contains(owner + "#" + name + " " + descriptor);
        }
        @Override public List<String> suggestClassAlternatives(String missing, int maxResults) {
            // Simple simple-name match against indexed classes
            int slash = missing.lastIndexOf('/');
            String simple = slash >= 0 ? missing.substring(slash + 1) : missing;
            return classes.stream()
                    .filter(c -> c.endsWith("/" + simple))
                    .filter(c -> !c.equals(missing))
                    .limit(maxResults)
                    .toList();
        }
        @Override public List<MemberSignature> suggestMethodAlternatives(
                String owner, String name, String desc, int maxResults) {
            return List.of();
        }
        @Override public String targetMcVersion() { return "test"; }
    }
}
