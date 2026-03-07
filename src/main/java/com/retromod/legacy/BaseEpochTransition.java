/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.legacy;

import org.objectweb.asm.*;
import org.objectweb.asm.commons.*;
import java.util.*;

/**
 * Base class for epoch transitions.
 */
public abstract class BaseEpochTransition implements EpochTransition {
    
    protected final Map<String, String> classRedirects = new HashMap<>();
    protected final Map<MethodKey, MethodTarget> methodRedirects = new HashMap<>();
    protected final Map<FieldKey, FieldTarget> fieldRedirects = new HashMap<>();
    protected final List<String> shimClasses = new ArrayList<>();
    
    @Override
    public Map<String, String> getClassRedirects() {
        return Collections.unmodifiableMap(classRedirects);
    }
    
    @Override
    public Map<MethodKey, MethodTarget> getMethodRedirects() {
        return Collections.unmodifiableMap(methodRedirects);
    }
    
    @Override
    public Map<FieldKey, FieldTarget> getFieldRedirects() {
        return Collections.unmodifiableMap(fieldRedirects);
    }
    
    @Override
    public String[] getRequiredShims() {
        return shimClasses.toArray(new String[0]);
    }
    
    @Override
    public ClassVisitor createTransformer(ClassVisitor delegate, ObfuscationDatabase obfDb) {
        Remapper remapper = new SimpleRemapper(classRedirects) {
            @Override
            public String mapMethodName(String owner, String name, String descriptor) {
                MethodKey key = new MethodKey(owner, name, descriptor);
                MethodTarget target = methodRedirects.get(key);
                if (target != null) {
                    return target.name();
                }
                return super.mapMethodName(owner, name, descriptor);
            }
            
            @Override
            public String mapFieldName(String owner, String name, String descriptor) {
                FieldKey key = new FieldKey(owner, name, descriptor);
                FieldTarget target = fieldRedirects.get(key);
                if (target != null) {
                    return target.name();
                }
                return super.mapFieldName(owner, name, descriptor);
            }
        };
        
        return new ClassRemapper(delegate, remapper);
    }
    
    protected void addClass(String oldClass, String newClass) {
        classRedirects.put(oldClass, newClass);
    }
    
    protected void addMethod(String oldOwner, String oldName, String oldDesc,
                           String newOwner, String newName, String newDesc) {
        methodRedirects.put(
            new MethodKey(oldOwner, oldName, oldDesc),
            new MethodTarget(newOwner, newName, newDesc)
        );
    }
    
    protected void addField(String oldOwner, String oldName, String oldDesc,
                          String newOwner, String newName, String newDesc) {
        fieldRedirects.put(
            new FieldKey(oldOwner, oldName, oldDesc),
            new FieldTarget(newOwner, newName, newDesc)
        );
    }
    
    protected void addShim(String shimClass) {
        shimClasses.add(shimClass);
    }
}
