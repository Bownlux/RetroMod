/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.virtual;

import java.lang.annotation.*;

/**
 * Virtual replacement for @Mod annotation.
 * Transforms old @Mod annotations to this virtual equivalent.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface VirtualMod {
    String modid();
    String name() default "";
    String version() default "";
    String[] dependencies() default {};
    boolean useMetadata() default false;
    String acceptedMinecraftVersions() default "";
    String acceptableRemoteVersions() default "";
    String acceptableSaveVersions() default "";
    String certificateFingerprint() default "";
    String modLanguage() default "java";
    String modLanguageAdapter() default "";
    boolean clientSideOnly() default false;
    boolean serverSideOnly() default false;
    String guiFactory() default "";
    String updateJSON() default "";
    int canBeDeactivated() default 0;
}
