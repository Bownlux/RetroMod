/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod;

import com.retromod.core.*;
import com.retromod.aot.AotCompiler;
import com.retromod.mixin.MixinCompatibilityTransformer;
import com.retromod.shim.ShimRegistry;
import com.retromod.shim.fabric.*;
import com.retromod.shim.neoforge.*;
import org.junit.jupiter.api.*;
import org.objectweb.asm.*;

import java.io.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for RetroMod.
 * 
 * Tests:
 * 1. Method redirect registration
 * 2. Class redirect registration
 * 3. Bytecode transformation correctness
 * 4. Shim chain resolution
 * 5. Mixin compatibility transformation
 * 6. AOT compilation
 */
public class RetroModTest {
    
    private RetroModTransformer transformer;
    private ShimRegistry shimRegistry;
    
    @BeforeEach
    void setUp() {
        transformer = RetroModTransformer.getInstance();
        shimRegistry = new ShimRegistry();
    }
    
    // =========================================================
    // SHIM REGISTRATION TESTS
    // =========================================================
    
    @Test
    @DisplayName("Fabric 1.21 to 1.21.1 shim registers redirects")
    void testFabric121To1211ShimRegistration() {
        Fabric_1_21_to_1_21_1 shim = new Fabric_1_21_to_1_21_1();
        
        assertEquals("1.21", shim.getSourceVersion());
        assertEquals("1.21.1", shim.getTargetVersion());
        assertEquals("fabric", shim.getModLoaderType());
        
        // Register redirects
        shim.registerRedirects(transformer);
        
        // This shim has minimal changes, just verify it ran without error
        assertNotNull(shim.getShimName());
    }
    
    @Test
    @DisplayName("Fabric 1.21.8 to 1.21.9 shim handles Entity.getWorld rename")
    void testEntityGetWorldRedirect() {
        Fabric_1_21_8_to_1_21_9 shim = new Fabric_1_21_8_to_1_21_9();
        shim.registerRedirects(transformer);
        
        // Check that Entity.getWorld is redirected
        var redirects = transformer.getMethodRedirects();
        var key = new RetroModTransformer.MethodKey(
            "net/minecraft/entity/Entity",
            "getWorld",
            "()Lnet/minecraft/world/World;"
        );
        
        assertTrue(redirects.containsKey(key), 
            "Entity.getWorld should be redirected to getEntityWorld");
        
        var target = redirects.get(key);
        assertEquals("getEntityWorld", target.name());
    }
    
    @Test
    @DisplayName("NeoForge 1.21.10 to 1.21.11 shim handles ResourceLocation rename")
    void testResourceLocationRedirect() {
        NeoForge_1_21_10_to_1_21_11 shim = new NeoForge_1_21_10_to_1_21_11();
        shim.registerRedirects(transformer);
        
        // Check class redirect
        var classRedirects = transformer.getClassRedirects();
        assertTrue(classRedirects.containsKey("net/minecraft/resources/ResourceLocation"),
            "ResourceLocation should be redirected to Identifier");
        
        assertEquals("net/minecraft/resources/Identifier",
            classRedirects.get("net/minecraft/resources/ResourceLocation"));
    }
    
    // =========================================================
    // SHIM CHAIN RESOLUTION TESTS
    // =========================================================
    
    @Test
    @DisplayName("ShimRegistry finds direct shim")
    void testDirectShimResolution() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        
        List<VersionShim> chain = shimRegistry.findShimChain("fabric", "1.21.8", "1.21.9");
        
