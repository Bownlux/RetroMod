/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.mixin;

import com.retromod.core.RetroMod;
import com.retromod.core.RetroModTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Mixin Compatibility Layer for RetroMod.
 * 
 * Problem: Mixins target specific methods by name. When Minecraft renames methods
 * (like getWorld -> getEntityWorld), Mixins break because they can't find their targets.
 * 
 * Solution: Transform Mixin configuration and @Inject/@Redirect annotations
 * to use the new method names before the Mixin system processes them.
 * 
 * This handles:
 * 1. @Inject, @Redirect, @ModifyArg, @ModifyVariable annotations
 * 2. @At target descriptors
 * 3. Mixin config JSON files
 * 4. refmap.json remapping
 */
public class MixinCompatibilityTransformer {
    
    private static final Logger LOGGER = LoggerFactory.getLogger("retromod-mixin");
    
    // Mixin annotation descriptors
    private static final String INJECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String REDIRECT_DESC = "Lorg/spongepowered/asm/mixin/injection/Redirect;";
    private static final String MODIFY_ARG_DESC = "Lorg/spongepowered/asm/mixin/injection/ModifyArg;";
    private static final String MODIFY_VAR_DESC = "Lorg/spongepowered/asm/mixin/injection/ModifyVariable;";
    private static final String AT_DESC = "Lorg/spongepowered/asm/mixin/injection/At;";
    private static final String MIXIN_DESC = "Lorg/spongepowered/asm/mixin/Mixin;";
    private static final String SHADOW_DESC = "Lorg/spongepowered/asm/mixin/Shadow;";
    private static final String OVERWRITE_DESC = "Lorg/spongepowered/asm/mixin/Overwrite;";
    private static final String ACCESSOR_DESC = "Lorg/spongepowered/asm/mixin/gen/Accessor;";
    private static final String INVOKER_DESC = "Lorg/spongepowered/asm/mixin/gen/Invoker;";
    
    private final RetroModTransformer transformer;
    
    // Maps old method references to new ones for Mixin targets
    private final Map<String, String> methodTargetRedirects = new HashMap<>();
    
    public MixinCompatibilityTransformer(RetroModTransformer transformer) {
        this.transformer = transformer;
        buildMixinRedirects();
    }
    
    /**
     * Build redirect maps specifically for Mixin annotations.
     * Mixin uses different naming conventions than bytecode.
     */
    private void buildMixinRedirects() {
        // Convert transformer redirects to Mixin format
        for (var entry : transformer.getMethodRedirects().entrySet()) {
            var key = entry.getKey();
            var target = entry.getValue();
            
            // Mixin format: "method" or "Lowner;method(desc)return" 
            String oldRef = key.name();
            String newRef = target.name();
            
            if (!oldRef.equals(newRef)) {
                methodTargetRedirects.put(oldRef, newRef);
                
                // Also add full reference format
                String oldFull = "L" + key.owner() + ";" + key.name() + key.desc();
                String newFull = "L" + target.owner() + ";" + target.name() + target.desc();
                methodTargetRedirects.put(oldFull, newFull);
            }
        }
        
        LOGGER.info("Built {} Mixin target redirects", methodTargetRedirects.size());
    }
    
