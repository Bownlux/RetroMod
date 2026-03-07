/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.legacy;

import org.objectweb.asm.*;
import java.util.*;

/**
 * Represents a transformation between two Minecraft version epochs.
 */
public interface EpochTransition {
    
    String name();
    int sourceEpoch();
    int targetEpoch();
    ClassVisitor createTransformer(ClassVisitor delegate, ObfuscationDatabase obfDb);
    String[] getRequiredShims();
    Map<String, String> getClassRedirects();
    Map<MethodKey, MethodTarget> getMethodRedirects();
    Map<FieldKey, FieldTarget> getFieldRedirects();
    
    record MethodKey(String owner, String name, String desc) {}
    record MethodTarget(String owner, String name, String desc) {}
    record FieldKey(String owner, String name, String desc) {}
    record FieldTarget(String owner, String name, String desc) {}
}
