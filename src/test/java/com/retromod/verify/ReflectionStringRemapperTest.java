/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.verify;

import com.retromod.core.verify.LoaderApiRenames;
import com.retromod.core.verify.ReflectionStringRemapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link ReflectionStringRemapper}. Covers:
 * <ul>
 *   <li>Basic {@code Class.forName("net.minecraft.X")} rewrite</li>
 *   <li>Inner-class {@code $} notation</li>
 *   <li>Non-sink LDCs are left alone (no false positives)</li>
 *   <li>Loader-API rename table is consulted as a fallback</li>
 *   <li>Metrics (rewrite count, suspicious unmapped)</li>
 * </ul>
 */
class ReflectionStringRemapperTest {

    private ReflectionStringRemapper remapper;

    @BeforeEach
    void setUp() {
        // Slashed-form class rename: net/minecraft/util/math/BlockPos → net/minecraft/core/BlockPos
        Map<String, String> slashedClassRedirects = new HashMap<>();
        slashedClassRedirects.put(
                "net/minecraft/util/math/BlockPos",
                "net/minecraft/core/BlockPos");
        slashedClassRedirects.put(
                "net/minecraft/util/text/TextFormatting",
                "net/minecraft/ChatFormatting");

        // Loader rename via the curated table (test-only factory)
        LoaderApiRenames loaderRenames = LoaderApiRenames.forTesting(
                Map.of("net/minecraftforge/eventbus/api/IEventBus",
                       "net/neoforged/bus/api/IEventBus"),
                Map.of(),
                Set.of());

        remapper = new ReflectionStringRemapper(
                slashedClassRedirects,
                Map.of(),   // no intermediary method names
                Map.of(),   // no intermediary field names
                loaderRenames);
    }

    @Test
    @DisplayName("Class.forName string gets rewritten to the new FQN")
    void rewritesClassForNameString() {
        byte[] classBytes = classWithForNameCall("net.minecraft.util.math.BlockPos");
        byte[] remapped = remapper.remap(classBytes);
        assertNotSame(classBytes, remapped, "Remapper should have rewritten the class");
        assertTrue(containsStringConstant(remapped, "net.minecraft.core.BlockPos"),
                "The remapped string should appear as an LDC constant");
        assertFalse(containsStringConstant(remapped, "net.minecraft.util.math.BlockPos"),
                "The old string should be gone");
        assertEquals(1, remapper.getStringsRemapped());
    }

    @Test
    @DisplayName("Loader-API rename is applied via the curated table fallback")
    void rewritesLoaderApiClassFqn() {
        // NOTE: loader rename matches use slash-form internal names, but reflection
        // strings use dotted FQNs. The remapper converts both directions internally.
        byte[] classBytes = classWithForNameCall("net.minecraftforge.eventbus.api.IEventBus");
        byte[] remapped = remapper.remap(classBytes);
        assertTrue(containsStringConstant(remapped, "net.neoforged.bus.api.IEventBus"),
                "The Forge→NeoForge rename should have been applied from the loader table");
    }

    @Test
    @DisplayName("Inner-class $ notation is preserved across the rename")
    void rewritesInnerClassWithDollar() {
        byte[] classBytes = classWithForNameCall("net.minecraft.util.math.BlockPos$Mutable");
        byte[] remapped = remapper.remap(classBytes);
        assertTrue(containsStringConstant(remapped, "net.minecraft.core.BlockPos$Mutable"),
                "Outer class gets renamed, $Mutable suffix stays intact");
    }

    @Test
    @DisplayName("Strings not near reflection sinks are left alone")
    void leavesNonReflectionStringsAlone() {
        // A class with the MC-looking string loaded but NOT followed by a reflection sink
        byte[] classBytes = classWithStringDataOnly("net.minecraft.util.math.BlockPos");
        byte[] remapped = remapper.remap(classBytes);
        // No rewrite = same bytes reference returned (remapper's short-circuit)
        assertSame(classBytes, remapped,
                "Remapper must not modify strings outside reflection sinks");
        assertEquals(0, remapper.getStringsRemapped());
    }

    @Test
    @DisplayName("Unmapped MC-looking FQN at a sink is flagged as suspicious")
    void suspiciousUnmappedIsCounted() {
        // MC-shaped string + reflection sink, but no rename registered for it
        byte[] classBytes = classWithForNameCall("net.minecraft.world.unknown.MadeUpClass");
        byte[] remapped = remapper.remap(classBytes);
        assertSame(classBytes, remapped, "No rewrite means same bytes returned");
        assertEquals(1, remapper.getSuspiciousUnmapped(),
                "MC-shaped FQNs near a sink with no mapping feed the gap report");
    }

    @Test
    @DisplayName("Non-MC strings near reflection sinks are ignored entirely")
    void nonMcStringsIgnored() {
        byte[] classBytes = classWithForNameCall("java.util.ArrayList");
        byte[] remapped = remapper.remap(classBytes);
        assertSame(classBytes, remapped);
        assertEquals(0, remapper.getStringsRemapped());
        // java.util.ArrayList doesn't match the MC FQN pattern so it's not even
        // "suspicious" — it's simply outside our scope of interest.
        assertEquals(0, remapper.getSuspiciousUnmapped());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS — construct test classes with specific LDC + invoke patterns
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Generate a class whose only method body is:
     * <pre>
     *   LDC "&lt;fqn&gt;"
     *   INVOKESTATIC java/lang/Class#forName (Ljava/lang/String;)Ljava/lang/Class;
     *   POP
     *   RETURN
     * </pre>
     * Mirrors the shape of real-world {@code Class.forName} call sites.
     */
    private static byte[] classWithForNameCall(String fqn) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/reflect/Caller",
                null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "trigger", "()V", null,
                new String[] { "java/lang/Exception" });
        mv.visitCode();
        mv.visitLdcInsn(fqn);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "java/lang/Class", "forName",
                "(Ljava/lang/String;)Ljava/lang/Class;",
                false);
        mv.visitInsn(Opcodes.POP);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Generate a class whose only method LOADS a string but does NOT feed it
     * to a reflection sink — used to verify we don't rewrite data strings.
     */
    private static byte[] classWithStringDataOnly(String value) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/reflect/DataHolder",
                null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "getValue", "()Ljava/lang/String;", null, null);
        mv.visitCode();
        mv.visitLdcInsn(value);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Scan a class's constant pool for a given string. */
    private static boolean containsStringConstant(byte[] classBytes, String needle) {
        Set<String> strings = new LinkedHashSet<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                                              String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitLdcInsn(Object value) {
                        if (value instanceof String s) strings.add(s);
                    }
                };
            }
        }, 0);
        return strings.contains(needle);
    }
}
