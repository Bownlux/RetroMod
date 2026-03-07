/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.legacy;

import org.objectweb.asm.*;

/**
 * Upgrades class file version to target Java version.
 */
public class ClassVersionUpgrader extends ClassVisitor {
    
    private final int targetVersion;
    
    public ClassVersionUpgrader(ClassVisitor cv, int targetVersion) {
        super(Opcodes.ASM9, cv);
        this.targetVersion = targetVersion;
    }
    
    @Override
    public void visit(int version, int access, String name, String signature, 
            String superName, String[] interfaces) {
        int newVersion = Math.max(version, targetVersion);
        super.visit(newVersion, access, name, signature, superName, interfaces);
    }
}
