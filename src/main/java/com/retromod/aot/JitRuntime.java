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

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JIT Runtime Transformer for RetroMod.
 * 
 * This transformer runs at class load time and handles:
 * 1. Classes/methods marked with @JitRequired annotation
 * 2. Dynamic transformation based on runtime conditions
 * 3. Fallback transformation for code that couldn't be AOT compiled
 * 
 * Works in conjunction with HybridCompiler:
 * - HybridCompiler marks code regions that need JIT
 * - JitRuntime transforms those regions when the class is loaded
 */
public class JitRuntime implements ClassFileTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-jit");
    
    private static final String JIT_REQUIRED_DESC = "Lcom/retromod/aot/JitRequired;";
    
    private final RetroModTransformer transformer;
    
    // Cache of already-transformed classes
    private final Map<String, byte[]> transformedCache = new ConcurrentHashMap<>();
    
    // Track transformation statistics
    private int classesTransformed = 0;
    private int methodsTransformed = 0;
    private int regionsTransformed = 0;
    
    public JitRuntime(RetroModTransformer transformer) {
        this.transformer = transformer;
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, 
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, 
            byte[] classfileBuffer) {
        
        if (className == null || classfileBuffer == null) {
            return null;
        }
        
        // Check if this class has JIT markers
        if (!hasJitMarkers(classfileBuffer)) {
            return null;
        }
        
        try {
            byte[] result = transformJitMarkedClass(classfileBuffer, className);
            classesTransformed++;
            return result;
        } catch (Exception e) {
            LOGGER.error("JIT transformation failed for: {}", className, e);
            return null;
        }
    }
    
    /**
     * Quick check if class bytecode contains JIT markers.
     */
    private boolean hasJitMarkers(byte[] classBytes) {
        // Fast scan for the annotation descriptor in the constant pool
        String marker = "com/retromod/aot/JitRequired";
        String content = new String(classBytes, java.nio.charset.StandardCharsets.ISO_8859_1);
        return content.contains(marker);
    }
    
    /**
     * Transform a class that has JIT-marked methods or regions.
     */
    private byte[] transformJitMarkedClass(byte[] classBytes, String className) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        
        boolean modified = false;
        
        for (MethodNode method : classNode.methods) {
            if (hasJitAnnotation(method)) {
                modified |= transformJitMethod(method, classNode.name);
            }
        }
        
        if (!modified) {
            return null;
        }
        
        // Remove JIT annotations after processing
        removeJitAnnotations(classNode);
        
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        classNode.accept(writer);
        return writer.toByteArray();
    }
    
    /**
     * Check if a method has the @JitRequired annotation.
     */
    private boolean hasJitAnnotation(MethodNode method) {
        if (method.visibleAnnotations == null) return false;
        
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (JIT_REQUIRED_DESC.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Transform a JIT-marked method.
     */
    private boolean transformJitMethod(MethodNode method, String className) {
        LOGGER.debug("JIT transforming: {}.{}", className, method.name);
        
        AnnotationNode jitAnnotation = getJitAnnotation(method);
        if (jitAnnotation == null) return false;
        
        // Check if it's full method or specific regions
        boolean fullMethod = isFullMethodJit(jitAnnotation);
        List<Integer> regions = getJitRegions(jitAnnotation);
        
        boolean modified = false;
        
        if (fullMethod) {
            // Transform entire method
            modified = transformEntireMethod(method);
            methodsTransformed++;
        } else if (!regions.isEmpty()) {
            // Transform only specific regions
            modified = transformMethodRegions(method, regions);
            regionsTransformed += regions.size();
        }
        
        return modified;
    }
    
    /**
     * Get the @JitRequired annotation from a method.
     */
    private AnnotationNode getJitAnnotation(MethodNode method) {
        if (method.visibleAnnotations == null) return null;
        
        for (AnnotationNode annotation : method.visibleAnnotations) {
            if (JIT_REQUIRED_DESC.equals(annotation.desc)) {
                return annotation;
            }
        }
        return null;
    }
    
    /**
     * Check if the JIT annotation indicates full method transformation.
     */
    private boolean isFullMethodJit(AnnotationNode annotation) {
        if (annotation.values == null) return true;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            if ("fullMethod".equals(key) && Boolean.TRUE.equals(value)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Get the list of instruction indices that need JIT transformation.
     */
    @SuppressWarnings("unchecked")
    private List<Integer> getJitRegions(AnnotationNode annotation) {
        if (annotation.values == null) return Collections.emptyList();
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            if ("regions".equals(key) && value instanceof List<?>) {
                return (List<Integer>) value;
            }
        }
        return Collections.emptyList();
    }
    
    /**
     * Transform an entire method.
     */
    private boolean transformEntireMethod(MethodNode method) {
        boolean modified = false;
        
        for (AbstractInsnNode insn : method.instructions) {
            modified |= transformInstruction(insn);
        }
        
        return modified;
    }
    
    /**
     * Transform only specific regions of a method.
     */
    private boolean transformMethodRegions(MethodNode method, List<Integer> regions) {
        boolean modified = false;
        Set<Integer> regionSet = new HashSet<>(regions);
        
        int index = 0;
        for (AbstractInsnNode insn : method.instructions) {
            if (regionSet.contains(index)) {
                modified |= transformInstruction(insn);
            }
            index++;
        }
        
        return modified;
    }
    
    /**
     * Transform a single instruction.
     */
    private boolean transformInstruction(AbstractInsnNode insn) {
        switch (insn.getType()) {
            case AbstractInsnNode.METHOD_INSN -> {
                MethodInsnNode methodInsn = (MethodInsnNode) insn;
                return transformMethodInsn(methodInsn);
            }
            
            case AbstractInsnNode.FIELD_INSN -> {
                FieldInsnNode fieldInsn = (FieldInsnNode) insn;
                return transformFieldInsn(fieldInsn);
            }
            
            case AbstractInsnNode.TYPE_INSN -> {
                TypeInsnNode typeInsn = (TypeInsnNode) insn;
                return transformTypeInsn(typeInsn);
            }
            
            case AbstractInsnNode.LDC_INSN -> {
                LdcInsnNode ldcInsn = (LdcInsnNode) insn;
                return transformLdcInsn(ldcInsn);
            }
        }
        
        return false;
    }
    
    private boolean transformMethodInsn(MethodInsnNode insn) {
        // Check for method redirect
        var key = new RetroModTransformer.MethodKey(insn.owner, insn.name, insn.desc);
        var target = transformer.getMethodRedirects().get(key);
        
        if (target != null) {
            LOGGER.trace("JIT redirect: {}.{} -> {}.{}", 
                insn.owner, insn.name, target.owner(), target.name());
            insn.owner = target.owner();
            insn.name = target.name();
            insn.desc = target.desc();
            return true;
        }
        
        // Check for class redirect
        String newOwner = transformer.getClassRedirects().get(insn.owner);
        if (newOwner != null) {
            insn.owner = newOwner;
            return true;
        }
        
        // Handle reflection-based calls dynamically
        if (isReflectiveCall(insn)) {
            return handleReflectiveCall(insn);
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
    
    /**
     * Check if a method call is reflection-based.
     */
    private boolean isReflectiveCall(MethodInsnNode insn) {
        return insn.owner.equals("java/lang/Class") ||
               insn.owner.equals("java/lang/reflect/Method") ||
               insn.owner.equals("java/lang/reflect/Field") ||
               insn.owner.startsWith("java/lang/invoke/");
    }
    
    /**
     * Handle reflection-based method calls.
     * This is where the real JIT magic happens - we intercept reflection
     * and redirect it to use the correct (transformed) names.
     */
    private boolean handleReflectiveCall(MethodInsnNode insn) {
        // For reflection calls, we can't statically transform them.
        // Instead, we inject a wrapper that handles the remapping at runtime.
        
        // This would involve:
        // 1. Detecting Class.getMethod("oldName", ...) 
        // 2. Replacing it with Class.getMethod(RetroModReflection.remap("oldName"), ...)
        
        // For now, we just log it - full implementation would inject wrapper calls
        LOGGER.trace("JIT detected reflective call: {}.{}", insn.owner, insn.name);
        
        return false;
    }
    
    /**
     * Remove @JitRequired annotations after processing.
     */
    private void removeJitAnnotations(ClassNode classNode) {
        for (MethodNode method : classNode.methods) {
            if (method.visibleAnnotations != null) {
                method.visibleAnnotations.removeIf(a -> JIT_REQUIRED_DESC.equals(a.desc));
            }
        }
    }
    
    /**
     * Get transformation statistics.
     */
    public JitStats getStats() {
        return new JitStats(classesTransformed, methodsTransformed, regionsTransformed);
    }
    
    public record JitStats(
        int classesTransformed,
        int methodsTransformed,
        int regionsTransformed
    ) {}
}
