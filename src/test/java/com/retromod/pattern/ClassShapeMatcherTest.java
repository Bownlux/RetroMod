/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.pattern;

import com.retromod.core.pattern.ClassShapeMatcher;
import com.retromod.core.pattern.MatchContext;
import com.retromod.core.pattern.PatternMatch;
import com.retromod.core.verify.LoaderApiRenames;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link ClassShapeMatcher} + the three bundled patterns.
 *
 * <p>Each test builds a small synthetic class via ASM that embodies (or
 * violates) one of the patterns, and verifies the matcher flags it correctly.</p>
 */
class ClassShapeMatcherTest {

    private final ClassShapeMatcher matcher = ClassShapeMatcher.defaultLibrary();

    @Test
    @DisplayName("ForgeEventListener pattern matches @SubscribeEvent methods")
    void detectsForgeEventListener() {
        byte[] cls = classWithSubscribeEventMethod(
                "com/example/mod/handlers/BlockEvents",
                "net/minecraftforge/eventbus/api/SubscribeEvent",
                "onBlockBreak",
                "net/minecraftforge/event/world/BlockEvent$BreakEvent");

        List<PatternMatch> matches = matcher.matchAll(cls, ctxFor("com/example/mod/handlers/BlockEvents"));
        assertFalse(matches.isEmpty(), "ForgeEventListener should have matched");
        PatternMatch match = matches.get(0);
        assertEquals("ForgeEventListener", match.patternName());
        assertEquals(1.0, match.confidence(), 0.001);
        assertEquals(1, match.metadata().get("handlerCount"));
        assertEquals("net/minecraftforge/eventbus/api/SubscribeEvent",
                match.metadata().get("annotationFqn"));
    }

    @Test
    @DisplayName("ForgeEventListener also matches NeoForge-style annotations")
    void detectsNeoForgeEventListener() {
        byte[] cls = classWithSubscribeEventMethod(
                "com/example/mod/handlers/NewEvents",
                "net/neoforged/bus/api/SubscribeEvent",
                "onEntityTick",
                "net/neoforged/neoforge/event/tick/EntityTickEvent");

        List<PatternMatch> matches = matcher.matchAll(cls,
                ctxFor("com/example/mod/handlers/NewEvents"));
        assertEquals(1, matches.size());
        assertEquals("net/neoforged/bus/api/SubscribeEvent",
                matches.get(0).metadata().get("annotationFqn"));
    }

    @Test
    @DisplayName("DeferredRegisterHolder pattern matches registry-holder classes")
    void detectsDeferredRegisterHolder() {
        byte[] cls = classWithDeferredRegisterField(
                "com/example/mod/init/ModItems",
                "net/neoforged/neoforge/registries/DeferredRegister",
                "ITEMS");

        List<PatternMatch> matches = matcher.matchAll(cls,
                ctxFor("com/example/mod/init/ModItems"));
        assertEquals(1, matches.size());
        PatternMatch match = matches.get(0);
        assertEquals("DeferredRegisterHolder", match.patternName());
        assertEquals(0.95, match.confidence(), 0.001);
        assertEquals(1, match.metadata().get("deferredRegisterCount"));
    }

    @Test
    @DisplayName("BlockEntityLike pattern matches NBT+tick class")
    void detectsBlockEntityLike() {
        byte[] cls = classWithNbtReadWriteTick(
                "com/example/mod/block/MyBlockEntity",
                "net/minecraft/world/level/block/entity/BlockEntity");

        List<PatternMatch> matches = matcher.matchAll(cls,
                ctxFor("com/example/mod/block/MyBlockEntity"));
        assertEquals(1, matches.size());
        PatternMatch match = matches.get(0);
        assertEquals("BlockEntityLike", match.patternName());
        // Parent name contains "BlockEntity" → high-confidence tier (0.9)
        assertEquals(0.9, match.confidence(), 0.001);
        assertEquals(true, match.metadata().get("hasNbtRead"));
        assertEquals(true, match.metadata().get("hasNbtWrite"));
        assertEquals(true, match.metadata().get("hasTick"));
    }

    @Test
    @DisplayName("MC/JDK classes are never matched (modOwnClasses filter)")
    void scopeFilterRejectsMcClasses() {
        // Same shape as the Forge event listener, but the class isn't in modOwnClasses
        byte[] cls = classWithSubscribeEventMethod(
                "net/minecraft/server/Main",
                "net/minecraftforge/eventbus/api/SubscribeEvent",
                "onTick", "net/minecraft/SomeEvent");

        List<PatternMatch> matches = matcher.matchAll(cls,
                ctxFor("com/example/mod/OtherClass"));
        assertTrue(matches.isEmpty(),
                "Classes outside modOwnClasses should never match");
    }

    @Test
    @DisplayName("ApiUsageFingerprintPattern detects MC API usage in method bodies")
    void detectsApiUsageFingerprint() {
        byte[] cls = classWithMcMethodCalls(
                "com/example/mod/SomeHelper",
                "net/minecraft/world/level/Level",
                "getBlockState",
                "(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;");

        List<PatternMatch> matches = matcher.matchAll(cls, ctxFor("com/example/mod/SomeHelper"));
        // May be multiple patterns matching; we're looking for the specific one here
        PatternMatch fingerprint = matches.stream()
                .filter(m -> m.patternName().equals("ApiUsageFingerprint"))
                .findFirst()
                .orElse(null);
        assertNotNull(fingerprint, "ApiUsageFingerprint should have matched");
        assertEquals(0.6, fingerprint.confidence(), 0.001);
        // At least some MC refs counted (method owner + parameter types all count)
        int total = (int) fingerprint.metadata().get("totalMcRefs");
        assertTrue(total >= 1, "Should have counted at least one MC ref, got " + total);
    }

