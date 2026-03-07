/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.legacy;

import org.objectweb.asm.*;

/**
 * Remaps obfuscated names based on the ObfuscationDatabase.
 */
public class ObfuscationRemapper extends ClassVisitor {
    
    private final ObfuscationDatabase database;
    private final String sourceVersion;
    private final String targetVersion;
    private String currentClass;
    
    public ObfuscationRemapper(ClassVisitor cv, ObfuscationDatabase database,
            String sourceVersion, String targetVersion) {
        super(Opcodes.ASM9, cv);
        this.database = database;
        this.sourceVersion = sourceVersion;
        this.targetVersion = targetVersion;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature,
            String superName, String[] interfaces) {
        
        this.currentClass = name;
        
        String newName = database.mapClass(sourceVersion, targetVersion, name);
        String newSuperName = superName != null ? 
            database.mapClass(sourceVersion, targetVersion, superName) : null;
        
        String[] newInterfaces = null;
        if (interfaces != null) {
            newInterfaces = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; i++) {
                newInterfaces[i] = database.mapClass(sourceVersion, targetVersion, interfaces[i]);
            }
        }
        
        String newSignature = signature != null ? remapSignature(signature) : null;
        
        super.visit(version, access, newName, newSignature, newSuperName, newInterfaces);
    }
    
    @Override
    public FieldVisitor visitField(int access, String name, String descriptor,
            String signature, Object value) {
        String newName = database.mapField(sourceVersion, targetVersion, currentClass, name);
        String newDescriptor = remapDescriptor(descriptor);
        return super.visitField(access, newName, newDescriptor, signature, value);
    }
    
    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor,
            String signature, String[] exceptions) {
        
        String newName = name;
        if (!name.equals("<init>") && !name.equals("<clinit>")) {
            newName = database.mapMethod(sourceVersion, targetVersion, 
                currentClass, name, descriptor);
        }
        
        String newDescriptor = remapDescriptor(descriptor);
        
        MethodVisitor mv = super.visitMethod(access, newName, newDescriptor, signature, exceptions);
        return new MethodRemapperVisitor(mv);
    }
    
    private class MethodRemapperVisitor extends MethodVisitor {
        
        MethodRemapperVisitor(MethodVisitor mv) {
            super(Opcodes.ASM9, mv);
        }
        
        @Override
        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
            String newOwner = database.mapClass(sourceVersion, targetVersion, owner);
            String newName = database.mapField(sourceVersion, targetVersion, owner, name);
            String newDescriptor = remapDescriptor(descriptor);
            super.visitFieldInsn(opcode, newOwner, newName, newDescriptor);
        }
        
        @Override
        public void visitMethodInsn(int opcode, String owner, String name, 
                String descriptor, boolean isInterface) {
            
            String newOwner = database.mapClass(sourceVersion, targetVersion, owner);
            String newName = name;
            if (!name.equals("<init>") && !name.equals("<clinit>")) {
                newName = database.mapMethod(sourceVersion, targetVersion, owner, name, descriptor);
            }
            String newDescriptor = remapDescriptor(descriptor);
            super.visitMethodInsn(opcode, newOwner, newName, newDescriptor, isInterface);
        }
        
        @Override
        public void visitTypeInsn(int opcode, String type) {
            String newType = database.mapClass(sourceVersion, targetVersion, type);
            super.visitTypeInsn(opcode, newType);
        }
        
        @Override
        public void visitLdcInsn(Object value) {
            if (value instanceof Type type) {
                if (type.getSort() == Type.OBJECT) {
                    String newName = database.mapClass(sourceVersion, targetVersion, 
                        type.getInternalName());
                    super.visitLdcInsn(Type.getObjectType(newName));
                    return;
                }
            }
            super.visitLdcInsn(value);
        }
    }
    
    private String remapDescriptor(String descriptor) {
        if (descriptor == null) return null;
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < descriptor.length()) {
            char c = descriptor.charAt(i);
            
            switch (c) {
                case 'V', 'Z', 'B', 'C', 'S', 'I', 'J', 'F', 'D' -> {
                    result.append(c);
                    i++;
                }
                case '[' -> {
                    result.append('[');
                    i++;
                }
                case 'L' -> {
                    int end = descriptor.indexOf(';', i);
                    String className = descriptor.substring(i + 1, end);
                    String newClassName = database.mapClass(sourceVersion, targetVersion, className);
                    result.append('L').append(newClassName).append(';');
                    i = end + 1;
                }
                case '(' -> {
                    result.append('(');
                    i++;
                }
                case ')' -> {
                    result.append(')');
                    i++;
                }
                default -> {
                    result.append(c);
                    i++;
                }
            }
        }
        
        return result.toString();
    }
    
    private String remapSignature(String signature) {
        StringBuilder result = new StringBuilder();
        int i = 0;
        
        while (i < signature.length()) {
            char c = signature.charAt(i);
            
            if (c == 'L') {
                int end = findClassEnd(signature, i);
                String className = signature.substring(i + 1, end);
                
                int genericStart = className.indexOf('<');
                if (genericStart > 0) {
                    String baseClass = className.substring(0, genericStart);
                    String newBaseClass = database.mapClass(sourceVersion, targetVersion, baseClass);
                    result.append('L').append(newBaseClass);
                    result.append(className.substring(genericStart));
                } else {
                    String newClassName = database.mapClass(sourceVersion, targetVersion, className);
                    result.append('L').append(newClassName);
                }
                i = end;
            } else {
                result.append(c);
                i++;
            }
        }
        
        return result.toString();
    }
    
    private int findClassEnd(String sig, int start) {
        int depth = 0;
        for (int i = start + 1; i < sig.length(); i++) {
            char c = sig.charAt(i);
            if (c == '<') depth++;
            else if (c == '>') depth--;
            else if (c == ';' && depth == 0) return i;
        }
        return sig.length();
    }
}
