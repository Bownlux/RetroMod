/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import com.retromod.core.RetroModTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Hybrid AOT/JIT Compiler for RetroMod.
 * 
 * Instead of falling back to JIT for entire classes when they contain
 * untransformable code, this compiler:
 * 
 * 1. Analyzes each METHOD individually
 * 2. AOT compiles methods that are fully analyzable
 * 3. Marks specific methods or code regions for JIT transformation
 * 4. Injects JIT hooks only where needed
 * 
 * This provides the speed benefits of AOT for most code while maintaining
 * compatibility with obfuscated or dynamic code patterns.
 * 
 * Code regions that require JIT:
 * - Reflection-based method calls
 * - Dynamic proxy invocations
 * - Obfuscated method bodies with unpredictable control flow
 * - Methods using invokedynamic with unknown bootstrap methods
 */
public class HybridCompiler {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-hybrid");
    
    // Marker annotation added to methods requiring JIT
    private static final String JIT_REQUIRED_MARKER = "Lcom/retromod/aot/JitRequired;";
    
    // Statistics
    private int methodsFullyAot = 0;
    private int methodsPartialAot = 0;
    private int methodsJitOnly = 0;
    private int regionsMarkedForJit = 0;
    
    private final RetroModTransformer transformer;
    
    public HybridCompiler(RetroModTransformer transformer) {
        this.transformer = transformer;
    }
    
