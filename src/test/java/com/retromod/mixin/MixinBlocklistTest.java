/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.mixin;

import com.retromod.core.RetromodTransformer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the mixin blocklist that turns fatal, unrepairable mixin handlers into
 * inert ones (issue #28 — a MixinExtras {@code @WrapOperation}/{@code @Local}
 * that crashes with a {@code VerifyError} on 26.1).
 */
class MixinBlocklistTest {

    @AfterEach
    void reset() {
        MixinBlocklist.resetForTesting();
    }

    /** Build a minimal @Mixin class with a blocked handler, an injector helper, and a plain helper. */
    private static byte[] mixinClass(String internalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, internalName, null, "java/lang/Object", null);
        cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", false).visitEnd();

        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        ctor.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(1, 1);
        ctor.visitEnd();

        // Blocked MixinExtras @WrapOperation handler (the #28 shape).
        MethodVisitor h = cw.visitMethod(Opcodes.ACC_PRIVATE,
                "deeperdarker$decrementStackOnServer", "()V", null, null);
        h.visitAnnotation("Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;", false).visitEnd();
        h.visitCode();
        h.visitInsn(Opcodes.RETURN);
        h.visitMaxs(0, 1);
        h.visitEnd();

        // A standard @Inject handler (used by the whole-class case).
        MethodVisitor inj = cw.visitMethod(Opcodes.ACC_PRIVATE, "onSomething", "()V", null, null);
        inj.visitAnnotation("Lorg/spongepowered/asm/mixin/injection/Inject;", false).visitEnd();
        inj.visitCode();
        inj.visitInsn(Opcodes.RETURN);
        inj.visitMaxs(0, 1);
        inj.visitEnd();

        // A plain helper with no injector annotation — must always survive.
        MethodVisitor keep = cw.visitMethod(Opcodes.ACC_PRIVATE, "plainHelper", "()V", null, null);
        keep.visitCode();
        keep.visitInsn(Opcodes.RETURN);
        keep.visitMaxs(0, 1);
        keep.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Set<String> methodNames(byte[] bytes) {
        ClassNode cn = new ClassNode();
        new ClassReader(bytes).accept(cn, 0);
        Set<String> names = new HashSet<>();
        for (MethodNode m : cn.methods) names.add(m.name);
        return names;
    }

    @Test
    @DisplayName("Bundled blocklist includes Deeper&Darker's PaintingItemMixin handler (#28)")
    void loadsBundledDeeperDarkerEntry() {
        MixinBlocklist.resetForTesting(); // force a fresh load of the bundled resource
        Set<String> m = MixinBlocklist.methodsToStrip(
                "com/kyanite/deeperdarker/mixin/PaintingItemMixin");
        assertNotNull(m, "bundled blocklist should include PaintingItemMixin");
        assertTrue(m.contains("deeperdarker$decrementStackOnServer"),
                "the crashing @WrapOperation handler must be listed");
    }

    @Test
    @DisplayName("Surgical: only the named handler is stripped, everything else stays")
    void stripsNamedHandlerOnly() {
        MixinBlocklist.setForTesting(Map.of(
                "test/FooMixin", Set.of("deeperdarker$decrementStackOnServer")));
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(mixinClass("test/FooMixin")));
        assertFalse(names.contains("deeperdarker$decrementStackOnServer"), "blocked handler removed");
        assertTrue(names.contains("onSomething"), "other injector kept (not named)");
        assertTrue(names.contains("plainHelper"), "plain helper kept");
        assertTrue(names.contains("<init>"), "constructor kept");
    }

    @Test
    @DisplayName("Whole-class (empty methods): every injector dropped, helpers/ctor kept")
    void wholeClassStripsInjectorsKeepsHelpers() {
        MixinBlocklist.setForTesting(Map.of("test/BarMixin", Set.of())); // empty = all injectors
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(mixinClass("test/BarMixin")));
        assertFalse(names.contains("deeperdarker$decrementStackOnServer"), "MixinExtras handler stripped");
        assertFalse(names.contains("onSomething"), "@Inject handler stripped");
        assertTrue(names.contains("plainHelper"), "plain helper kept");
        assertTrue(names.contains("<init>"), "constructor kept");
    }

    @Test
    @DisplayName("End-to-end: the BUNDLED PaintingItemMixin entry strips the handler via transformMixinClass")
    void bundledEntryStripsRealMixin() {
        MixinBlocklist.resetForTesting(); // use the real bundled list, not a test injection
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(
                mixinClass("com/kyanite/deeperdarker/mixin/PaintingItemMixin")));
        assertFalse(names.contains("deeperdarker$decrementStackOnServer"),
                "bundled blocklist must strip the #28 handler from the real mixin name");
        assertTrue(names.contains("plainHelper"), "unrelated helper survives");
        assertTrue(names.contains("onSomething"), "non-listed handler survives");
    }

    @Test
    @DisplayName("A mixin not on the blocklist is left untouched")
    void nonBlockedClassUntouched() {
        MixinBlocklist.setForTesting(Map.of("other/Thing", Set.of("x")));
        var t = new MixinCompatibilityTransformer(RetromodTransformer.getInstance());

        Set<String> names = methodNames(t.transformMixinClass(mixinClass("test/FooMixin")));
        assertTrue(names.contains("deeperdarker$decrementStackOnServer"),
                "non-blocked mixin keeps all handlers");
        assertTrue(names.contains("onSomething"));
    }
}
