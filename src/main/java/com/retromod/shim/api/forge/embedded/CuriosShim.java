package com.retromod.shim.api.forge.embedded;
import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;
import java.util.HashMap;
public class CuriosShim {
    public static void onEquip(Object curio, String slot, Object entity) {
        try {
            Object context = createSlotContext(slot, entity);
            Method m = curio.getClass().getMethod("onEquip", Class.forName("top.theillusivec4.curios.api.SlotContext"), Class.forName("net.minecraft.world.item.ItemStack"));
            m.invoke(curio, context, null);
        } catch (Exception e) { }
    }
    public static void onUnequip(Object curio, String slot, Object entity) {
        try {
            Object context = createSlotContext(slot, entity);
            Method m = curio.getClass().getMethod("onUnequip", Class.forName("top.theillusivec4.curios.api.SlotContext"), Class.forName("net.minecraft.world.item.ItemStack"));
            m.invoke(curio, context, null);
        } catch (Exception e) { }
    }
    public static void curioTick(Object curio, String slot, Object entity) {
        try {
            Object context = createSlotContext(slot, entity);
            curio.getClass().getMethod("curioTick", Class.forName("top.theillusivec4.curios.api.SlotContext")).invoke(curio, context);
        } catch (Exception e) { }
    }
    private static Object createSlotContext(String slot, Object entity) {
        try {
            return Class.forName("top.theillusivec4.curios.api.SlotContext")
                .getConstructor(String.class, Class.forName("net.minecraft.world.entity.LivingEntity"), int.class, boolean.class, boolean.class)
                .newInstance(slot, entity, 0, false, true);
        } catch (Exception e) { return null; }
    }
    public static Object getCuriosHelper() {
        try { return Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethod("getCuriosHelper").invoke(null);
        } catch (Exception e) { return null; }
    }
    public static Optional<?> getCuriosHandler(Object entity) {
        try {
            Object lazyOpt = Class.forName("top.theillusivec4.curios.api.CuriosApi")
                .getMethod("getCuriosInventory", Class.forName("net.minecraft.world.entity.LivingEntity")).invoke(null, entity);
            return (Optional<?>) lazyOpt.getClass().getMethod("resolve").invoke(lazyOpt);
        } catch (Exception e) { return Optional.empty(); }
    }
    public static String getSlotIdentifier(Object preset) {
        try { return (String) preset.getClass().getMethod("getIdentifier").invoke(preset);
        } catch (Exception e) { return "unknown"; }
    }
    public static void registerSlotType(String modId, Object slotType) {
        try {
            for (Method m : Class.forName("top.theillusivec4.curios.api.CuriosApi").getMethods()) {
                if (m.getName().contains("registerSlot") || m.getName().contains("enqueueSlotType")) {
                    m.invoke(null, modId, slotType); return;
                }
            }
        } catch (Exception e) { }
    }
    public static Object getAttributeModifiers(Object curio, String slot) {
        try {
            Object context = createSlotContext(slot, null);
            return curio.getClass().getMethod("getAttributeModifiers", Class.forName("top.theillusivec4.curios.api.SlotContext"), UUID.class)
                .invoke(curio, context, UUID.randomUUID());
        } catch (Exception e) {
            // Return empty Multimap via reflection (Guava available at runtime)
            try {
                Class<?> multimapClass = Class.forName("com.google.common.collect.HashMultimap");
                return multimapClass.getMethod("create").invoke(null);
            } catch (Exception ex) {
                return new HashMap<>(); // Fallback
            }
        }
    }
    public static boolean canRender(Object curio, String slot, Object entity) {
        try {
            Object context = createSlotContext(slot, entity);
            return (Boolean) curio.getClass().getMethod("canRender", Class.forName("top.theillusivec4.curios.api.SlotContext")).invoke(curio, context);
        } catch (Exception e) { return true; }
    }
    public static void render(Object curio, String slot, Object poseStack, Object bufferSource, int light, Object entity, float a, float b, float c, float d, float e, float f) {
        try {
            Object context = createSlotContext(slot, entity);
            for (Method m : curio.getClass().getMethods()) {
                if (m.getName().equals("render") || m.getName().equals("curioRender")) {
                    m.invoke(curio, context, poseStack, bufferSource, light, a, b, c, d, e, f); return;
                }
            }
        } catch (Exception ex) { }
    }
}
