/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 */
package com.retromod.gui;

import com.retromod.util.McReflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.function.Consumer;

/**
 * Universal title screen button injector that works across ALL mod loaders.
 *
 * Detects the running loader at runtime and uses the appropriate event system:
 *   - Fabric:   ScreenEvents.AFTER_INIT  (Fabric API)
 *   - NeoForge: NeoForge.EVENT_BUS       (ScreenEvent.Init.Post)
 *   - Forge:    MinecraftForge.EVENT_BUS  (ScreenEvent.Init.Post)
 *
 * All API calls are done via reflection so RetroMod compiles without
 * compile-time dependencies on any loader-specific event classes.
 *
 * When the title screen opens, this injector adds a small "RetroMod" button
 * that opens the RetroMod mod manager (file picker + transformation).
 */
public final class TitleScreenButtonInjector {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-TitleButton");
    private static boolean registered = false;

    // Resolved MC classes (cached after first successful lookup)
    private static Class<?> titleScreenClass;
    private static Class<?> screenClass;
    private static Class<?> textClass;
    private static Class<?> buttonWidgetClass;

    private TitleScreenButtonInjector() {}

    /**
     * Register the title screen button injector.
     * Auto-detects the mod loader and uses the appropriate event system.
     * Safe to call multiple times - only registers once.
     */
    public static synchronized void register() {
        if (registered) return;

        // Pre-resolve MC classes we'll need for button injection
        if (!resolveMcClasses()) {
            LOGGER.warn("Could not resolve Minecraft GUI classes - title screen button unavailable");
            return;
        }

        boolean success = false;

        // Try each loader's event system
        if (McReflect.isFabric()) {
            success = tryFabricScreenEvents();
        }

        if (!success && McReflect.isNeoForge()) {
            success = tryNeoForgeEvents();
        }

        if (!success && McReflect.isForge()) {
            success = tryForgeEvents();
        }

        if (success) {
            registered = true;
            LOGGER.info("Title screen button injector registered");
        } else {
            LOGGER.info("Title screen button not available (no supported event system found)");
        }
    }

    // =====================================================================
    // MC Class Resolution
    // =====================================================================

