/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.aot;

import java.lang.annotation.*;

/**
 * Marker annotation indicating that a method or code region requires
 * JIT (Just-In-Time) transformation at class load time.
 * 
 * This is added by the HybridCompiler when:
 * - A method contains reflection-based calls that can't be statically analyzed
 * - Code uses invokedynamic with unknown bootstrap methods
 * - Parts of the code appear to be obfuscated
 * - The compiler couldn't fully analyze the code path
 * 
 * The JitRuntime transformer looks for this annotation and performs
 * the necessary transformations when the class is loaded.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface JitRequired {
    
    /**
     * If true, the entire method requires JIT transformation.
     * If false, only specific regions (indicated by regions()) need JIT.
     */
    boolean fullMethod() default true;
    
    /**
     * Instruction indices that require JIT transformation.
     * Only used when fullMethod is false.
     */
    int[] regions() default {};
    
    /**
     * Human-readable reason why JIT is required.
     * Useful for debugging and logging.
     */
    String reason() default "";
}
