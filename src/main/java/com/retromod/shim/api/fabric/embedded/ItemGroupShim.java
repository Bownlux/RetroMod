/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.api.fabric.embedded;

import java.lang.reflect.Method;

public class ItemGroupShim {
    public static Object modifyEntriesEvent(Object itemGroup) {
        try {
            Class<?> events = Class.forName("net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents");
            Method method = events.getMethod("modifyEntriesEvent", Class.forName("net.minecraft.registry.RegistryKey"));
            return method.invoke(null, itemGroup);
        } catch (Exception e) {
            throw new RuntimeException("ItemGroupShim failed", e);
        }
    }
}
