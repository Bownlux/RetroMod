package com.retromod.shim.api.fabric.embedded;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
public class TrinketsShim {
    public static Optional<?> getTrinketComponent(Object entity) {
        try {
            Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            Method method = api.getMethod("getTrinketComponent", Class.forName("net.minecraft.entity.LivingEntity"));
            return (Optional<?>) method.invoke(null, entity);
        } catch (Exception e) { return Optional.empty(); }
    }
    public static void registerTrinket(Object item, Object trinket) {
        try {
            Class<?> api = Class.forName("dev.emi.trinkets.api.TrinketsApi");
            Method method = api.getMethod("registerTrinket", Class.forName("net.minecraft.item.Item"), Class.forName("dev.emi.trinkets.api.Trinket"));
            method.invoke(null, item, trinket);
        } catch (Exception e) { }
    }
    public static Object getModifiers(Object trinket, Object stack, Object slot, Object entity, UUID uuid) {
        try {
            for (Method m : trinket.getClass().getMethods()) {
                if (m.getName().equals("getModifiers") || m.getName().equals("getAttributeModifiers")) {
                    return m.invoke(trinket, stack, slot, entity, uuid);
                }
            }
        } catch (Exception e) { }
        // Return empty Multimap via reflection (Guava is available at runtime)
        try {
            Class<?> multimapClass = Class.forName("com.google.common.collect.HashMultimap");
            return multimapClass.getMethod("create").invoke(null);
        } catch (Exception e) {
            return new HashMap<>(); // Fallback
        }
    }
    public static Object getSlotGroup(String name) {
        try {
            Class<?> groups = Class.forName("dev.emi.trinkets.api.SlotGroups");
            Method method = groups.getMethod("getSlotGroup", String.class);
            return method.invoke(null, name);
        } catch (Exception e) { return null; }
    }
}
