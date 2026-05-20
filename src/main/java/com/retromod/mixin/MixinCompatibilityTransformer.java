/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.mixin;

import com.retromod.core.RetromodTransformer;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.*;

/**
 * Mixin Compatibility Layer for Retromod.
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
    
    private final RetromodTransformer transformer;
    
    // Maps old method references to new ones for Mixin targets
    private final Map<String, String> methodTargetRedirects = new HashMap<>();
    
    public MixinCompatibilityTransformer(RetromodTransformer transformer) {
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

        // Strip blocklisted handler methods FIRST. Some mixin handlers fatally
        // crash on the target MC and can't be repaired by remapping — chiefly a
        // MixinExtras @WrapOperation/@ModifyExpressionValue that captures a @Local
        // from a vanilla method whose local layout changed (the @Local resolves to
        // the wrong slot and MixinExtras emits an invalid bridge → VerifyError at
        // load, fatal before any soft-fail can run). Removing the handler here means
        // the framework never processes it: the mod loads with that one feature
        // inert instead of the whole game crashing. Curated in mixin-blocklist.json.
        Set<String> blockedMethods = MixinBlocklist.methodsToStrip(classNode.name);
        if (blockedMethods != null) {
            int before = classNode.methods.size();
            if (blockedMethods.isEmpty()) {
                // Whole-class: drop every injector handler (keeps <init>, helpers).
                classNode.methods.removeIf(MixinCompatibilityTransformer::hasInjectorAnnotation);
            } else {
                classNode.methods.removeIf(m -> blockedMethods.contains(m.name));
            }
            int removed = before - classNode.methods.size();
            if (removed > 0) {
                modified = true;
                LOGGER.info("Mixin blocklist: stripped {} handler method(s) from {} "
                        + "— this mixin is known to crash on the target MC; the mod loads "
                        + "with that feature inert", removed, classNode.name);
            }
        }

        // Transform class-level annotations (@Mixin targets)
        // Check BOTH visible and invisible — @Mixin is RuntimeInvisibleAnnotation
        for (List<AnnotationNode> annotations : List.of(
                classNode.visibleAnnotations != null ? classNode.visibleAnnotations : List.<AnnotationNode>of(),
                classNode.invisibleAnnotations != null ? classNode.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode annotation : annotations) {
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
        // Check both visible and invisible annotations
        // @Mixin is typically stored as RuntimeInvisibleAnnotation
        if (classNode.visibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.visibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) return true;
            }
        }
        if (classNode.invisibleAnnotations != null) {
            for (AnnotationNode annotation : classNode.invisibleAnnotations) {
                if (MIXIN_DESC.equals(annotation.desc)) return true;
            }
        }
        return false;
    }

    /**
     * Check if a class node is an interface mixin (e.g., @Accessor/@Invoker interfaces).
     */
    private boolean isInterfaceMixin(ClassNode classNode) {
        return (classNode.access & Opcodes.ACC_INTERFACE) != 0 && isMixinClass(classNode);
    }

    /**
     * Count the number of parameter slots in a method descriptor.
     * Doubles and longs take 2 slots, everything else takes 1.
     */
    /**
     * Whether a method carries an injector annotation — SpongePowered
     * ({@code @Inject}/{@code @Redirect}/{@code @ModifyArg}/{@code @ModifyVariable}/
     * {@code @ModifyConstant}), {@code @Overwrite}, or any MixinExtras annotation
     * ({@code @WrapOperation}, {@code @ModifyExpressionValue}, {@code @WrapMethod}, …).
     * Used for whole-class blocklist entries to drop every handler while leaving
     * constructors, {@code @Shadow}/{@code @Accessor} members, and plain helpers intact.
     */
    private static boolean hasInjectorAnnotation(MethodNode m) {
        return injectorPresent(m.visibleAnnotations) || injectorPresent(m.invisibleAnnotations);
    }

    private static boolean injectorPresent(List<AnnotationNode> anns) {
        if (anns == null) return false;
        for (AnnotationNode a : anns) {
            if (a.desc == null) continue;
            if (a.desc.contains("spongepowered/asm/mixin/injection/")
                    || a.desc.contains("llamalad7/mixinextras/")
                    || OVERWRITE_DESC.equals(a.desc)) {
                return true;
            }
        }
        return false;
    }

    private int countParameterSlots(String desc) {
        int slots = 0;
        int i = 1; // skip '('
        while (i < desc.length() && desc.charAt(i) != ')') {
            char c = desc.charAt(i);
            if (c == 'D' || c == 'J') {
                slots += 2;
                i++;
            } else if (c == 'L') {
                slots++;
                i = desc.indexOf(';', i) + 1;
            } else if (c == '[') {
                i++;
                // Skip array dimensions
                while (i < desc.length() && desc.charAt(i) == '[') i++;
                if (i < desc.length() && desc.charAt(i) == 'L') {
                    i = desc.indexOf(';', i) + 1;
                } else {
                    i++; // primitive array
                }
                slots++;
            } else {
                slots++;
                i++;
            }
        }
        return slots;
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
                // String-based targets: @Mixin(targets = {"net.minecraft.class_310"})
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
            } else if ("value".equals(key) && value instanceof List<?> values) {
                // Type-based targets: @Mixin(value = {class_310.class})
                // In ASM, class references in annotations are stored as Type objects
                List<Object> newValues = new ArrayList<>();
                for (Object v : values) {
                    if (v instanceof org.objectweb.asm.Type type) {
                        String internal = type.getInternalName();
                        String redirected = transformer.getClassRedirects()
                            .getOrDefault(internal, internal);
                        if (!internal.equals(redirected)) {
                            newValues.add(org.objectweb.asm.Type.getObjectType(redirected));
                            modified = true;
                            LOGGER.debug("Redirected Mixin value target: {} -> {}", internal, redirected);
                        } else {
                            newValues.add(v);
                        }
                    } else {
                        newValues.add(v);
                    }
                }
                annotation.values.set(i + 1, newValues);
            }
        }

        return modified;
    }
    
    /**
     * Transform method annotations (@Inject, @Redirect, etc).
     */
    private boolean transformMethodAnnotations(MethodNode method) {
        boolean modified = false;

        // Check BOTH visible and invisible annotations
        // Mixin annotations can be in either category
        for (List<AnnotationNode> annotations : List.of(
                method.visibleAnnotations != null ? method.visibleAnnotations : List.<AnnotationNode>of(),
                method.invisibleAnnotations != null ? method.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode annotation : annotations) {
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
        }

        return modified;
    }
    
    /**
     * Transform @Inject, @Redirect, @ModifyArg, @ModifyVariable annotations.
     */
    private boolean transformInjectionAnnotation(AnnotationNode annotation) {
        boolean modified = false;

        if (annotation.values == null) {
            annotation.values = new ArrayList<>();
        }

        boolean hasRequire = false;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            if ("require".equals(key)) {
                hasRequire = true;
            }

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

            // Transform "target" — used by MixinExtras annotations like
            // @ModifyExpressionValue(target = "Lnet/minecraft/client/gui/Gui;getDebugCrosshair()Z")
            // and by some standard @Inject variants. Same shape as @At's
            // target argument, so we run it through the same redirector that
            // transformAtAnnotation uses. Without this, a mixin whose OUTER
            // @Mixin target gets renamed by Retromod still has stale class
            // references inside its inner annotations — the mixin processor
            // then refuses the injection with "specifies a target class 'X',
            // which is not supported" (where X is the pre-rename class name).
            //
            // Crash report that motivated the addition: CustomHUD 4.1.3 on
            // MC 26.1.2 — outer target renamed to GuiGraphicsExtractor
            // correctly, but the @ModifyExpressionValue inner target still
            // said net/minecraft/client/gui/Gui and the injection failed.
            if ("target".equals(key)) {
                if (value instanceof List<?> targets) {
                    List<String> newTargets = new ArrayList<>();
                    boolean changed = false;
                    for (Object t : targets) {
                        if (t instanceof String s) {
                            String redirected = redirectMethodTarget(s);
                            newTargets.add(redirected);
                            if (!s.equals(redirected)) {
                                changed = true;
                                LOGGER.debug("Redirected @ModifyExpressionValue/@Inject target: {} -> {}", s, redirected);
                            }
                        } else {
                            // Preserve non-string entries unchanged.
                            // newTargets is typed as List<String> for the all-string case;
                            // if we hit a non-string we fall back to leaving the original list alone.
                            newTargets = null;
                            break;
                        }
                    }
                    if (newTargets != null && changed) {
                        annotation.values.set(i + 1, newTargets);
                        modified = true;
                    }
                } else if (value instanceof String s) {
                    String redirected = redirectMethodTarget(s);
                    if (!s.equals(redirected)) {
                        annotation.values.set(i + 1, redirected);
                        modified = true;
                        LOGGER.debug("Redirected @ModifyExpressionValue/@Inject target: {} -> {}", s, redirected);
                    }
                }
            }

            // Downgrade CAPTURE_FAILHARD to CAPTURE_FAILSOFT.
            //
            // WHY: "locals = LocalCapture.CAPTURE_FAILHARD" makes the mixin
            // framework CRASH the JVM when the local-variable table at the
            // injection site doesn't match the mixin's expected shape. MC
            // rewrites method bodies between versions — new locals are added,
            // removed, or reordered — so a mixin that targeted an older MC's
            // LVT will often find the new layout incompatible.
            //
            // With FAILHARD this manifests as a fatal BootstrapMethodError
            // at MC class load time and MC refuses to boot. Seen in the wild
            // with architectury.mixins.json:MixinFallingBlockEntity on 26.1
            // because FallingBlockEntity.tick() got a ServerLevel local added.
            //
            // FAILSOFT keeps the same runtime check but treats a mismatch as
            // "skip this injection" with a warning, so the mod's specific
            // feature hooked there is dead but MC launches and everything
            // else loads. That's the correct tradeoff for "run old mods on
            // new MC" — best-effort, not crash-everything-when-anything-drifts.
            //
            // The annotation attribute format for enum values is a String[]
            // where [0] is the enum descriptor and [1] is the constant name.
            if ("locals".equals(key) && value instanceof String[] enumValue
                    && enumValue.length == 2
                    && "Lorg/spongepowered/asm/mixin/injection/callback/LocalCapture;".equals(enumValue[0])
                    && "CAPTURE_FAILHARD".equals(enumValue[1])) {
                // Allocate a NEW String[] — don't mutate the value in place.
                // ASM can hand back the same interned array across multiple
                // AnnotationNode instances if classnodes are reused during the
                // iterative-loop's repeated passes. Mutating one instance's
                // slot would also rewrite every other AnnotationNode pointing
                // at it, which can make the second pass see no CAPTURE_FAILHARD
                // and decide the class is "stable" when it isn't.
                annotation.values.set(i + 1, new String[]{enumValue[0], "CAPTURE_FAILSOFT"});
                modified = true;
                LOGGER.debug("Downgraded CAPTURE_FAILHARD to CAPTURE_FAILSOFT in mixin annotation");
            }
        }

        // Set require=0 on all injection annotations to make them soft-fail.
        // Old mods on new MC versions should best-effort, not hard-crash when a
        // target method no longer exists.
        if (!hasRequire) {
            annotation.values.add("require");
            annotation.values.add(0);
            modified = true;
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
        String newName = remapMethodName(oldName);

        if (!newName.equals(oldName)) {
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

        // Check if annotation has explicit "value" parameter
        boolean hasExplicitValue = false;
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                String key = (String) annotation.values.get(i);
                Object value = annotation.values.get(i + 1);

                if ("value".equals(key) && value instanceof String s) {
                    hasExplicitValue = true;
                    String redirected = remapMethodName(s);
                    if (redirected.equals(s)) {
                        redirected = remapFieldName(s);
                    }
                    if (!s.equals(redirected)) {
                        annotation.values.set(i + 1, redirected);
                        modified = true;
                        LOGGER.debug("Redirected @Accessor/Invoker: {} -> {}", s, redirected);
                    }
                }
            }
        }

        // If no explicit value, derive target from method name and check for redirects
        if (!hasExplicitValue) {
            String methodName = method.name;
            String target = null;
            boolean isInvoker = INVOKER_DESC.equals(annotation.desc);

            if (isInvoker && methodName.startsWith("invoke")) {
                // @Invoker: invokeFindSlot → findSlot
                target = Character.toLowerCase(methodName.charAt(6)) + methodName.substring(7);
            } else if (methodName.startsWith("get")) {
                // @Accessor getter: getBoundKey → boundKey
                target = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("set")) {
                // @Accessor setter: setBoundKey → boundKey
                target = Character.toLowerCase(methodName.charAt(3)) + methodName.substring(4);
            } else if (methodName.startsWith("is")) {
                // @Accessor boolean getter: isQuickCrafting → quickCrafting (or isQuickCrafting)
                target = methodName; // Keep as-is, try both forms
            }

            if (target != null) {
                String redirected = isInvoker ? remapMethodName(target) : remapFieldName(target);
                if (!redirected.equals(target)) {
                    // Add explicit value parameter with the redirected name
                    if (annotation.values == null) {
                        annotation.values = new ArrayList<>();
                    }
                    annotation.values.add("value");
                    annotation.values.add(redirected);
                    modified = true;
                    LOGGER.debug("Added @{} value: {} -> {} (from method {})",
                            isInvoker ? "Invoker" : "Accessor", target, redirected, methodName);
                }
            }
        }

        return modified;
    }
    
    /**
     * Transform field annotations.
     */
    private boolean transformFieldAnnotations(FieldNode field) {
        boolean modified = false;
        // Check both visible and invisible annotations for @Shadow fields
        for (List<AnnotationNode> annotations : List.of(
                field.visibleAnnotations != null ? field.visibleAnnotations : List.<AnnotationNode>of(),
                field.invisibleAnnotations != null ? field.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode annotation : annotations) {
                if (SHADOW_DESC.equals(annotation.desc)) {
                    // Remap field name if it's an intermediary name
                    String oldName = field.name;
                    String newName = remapFieldName(oldName);
                    if (!newName.equals(oldName)) {
                        field.name = newName;
                        LOGGER.debug("Renamed @Shadow field: {} -> {}", oldName, newName);
                        modified = true;
                    }
                }
            }
        }
        return modified;
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
            return remapDescriptorClasses(direct);
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

            // Redirect method name (check shim redirects, then intermediary names)
            String newMethod = remapMethodName(methodName);

            // Remap class references within the descriptor
            desc = remapDescriptorClasses(desc);

            return "L" + newOwner + ";" + newMethod + desc;
        }

        // Check if it has a descriptor
        int descIdx = target.indexOf('(');
        if (descIdx >= 0) {
            String methodName = target.substring(0, descIdx);
            String desc = target.substring(descIdx);

            // Never rename constructors/static initializers
            String newMethod = methodName.startsWith("<")
                ? methodName
                : remapMethodName(methodName);
            // Remap class references within the descriptor
            desc = remapDescriptorClasses(desc);
            return newMethod + desc;
        }

        // Simple name - direct lookup (never rename constructors)
        if (target.startsWith("<")) return target;
        return remapMethodName(target);
    }

    /**
     * Remap a method name using shim redirects first, then intermediary→Mojang mappings.
     */
    private String remapMethodName(String methodName) {
        // Check shim-based redirects first
        String redirected = methodTargetRedirects.get(methodName);
        if (redirected != null) return redirected;

        // Check intermediary method names (method_XXXX → Mojang name)
        if (methodName.startsWith("method_")) {
            Map<String, String> intermediaryMethods = transformer.getIntermediaryMethodNames();
            String mojang = intermediaryMethods.get(methodName);
            if (mojang != null) return mojang;
        }

        return methodName;
    }

    /**
     * Remap a field name using intermediary→Mojang mappings and shim field redirects.
     */
    private String remapFieldName(String fieldName) {
        if (fieldName.startsWith("field_")) {
            Map<String, String> intermediaryFields = transformer.getIntermediaryFieldNames();
            String mojang = intermediaryFields.get(fieldName);
            if (mojang != null) return mojang;
        }
        // Also check shim-registered field redirects (e.g. boundKey → key)
        for (var entry : transformer.getFieldRedirects().entrySet()) {
            if (entry.getKey().name().equals(fieldName)) {
                return entry.getValue().name();
            }
        }
        return fieldName;
    }

    /**
     * Remap intermediary class references within a descriptor string.
     * E.g. "(Lnet/minecraft/class_542;)V" → "(Lnet/minecraft/client/main/GameConfig;)V"
     */
    private String remapDescriptorClasses(String descriptor) {
        if (descriptor == null || !descriptor.contains("class_")) return descriptor;

        Map<String, String> classRedirects = transformer.getClassRedirects();
        StringBuilder result = new StringBuilder(descriptor.length());
        int i = 0;
        while (i < descriptor.length()) {
            if (descriptor.charAt(i) == 'L') {
                // Found a class reference — find the semicolon
                int semi = descriptor.indexOf(';', i);
                if (semi > 0) {
                    String className = descriptor.substring(i + 1, semi);
                    String remapped = classRedirects.getOrDefault(className, className);
                    result.append('L').append(remapped).append(';');
                    i = semi + 1;
                } else {
                    result.append(descriptor.charAt(i));
                    i++;
                }
            } else {
                result.append(descriptor.charAt(i));
                i++;
            }
        }
        return result.toString();
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
     * Strips mixin entries that reference classes with broken targets
     * (removed methods, removed inner classes, etc) that would crash
     * the mixin system during application.
     *
     * @param configJson the mixin config JSON string
     * @param classDataLookup a function to get class bytes by internal name (package/Class),
     *                        or null if class analysis is not available
     * @return the transformed JSON with broken mixins stripped
     */
    public String transformMixinConfig(String configJson, Map<String, byte[]> classDataLookup) {
        if (classDataLookup == null || classDataLookup.isEmpty()) {
            return configJson;
        }

        // Extract the package prefix from the config
        String packagePrefix = extractJsonString(configJson, "package");
        if (packagePrefix == null) {
            return configJson;
        }

        String packagePath = packagePrefix.replace('.', '/');

        // Process "mixins" array (common/server)
        configJson = stripBrokenMixinEntries(configJson, "mixins", packagePath, classDataLookup);

        // Process "client" array
        configJson = stripBrokenMixinEntries(configJson, "client", packagePath, classDataLookup);

        // Process "server" array
        configJson = stripBrokenMixinEntries(configJson, "server", packagePath, classDataLookup);

        return configJson;
    }

    /**
     * Convenience overload for when no class data is available.
     */
    public String transformMixinConfig(String configJson) {
        return configJson;
    }

    /**
     * Process mixin entries: relocate targets where possible, partially strip broken methods,
     * and only fully strip a mixin as a last resort.
     *
     * Three-phase approach per mixin class:
     *   1. RELOCATE — rewrite annotation targets using redirect maps (method renames, class renames)
     *   2. PARTIAL STRIP — if some methods reference removed APIs, remove just those methods
     *   3. FULL STRIP — only if all mixin methods are broken or the class itself can't load
     */
    private String stripBrokenMixinEntries(String json, String arrayKey, String packagePath,
                                            Map<String, byte[]> classDataLookup) {
        // Find the array in JSON: "client": ["entry1", "entry2", ...]
        Pattern arrayPattern = Pattern.compile(
            "\"" + arrayKey + "\"\\s*:\\s*\\[([^\\]]*)]",
            Pattern.DOTALL
        );

        Matcher matcher = arrayPattern.matcher(json);
        if (!matcher.find()) {
            return json;
        }

        String arrayContent = matcher.group(1);

        // Extract individual entries
        Pattern entryPattern = Pattern.compile("\"([^\"]+)\"");
        Matcher entryMatcher = entryPattern.matcher(arrayContent);

        List<String> validEntries = new ArrayList<>();
        List<String> removedEntries = new ArrayList<>();
        int relocated = 0;
        int partiallyStripped = 0;

        while (entryMatcher.find()) {
            String mixinClassName = entryMatcher.group(1);
            String fullClassPath = packagePath + "/" + mixinClassName.replace('.', '/');

            byte[] classData = classDataLookup.get(fullClassPath + ".class");
            if (classData == null) {
                classData = classDataLookup.get(fullClassPath);
            }

            if (classData == null) {
                // Can't find class data — keep it and let the mixin system handle it
                validEntries.add(mixinClassName);
                continue;
            }

            // Phase 1: Try to RELOCATE the mixin (rewrite targets using redirect maps)
            byte[] relocatedData = relocateMixinClass(classData, fullClassPath);
            boolean wasRelocated = (relocatedData != classData);

            // Phase 2: Try PARTIAL STRIPPING (remove individual broken methods)
            PartialStripResult stripResult = partialStripMixin(
                wasRelocated ? relocatedData : classData, fullClassPath);

            if (stripResult.allBroken && !stripResult.isAccessorMixin) {
                // Phase 3: FULL STRIP — entire mixin is unsalvageable
                // NEVER strip accessor/invoker mixins (interfaces) — stripping them causes
                // IllegalClassLoadError when code references the mixin class directly.
                // With required=false, the mixin just fails to apply gracefully instead.
                removedEntries.add(mixinClassName);
                LOGGER.warn("Fully stripping mixin '{}' (all targets removed/broken)", mixinClassName);
            } else {
                validEntries.add(mixinClassName);

                // Update the class data in the lookup map so it gets written to the JAR
                byte[] finalData = stripResult.modifiedData != null ? stripResult.modifiedData :
                                   (wasRelocated ? relocatedData : classData);
                classDataLookup.put(fullClassPath + ".class", finalData);

                if (wasRelocated) {
                    relocated++;
                    LOGGER.info("Relocated mixin '{}' targets to new API names", mixinClassName);
                }
                if (stripResult.strippedMethods > 0) {
                    partiallyStripped++;
                    LOGGER.info("Partially stripped mixin '{}': removed {} broken method(s), kept {} working",
                        mixinClassName, stripResult.strippedMethods, stripResult.keptMethods);
                }
            }
        }

        if (removedEntries.isEmpty() && relocated == 0 && partiallyStripped == 0) {
            return json;
        }

        if (relocated > 0) {
            LOGGER.info("Relocated {} mixin(s) in '{}' array to use updated targets", relocated, arrayKey);
        }
        if (partiallyStripped > 0) {
            LOGGER.info("Partially stripped {} mixin(s) in '{}' array (removed broken methods, kept working ones)",
                partiallyStripped, arrayKey);
        }
        if (!removedEntries.isEmpty()) {
            LOGGER.info("Fully stripped {} mixin(s) from '{}' array: {}", removedEntries.size(), arrayKey, removedEntries);
        }

        // Rebuild the array
        StringBuilder newArray = new StringBuilder("\"" + arrayKey + "\": [");
        for (int i = 0; i < validEntries.size(); i++) {
            if (i > 0) newArray.append(",");
            newArray.append("\n    \"").append(validEntries.get(i)).append("\"");
        }
        if (!validEntries.isEmpty()) {
            newArray.append("\n  ");
        }
        newArray.append("]");

        return json.substring(0, matcher.start()) + newArray + json.substring(matcher.end());
    }

    /**
     * Result of partial mixin stripping.
     */
    private record PartialStripResult(
        boolean allBroken,      // true if ALL mixin methods are broken → full strip
        byte[] modifiedData,    // modified class bytes (null if no changes)
        int strippedMethods,    // number of methods removed
        int keptMethods,        // number of methods preserved
        boolean isAccessorMixin // true if mixin is an interface (@Accessor/@Invoker only)
    ) {
        PartialStripResult(boolean allBroken, byte[] modifiedData, int strippedMethods, int keptMethods) {
            this(allBroken, modifiedData, strippedMethods, keptMethods, false);
        }
    }

    /**
     * Relocate a mixin class by rewriting its annotation targets using the redirect maps.
     * This handles method renames, class renames, and descriptor updates.
     * Returns the original data if no relocation was needed.
     */
    private byte[] relocateMixinClass(byte[] classData, String className) {
        try {
            // Use the existing transformMixinClass() which already rewrites
            // @Inject/@Redirect method targets and @At targets using methodTargetRedirects
            byte[] result = transformMixinClass(classData);

            // Also apply class redirects to the mixin's @Mixin target annotations
            // and any type references in method descriptors
            if (result != classData) {
                LOGGER.debug("Relocated mixin targets in {}", className);
            }
            return result;
        } catch (Exception e) {
            LOGGER.debug("Failed to relocate mixin {}: {}", className, e.getMessage());
            return classData;
        }
    }

    /**
     * Partially strip a mixin class — remove individual broken methods while keeping working ones.
     * This preserves partial mod functionality when only some mixin handlers reference removed APIs.
     */
    private PartialStripResult partialStripMixin(byte[] classData, String className) {
        try {
            ClassReader reader = new ClassReader(classData);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            if (!isMixinClass(classNode)) {
                return new PartialStripResult(false, null, 0, classNode.methods.size());
            }

            List<MethodNode> brokenMethods = new ArrayList<>();
            List<MethodNode> workingMethods = new ArrayList<>();

            for (MethodNode method : classNode.methods) {
                // Skip constructors and non-mixin methods
                if (method.name.equals("<init>") || method.name.equals("<clinit>")) {
                    workingMethods.add(method);
                    continue;
                }

                // Check if this specific method has broken references
                if (isMethodBroken(method, classNode)) {
                    brokenMethods.add(method);
                } else {
                    workingMethods.add(method);
                }
            }

            if (brokenMethods.isEmpty()) {
                // Nothing broken — check class-level issues (superclass, @Shadow fields)
                if (hasClassLevelBreakage(classNode)) {
                    boolean isAccessor = (classNode.access & Opcodes.ACC_INTERFACE) != 0;
                    return new PartialStripResult(true, null, 0, 0, isAccessor);
                }
                return new PartialStripResult(false, null, 0, workingMethods.size());
            }

            // Check if ALL mixin handler methods are broken
            long workingHandlers = workingMethods.stream()
                .filter(m -> !m.name.equals("<init>") && !m.name.equals("<clinit>"))
                .count();

            if (workingHandlers == 0) {
                // All handlers are broken — full strip
                // But check if it's an accessor mixin (interface with only abstract methods)
                boolean isAccessor = (classNode.access & Opcodes.ACC_INTERFACE) != 0;
                return new PartialStripResult(true, null, brokenMethods.size(), 0, isAccessor);
            }

            // Partial strip — remove broken methods, keep working ones
            for (MethodNode broken : brokenMethods) {
                classNode.methods.remove(broken);
                LOGGER.debug("Stripped broken method '{}' from mixin {}", broken.name, className);
            }

            // Write modified class
            ClassWriter writer = new ClassWriter(0);
            classNode.accept(writer);
            byte[] modifiedData = writer.toByteArray();

            return new PartialStripResult(false, modifiedData, brokenMethods.size(), (int) workingHandlers);

        } catch (Exception e) {
            LOGGER.warn("Failed to partial-strip mixin {}: {}", className, e.getMessage());
            return new PartialStripResult(false, null, 0, 0);
        }
    }

    /**
     * Check if a specific method in a mixin class has broken references.
     */
    private boolean isMethodBroken(MethodNode method, ClassNode classNode) {
        // Check bytecode instructions for removed method/field/class references
        if (method.instructions != null) {
            for (var insn : method.instructions) {
                if (insn instanceof MethodInsnNode methodInsn) {
                    String ref = methodInsn.owner + "." + methodInsn.name;
                    if (isKnownRemovedMethod(ref)) return true;
                } else if (insn instanceof FieldInsnNode fieldInsn) {
                    String ref = fieldInsn.owner + "." + fieldInsn.name;
                    if (isKnownRemovedField(ref)) return true;
                } else if (insn instanceof TypeInsnNode typeInsn) {
                    if (isKnownRemovedClass(typeInsn.desc)) return true;
                }
            }
        }

        // Check mixin annotation targets — BOTH visible and invisible
        for (List<AnnotationNode> annotations : List.of(
                method.visibleAnnotations != null ? method.visibleAnnotations : List.<AnnotationNode>of(),
                method.invisibleAnnotations != null ? method.invisibleAnnotations : List.<AnnotationNode>of())) {
            for (AnnotationNode ann : annotations) {
                if (INJECT_DESC.equals(ann.desc) || REDIRECT_DESC.equals(ann.desc) ||
                    MODIFY_ARG_DESC.equals(ann.desc) || MODIFY_VAR_DESC.equals(ann.desc)) {
                    List<String> targets = extractAnnotationMethodTargets(ann);
                    for (String target : targets) {
                        if (isKnownRemovedMethod("." + target)) return true;
                        // Detect unresolved intermediary method names (not in our mapping)
                        if (hasUnresolvedIntermediaryName(target)) return true;
                    }
                    List<String> atTargets = extractAtTargets(ann);
                    for (String atTarget : atTargets) {
                        if (isKnownRemovedMethod(atTarget.replace(";", "."))) return true;
                        if (hasUnresolvedIntermediaryName(atTarget)) return true;
                    }
                }
                // @Overwrite on removed method
                if (OVERWRITE_DESC.equals(ann.desc)) {
                    if (isKnownRemovedMethod("." + method.name)) return true;
                }
                // @Shadow on unresolved intermediary method
                if (SHADOW_DESC.equals(ann.desc)) {
                    if (method.name.startsWith("method_") || method.name.startsWith("field_")) {
                        return true;
                    }
                }
                // @Accessor/@Invoker with unresolved intermediary or removed targets
                if (ACCESSOR_DESC.equals(ann.desc) || INVOKER_DESC.equals(ann.desc)) {
                    // Check the "value" field if present
                    if (ann.values != null) {
                        for (int ai = 0; ai < ann.values.size(); ai += 2) {
                            if ("value".equals(ann.values.get(ai)) && ann.values.get(ai + 1) instanceof String val) {
                                if (val.startsWith("method_") || val.startsWith("field_")) {
                                    return true;
                                }
                            }
                        }
                    }
                    // Also check the method's return type for removed classes
                    if (method.desc != null && method.desc.contains("class_")) {
                        return true;
                    }
                    // If the accessor/invoker descriptor references a polyfill embedded class,
                    // the original type was removed/changed and the accessor won't match
                    // the target field's type in the new MC version.
                    if (method.desc != null && method.desc.contains("com/retromod/polyfill/")) {
                        return true;
                    }
                }
            }
        }

        // Check return type references
        if (method.desc != null) {
            int retIdx = method.desc.lastIndexOf(')');
            if (retIdx >= 0) {
                String retType = method.desc.substring(retIdx + 1);
                if (retType.startsWith("L") && retType.endsWith(";")) {
                    String retClass = retType.substring(1, retType.length() - 1);
                    if (isKnownRemovedClass(retClass)) return true;
                }
            }
        }

        return false;
    }

    /**
     * Check for class-level breakage that affects the entire mixin
     * (e.g., superclass removed, @Shadow fields on removed targets).
     */
    private boolean hasClassLevelBreakage(ClassNode classNode) {
        // Check superclass
        if (classNode.superName != null && isKnownRemovedClass(classNode.superName)) {
            return true;
        }

        // Check @Shadow fields referencing removed types — check both visible and invisible
        for (FieldNode field : classNode.fields) {
            for (List<AnnotationNode> annotations : List.of(
                    field.visibleAnnotations != null ? field.visibleAnnotations : List.<AnnotationNode>of(),
                    field.invisibleAnnotations != null ? field.invisibleAnnotations : List.<AnnotationNode>of())) {
                for (AnnotationNode ann : annotations) {
                    if (SHADOW_DESC.equals(ann.desc)) {
                        if (isKnownRemovedField("." + field.name)) return true;
                        // Unresolved intermediary field names
                        if (field.name.startsWith("field_")) return true;
                        if (field.desc != null && field.desc.startsWith("L") && field.desc.endsWith(";")) {
                            String fieldType = field.desc.substring(1, field.desc.length() - 1);
                            if (isKnownRemovedClass(fieldType)) return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * Check if a mixin class has broken references that would crash the mixin system.
     * Analyzes the class bytecode for references to removed methods, fields, or inner classes.
     * Also checks mixin annotation targets (@Inject method=, @Redirect target=, @Overwrite, @Shadow)
     * against known-removed and known-renamed APIs.
     */
    private boolean isMixinBroken(byte[] classData, String className) {
        try {
            ClassReader reader = new ClassReader(classData);
            ClassNode classNode = new ClassNode();
            reader.accept(classNode, 0);

            if (!isMixinClass(classNode)) {
                return false;
            }

            // Check for references to known-removed classes/methods
            Set<String> referencedClasses = new HashSet<>();
            Set<String> referencedMethods = new HashSet<>();
            Set<String> referencedFields = new HashSet<>();

            // Scan bytecode instructions for class/method/field references
            for (MethodNode method : classNode.methods) {
                if (method.instructions == null) continue;

                for (var insn : method.instructions) {
                    if (insn instanceof MethodInsnNode methodInsn) {
                        referencedMethods.add(methodInsn.owner + "." + methodInsn.name + methodInsn.desc);
                        referencedClasses.add(methodInsn.owner);
                    } else if (insn instanceof FieldInsnNode fieldInsn) {
                        referencedFields.add(fieldInsn.owner + "." + fieldInsn.name);
                        referencedClasses.add(fieldInsn.owner);
                    } else if (insn instanceof TypeInsnNode typeInsn) {
                        referencedClasses.add(typeInsn.desc);
                    }
                }
            }

            // Check @Shadow fields — if they reference return types that are removed inner classes
            for (FieldNode field : classNode.fields) {
                if (field.desc != null && field.desc.startsWith("L") && field.desc.endsWith(";")) {
                    String refClass = field.desc.substring(1, field.desc.length() - 1);
                    referencedClasses.add(refClass);
                }
            }

            // Check method return types and parameter types
            for (MethodNode method : classNode.methods) {
                if (method.desc != null) {
                    int retIdx = method.desc.lastIndexOf(')');
                    if (retIdx >= 0) {
                        String retType = method.desc.substring(retIdx + 1);
                        if (retType.startsWith("L") && retType.endsWith(";")) {
                            String retClass = retType.substring(1, retType.length() - 1);
                            referencedClasses.add(retClass);
                        }
                    }
                }
            }

            // --- Check mixin annotation targets ---
            // This catches @Inject(method="oldMethod"), @Redirect(target="..."),
            // @Overwrite methods with old names, @Shadow fields with old names

            // Check @Overwrite methods — if the method name matches a removed/renamed method
            for (MethodNode method : classNode.methods) {
                if (method.visibleAnnotations != null) {
                    for (AnnotationNode ann : method.visibleAnnotations) {
                        if (OVERWRITE_DESC.equals(ann.desc)) {
                            // The method name IS the target — check if it's removed
                            if (isKnownRemovedMethod("." + method.name)) {
                                LOGGER.debug("Mixin {} has @Overwrite on removed method: {}", className, method.name);
                                return true;
                            }
                        }
                    }
                }
            }

            // Check @Inject/@Redirect method targets from annotations
            for (MethodNode method : classNode.methods) {
                if (method.visibleAnnotations == null) continue;
                for (AnnotationNode ann : method.visibleAnnotations) {
                    if (INJECT_DESC.equals(ann.desc) || REDIRECT_DESC.equals(ann.desc) ||
                        MODIFY_ARG_DESC.equals(ann.desc) || MODIFY_VAR_DESC.equals(ann.desc)) {
                        // Check "method" targets
                        List<String> targets = extractAnnotationMethodTargets(ann);
                        for (String target : targets) {
                            if (isKnownRemovedMethod("." + target)) {
                                LOGGER.debug("Mixin {} @Inject/@Redirect targets removed method: {}", className, target);
                                return true;
                            }
                        }
                        // Check @At targets
                        List<String> atTargets = extractAtTargets(ann);
                        for (String atTarget : atTargets) {
                            if (isKnownRemovedMethod(atTarget.replace(";", "."))) {
                                LOGGER.debug("Mixin {} @At targets removed method: {}", className, atTarget);
                                return true;
                            }
                        }
                    }
                }
            }

            // Check @Shadow fields by name
            for (FieldNode field : classNode.fields) {
                if (field.visibleAnnotations != null) {
                    for (AnnotationNode ann : field.visibleAnnotations) {
                        if (SHADOW_DESC.equals(ann.desc)) {
                            if (isKnownRemovedField("." + field.name)) {
                                LOGGER.debug("Mixin {} has @Shadow on removed field: {}", className, field.name);
                                return true;
                            }
                        }
                    }
                }
            }

            // --- Check against known-removed registries ---

            for (String refClass : referencedClasses) {
                if (isKnownRemovedClass(refClass)) {
                    LOGGER.debug("Mixin {} references removed class: {}", className, refClass);
                    return true;
                }
            }

            for (String refMethod : referencedMethods) {
                if (isKnownRemovedMethod(refMethod)) {
                    LOGGER.debug("Mixin {} references removed method: {}", className, refMethod);
                    return true;
                }
            }

            for (String refField : referencedFields) {
                if (isKnownRemovedField(refField)) {
                    LOGGER.debug("Mixin {} references removed field: {}", className, refField);
                    return true;
                }
            }

            return false;

        } catch (Exception e) {
            LOGGER.warn("Failed to analyze mixin class {}: {}", className, e.getMessage());
            return false;
        }
    }

    /**
     * Extract method target strings from @Inject/@Redirect annotations.
     */
    private List<String> extractAnnotationMethodTargets(AnnotationNode annotation) {
        List<String> targets = new ArrayList<>();
        if (annotation.values == null) return targets;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            if ("method".equals(key)) {
                if (value instanceof List<?> methods) {
                    for (Object m : methods) {
                        if (m instanceof String s) {
                            // Strip descriptor if present: "methodName(Largs;)V" -> "methodName"
                            int descIdx = s.indexOf('(');
                            targets.add(descIdx >= 0 ? s.substring(0, descIdx) : s);
                        }
                    }
                } else if (value instanceof String s) {
                    int descIdx = s.indexOf('(');
                    targets.add(descIdx >= 0 ? s.substring(0, descIdx) : s);
                }
            }
        }
        return targets;
    }

    /**
     * Extract @At target strings from an injection annotation.
     */
    private List<String> extractAtTargets(AnnotationNode annotation) {
        List<String> targets = new ArrayList<>();
        if (annotation.values == null) return targets;

        for (int i = 0; i < annotation.values.size(); i += 2) {
            String key = (String) annotation.values.get(i);
            Object value = annotation.values.get(i + 1);

            if ("at".equals(key)) {
                if (value instanceof AnnotationNode at) {
                    String target = extractAtTarget(at);
                    if (target != null) targets.add(target);
                } else if (value instanceof List<?> ats) {
                    for (Object at : ats) {
                        if (at instanceof AnnotationNode atNode) {
                            String target = extractAtTarget(atNode);
                            if (target != null) targets.add(target);
                        }
                    }
                }
            }
        }
        return targets;
    }

    /**
     * Extract the target string from a single @At annotation.
     */
    private String extractAtTarget(AnnotationNode at) {
        if (at.values == null) return null;
        for (int i = 0; i < at.values.size(); i += 2) {
            String key = (String) at.values.get(i);
            if ("target".equals(key) && at.values.get(i + 1) instanceof String s) {
                return s;
            }
        }
        return null;
    }

    // --- Known removed classes and methods registry ---
    // These are classes/methods that were completely removed between MC versions
    // and would cause mixin application to crash

    private static final Set<String> KNOWN_REMOVED_CLASSES = new HashSet<>();
    private static final Set<String> KNOWN_REMOVED_METHODS = new HashSet<>();
    private static final Set<String> KNOWN_REMOVED_FIELDS = new HashSet<>();

    static {
        // BufferBuilder inner class BuiltBuffer - removed in rendering rewrite
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_287$class_7433");

        // ChatOptionsScreen - removed
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_5500");

        // Various removed screen/GUI classes
        KNOWN_REMOVED_CLASSES.add("net/minecraft/class_442"); // SocialInteractionsScreen in some versions
    }

    static {
        // MinecraftClient.scheduledTasks Queue - removed
        KNOWN_REMOVED_FIELDS.add("net/minecraft/class_310.field_17404");

        // BufferBuilder.building flag - removed in rendering rewrite
        KNOWN_REMOVED_FIELDS.add("net/minecraft/class_287.field_1556");

        // Mouse.cursorLocked - removed
        KNOWN_REMOVED_FIELDS.add("net/minecraft/class_315.field_1866");
    }

    static {
        // BufferBuilder.end() / build() - removed in rendering rewrite
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_287.method_1326");

        // MinecraftClient removed methods
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_310.method_18858");
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_310.method_16901");

        // MinecraftClient.getFramerateLimit() - removed (Dynamic FPS crash)
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_310.method_16009");

        // ReentrantThreadExecutor.send() - removed
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_4093.method_18858");
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_4093.method_16901");

        // Player.addAdditionalSaveData - removed (Carry On crash)
        KNOWN_REMOVED_METHODS.add("net/minecraft/class_1657.method_5652");

        // Note: method_569 (writeToFile), method_5647 (writeNbt), method_38244 (createNbt)
        // still EXIST but with changed signatures. These are NOT removed — they should be
        // handled by the shim's method redirect system, not stripped.
    }

    /**
     * Register additional known-removed classes. Called during polyfill registration.
     */
    public static void registerRemovedClass(String internalName) {
        KNOWN_REMOVED_CLASSES.add(internalName);
    }

    /**
     * Register additional known-removed methods. Called during shim registration.
     */
    public static void registerRemovedMethod(String ownerAndName) {
        KNOWN_REMOVED_METHODS.add(ownerAndName);
    }

    private boolean isKnownRemovedClass(String internalName) {
        return KNOWN_REMOVED_CLASSES.contains(internalName);
    }

    private boolean isKnownRemovedField(String refField) {
        for (String removed : KNOWN_REMOVED_FIELDS) {
            if (refField.startsWith(removed)) {
                return true;
            }
        }
        return false;
    }

    private boolean isKnownRemovedMethod(String refMethod) {
        // refMethod format: "owner.nameDesc"
        // Check against "owner.name" (without descriptor)
        for (String removed : KNOWN_REMOVED_METHODS) {
            if (refMethod.startsWith(removed)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Check if a method target string contains unresolved intermediary names.
     * If a method_XXXX or class_XXXX name survived all remapping passes, it means
     * the target was removed from Minecraft and the reference is broken.
     */
    private boolean hasUnresolvedIntermediaryName(String target) {
        if (target == null) return false;
        // Check for unresolved class_XXXX in any part of the string (including descriptors)
        if (target.contains("class_")) return true;
        // Extract method name part (before descriptor)
        int descIdx = target.indexOf('(');
        String methodPart;
        if (target.contains(";") && target.startsWith("L")) {
            // Full reference: Lowner;methodName(desc)
            int semiIdx = target.indexOf(';');
            methodPart = descIdx >= 0 ? target.substring(semiIdx + 1, descIdx) : target.substring(semiIdx + 1);
        } else {
            methodPart = descIdx >= 0 ? target.substring(0, descIdx) : target;
        }
        return methodPart.startsWith("method_") || methodPart.startsWith("field_");
    }

    /**
     * Extract a string value from JSON by key name.
     */
    private String extractJsonString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + key + "\"\\s*:\\s*\"([^\"]+)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