    /**
     * Pre-resolve the Minecraft classes needed for button creation.
     * Uses McReflect to handle yarn/mojang/intermediary name differences.
     */
    private static boolean resolveMcClasses() {
        try {
            // TitleScreen
            titleScreenClass = McReflect.findClass(
                "net.minecraft.client.gui.screen.TitleScreen",   // yarn
                "net.minecraft.client.gui.screens.TitleScreen"   // mojang
            );

            // Screen (parent class)
            screenClass = McReflect.findClass(
                "net.minecraft.client.gui.screen.Screen",   // yarn
                "net.minecraft.client.gui.screens.Screen"   // mojang
            );

            // Text / Component (for button label)
            textClass = McReflect.findClass(
                "net.minecraft.text.Text",                    // yarn
                "net.minecraft.network.chat.Component"        // mojang
            );

            // ButtonWidget / Button
            buttonWidgetClass = McReflect.findClass(
                "net.minecraft.client.gui.widget.ButtonWidget",  // yarn
                "net.minecraft.client.gui.components.Button"     // mojang
            );

            return titleScreenClass != null && screenClass != null
                && textClass != null && buttonWidgetClass != null;

        } catch (Exception e) {
            LOGGER.debug("Failed to resolve MC GUI classes: {}", e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Fabric: ScreenEvents.AFTER_INIT
    // =====================================================================

    /**
     * Register via Fabric API's ScreenEvents.AFTER_INIT.
     *
     * Fabric API class names (net.fabricmc.fabric.api.*) are NOT obfuscated,
     * so plain Class.forName works in both dev and production.
     *
     * Callback signature:
     *   void afterInit(MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight)
     */
    private static boolean tryFabricScreenEvents() {
        try {
            Class<?> screenEventsClass = Class.forName(
                "net.fabricmc.fabric.api.client.screen.v1.ScreenEvents");
            Object afterInitEvent = screenEventsClass.getField("AFTER_INIT").get(null);

            // Find the AfterInit callback interface
            Class<?> afterInitInterface = Class.forName(
                "net.fabricmc.fabric.api.client.screen.v1.ScreenEvents$AfterInit");

            // Create a proxy that implements AfterInit
            Object proxy = Proxy.newProxyInstance(
                afterInitInterface.getClassLoader(),
                new Class<?>[]{afterInitInterface},
                (proxyObj, method, args) -> {
                    // AfterInit has one functional method with 4 params:
                    // (MinecraftClient client, Screen screen, int scaledWidth, int scaledHeight)
                    if (method.getParameterCount() == 4 && args != null) {
                        Object screen = args[1];
                        if (titleScreenClass.isInstance(screen)) {
                            addRetroModButton(screen);
                        }
                    }
                    return null;
                }
            );

            // Register: AFTER_INIT.register(proxy)
            Method registerMethod = afterInitEvent.getClass().getMethod("register", Object.class);
            registerMethod.invoke(afterInitEvent, proxy);

            LOGGER.debug("Registered via Fabric ScreenEvents.AFTER_INIT");
            return true;

        } catch (ClassNotFoundException e) {
            LOGGER.debug("Fabric API ScreenEvents not available (Fabric API may not be installed)");
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to register Fabric screen events: {}", e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // NeoForge: NeoForge.EVENT_BUS + ScreenEvent.Init.Post
    // =====================================================================

    /**
     * Register via NeoForge's event bus.
     *
     * Uses: NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false,
     *           ScreenEvent.Init.Post.class, consumer)
     */
    private static boolean tryNeoForgeEvents() {
        try {
            // Get NeoForge.EVENT_BUS
            Class<?> neoForgeClass = Class.forName("net.neoforged.neoforge.common.NeoForge");
            Object eventBus = neoForgeClass.getField("EVENT_BUS").get(null);

            // Get ScreenEvent.Init.Post class
            Class<?> screenEventClass = Class.forName(
                "net.neoforged.neoforge.client.event.ScreenEvent$Init$Post");

            // Get EventPriority.NORMAL
            Class<?> priorityClass = Class.forName("net.neoforged.bus.api.EventPriority");
            Object normalPriority = priorityClass.getField("NORMAL").get(null);

            // Create a Consumer proxy
            @SuppressWarnings("unchecked")
            Consumer<Object> consumer = event -> handleForgeScreenEvent(event);

            // Call: eventBus.addListener(NORMAL, false, screenEventClass, consumer)
            Method addListener = findAddListenerMethod(eventBus.getClass(), priorityClass);
            if (addListener != null) {
                addListener.invoke(eventBus, normalPriority, false, screenEventClass, consumer);
            } else {
                // Fallback: try simpler addListener(Class, Consumer) overload
                Method simpleAdd = eventBus.getClass().getMethod("addListener", Class.class, Consumer.class);
                simpleAdd.invoke(eventBus, screenEventClass, consumer);
            }

            LOGGER.debug("Registered via NeoForge EVENT_BUS");
            return true;

        } catch (ClassNotFoundException e) {
            LOGGER.debug("NeoForge event bus not available");
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to register NeoForge screen events: {}", e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Forge: MinecraftForge.EVENT_BUS + ScreenEvent.Init.Post
    // =====================================================================

    /**
     * Register via Forge's event bus (legacy Forge, pre-NeoForge).
     */
    private static boolean tryForgeEvents() {
        try {
            // Get MinecraftForge.EVENT_BUS
            Class<?> forgeClass = Class.forName("net.minecraftforge.common.MinecraftForge");
            Object eventBus = forgeClass.getField("EVENT_BUS").get(null);

            // Try different event class names (changed across Forge versions)
            Class<?> screenEventClass = null;
            String[] eventClassNames = {
                "net.minecraftforge.client.event.ScreenEvent$Init$Post",
                "net.minecraftforge.client.event.ScreenEvent$InitScreenEvent$Post"
            };
            for (String name : eventClassNames) {
                try {
                    screenEventClass = Class.forName(name);
                    break;
                } catch (ClassNotFoundException ignored) {}
            }

            if (screenEventClass == null) {
                LOGGER.debug("Forge ScreenEvent class not found");
                return false;
            }

            // Get EventPriority.NORMAL
            Class<?> priorityClass = Class.forName("net.minecraftforge.eventbus.api.EventPriority");
            Object normalPriority = priorityClass.getField("NORMAL").get(null);

            // Create a Consumer proxy
            @SuppressWarnings("unchecked")
            Consumer<Object> consumer = event -> handleForgeScreenEvent(event);

            // Call: eventBus.addListener(NORMAL, false, screenEventClass, consumer)
            Method addListener = findAddListenerMethod(eventBus.getClass(), priorityClass);
            if (addListener != null) {
                addListener.invoke(eventBus, normalPriority, false, screenEventClass, consumer);
            } else {
                Method simpleAdd = eventBus.getClass().getMethod("addListener", Class.class, Consumer.class);
                simpleAdd.invoke(eventBus, screenEventClass, consumer);
            }

            LOGGER.debug("Registered via Forge EVENT_BUS");
            return true;

        } catch (ClassNotFoundException e) {
            LOGGER.debug("Forge event bus not available");
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to register Forge screen events: {}", e.getMessage());
            return false;
        }
    }

    // =====================================================================
    // Shared Event Handler (NeoForge / Forge)
    // =====================================================================

    /**
     * Handle a Forge/NeoForge ScreenEvent.Init.Post event.
     * Both loaders use the same event structure: event.getScreen() returns the Screen.
     */
    private static void handleForgeScreenEvent(Object event) {
        try {
            Method getScreen = event.getClass().getMethod("getScreen");
            Object screen = getScreen.invoke(event);

            if (titleScreenClass.isInstance(screen)) {
                addRetroModButton(screen);
            }
        } catch (Exception e) {
            LOGGER.debug("Error handling screen event: {}", e.getMessage());
        }
    }

    // =====================================================================
    // Button Injection (shared across all loaders)
    // =====================================================================

    /**
     * Add the RetroMod button to the title screen.
     *
     * Uses McReflect to resolve MC class/method names across all loaders.
     * The button opens the RetroMod mod manager (file picker + transformation).
     */
    private static void addRetroModButton(Object screen) {
        try {
            // Get screen dimensions
            int width = McReflect.getIntField(screen, screenClass, 854, "width");
            int height = McReflect.getIntField(screen, screenClass, 480, "height");

            // Position: above the language selector (bottom-left)
            int buttonX = width / 2 - 124;
            int buttonY = height - 52;

            // Create button text: Text.literal("RetroMod") / Component.literal("RetroMod")
            Method literalMethod = McReflect.findMethod(textClass,
                new Class[]{String.class}, "literal");
            if (literalMethod == null) {
                LOGGER.debug("Could not find Text.literal / Component.literal method");
                return;
            }
            Object buttonText = literalMethod.invoke(null, "RetroMod");

            // Create PressAction / OnPress callback via Proxy
            // yarn: ButtonWidget$PressAction, mojang: Button$OnPress
            Class<?> pressActionClass = findPressActionClass();
            if (pressActionClass == null) {
                LOGGER.debug("Could not find PressAction / OnPress class");
                return;
            }

            Object pressAction = Proxy.newProxyInstance(
                pressActionClass.getClassLoader(),
                new Class<?>[]{pressActionClass},
                (proxy, method, args) -> {
                    // Filter: only respond to the actual press method, not Object methods
                    if (method.getDeclaringClass() != Object.class) {
                        openRetroModManager(screen);
                    }
                    return null;
                }
            );

            // Build the button: ButtonWidget.builder(text, action).dimensions(x,y,w,h).build()
            // yarn: ButtonWidget.builder, mojang: Button.builder
            Method builderMethod = McReflect.findMethod(buttonWidgetClass,
                new Class[]{textClass, pressActionClass}, "builder");
            if (builderMethod == null) {
                LOGGER.debug("Could not find ButtonWidget.builder / Button.builder");
                return;
            }
            Object builder = builderMethod.invoke(null, buttonText, pressAction);

            // .dimensions(x, y, width, height) / .bounds(x, y, width, height)
            Method dimensionsMethod = McReflect.findMethod(builder.getClass(),
                new Class[]{int.class, int.class, int.class, int.class},
                "dimensions", "bounds", "pos");
            if (dimensionsMethod != null) {
                builder = dimensionsMethod.invoke(builder, buttonX, buttonY, 20, 20);
            }

            // .build()
            Method buildMethod = McReflect.findMethod(builder.getClass(), "build");
            if (buildMethod == null) {
                LOGGER.debug("Could not find builder.build() method");
                return;
            }
            Object button = buildMethod.invoke(builder);

            // Add button to screen:
            // yarn: screen.addDrawableChild(element)
            // mojang: screen.addRenderableWidget(widget)
            Method addMethod = McReflect.findMethod(screenClass,
                "addDrawableChild", "addRenderableWidget", "addWidget");
            if (addMethod != null) {
                addMethod.setAccessible(true);
                addMethod.invoke(screen, button);
            }

            LOGGER.debug("RetroMod button added to title screen");

        } catch (Exception e) {
            LOGGER.warn("Could not add RetroMod button to title screen: {}", e.getMessage());
            LOGGER.debug("Detailed error:", e);
        }
    }

    /**
     * Open the RetroMod mod manager when the button is clicked.
     */
    private static void openRetroModManager(Object screen) {
        try {
            // Get MinecraftClient/Minecraft instance from the screen
            Object client = McReflect.getField(screen, screenClass, "client", "minecraft");
            if (client == null) {
                // Fallback: try static getInstance()
                Class<?> mcClass = McReflect.findClass(
                    "net.minecraft.client.MinecraftClient",  // yarn
                    "net.minecraft.client.Minecraft"         // mojang
                );
                if (mcClass != null) {
                    Method getInstance = McReflect.findMethod(mcClass, "getInstance");
                    if (getInstance != null) {
                        client = getInstance.invoke(null);
                    }
                }
            }

            if (client == null) {
                LOGGER.error("Could not get Minecraft client instance");
                return;
            }

            // Create and open RetroModScreen
            RetroModScreen retroScreen = new RetroModScreen(client, screen);
            retroScreen.open();

        } catch (Exception e) {
            LOGGER.error("Could not open RetroMod manager", e);
        }
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    /**
     * Find the PressAction / OnPress inner class of ButtonWidget / Button.
     */
    private static Class<?> findPressActionClass() {
        // Try yarn name
        Class<?> c = McReflect.findClass(
            "net.minecraft.client.gui.widget.ButtonWidget$PressAction",  // yarn
            "net.minecraft.client.gui.components.Button$OnPress"         // mojang
        );
        if (c != null) return c;

        // Fallback: search inner classes of ButtonWidget
        if (buttonWidgetClass != null) {
            for (Class<?> inner : buttonWidgetClass.getDeclaredClasses()) {
                if (inner.isInterface() && inner.getDeclaredMethods().length == 1) {
                    // Single abstract method interface — likely the press action
                    return inner;
                }
            }
        }
        return null;
    }

    /**
     * Find the addListener method with the full signature:
     * addListener(EventPriority, boolean, Class, Consumer)
     */
    private static Method findAddListenerMethod(Class<?> busClass, Class<?> priorityClass) {
        for (Method m : busClass.getMethods()) {
            if ("addListener".equals(m.getName())) {
                Class<?>[] params = m.getParameterTypes();
                if (params.length == 4
                    && params[0] == priorityClass
                    && params[1] == boolean.class
                    && params[2] == Class.class
                    && Consumer.class.isAssignableFrom(params[3])) {
                    return m;
                }
            }
        }
        return null;
    }
}
