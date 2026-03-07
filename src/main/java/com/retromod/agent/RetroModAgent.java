/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.agent;

import com.retromod.core.RetroModTransformer;

import java.lang.instrument.Instrumentation;

/**
 * Java Agent for RetroMod.
 * 
 * This can be attached to the JVM using -javaagent:retromod-agent.jar
 * to enable full bytecode transformation capabilities.
 * 
 * Usage:
 *   java -javaagent:retromod-1.0.0-agent.jar -jar minecraft.jar
 * 
 * Or in Fabric launcher:
 *   Add to JVM arguments in your launcher
 */
public class RetroModAgent {
    
    // Store instrumentation for later use
    public static Instrumentation instrumentation;
    
    /**
     * Called when agent is attached at JVM startup via -javaagent.
     */
    public static void premain(String agentArgs, Instrumentation inst) {
        System.out.println("[RetroMod Agent] Installing class transformer (premain)");
        
        instrumentation = inst;
        
        // Install the transformer
        inst.addTransformer(RetroModTransformer.getInstance(), true);
        
        // Store reference for later access
        System.setProperty("retromod.agent.class", RetroModAgent.class.getName());
        
        System.out.println("[RetroMod Agent] Transformer installed successfully");
    }
    
    /**
     * Called when agent is attached to running JVM via Attach API.
     */
    public static void agentmain(String agentArgs, Instrumentation inst) {
        System.out.println("[RetroMod Agent] Installing class transformer (agentmain)");
        
        instrumentation = inst;
        
        // When attaching to a running JVM, we may need to retransform
        // already-loaded classes
        inst.addTransformer(RetroModTransformer.getInstance(), true);
        
        // Try to retransform already-loaded mod classes
        retransformLoadedClasses(inst);
        
        System.setProperty("retromod.agent.class", RetroModAgent.class.getName());
        
        System.out.println("[RetroMod Agent] Transformer installed and classes retransformed");
    }
    
    /**
     * Retransform classes that were already loaded before the agent was attached.
     */
    private static void retransformLoadedClasses(Instrumentation inst) {
        Class<?>[] loadedClasses = inst.getAllLoadedClasses();
        
        for (Class<?> clazz : loadedClasses) {
            String name = clazz.getName();
            
            // Only retransform mod classes, not Minecraft/system classes
            if (shouldRetransform(name) && inst.isModifiableClass(clazz)) {
                try {
                    inst.retransformClasses(clazz);
                } catch (Exception e) {
                    System.err.println("[RetroMod Agent] Failed to retransform: " + name);
                }
            }
        }
    }
    
    /**
     * Check if a class should be retransformed.
     */
    private static boolean shouldRetransform(String className) {
        // Skip JDK classes
        if (className.startsWith("java.") || 
            className.startsWith("javax.") ||
            className.startsWith("sun.") ||
            className.startsWith("jdk.")) {
            return false;
        }
        
        // Skip Minecraft core (we transform mod code, not MC itself)
        if (className.startsWith("net.minecraft.") ||
            className.startsWith("com.mojang.")) {
            return false;
        }
        
        // Skip mod loaders themselves
        if (className.startsWith("net.fabricmc.loader.") ||
            className.startsWith("net.minecraftforge.fml.") ||
            className.startsWith("cpw.mods.")) {
            return false;
        }
        
        // Skip RetroMod's own classes
        if (className.startsWith("com.retromod.")) {
            return false;
        }
        
        // Everything else might be a mod - retransform it
        return true;
    }
    
    /**
     * Get the stored Instrumentation instance.
     */
    public static Instrumentation getInstrumentation() {
        return instrumentation;
    }
    
    /**
     * Check if the agent is active.
     */
    public static boolean isActive() {
        return instrumentation != null;
    }
}
