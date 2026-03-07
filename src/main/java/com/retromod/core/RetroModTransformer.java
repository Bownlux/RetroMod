/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * This is the core bytecode transformer that intercepts class loading
 * and rewrites method calls to maintain compatibility across versions.
 */
package com.retromod.core;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Core transformer that rewrites bytecode at class load time.
 * OPTIMIZED: Uses caching and efficient lookup structures.
 * 
 * This handles:
 * 1. Method renames (e.g., getWorld -> getEntityWorld)
 * 2. Method removals (redirect to embedded shims)
 * 3. Class relocations (package changes between versions)
 * 4. Signature changes (parameter/return type modifications)
 */
public class RetroModTransformer implements ClassFileTransformer {
    
    private static final RetroModTransformer INSTANCE = new RetroModTransformer();
    
    // Maps: oldOwner/oldName/oldDesc -> newOwner/newName/newDesc
    private final Map<MethodKey, MethodTarget> methodRedirects = new ConcurrentHashMap<>(256);
    
    // Maps: oldClassName -> newClassName (internal names with /)
    private final Map<String, String> classRedirects = new ConcurrentHashMap<>(64);
    
    // Maps: oldFieldOwner/oldFieldName -> newFieldOwner/newFieldName
    private final Map<FieldKey, FieldTarget> fieldRedirects = new ConcurrentHashMap<>(64);
    
    // Packages that should be transformed (mod packages, not minecraft itself)
    private final Set<String> transformablePackages = ConcurrentHashMap.newKeySet();
    
    // Track which shim classes are embedded and available
    private final Set<String> embeddedShimClasses = ConcurrentHashMap.newKeySet();
    
    // OPTIMIZATION: Cache the remapper if no class redirects change
    private volatile Remapper cachedRemapper;
    private final AtomicInteger classRedirectsVersion = new AtomicInteger(0);
    
    // OPTIMIZATION: Fast owner lookup cache (reduces hash lookups)
    private final Set<String> methodRedirectOwners = ConcurrentHashMap.newKeySet();
    
    private RetroModTransformer() {
        // Register default shim package as transformable
        transformablePackages.add("com/retromod/shim/");
    }
    
    public static RetroModTransformer getInstance() {
        return INSTANCE;
    }
    