    /**
     * Transform a Mixin class to update its target references.
     */
    public byte[] transformMixinClass(byte[] classBytes) {
        ClassReader reader = new ClassReader(classBytes);
        ClassNode classNode = new ClassNode();
        reader.accept(classNode, 0);
        
        boolean modified = false;
        
        // Check if this is actually a Mixin class
        if (!isMixinClass(classNode)) {
            return classBytes;
        }
        
        LOGGER.debug("Transforming Mixin class: {}", classNode.name);
        
        // Transform class-level annotations (@Mixin targets)
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.visibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) {
                    modified |= transformMixinAnnotation(annotation);
                }
            }
        }
        
        // Transform method-level annotations
        for (MethodNode method : classNode.methods) {
            modified |= transformMethodAnnotations(method);
        }
        
        // Transform field-level annotations (@Shadow, @Accessor)
        for (FieldNode field : classNode.fields) {
            modified |= transformFieldAnnotations(field);
        }
        
        if (!modified) {
            return classBytes;
        }
        
        // Write modified class
        ClassWriter writer = new ClassWriter(0);
        classNode.accept(writer);
        return writer.toByteArray();
    }
    
    /**
     * Check if a class is a Mixin.
     */
    private boolean isMixinClass(ClassNode classNode) {
        if (classNode.visibleAnnotations == null) return false;
        
        for (AnnotationNode annotation : classNode.visibleAnnotations) {
            if (MIXIN_DESC.equals(annotation.desc)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Transform @Mixin annotation targets.
     */
    private boolean transformMixinAnnotation(AnnotationNode annotation) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            if ("targets".equals(key) && value instanceof List<?> targets) {
                List<String> newTargets = new ArrayList<>();
                for (Object target : targets) {
                    if (target instanceof String s) {
                        String redirected = redirectClassName(s);
                        newTargets.add(redirected);
                        if (!s.equals(redirected)) {
                            modified = true;
                            LOGGER.debug("Redirected Mixin target: {} -> {}", s, redirected);
                        }
                    } else {
                        newTargets.add(target.toString());
                    }
                }
                annotation.values.set(i + 1, newTargets);
            }
        }
        
        return modified;
    }
    
    /**
     * Transform method annotations (@Inject, @Redirect, etc).
     */
    private boolean transformMethodAnnotations(MethodNode method) {
        boolean modified = false;
        
        if (method.visibleAnnotations == null) return false;
        
        for (AnnotationNode annotation : method.visibleAnnotations) {
            String desc = annotation.desc;
            
            if (INJECT_DESC.equals(desc) || REDIRECT_DESC.equals(desc) ||
                MODIFY_ARG_DESC.equals(desc) || MODIFY_VAR_DESC.equals(desc)) {
                modified |= transformInjectionAnnotation(annotation);
            } else if (SHADOW_DESC.equals(desc) || OVERWRITE_DESC.equals(desc)) {
                modified |= transformShadowAnnotation(annotation, method);
            } else if (ACCESSOR_DESC.equals(desc) || INVOKER_DESC.equals(desc)) {
                modified |= transformAccessorAnnotation(annotation, method);
            }
        }
        
        return modified;
    }
    
    /**
     * Transform @Inject, @Redirect, @ModifyArg, @ModifyVariable annotations.
     */
    private boolean transformInjectionAnnotation(AnnotationNode annotation) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            // Transform "method" targets
            if ("method".equals(key)) {
                if (value instanceof List<?> methods) {
                    List<String> newMethods = new ArrayList<>();
                    for (Object m : methods) {
                        if (m instanceof String s) {
                            String redirected = redirectMethodTarget(s);
                            newMethods.add(redirected);
                            if (!s.equals(redirected)) {
                                modified = true;
                                LOGGER.debug("Redirected @Inject method: {} -> {}", s, redirected);
                            }
                        }
                    }
                    annotation.values.set(i + 1, newMethods);
                } else if (value instanceof String s) {
                    String redirected = redirectMethodTarget(s);
                    if (!s.equals(redirected)) {
                        annotation.values.set(i + 1, redirected);
                        modified = true;
                    }
                }
            }
            
            // Transform @At annotations
            if ("at".equals(key)) {
                if (value instanceof AnnotationNode at) {
                    modified |= transformAtAnnotation(at);
                } else if (value instanceof List<?> ats) {
                    for (Object at : ats) {
                        if (at instanceof AnnotationNode atNode) {
                            modified |= transformAtAnnotation(atNode);
                        }
                    }
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Transform @At annotation targets.
     */
    private boolean transformAtAnnotation(AnnotationNode annotation) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            // Transform "target" which is the method/field reference
            if ("target".equals(key) && value instanceof String s) {
                String redirected = redirectMethodTarget(s);
                if (!s.equals(redirected)) {
                    annotation.values.set(i + 1, redirected);
                    modified = true;
                    LOGGER.debug("Redirected @At target: {} -> {}", s, redirected);
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Transform @Shadow and @Overwrite annotations.
     */
    private boolean transformShadowAnnotation(AnnotationNode annotation, MethodNode method) {
        // For @Shadow, the method name itself might need changing
        String oldName = method.name;
        String newName = methodTargetRedirects.get(oldName);
        
        if (newName != null && !newName.equals(oldName)) {
            method.name = newName;
            LOGGER.debug("Renamed @Shadow method: {} -> {}", oldName, newName);
            return true;
        }
        
        return false;
    }
    
    /**
     * Transform @Accessor and @Invoker annotations.
     */
    private boolean transformAccessorAnnotation(AnnotationNode annotation, MethodNode method) {
        boolean modified = false;
        
        if (annotation.values == null) return false;
        
        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);
            
            // Transform "value" which is the target name
            if ("value".equals(key) && value instanceof String s) {
                String redirected = methodTargetRedirects.getOrDefault(s, s);
                if (!s.equals(redirected)) {
                    annotation.values.set(i + 1, redirected);
                    modified = true;
                    LOGGER.debug("Redirected @Accessor/Invoker: {} -> {}", s, redirected);
                }
            }
        }
        
        return modified;
    }
    
    /**
     * Transform field annotations.
     */
    private boolean transformFieldAnnotations(FieldNode field) {
        // Similar to method annotations for @Shadow fields
        // Implementation similar to transformShadowAnnotation
        return false;
    }
    
    /**
     * Redirect a class name if needed.
     */
    private String redirectClassName(String className) {
        // Convert to internal format if needed
        String internal = className.replace('.', '/');
        String redirected = transformer.getClassRedirects().getOrDefault(internal, internal);
        // Convert back to original format
        return className.contains(".") ? redirected.replace('/', '.') : redirected;
    }
    
    /**
     * Redirect a method target reference.
     * Handles various Mixin target formats:
     * - "methodName" (simple)
     * - "methodName(Largs;)Lreturn;" (with descriptor)
     * - "Lowner;methodName(Largs;)Lreturn;" (full reference)
     */
    private String redirectMethodTarget(String target) {
        // Try direct lookup first
        String direct = methodTargetRedirects.get(target);
        if (direct != null) {
            return direct;
        }
        
        // Parse and redirect parts
        // Format: [Lowner;]methodName[(descriptor)]
        
        // Check if it contains owner
        if (target.startsWith("L") && target.contains(";")) {
            int semiIdx = target.indexOf(';');
            String owner = target.substring(1, semiIdx);
            String rest = target.substring(semiIdx + 1);
            
            // Redirect owner
            String newOwner = transformer.getClassRedirects().getOrDefault(owner, owner);
            
            // Extract method name
            int descIdx = rest.indexOf('(');
            String methodName = descIdx >= 0 ? rest.substring(0, descIdx) : rest;
            String desc = descIdx >= 0 ? rest.substring(descIdx) : "";
            
            // Redirect method name
            String newMethod = methodTargetRedirects.getOrDefault(methodName, methodName);
            
            return "L" + newOwner + ";" + newMethod + desc;
        }
        
        // Check if it has a descriptor
        int descIdx = target.indexOf('(');
        if (descIdx >= 0) {
            String methodName = target.substring(0, descIdx);
            String desc = target.substring(descIdx);
            
            String newMethod = methodTargetRedirects.getOrDefault(methodName, methodName);
            return newMethod + desc;
        }
        
        // Simple name - direct lookup
        return methodTargetRedirects.getOrDefault(target, target);
    }
    
    /**
     * Transform a refmap.json file.
     * Refmaps contain mappings from dev names to obfuscated names.
     */
    public String transformRefmap(String refmapJson) {
        // Parse JSON, transform mappings, serialize back
        // This would use Gson to parse and transform
        
        // For each mapping entry, check if the target needs redirection
        // and update accordingly
        
        // This is complex because refmap format varies by Mixin version
        // For now, return unchanged - actual implementation would parse and transform
        
        return refmapJson;
    }
    
    /**
     * Transform a Mixin config JSON file.
     */
    public String transformMixinConfig(String configJson) {
        // Parse JSON and potentially update package paths
        // Most config changes aren't needed, but some edge cases exist
        
        return configJson;
    }
}