    @Test
    @DisplayName("MixinTargetPattern detects @Mixin classes with target list")
    void detectsMixinTargetPattern() {
        byte[] cls = classWithMixinAnnotation(
                "com/example/mod/mixin/PlayerMixin",
                "net/minecraft/world/entity/player/Player");

        List<PatternMatch> matches = matcher.matchAll(cls,
                ctxFor("com/example/mod/mixin/PlayerMixin"));
        PatternMatch mixin = matches.stream()
                .filter(m -> m.patternName().equals("MixinTarget"))
                .findFirst()
                .orElse(null);
        assertNotNull(mixin, "MixinTargetPattern should have matched");
        assertEquals(1.0, mixin.confidence(), 0.001);
        String targets = (String) mixin.metadata().get("targetClasses");
        assertTrue(targets != null && targets.contains("net/minecraft/world/entity/player/Player"),
                "Target class should appear in metadata; got: " + targets);
    }

    @Test
    @DisplayName("Empty class produces no matches")
    void emptyClassNoMatches() {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "com/example/mod/Empty",
                null, "java/lang/Object", null);
        cw.visitEnd();

        List<PatternMatch> matches = matcher.matchAll(cw.toByteArray(),
                ctxFor("com/example/mod/Empty"));
        assertTrue(matches.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    private static MatchContext ctxFor(String modClass) {
        return new MatchContext(
                Set.of(modClass),
                LoaderApiRenames.forTesting(null, null, null),
                MatchContext.empty().mcIndex());
    }

    /**
     * Generate a class with one method annotated with the given @SubscribeEvent
     * FQN, taking the given event class as parameter.
     */
    private static byte[] classWithSubscribeEventMethod(String className,
                                                         String annotationInternalName,
                                                         String methodName,
                                                         String eventInternalName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className,
                null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                methodName, "(L" + eventInternalName + ";)V", null, null);
        // Attach the annotation to the method
        AnnotationVisitor av = mv.visitAnnotation("L" + annotationInternalName + ";", true);
        av.visitEnd();
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Generate a class with a public static final DeferredRegister field. */
    private static byte[] classWithDeferredRegisterField(String className,
                                                          String deferredRegisterInternal,
                                                          String fieldName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className,
                null, "java/lang/Object", null);

        FieldVisitor fv = cw.visitField(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                fieldName, "L" + deferredRegisterInternal + ";",
                "L" + deferredRegisterInternal + "<Lnet/minecraft/world/item/Item;>;",
                null);
        fv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Generate a class whose one method body makes an INVOKEVIRTUAL into a MC class. */
    private static byte[] classWithMcMethodCalls(String className, String calleeOwner,
                                                  String calleeName, String calleeDesc) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "invoke",
                // takes a Level and a BlockPos, returns BlockState
                "(L" + calleeOwner + ";Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;",
                null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, calleeOwner, calleeName, calleeDesc, false);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Generate a class annotated with @Mixin(SomeMcClass.class). */
    private static byte[] classWithMixinAnnotation(String className, String targetInternalName) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, "java/lang/Object", null);

        AnnotationVisitor mixinAv = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", true);
        AnnotationVisitor valueArray = mixinAv.visitArray("value");
        valueArray.visit(null, org.objectweb.asm.Type.getObjectType(targetInternalName));
        valueArray.visitEnd();
        mixinAv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    /** Generate a class resembling a BlockEntity — load/save + tick methods. */
    private static byte[] classWithNbtReadWriteTick(String className, String superName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null, superName, null);

        // Default constructor
        MethodVisitor ctor = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        ctor.visitCode();
        ctor.visitVarInsn(Opcodes.ALOAD, 0);
        // We don't actually call super since superName is a pretend class for tests
        ctor.visitInsn(Opcodes.RETURN);
        ctor.visitMaxs(0, 0);
        ctor.visitEnd();

        // public void load(CompoundTag tag) — NBT read
        MethodVisitor load = cw.visitMethod(Opcodes.ACC_PUBLIC,
                "load", "(Lnet/minecraft/nbt/CompoundTag;)V", null, null);
        load.visitCode();
        load.visitInsn(Opcodes.RETURN);
        load.visitMaxs(0, 0);
        load.visitEnd();

        // public CompoundTag save(CompoundTag tag) — NBT write
        MethodVisitor save = cw.visitMethod(Opcodes.ACC_PUBLIC,
                "save",
                "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
                null, null);
        save.visitCode();
        save.visitVarInsn(Opcodes.ALOAD, 1);
        save.visitInsn(Opcodes.ARETURN);
        save.visitMaxs(0, 0);
        save.visitEnd();

        // public void tick() — tick method
        MethodVisitor tick = cw.visitMethod(Opcodes.ACC_PUBLIC, "tick", "()V", null, null);
        tick.visitCode();
        tick.visitInsn(Opcodes.RETURN);
        tick.visitMaxs(0, 0);
        tick.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
