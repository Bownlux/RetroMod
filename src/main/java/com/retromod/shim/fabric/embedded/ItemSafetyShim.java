/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;

/**
 * Safety shim for Item.getDefaultInstance() in MC 26.1+.
 *
 * In MC 26.1, item components are data-driven and bound during data pack loading.
 * Before components are bound, calling Item.getDefaultInstance() triggers
 * ItemStack → Holder$Reference.components() → NullPointerException("Components not bound yet").
 *
 * Old mods that create ItemStacks during static initialization or early lifecycle
 * callbacks (e.g., CLIENT_STARTED) hit this crash because components aren't bound yet.
 *
 * This shim wraps getDefaultInstance() to catch the NPE and return ItemStack.EMPTY,
 * allowing the mod to continue loading. The affected code paths typically don't need
 * a real ItemStack at that point — they're registering handlers or building data structures
 * that will be populated later when items are actually available.
 */
public class ItemSafetyShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-ItemSafety");
    private static volatile boolean warned = false;

    // Cached reflection lookups (initialized lazily, thread-safe via volatile).
    // We use reflection because MC classes aren't on the compile classpath — this
    // shim is compiled as part of RetroMod, not against a specific MC version.
    private static volatile Method getDefaultInstanceMethod;
    private static volatile Object itemStackEmpty;
    private static volatile boolean emptyResolved = false;

    /**
     * Safe wrapper for Item.getDefaultInstance().
     * Devirtualized: the Item instance is passed as the first parameter.
     *
     * @param item the Item to get the default stack for
     * @return the default ItemStack, or ItemStack.EMPTY if components aren't bound yet
     */
    public static Object safeGetDefaultInstance(Object item) {
        try {
            // Cache the Method object for performance (called frequently)
            Method m = getDefaultInstanceMethod;
            if (m == null || m.getDeclaringClass() != item.getClass()) {
                m = item.getClass().getMethod("getDefaultInstance");
                getDefaultInstanceMethod = m;
            }
            return m.invoke(item);
        } catch (Exception e) {
            // Check if it's the "Components not bound yet" error
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof NullPointerException npe
                    && npe.getMessage() != null
                    && npe.getMessage().contains("Components not bound")) {
                if (!warned) {
                    LOGGER.warn("Item.getDefaultInstance() called before components are bound — " +
                        "returning EMPTY. This is expected during early mod initialization in 26.1.");
                    warned = true;
                }
                return getItemStackEmpty();
            }
            // Not the expected error — rethrow
            if (e instanceof RuntimeException re) throw re;
            throw new RuntimeException(e);
        }
    }

    private static Object getItemStackEmpty() {
        if (!emptyResolved) {
            try {
                Class<?> itemStackClass = Class.forName("net.minecraft.world.item.ItemStack");
                itemStackEmpty = itemStackClass.getField("EMPTY").get(null);
            } catch (Exception e) {
                LOGGER.error("Failed to get ItemStack.EMPTY: {}", e.getMessage());
            }
            emptyResolved = true;
        }
        return itemStackEmpty;
    }

    /**
     * No-op method for redirecting removed void methods.
     * Used for e.g. Listener.setGain(float) which was removed in 26.1.
     * Devirtualized: the instance is passed as first parameter.
     */
    public static void noOp(Object instance, float value) {
        // Intentionally empty — method was removed in 26.1
    }

    /**
     * No-op for removed void methods with no params (devirtualized).
     * Used for e.g. VertexConsumer.endVertex() which auto-ends in 26.1.
     */
    public static void noOpVoid(Object instance) {
        // Intentionally empty — method was removed in 26.1
    }

    /** No-op for removed static void methods (e.g., RenderSystem.enableBlend). */
    public static void noOpStatic() {
        // Intentionally empty
    }

    /** No-op for removed RenderSystem.setShaderColor(float, float, float, float). */
    public static void noOpColor(float r, float g, float b, float a) {
        // Intentionally empty — rendering is now pipeline-based
    }

    /**
     * Bridge for TranslatableText.getKey() / TranslatableContents.getKey().
     * In old MC, TranslatableText was a Component with getKey().
     * In 26.1, the key is inside TranslatableContents (accessed via getContents()).
     * We redirect class_2588 → MutableComponent for instanceof to work,
     * but MutableComponent doesn't have getKey(). This bridge extracts it.
     * Devirtualized: the MutableComponent is passed as first arg.
     */
    public static String getTranslationKey(Object component) {
        try {
            // component.getContents() → ComponentContents
            Object contents = component.getClass().getMethod("getContents").invoke(component);
            // contents.getKey() → String (if TranslatableContents)
            return (String) contents.getClass().getMethod("getKey").invoke(contents);
        } catch (Exception e) {
            return ""; // Not a translatable component
        }
    }

    // ================================================================
    // CommandSourceStack.hasPermission(int) bridge for 26.1
    // ================================================================

    /**
     * Bridge for CommandSourceStack.hasPermission(int) removed in 26.1.
     * The permission system changed from int-based to PermissionSet-based.
     * Devirtualized: CommandSourceStack is passed as first arg.
     *
     * Maps old int levels to new PermissionLevel:
     *   0 = ALL, 1 = MODERATORS, 2 = GAMEMASTERS, 3 = ADMINS, 4 = OWNERS
     */
    public static boolean hasPermission(Object source, int level) {
        try {
            // source.permissions().hasPermission(new HasCommandLevel(PermissionLevel.byId(level)))
            ClassLoader cl = source.getClass().getClassLoader();

            // Get PermissionLevel.byId(level)
            Class<?> permLevelClass = cl.loadClass("net.minecraft.server.permissions.PermissionLevel");
            Object permLevel = permLevelClass.getMethod("byId", int.class).invoke(null, level);

            // Create HasCommandLevel(permLevel)
            Class<?> hasCommandLevelClass = cl.loadClass("net.minecraft.server.permissions.Permission$HasCommandLevel");
            Object permission = hasCommandLevelClass.getConstructor(permLevelClass).newInstance(permLevel);

            // source.permissions()
            Object permSet = source.getClass().getMethod("permissions").invoke(source);

            // permSet.hasPermission(permission)
            Class<?> permClass = cl.loadClass("net.minecraft.server.permissions.Permission");
            return (boolean) permSet.getClass().getMethod("hasPermission", permClass).invoke(permSet, permission);
        } catch (Exception e) {
            // Fallback: return true for level 0, false for higher
            return level <= 0;
        }
    }

    // ================================================================
    // Item tooltip dummy — prevents AbstractMethodError on hover
    // ================================================================

    /**
     * Returns a dummy Event for ItemTooltipCallback.
     * Replaces GETSTATIC ItemTooltipCallback.EVENT via field-to-method redirect.
     * The dummy event accepts .register() and .addPhaseOrdering() calls
     * but never fires, so old mods with 3-param getTooltip lambdas don't crash.
     */
    public static Object getDummyTooltipEvent() {
        return getDummyEvent("net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback");
    }

    // ================================================================
    // Screen mouse event dummy — prevents AbstractMethodError on click
    // ================================================================

    // Cache of dummy Events per listener type (singleton, never fires).
    // Old mods call EVENT.register(listener) — if the event interface changed signature,
    // we give them a dummy Event that silently accepts registrations but never invokes
    // the callback. This prevents AbstractMethodError at registration time.
    private static final java.util.concurrent.ConcurrentHashMap<String, Object> dummyEvents =
        new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Bridge for ScreenMouseEvents.allowMouseClick(Screen).
     */
    public static Object dummyAllowMouseClick(Object screen) {
        return getDummyEvent("net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents$AllowMouseClick");
    }

    /**
     * Bridge for ScreenMouseEvents.allowMouseRelease(Screen).
     */
    public static Object dummyAllowMouseRelease(Object screen) {
        return getDummyEvent("net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents$AllowMouseRelease");
    }

    /**
     * Bridge for ScreenMouseEvents.afterMouseScroll(Screen).
     */
    public static Object dummyAfterMouseScroll(Object screen) {
        return getDummyEvent("net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents$AfterMouseScroll");
    }

    /**
     * Bridge for ScreenMouseEvents.beforeMouseScroll(Screen).
     */
    public static Object dummyBeforeMouseScroll(Object screen) {
        return getDummyEvent("net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents$BeforeMouseScroll");
    }

    private static Object getDummyEvent(String listenerClassName) {
        return dummyEvents.computeIfAbsent(listenerClassName, ItemSafetyShim::createDummyEvent);
    }

    // ================================================================
    // Safe Event.register() — catches ArrayStoreException
    // ================================================================

    /**
     * Safe wrapper for Event.register(Object listener).
     * Catches ArrayStoreException that occurs when old mods register callbacks
     * with incompatible signatures on changed Fabric API events.
     * Devirtualized: Event is passed as first arg.
     */
    public static void safeEventRegister(Object event, Object listener) {
        try {
            event.getClass().getMethod("register", Object.class).invoke(event, listener);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ArrayStoreException) {
                LOGGER.debug("Skipped incompatible event listener: {}", listener.getClass().getName());
            } else {
                LOGGER.warn("Failed to register event listener: {}", cause.getMessage());
            }
        }
    }

    /**
     * Safe wrapper for Event.register(Identifier phase, Object listener).
     * Devirtualized: Event is passed as first arg.
     */
    public static void safeEventRegisterPhased(Object event, Object phase, Object listener) {
        try {
            for (java.lang.reflect.Method m : event.getClass().getMethods()) {
                if (m.getName().equals("register") && m.getParameterCount() == 2
                        && !m.getParameterTypes()[0].equals(Object.class)) {
                    m.invoke(event, phase, listener);
                    return;
                }
            }
            // Fallback: try 2-param register directly
            event.getClass().getMethod("register", phase.getClass(), Object.class).invoke(event, phase, listener);
        } catch (Exception e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof ArrayStoreException) {
                LOGGER.debug("Skipped incompatible phased event listener: {}", listener.getClass().getName());
            } else {
                LOGGER.warn("Failed to register phased event listener: {}", cause.getMessage());
            }
        }
    }

    /**
     * Create a Fabric Event instance that accepts registrations but never fires.
     * Uses EventFactory.createArrayBacked(Class, Function) via reflection.
     */
    private static Object createDummyEvent(String listenerClassName) {
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Class<?> listenerClass = cl.loadClass(listenerClassName);
            Class<?> eventFactory = cl.loadClass("net.fabricmc.fabric.api.event.EventFactory");

            // Find: EventFactory.createArrayBacked(Class, listeners -> invoker)
            // The invoker is what gets called when the event fires.
            // Since this is a dummy event that never fires, the invoker can be anything.
            for (Method m : eventFactory.getMethods()) {
                if (m.getName().equals("createArrayBacked") && m.getParameterCount() == 2) {
                    Class<?>[] params = m.getParameterTypes();
                    if (params[0] == Class.class && params[1].getName().contains("Function")) {
                        // Create a no-op invoker function via Proxy
                        Object invokerFunction = java.lang.reflect.Proxy.newProxyInstance(
                            cl, new Class<?>[]{ params[1] },
                            (proxy, method, args) -> {
                                // The function receives T[] and returns T (the invoker)
                                // Return a Proxy of the listener that returns true/null for any call
                                return java.lang.reflect.Proxy.newProxyInstance(
                                    cl, new Class<?>[]{ listenerClass },
                                    (p2, m2, a2) -> {
                                        if (m2.getReturnType() == boolean.class) return true;
                                        return null;
                                    });
                            });

                        return m.invoke(null, listenerClass, invokerFunction);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to create dummy mouse event: {}", e.getMessage());
        }
        return null; // Fallback: will cause NPE on .register() — better than AbstractMethodError spam
    }
}