    /**
     * Compile a class using hybrid AOT/JIT approach.
     * Returns the transformed bytecode with JIT markers where needed.
     */
    public HybridCompilationResult compileClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, ClassReader.EXPAND_FRAMES);
        
        List<MethodAnalysis> methodAnalyses = new ArrayList<>();
        boolean classModified = false;
        
        // Analyze and transform each method
        for (MethodNode method : classNode.methods) {
            MethodAnalysis analysis = analyzeMethod(classNode.name, method);
            methodAnalyses.add(analysis);
            
            switch (analysis.compilationMode) {
                case FULL_AOT:
                    // Transform the entire method at compile time
                    boolean modified = transformMethodAot(method);
                    if (modified) classModified = true;
                    methodsFullyAot++;
                    break;
                    
                case PARTIAL_AOT:
                    // Transform what we can, mark regions for JIT
                    transformMethodPartial(method, analysis);
                    classModified = true;
                    methodsPartialAot++;
                    break;
                    
                case JIT_ONLY:
                    // Mark entire method for JIT
                    markMethodForJit(method);
                    classModified = true;
                    methodsJitOnly++;
                    break;
            }
        }
        
        // Write the modified class
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        byte[] result = writer.toByteArray();
        
        return new HybridCompilationResult(
            result,
            methodsFullyAot,
            methodsPartialAot,
            methodsJitOnly,
            regionsMarkedForJit,
            methodAnalyses
        );
    }
    
    /**
     * Analyze a method to determine its compilation mode.
     */
    private MethodAnalysis analyzeMethod(String className, MethodNode method) {
        MethodAnalysis analysis = new MethodAnalysis(method.name, method.desc);
        
        if (method.instructions == null || method.instructions.size() == 0) {
            analysis.compilationMode = CompilationMode.FULL_AOT;
            return analysis;
        }
        
        // Scan all instructions
        for (AbstractInsnNode insn : method.instructions) {
            InstructionAnalysis insnAnalysis = analyzeInstruction(insn);
            
            if (insnAnalysis.requiresJit) {
                analysis.jitRegions.add(new JitRegion(
                    method.instructions.indexOf(insn),
                    insnAnalysis.reason
                ));
            }
            
            if (insnAnalysis.isObfuscated) {
                analysis.obfuscatedRegions.add(method.instructions.indexOf(insn));
            }
            
            if (insnAnalysis.needsTransformation) {
                analysis.transformableInstructions.add(method.instructions.indexOf(insn));
            }
        }
        
        // Determine compilation mode
        if (analysis.jitRegions.isEmpty() && analysis.obfuscatedRegions.isEmpty()) {
            analysis.compilationMode = CompilationMode.FULL_AOT;
        } else if (analysis.transformableInstructions.size() > analysis.jitRegions.size() * 2) {
            // More transformable than JIT-required - use partial
            analysis.compilationMode = CompilationMode.PARTIAL_AOT;
        } else {
            // Too much JIT-required code - fall back entirely
            analysis.compilationMode = CompilationMode.JIT_ONLY;
        }
        
        return analysis;
    }
    
    /**
     * Analyze a single instruction.
     */
    private InstructionAnalysis analyzeInstruction(AbstractInsnNode insn) {
        InstructionAnalysis result = new InstructionAnalysis();
        
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                result.needsTransformation = needsMethodRedirect(methodInsn);
                result.requiresJit = isReflectiveCall(methodInsn);
                result.isObfuscated = isObfuscatedReference(methodInsn.owner, methodInsn.name);
                
                if (result.requiresJit) {
                    result.reason = "Reflective method call: " + methodInsn.owner + "." + methodInsn.name;
                }
            }
            
            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                result.needsTransformation = needsFieldRedirect(fieldInsn);
                result.isObfuscated = isObfuscatedReference(fieldInsn.owner, fieldInsn.name);
            }
            
            case AbstractInsnNode.TYPE_INSN -> {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                result.needsTransformation = needsClassRedirect(typeInsn.desc);
                result.isObfuscated = isObfuscatedClassName(typeInsn.desc);
            }
            
            case AbstractInsnNode.INVOKE_DYNAMIC_INSN -> {
                InvokeDynamicInsnNode dynInsn = (InvokeDynamicInsnNode) insn;
                result.requiresJit = !isKnownBootstrapMethod(dynInsn);
                result.reason = "Unknown invokedynamic: " + dynInsn.name;
            }
            
            case AbstractInsnNode.LDC_INSN -> {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                if (ldcInsn.cst instanceof Type type) {
                    result.needsTransformation = needsClassRedirect(type.getInternalName());
                }
            }
        }
        
        return result;
    }
    
    /**
     * Check if a method call is to reflection APIs.
     */
    private boolean isReflectiveCall(MethodInsnNode insn) {
        String owner = insn.owner;
        
        // Core reflection
        if (owner.equals("java/lang/Class") && 
            (insn.name.equals("getMethod") || insn.name.equals("getDeclaredMethod") ||
             insn.name.equals("getField") || insn.name.equals("getDeclaredField"))) {
            return true;
        }
        
        if (owner.equals("java/lang/reflect/Method") && insn.name.equals("invoke")) {
            return true;
        }
        
        if (owner.equals("java/lang/reflect/Field") && 
            (insn.name.equals("get") || insn.name.equals("set"))) {
            return true;
        }
        
        // MethodHandles
        if (owner.startsWith("java/lang/invoke/MethodHandle")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a reference appears obfuscated.
     */
    private boolean isObfuscatedReference(String owner, String name) {
        // Short names are suspicious
        if (name.length() <= 2 && !name.equals("<init>") && !name.equals("<clinit>")) {
            // Unless it's a common Java convention
            if (!name.matches("(is|to|of|at|in|on|up|id|op)")) {
                return true;
            }
        }
        
        // Check owner too
        return isObfuscatedClassName(owner);
    }
    
    /**
     * Check if a class name appears obfuscated.
     */
    private boolean isObfuscatedClassName(String className) {
        if (className == null) return false;
        
        String simpleName = className.substring(className.lastIndexOf('/') + 1);
        
        // Very short names
        if (simpleName.length() <= 2) {
            return true;
        }
        
        // Proguard-style names (aaa, aab, etc.)
        if (simpleName.matches("[a-z]{2,3}")) {
            return true;
        }
        
        // Default package
        if (!className.contains("/")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if an invokedynamic uses a known bootstrap method.
     */
    private boolean isKnownBootstrapMethod(InvokeDynamicInsnNode insn) {
        Handle bsm = insn.bsm;
        String owner = bsm.getOwner();
        
        // Lambda metafactory (standard lambdas)
        if (owner.equals("java/lang/invoke/LambdaMetafactory")) {
            return true;
        }
        
        // String concatenation
        if (owner.equals("java/lang/invoke/StringConcatFactory")) {
            return true;
        }
        
        // Record components
        if (owner.equals("java/lang/runtime/ObjectMethods")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Check if a method call needs to be redirected.
     */
    private boolean needsMethodRedirect(MethodInsnNode insn) {
        var key = new RetroModTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        return transformer.getMethodRedirects().containsKey(key);
    }
    
    /**
     * Check if a field access needs to be redirected.
     */
    private boolean needsFieldRedirect(FieldInsnNode insn) {
        // Check if the owner class is being redirected
        return transformer.getClassRedirects().containsKey(insn.owner);
    }
    
    /**
     * Check if a class reference needs to be redirected.
     */
    private boolean needsClassRedirect(String className) {
        return transformer.getClassRedirects().containsKey(className);
    }
    
    /**
     * Transform an entire method using AOT.
     */
    private boolean transformMethodAot(MethodNode method) {
        boolean modified = false;
        
        for (AbstractInsnNode insn : method.instructions) {
            switch (insn.getType()) {
                case AbstractInsnNode.METHOD_INSN -> {
                    MethodInsnNode methodInsn = (MethodInsnNode) insn;
                    modified |= transformMethodInsn(methodInsn);
                }
                
                case AbstractInsnNode.FIELD_INSN -> {
                    FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                    modified |= transformFieldInsn(fieldInsn);
                }
                
                case AbstractInsnNode.TYPE_INSN -> {
                    TypeInsnNode typeInsn = (TypeInsnNode) insn;
                    modified |= transformTypeInsn(typeInsn);
                }
                
                case AbstractInsnNode.LDC_INSN -> {
                    LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                    modified |= transformLdcInsn(ldcInsn);
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Transform a method partially, marking specific regions for JIT.
     * Uses InstructionLevelTransformer for maximum granularity.
     */
    private void transformMethodPartial(MethodNode method, MethodAnalysis analysis) {
        // Use instruction-level transformer for fine-grained partial AOT
        InstructionLevelTransformer insnTransformer = new InstructionLevelTransformer(transformer);
        InstructionLevelTransformer.TransformResult result = 
            insnTransformer.transformMethod(method, "partial");
        
        // Update statistics
        regionsMarkedForJit += result.jitRegions.size();
        
        if (!result.jitRegions.isEmpty()) {
            LOGGER.debug("Method {} has {} AOT, {} JIT instructions",
                method.name, result.aotCount, result.jitCount);
        }
    }
    
    /**
     * Inject JIT transformation hooks at specific instruction indices.
     */
    private void injectJitHooks(MethodNode method, List<JitRegion> regions) {
        if (regions.isEmpty()) return;
        
        // For each JIT region, we wrap it in a try block that catches
        // any transformation-related issues and falls back to the original
        
        // Actually, the simpler approach: mark methods with annotation
        // and let the JIT transformer handle them at runtime
        
        // Add annotation to indicate partial JIT
        if (method.visibleAnnotations == null) {
            method.visibleAnnotations = new ArrayList<>();
        }
        
        AnnotationNode jitAnnotation = new AnnotationNode(JIT_REQUIRED_MARKER);
        jitAnnotation.values = new ArrayList<>();
        jitAnnotation.values.add("regions");
        
        List<Integer> indices = new ArrayList<>();
        for (JitRegion region : regions) {
            indices.add(region.instructionIndex);
        }
        jitAnnotation.values.add(indices);
        
        method.visibleAnnotations.add(jitAnnotation);
        
        LOGGER.debug("Method {} has {} JIT regions", method.name, regions.size());
    }
    
    /**
     * Mark an entire method for JIT transformation.
     */
    private void markMethodForJit(MethodNode method) {
        if (method.visibleAnnotations == null) {
            method.visibleAnnotations = new ArrayList<>();
        }
        
        AnnotationNode jitAnnotation = new AnnotationNode(JIT_REQUIRED_MARKER);
        jitAnnotation.values = new ArrayList<>();
        jitAnnotation.values.add("fullMethod");
        jitAnnotation.values.add(true);
        
        method.visibleAnnotations.add(jitAnnotation);
    }
    
    private boolean transformMethodInsn(MethodInsnNode insn) {
        var key = new RetroModTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        var target = transformer.getMethodRedirects().get(key);
        
        if (target != null) {
            insn.owner = target.owner();
            insn.name = target.name();
            insn.desc = target.desc();
            return true;
        }
        
        // Check class redirect
        String newOwner = transformer.getClassRedirects().get(insn.owner);
        if (newOwner != null) {
            insn.owner = newOwner;
            return true;
        }
        
        return false;
    }
    
    private boolean transformFieldInsn(FieldInsnNode insn) {
        String newOwner = transformer.getClassRedirects().get(insn.owner);
        if (newOwner != null) {
            insn.owner = newOwner;
            return true;
        }
        return false;
    }
    
    private boolean transformTypeInsn(TypeInsnNode insn) {
        String newType = transformer.getClassRedirects().get(insn.desc);
        if (newType != null) {
            insn.desc = newType;
            return true;
        }
        return false;
    }
    
    private boolean transformLdcInsn(LdcInsnNode insn) {
        if (insn.cst instanceof Type type) {
            if (type.getSort() == Type.OBJECT) {
                String newType = transformer.getClassRedirects().get(type.getInternalName());
                if (newType != null) {
                    insn.cst = Type.getObjectType(newType);
                    return true;
                }
            }
        }
        return false;
    }
    
    private void transformInstruction(AbstractInsnNode insn) {
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> transformMethodInsn((MethodInsnNode) insn);
            case AbstractInsnNode.FIELD_INSN -> transformFieldInsn((FieldInsnNode) insn);
            case AbstractInsnNode.TYPE_INSN -> transformTypeInsn((TypeInsnNode) insn);
            case AbstractInsnNode.LDC_INSN -> transformLdcInsn((LdcInsnNode) insn);
        }
    }
    
    /**
     * Get compilation statistics.
     */
    public CompilationStats getStats() {
        return new CompilationStats(methodsFullyAot, methodsPartialAot, methodsJitOnly, regionsMarkedForJit);
    }
    
    /**
     * Reset statistics.
     */
    public void resetStats() {
        methodsFullyAot = 0;
        methodsPartialAot = 0;
        methodsJitOnly = 0;
        regionsMarkedForJit = 0;
    }
    
    // --- Inner classes ---
    
    public enum CompilationMode {
        FULL_AOT,      // Entire method can be AOT compiled
        PARTIAL_AOT,   // Some instructions AOT, some JIT
        JIT_ONLY       // Entire method needs JIT
    }
    
    public static class MethodAnalysis {
        public final String methodName;
        public final String methodDesc;
        public CompilationMode compilationMode = CompilationMode.FULL_AOT;
        public List<JitRegion> jitRegions = new ArrayList<>();
        public List<Integer> obfuscatedRegions = new ArrayList<>();
        public List<Integer> transformableInstructions = new ArrayList<>();
        
        public MethodAnalysis(String name, String desc) {
            this.methodName = name;
            this.methodDesc = desc;
        }
    }
    
    public static class JitRegion {
        public final int instructionIndex;
        public final String reason;
        
        public JitRegion(int index, String reason) {
            this.instructionIndex = index;
            this.reason = reason;
        }
    }
    
    public static class InstructionAnalysis {
        public boolean needsTransformation = false;
        public boolean requiresJit = false;
        public boolean isObfuscated = false;
        public String reason = null;
    }
    
    public record HybridCompilationResult(
        byte[] bytecode,
        int methodsFullyAot,
        int methodsPartialAot,
        int methodsJitOnly,
        int jitRegions,
        List<MethodAnalysis> methodAnalyses
    ) {}
    
    public record CompilationStats(
        int methodsFullyAot,
        int methodsPartialAot,
        int methodsJitOnly,
        int jitRegions
    ) {}
}