    /**
     * Register a method redirect.
     * When legacy code calls oldOwner.oldName(oldDesc), 
     * it will be rewritten to call newOwner.newName(newDesc).
     */
    public void registerMethodRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc) {
        
        MethodKey key = new MethodKey(oldOwner, oldName, oldDesc);
        MethodTarget target = new MethodTarget(newOwner, newName, newDesc);
        methodRedirects.put(key, target);
        methodRedirectOwners.add(oldOwner); // OPTIMIZATION: track owners
        
        RetroMod.LOGGER.debug("Registered method redirect: {}.{}{} -> {}.{}{}",
                oldOwner, oldName, oldDesc, newOwner, newName, newDesc);
    }
    
    /**
     * Register a class redirect (for relocated/renamed classes).
     */
    public void registerClassRedirect(String oldClass, String newClass) {
        classRedirects.put(oldClass, newClass);
        classRedirectsVersion.incrementAndGet(); // Invalidate cached remapper
        cachedRemapper = null;
        RetroMod.LOGGER.debug("Registered class redirect: {} -> {}", oldClass, newClass);
    }
    
    /**
     * Register a field redirect (field to field).
     */
    public void registerFieldRedirect(
            String oldOwner, String oldName,
            String newOwner, String newName) {
        
        FieldKey key = new FieldKey(oldOwner, oldName);
        FieldTarget target = new FieldTarget(newOwner, newName, null, null);
        fieldRedirects.put(key, target);
    }
    
    /**
     * Register a field redirect with descriptors.
     * If newDesc starts with "(", this is a field-to-method redirect
     * (GETSTATIC/GETFIELD becomes INVOKESTATIC/INVOKEVIRTUAL).
     */
    public void registerFieldRedirect(
            String oldOwner, String oldName, String oldDesc,
            String newOwner, String newName, String newDesc) {
        
        FieldKey key = new FieldKey(oldOwner, oldName);
        FieldTarget target = new FieldTarget(newOwner, newName, oldDesc, newDesc);
        fieldRedirects.put(key, target);
        
        RetroMod.LOGGER.debug("Registered field redirect: {}.{} {} -> {}.{} {}",
                oldOwner, oldName, oldDesc, newOwner, newName, newDesc);
    }
    
    /**
     * Mark a package as containing legacy mod code that should be transformed.
     */
    public void addTransformablePackage(String packagePrefix) {
        // Ensure it ends with /
        if (!packagePrefix.endsWith("/")) {
            packagePrefix = packagePrefix + "/";
        }
        transformablePackages.add(packagePrefix);
    }
    
    /**
     * Register an embedded shim class that provides removed API implementations.
     */
    public void registerEmbeddedShim(String className) {
        embeddedShimClasses.add(className);
    }
    
    @Override
    public byte[] transform(ClassLoader loader, String className, 
            Class<?> classBeingRedefined, ProtectionDomain protectionDomain, 
            byte[] classfileBuffer) {
        
        if (className == null || classfileBuffer == null) {
            return null;
        }
        
        // Only transform classes in registered packages
        if (!shouldTransform(className)) {
            return null;
        }
        
        // Use the hybrid engine which prefers AOT, falls back to JIT
        // This also handles performance monitoring automatically
        try {
            HybridTransformationEngine hybrid = HybridTransformationEngine.getInstance();
            String modId = guessModFromClass(className);
            return hybrid.transform(className, classfileBuffer, modId);
        } catch (Exception e) {
            RetroMod.LOGGER.error("Failed to transform class: {}", className, e);
            return null;
        }
    }
    
    /**
     * Guess which mod a class belongs to based on package name.
     */
    private String guessModFromClass(String className) {
        // Common mod package patterns
        // e.g., com/example/mymod/... -> mymod
        String[] parts = className.split("/");
        if (parts.length >= 3) {
            return parts[2]; // Usually the mod name is the 3rd part
        }
        return "unknown";
    }
    
    private boolean shouldTransform(String className) {
        for (String prefix : transformablePackages) {
            if (className.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Transform a class's bytecode, rewriting method/field/class references.
     * This is the core JIT transformation logic, also used by AOT.
     * OPTIMIZED: Uses cached remapper and efficient lookup structures.
     */
    public byte[] transformClass(byte[] originalBytes, String className) {
        // OPTIMIZATION: Skip if no redirects registered
        if (methodRedirects.isEmpty() && classRedirects.isEmpty() && fieldRedirects.isEmpty()) {
            return originalBytes;
        }

        ClassReader reader = new ClassReader(originalBytes);
        // Use SafeClassWriter with COMPUTE_FRAMES to properly generate StackMapTable.
        // SafeClassWriter handles getCommonSuperClass safely in modded environments.
        ClassWriter writer = new SafeClassWriter(reader, ClassWriter.COMPUTE_FRAMES);

        // OPTIMIZATION: Use cached remapper if class redirects haven't changed
        Remapper classRemapper = cachedRemapper;
        if (classRemapper == null && !classRedirects.isEmpty()) {
            classRemapper = new Remapper() {
                @Override
                public String map(String internalName) {
                    return classRedirects.getOrDefault(internalName, internalName);
                }
            };
            cachedRemapper = classRemapper;
        }

        ClassVisitor visitor = writer;

        // Only add remapping visitor if we have class redirects
        if (classRemapper != null) {
            visitor = new ClassRemapper(visitor, classRemapper);
        }

        // Add method/field redirect visitor
        visitor = new RetroModClassVisitor(Opcodes.ASM9, visitor);

        try {
            // Use EXPAND_FRAMES to properly feed frame data into COMPUTE_FRAMES
            reader.accept(visitor, ClassReader.EXPAND_FRAMES);
            return writer.toByteArray();
        } catch (Exception e) {
            // Fallback: try with COMPUTE_MAXS and preserve existing frames
            try {
                ClassWriter fallbackWriter = new SafeClassWriter(reader, ClassWriter.COMPUTE_MAXS);
                ClassVisitor fallbackVisitor = fallbackWriter;
                if (classRemapper != null) {
                    fallbackVisitor = new ClassRemapper(fallbackVisitor, classRemapper);
                }
                fallbackVisitor = new RetroModClassVisitor(Opcodes.ASM9, fallbackVisitor);
                // Don't skip frames - preserve existing StackMapTable entries
                reader.accept(fallbackVisitor, 0);
                return fallbackWriter.toByteArray();
            } catch (Exception e2) {
                RetroMod.LOGGER.warn("Transform failed for {}, returning original", className);
                return originalBytes;
            }
        }
    }

    /**
     * ClassWriter that safely handles getCommonSuperClass in modded environments.
     * ASM's default implementation uses Class.forName() which fails when classes
     * are loaded by custom classloaders (as in Minecraft mod loaders).
     */
    private static class SafeClassWriter extends ClassWriter {

        public SafeClassWriter(ClassReader classReader, int flags) {
            super(classReader, flags);
        }

        @Override
        protected String getCommonSuperClass(String type1, String type2) {
            try {
                return super.getCommonSuperClass(type1, type2);
            } catch (RuntimeException e) {
                // In modded MC, classes may not be resolvable via Class.forName().
                // java/lang/Object is always a valid common superclass.
                return "java/lang/Object";
            }
        }
    }
    
    /**
     * ASM ClassVisitor that rewrites method calls and field accesses.
     */
    private class RetroModClassVisitor extends ClassVisitor {
        
        public RetroModClassVisitor(int api, ClassVisitor classVisitor) {
            super(api, classVisitor);
        }
        
        @Override
        public MethodVisitor visitMethod(int access, String name, String descriptor,
                String signature, String[] exceptions) {
            
            MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
            // OPTIMIZATION: Only wrap if we have redirects
            if (methodRedirects.isEmpty() && fieldRedirects.isEmpty()) {
                return mv;
            }
            return new RetroModMethodVisitor(api, mv);
        }
    }
    
    /**
     * ASM MethodVisitor that rewrites individual method invocations.
     * OPTIMIZED: Uses fast owner lookup to skip unrelated calls.
     */
    private class RetroModMethodVisitor extends MethodVisitor {
        
        public RetroModMethodVisitor(int api, MethodVisitor methodVisitor) {
            super(api, methodVisitor);
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, 
                String descriptor, boolean isInterface) {
            
            // OPTIMIZATION: Fast path - skip if owner not in our redirect set
            if (!methodRedirectOwners.contains(owner)) {
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
            }
            
            // Check if this method call needs to be redirected
            MethodKey key = new MethodKey(owner, name, descriptor);
            MethodTarget target = methodRedirects.get(key);
            
            if (target != null) {
                // Redirect the call
                RetroMod.LOGGER.trace("Redirecting {}.{}{} -> {}.{}{}",
                        owner, name, descriptor,
                        target.owner, target.name, target.desc);
                
                super.visitMethodInsn(opcode, target.owner, target.name, 
                        target.desc, isInterface);
            } else {
                // No redirect, pass through unchanged
                super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            }
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            // Check if this field access needs to be redirected
            FieldKey key = new FieldKey(owner, name);
            FieldTarget target = fieldRedirects.get(key);
            
            if (target != null) {
                // Check if this is a field-to-method redirect
                if (target.newDesc != null && target.newDesc.startsWith("(")) {
                    // Convert GETSTATIC/GETFIELD to INVOKESTATIC
                    // (Field access becomes a static method call that returns the value)
                    int newOpcode = Opcodes.INVOKESTATIC;
                    super.visitMethodInsn(newOpcode, target.owner, target.name, target.newDesc, false);
                    RetroMod.LOGGER.trace("Redirected field {}.{} -> method {}.{}{}",
                            owner, name, target.owner, target.name, target.newDesc);
                } else {
                    // Standard field-to-field redirect
                    // Use new descriptor if provided, otherwise keep original
                    String newDescriptor = (target.newDesc != null) ? target.newDesc : descriptor;
                    super.visitFieldInsn(opcode, target.owner, target.name, newDescriptor);
                }
            } else {
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }
        }
    }
    
    // --- Key/Target record classes ---
    
    public record MethodKey(String owner, String name, String desc) {}
    public record MethodTarget(String owner, String name, String desc) {}
    public record FieldKey(String owner, String name) {}
    public record FieldTarget(String owner, String name, String oldDesc, String newDesc) {}
    
    public int getMethodRedirectCount() {
        return methodRedirects.size();
    }
    
    public int getClassRedirectCount() {
        return classRedirects.size();
    }
    
    public int getFieldRedirectCount() {
        return fieldRedirects.size();
    }
    
    public Map<MethodKey, MethodTarget> getMethodRedirects() {
        return Collections.unmodifiableMap(methodRedirects);
    }
    
    public Map<String, String> getClassRedirects() {
        return Collections.unmodifiableMap(classRedirects);
    }
}
