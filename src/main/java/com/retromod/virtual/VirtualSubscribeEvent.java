/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

import java.lang.annotation.*;

/**
 * Virtual replacement for @SubscribeEvent annotation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface VirtualSubscribeEvent {
    EventPriority priority() default EventPriority.NORMAL;
    boolean receiveCanceled() default false;
    
    enum EventPriority {
        HIGHEST, HIGH, NORMAL, LOW, LOWEST
    }
}