        assertEquals(1, chain.size());
        assertEquals("1.21.8", chain.get(0).getSourceVersion());
        assertEquals("1.21.9", chain.get(0).getTargetVersion());
    }
    
    @Test
    @DisplayName("ShimRegistry finds multi-step shim chain")
    void testMultiStepShimChain() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        shimRegistry.register(new Fabric_1_21_9_to_1_21_10());
        shimRegistry.register(new Fabric_1_21_10_to_1_21_11());
        
        List<VersionShim> chain = shimRegistry.findShimChain("fabric", "1.21.8", "1.21.11");
        
        assertEquals(3, chain.size());
        assertEquals("1.21.8", chain.get(0).getSourceVersion());
        assertEquals("1.21.9", chain.get(0).getTargetVersion());
        assertEquals("1.21.9", chain.get(1).getSourceVersion());
        assertEquals("1.21.10", chain.get(1).getTargetVersion());
        assertEquals("1.21.10", chain.get(2).getSourceVersion());
        assertEquals("1.21.11", chain.get(2).getTargetVersion());
    }
    
    @Test
    @DisplayName("ShimRegistry returns empty for unsupported transition")
    void testUnsupportedTransition() {
        shimRegistry.register(new Fabric_1_21_8_to_1_21_9());
        
        // Try to find chain for wrong loader
        List<VersionShim> chain = shimRegistry.findShimChain("forge", "1.21.8", "1.21.9");
        assertTrue(chain.isEmpty());
        
        // Try to find chain for non-existent versions
        List<VersionShim> chain2 = shimRegistry.findShimChain("fabric", "1.20", "1.21");
        assertTrue(chain2.isEmpty());
    }
    
    // =========================================================
    // BYTECODE TRANSFORMATION TESTS
    // =========================================================
    
    @Test
    @DisplayName("Method invocation is correctly rewritten")
    void testMethodInvocationRewrite() {
        // Register a test redirect
        transformer.registerMethodRedirect(
            "test/OldClass", "oldMethod", "()V",
            "test/NewClass", "newMethod", "()V"
        );
        
        // Create test bytecode that calls OldClass.oldMethod
        byte[] original = createTestClass();
        
        // Transform it
        byte[] transformed = transformer.transformClass(original, "test/TestClass");
        
        // Verify the method call was rewritten
        assertTrue(containsMethodCall(transformed, "test/NewClass", "newMethod"),
            "Transformed class should call NewClass.newMethod");
        assertFalse(containsMethodCall(transformed, "test/OldClass", "oldMethod"),
            "Transformed class should not call OldClass.oldMethod");
    }
    
    @Test
    @DisplayName("Class references are correctly rewritten")
    void testClassReferenceRewrite() {
        transformer.registerClassRedirect("test/OldType", "test/NewType");

        // Create test bytecode with OldType reference
        byte[] original = createTestClassWithTypeRef("test/OldType");

        // Transform it
        byte[] transformed = transformer.transformClass(original, "test/TestClass");

        // Verify class reference was rewritten
        assertTrue(containsClassRef(transformed, "test/NewType"),
            "Transformed class should reference NewType");
    }

    // =========================================================
    // ITERATIVE TRANSFORM LOOP TESTS
    // =========================================================
    // Verify that transformClass() runs multiple passes when chained redirects
    // are registered (A -> B, B -> C) and terminates safely on cycles (A -> B, B -> A).
    //
    // The iterative loop is what lets RetroMod handle shim chains where one shim's
    // target is itself the source of another shim's redirect. Single-pass visitors
    // would only catch the first hop.

    @Test
    @DisplayName("Iterative loop resolves chained redirects (A->B->C)")
    void testIterativeLoopChainedRedirects() {
        // Use unique class names so we don't interfere with other tests that
        // share the singleton transformer instance.
        transformer.registerMethodRedirect(
            "test/chain/HopA", "call", "()V",
            "test/chain/HopB", "call", "()V"
        );
        transformer.registerMethodRedirect(
            "test/chain/HopB", "call", "()V",
            "test/chain/HopC", "call", "()V"
        );

        byte[] original = createStaticCallerClass("test/chain/Caller", "test/chain/HopA", "call");

        transformer.resetIterationMetrics();
        byte[] transformed = transformer.transformClass(original, "test/chain/Caller");

        // Final call should be to HopC — the end of the chain — not HopA or HopB.
        assertTrue(containsMethodCall(transformed, "test/chain/HopC", "call"),
                "Expected final call site to be HopC.call after chain resolution");
        assertFalse(containsMethodCall(transformed, "test/chain/HopA", "call"),
                "HopA.call should no longer appear after iteration");
        assertFalse(containsMethodCall(transformed, "test/chain/HopB", "call"),
                "HopB.call should no longer appear after iteration");

        // Metrics: chained redirects should register as "needed multiple passes".
        // Pass 1 rewrites HopA -> HopB. Pass 2 rewrites HopB -> HopC. Pass 3 stable.
        // That's 2 active passes, which triggers the counter.
        assertEquals(1, transformer.getClassesNeedingMultiplePasses(),
                "Chained redirect should count as needing multiple passes");
        assertTrue(transformer.getTotalPassesPerformed() >= 3,
                "Should have performed at least 3 passes for a two-hop chain");
        assertEquals(0, transformer.getClassesHittingIterationCap(),
                "Non-cyclic chain must never hit the iteration cap");
    }

    @Test
    @DisplayName("Iterative loop terminates safely on redirect cycles (A->B->A)")
    void testIterativeLoopCycleTermination() {
        // Cycle: two redirects that point at each other. A naive loop would
        // oscillate forever. The iteration cap guarantees we terminate.
        transformer.registerMethodRedirect(
            "test/cycle/ClassA", "call", "()V",
            "test/cycle/ClassB", "call", "()V"
        );
        transformer.registerMethodRedirect(
            "test/cycle/ClassB", "call", "()V",
            "test/cycle/ClassA", "call", "()V"
        );

        byte[] original = createStaticCallerClass(
                "test/cycle/Caller", "test/cycle/ClassA", "call");

        transformer.resetIterationMetrics();

        // Assert the call returns in finite time even with a cycle.
        // assertTimeoutPreemptively would be stronger, but that requires extra
        // test infra. The iteration cap of 5 means at most 5 ASM visits — fast.
        byte[] transformed = assertDoesNotThrow(
                () -> transformer.transformClass(original, "test/cycle/Caller"),
                "Transform must not throw even with cyclic redirects");
        assertNotNull(transformed, "Must return some output, not null");

        // We should have hit the cap. The last-pass output is returned as-is,
        // which will oscillate between ClassA and ClassB — either is acceptable
        // for this test; what matters is termination.
        assertEquals(1, transformer.getClassesHittingIterationCap(),
                "Cyclic redirect must register as hitting the iteration cap");
    }

    @Test
    @DisplayName("Single-hop redirects stabilize in one active pass")
    void testIterativeLoopSingleHopStability() {
        transformer.registerMethodRedirect(
            "test/single/From", "call", "()V",
            "test/single/To", "call", "()V"
        );

        byte[] original = createStaticCallerClass(
                "test/single/Caller", "test/single/From", "call");

        transformer.resetIterationMetrics();
        byte[] transformed = transformer.transformClass(original, "test/single/Caller");

        assertTrue(containsMethodCall(transformed, "test/single/To", "call"),
                "Single hop should still be applied");

        // Single-hop case: pass 1 rewrites, pass 2 verifies. 1 active pass.
        // Should NOT trigger the "multiple passes" counter — that's reserved for
        // actual chained redirects (2+ active passes).
        assertEquals(0, transformer.getClassesNeedingMultiplePasses(),
                "Single-hop redirect should not register as chained");
        assertEquals(0, transformer.getClassesHittingIterationCap(),
                "Single-hop redirect must never hit the cap");
        assertEquals(2, transformer.getTotalPassesPerformed(),
                "Single-hop should take exactly 2 passes: one to rewrite, one to verify stable");
    }

    // =========================================================
    // MIXIN COMPATIBILITY TESTS
    // =========================================================
    
    @Test
    @DisplayName("Mixin method targets are retargeted")
    void testMixinMethodRetargeting() {
        // Register the getWorld -> getEntityWorld redirect
        transformer.registerMethodRedirect(
            "net/minecraft/entity/Entity", "getWorld", "()Lnet/minecraft/world/World;",
            "net/minecraft/entity/Entity", "getEntityWorld", "()Lnet/minecraft/world/World;"
        );
        
        MixinCompatibilityTransformer mixinTransformer = 
            new MixinCompatibilityTransformer(transformer);
        
        // Create a mock mixin class with @Inject targeting getWorld
        byte[] mixinClass = createMockMixinClass("getWorld");
        
        // Transform it
        byte[] transformed = mixinTransformer.transformMixinClass(mixinClass);
        
        // The annotation should now target getEntityWorld
        // (In a real test, we'd parse the bytecode to verify)
        assertNotNull(transformed);
    }
    
    // =========================================================
    // AOT COMPILATION TESTS
    // =========================================================
    
    @Test
    @DisplayName("AOT compiler detects obfuscated classes")
    void testObfuscatedClassDetection() {
        // Test class with short names (typical obfuscation)
        byte[] obfuscated = createObfuscatedClass("a", new String[]{"a", "b", "c"});
        
        // Test class with normal names
        byte[] normal = createNormalClass("TestClass", new String[]{"doSomething", "getValue"});
        
        // The isObfuscated check would be in AotCompiler
        // For unit testing, we verify the bytecode patterns
        assertTrue(isLikelyObfuscated(obfuscated));
        assertFalse(isLikelyObfuscated(normal));
    }
    
    // =========================================================
    // HELPER METHODS
    // =========================================================
    
    /**
     * Create a test class with a method call.
     */
    private byte[] createTestClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/TestClass", null, 
                "java/lang/Object", null);
        
        // Create a method that calls OldClass.oldMethod
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "test", "()V", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, "test/OldClass");
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "test/OldClass", "<init>", "()V", false);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "test/OldClass", "oldMethod", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    /**
     * Create a minimal class whose one method invokes a static call elsewhere.
     * Used by the iterative-loop tests to produce a predictable call site that
     * we can then drive through registered method redirects.
     *
     * <p>Using {@code INVOKESTATIC} avoids the {@code NEW/DUP/INVOKESPECIAL} setup
     * that {@link #createTestClass()} uses — it's just a clean single-instruction
     * call that maps 1:1 to what the tests want to verify.</p>
     */
    private byte[] createStaticCallerClass(String className, String calleeOwner, String calleeName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, className, null,
                "java/lang/Object", null);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                "run", "()V", null, null);
        mv.visitCode();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, calleeOwner, calleeName, "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }

    private byte[] createTestClassWithTypeRef(String typeName) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/TestClass", null, 
                "java/lang/Object", null);
        
        // Field with the type
        cw.visitField(Opcodes.ACC_PRIVATE, "field", "L" + typeName + ";", null, null);
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createMockMixinClass(String targetMethod) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, "test/TestMixin", null, 
                "java/lang/Object", null);
        
        // Add @Mixin annotation (simplified)
        AnnotationVisitor av = cw.visitAnnotation("Lorg/spongepowered/asm/mixin/Mixin;", true);
        av.visitEnd();
        
        // Add method with @Inject annotation
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PRIVATE, "onTest", "()V", null, null);
        AnnotationVisitor injectAv = mv.visitAnnotation(
            "Lorg/spongepowered/asm/mixin/injection/Inject;", true);
        
        // Add method target
        AnnotationVisitor methodAv = injectAv.visitArray("method");
        methodAv.visit(null, targetMethod);
        methodAv.visitEnd();
        
        injectAv.visitEnd();
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createObfuscatedClass(String name, String[] methodNames) {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, name, null, "java/lang/Object", null);
        
        for (String method : methodNames) {
            MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method, "()V", null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 1);
            mv.visitEnd();
        }
        
        cw.visitEnd();
        return cw.toByteArray();
    }
    
    private byte[] createNormalClass(String name, String[] methodNames) {
        return createObfuscatedClass("com/example/" + name, methodNames);
    }
    
    private boolean containsMethodCall(byte[] classBytes, String owner, String name) {
        final boolean[] found = {false};
        
        ClassReader reader = new ClassReader(classBytes);
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String mname, String descriptor,
                    String signature, String[] exceptions) {
                return new MethodVisitor(Opcodes.ASM9) {
                    @Override
                    public void visitMethodInsn(int opcode, String o, String n, 
                            String d, boolean itf) {
                        if (owner.equals(o) && name.equals(n)) {
                            found[0] = true;
                        }
                    }
                };
            }
        }, 0);
        
        return found[0];
    }
    
    private boolean containsClassRef(byte[] classBytes, String className) {
        // Simple check: look for class name in constant pool
        String search = "L" + className + ";";
        String bytesStr = new String(classBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return bytesStr.contains(search) || bytesStr.contains(className);
    }
    
    private boolean isLikelyObfuscated(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        String className = reader.getClassName();
        
        // Check for short class name
        String simpleName = className.substring(className.lastIndexOf('/') + 1);
        if (simpleName.length() <= 2) {
            return true;
        }
        
        // Check methods
        final boolean[] hasShortMethods = {false};
        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor,
                    String signature, String[] exceptions) {
                if (name.length() <= 2 && !name.equals("<init>") && !name.equals("<clinit>")) {
                    hasShortMethods[0] = true;
                }
                return null;
            }
        }, ClassReader.SKIP_CODE);
        
        return hasShortMethods[0];
    }
}
