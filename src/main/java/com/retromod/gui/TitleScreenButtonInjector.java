/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
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
            //
            // IMPORTANT: on Fabric Loader 0.18.4 + Java 25, resolving this
            // method via afterInitEvent.getClass() lands on the CONCRETE class
            // ArrayBackedEvent, which lives in the non-exported impl package
            // net.fabricmc.fabric.impl.base.event. Java 25's strict module
            // access blocks reflective invocation on members of that class
            // even though the method itself is public — setAccessible can't
            // override module boundaries. We saw this as:
            //   "class ... cannot access a member of class
            //    net.fabricmc.fabric.impl.base.event.ArrayBackedEvent
            //    with modifiers 'public'"
            //
            // Fix: resolve `register` on the EXPORTED API superclass
            // net.fabricmc.fabric.api.event.Event instead. Reflection then
            // binds the Method to the public-module declaring class, and
            // invoke() dispatches virtually to ArrayBackedEvent's impl at
            // runtime — exactly how normal Java calls work. No module-boundary
            // violation because we never reference the impl class directly.
            Class<?> eventApiClass;
            try {
                eventApiClass = Class.forName("net.fabricmc.fabric.api.event.Event");
            } catch (ClassNotFoundException notFound) {
                // Older Fabric API? Fall back to the concrete-class search.
                eventApiClass = afterInitEvent.getClass();
            }
            Method registerMethod = null;
            try {
                // Erasure: Event<T>.register(T) -> register(Object) in bytecode.
                registerMethod = eventApiClass.getMethod("register", Object.class);
            } catch (NoSuchMethodException noSuch) {
                // Very old Fabric API? Walk methods by name/arity as a last resort.
                for (Method m : eventApiClass.getMethods()) {
                    if ("register".equals(m.getName()) && m.getParameterCount() == 1) {
                        registerMethod = m;
                        break;
                    }
                }
            }
            if (registerMethod == null) {
                throw new NoSuchMethodException(
                        "register(Object) not found on Event API class " + eventApiClass.getName());
            }
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

            // Position: top-right corner, 80x20 — out of the way of vanilla
            // title-screen buttons and language selector. Two-pixel margin so
            // it doesn't touch the screen edge.
            int buttonX = width - 82;
            int buttonY = 2;

            // Press-action proxy: opens the RetroMod settings/file-picker
            // screen. We use ConfigScreenFactory because it stays on the
            // render thread (no AWT FileDialog → no platform issues), and it
            // includes a button to open the input folder for adding mods.
            Class<?> pressActionClass = findPressActionClass();
            if (pressActionClass == null) {
                LOGGER.debug("Could not find PressAction / OnPress class");
                return;
            }
            Object pressAction = Proxy.newProxyInstance(
                pressActionClass.getClassLoader(),
                new Class<?>[]{pressActionClass},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() != Object.class) {
                        // INFO so it shows in default logs — easy to confirm
                        // the click actually fired. If this line is missing
                        // when the user clicks, the click never reached us.
                        LOGGER.info("[RetroMod] Title-screen button clicked");
                        try {
                            MainScreenFactory.open(screen);
                            LOGGER.info("[RetroMod] MainScreenFactory.open() returned");
                        } catch (Throwable t) {
                            LOGGER.warn("[RetroMod] Failed to open RetroMod screen: {}",
                                    t.getMessage(), t);
                        }
                    }
                    return null;
                }
            );

            // Plain ButtonWidget with a wide text label. This is the most
            // reliable path: SpriteIconButton requires the GUI sprite atlas
            // to register our texture, which can fail silently and leave
            // an invisible/non-clickable button. Text button always works.
            Object button = buildPlainTextButton(buttonX, buttonY, pressAction, pressActionClass);
            if (button == null) {
                LOGGER.warn("[RetroMod] Could not build title-screen button (button==null)");
                return;
            }

            // Add button to screen:
            // yarn: screen.addDrawableChild(element) / mojang: screen.addRenderableWidget(widget)
            // We try addRenderableWidget FIRST because that's the one that
            // also wires up event handling (clicks). addDrawableChild on
            // some versions only wires rendering, not events.
            Method addMethod = McReflect.findMethod(screenClass,
                "addRenderableWidget", "addDrawableChild", "addWidget");
            if (addMethod != null) {
                addMethod.setAccessible(true);
                addMethod.invoke(screen, button);
                LOGGER.info("[RetroMod] Title-screen button registered via {} at ({},{})",
                        addMethod.getName(), buttonX, buttonY);
            } else {
                LOGGER.warn("[RetroMod] Could not find any add-widget method on Screen");
            }

        } catch (Exception e) {
            LOGGER.warn("[RetroMod] Could not add title-screen button: {}", e.getMessage(), e);
        }
    }

    /**
     * Build a wide ButtonWidget with the text "RetroMod". This is the
     * known-working fallback — uses the same Button.builder() API that
     * vanilla MC uses for its own buttons, so click events are wired
     * correctly by the framework.
     */
    private static Object buildPlainTextButton(int x, int y, Object pressAction,
                                                 Class<?> pressActionClass) {
        try {
            Method literal = McReflect.findMethod(textClass,
                new Class[]{String.class}, "literal");
            if (literal == null) {
                LOGGER.warn("[RetroMod] Component.literal() not found");
                return null;
            }
            Object text = literal.invoke(null, "RetroMod");

            Method builder = McReflect.findMethod(buttonWidgetClass,
                new Class[]{textClass, pressActionClass}, "builder");
            if (builder == null) {
                LOGGER.warn("[RetroMod] Button.builder() not found");
                return null;
            }
            Object b = builder.invoke(null, text, pressAction);

            // Width 80 fits "RetroMod" comfortably; height 20 is standard.
            Method dim = McReflect.findMethod(b.getClass(),
                new Class[]{int.class, int.class, int.class, int.class},
                "dimensions", "bounds", "pos");
            if (dim != null) b = dim.invoke(b, x, y, 80, 20);

            Method build = McReflect.findMethod(b.getClass(), "build");
            return build != null ? build.invoke(b) : null;
        } catch (Exception e) {
            LOGGER.warn("[RetroMod] buildPlainTextButton failed: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Build a 20x20 logo button. Tries SpriteIconButton (icon-only API) first;
     * if that fails or the API doesn't exist on this MC version, falls back
     * to a regular small text button labeled "R".
     */
    private static Object buildLogoButton(int x, int y, Object pressAction,
                                           Class<?> pressActionClass) {
        // Attempt 1: SpriteIconButton.builder(message, onPress, narrate)
        //                          .width(20).sprite(spriteId, w, h).build()
        Object iconBtn = trySpriteIconButton(x, y, pressAction, pressActionClass);
        if (iconBtn != null) return iconBtn;

        // Fallback: regular ButtonWidget with a single character "R"
        try {
            Method literal = McReflect.findMethod(textClass,
                new Class[]{String.class}, "literal");
            if (literal == null) return null;
            Object text = literal.invoke(null, "R");

            Method builder = McReflect.findMethod(buttonWidgetClass,
                new Class[]{textClass, pressActionClass}, "builder");
            if (builder == null) return null;
            Object b = builder.invoke(null, text, pressAction);

            Method dim = McReflect.findMethod(b.getClass(),
                new Class[]{int.class, int.class, int.class, int.class},
                "dimensions", "bounds", "pos");
            if (dim != null) b = dim.invoke(b, x, y, 20, 20);

            Method build = McReflect.findMethod(b.getClass(), "build");
            return build != null ? build.invoke(b) : null;
        } catch (Exception e) {
            LOGGER.debug("Fallback text button failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Try to construct an icon-only button using MC's SpriteIconButton API.
     * Returns null if the API isn't available — caller falls back to a text button.
     */
    private static Object trySpriteIconButton(int x, int y, Object pressAction,
                                                Class<?> pressActionClass) {
        try {
            Class<?> sib = McReflect.findClass(
                "net.minecraft.client.gui.widget.SpriteIconButton",
                "net.minecraft.client.gui.components.SpriteIconButton"
            );
            if (sib == null) return null;

            Class<?> identifierClass = McReflect.findClass(
                "net.minecraft.util.Identifier",
                "net.minecraft.resources.ResourceLocation"
            );
            if (identifierClass == null) return null;

            // Build a ResourceLocation/Identifier "retromod:retromod_logo"
            // Newer MC: ResourceLocation.fromNamespaceAndPath(ns, path)
            // Older MC: new ResourceLocation(ns, path)
            Object spriteId = null;
            try {
                Method fromNs = identifierClass.getMethod("fromNamespaceAndPath",
                    String.class, String.class);
                spriteId = fromNs.invoke(null, "retromod", "retromod_logo");
            } catch (NoSuchMethodException ignored) {
                try {
                    spriteId = identifierClass.getConstructor(String.class, String.class)
                        .newInstance("retromod", "retromod_logo");
                } catch (Exception e) {
                    return null;
                }
            }
            if (spriteId == null) return null;

            // SpriteIconButton.builder(message, onPress, narrate) → builder
            //   .width(20).sprite(id, 20, 20).build()
            Method literal = McReflect.findMethod(textClass,
                new Class[]{String.class}, "literal");
            if (literal == null) return null;
            Object message = literal.invoke(null, "RetroMod");

            Method builderMethod = McReflect.findMethod(sib,
                new Class[]{textClass, pressActionClass, boolean.class}, "builder");
            if (builderMethod == null) {
                builderMethod = McReflect.findMethod(sib,
                    new Class[]{textClass, pressActionClass}, "builder");
                if (builderMethod == null) return null;
            }
            Object builder = builderMethod.getParameterCount() == 3
                ? builderMethod.invoke(null, message, pressAction, true)
                : builderMethod.invoke(null, message, pressAction);

            // .width(20)
            Method widthMethod = McReflect.findMethod(builder.getClass(),
                new Class[]{int.class}, "width");
            if (widthMethod != null) builder = widthMethod.invoke(builder, 20);

            // .sprite(id, 20, 20)
            Method spriteMethod = McReflect.findMethod(builder.getClass(),
                new Class[]{identifierClass, int.class, int.class}, "sprite");
            if (spriteMethod != null) {
                builder = spriteMethod.invoke(builder, spriteId, 20, 20);
            }

            // .build()
            Method build = McReflect.findMethod(builder.getClass(), "build");
            Object btn = build != null ? build.invoke(builder) : null;
            if (btn == null) return null;

            // Position the button (SpriteIconButton.builder doesn't set position)
            Method setX = McReflect.findMethod(btn.getClass(), new Class[]{int.class}, "setX");
            Method setY = McReflect.findMethod(btn.getClass(), new Class[]{int.class}, "setY");
            if (setX != null) setX.invoke(btn, x);
            if (setY != null) setY.invoke(btn, y);

            LOGGER.debug("Built SpriteIconButton with retromod_logo");
            return btn;
        } catch (Exception e) {
            LOGGER.debug("SpriteIconButton not available: {}", e.getMessage());
            return null;
        }
    }

    // (Old helper kept around but unused — settings now reached via the
    // RetroMod logo button itself.)
    @SuppressWarnings("unused")
    private static void addSettingsButton(Object screen, int x, int y,
            Class<?> pressActionClass, Method literalMethod,
            Method builderMethod, Method addMethod) {
        try {
            Object settingsText = literalMethod.invoke(null, "\u2699");

            Object settingsAction = Proxy.newProxyInstance(
                pressActionClass.getClassLoader(),
                new Class<?>[]{pressActionClass},
                (proxy, method, args) -> {
                    if (method.getDeclaringClass() != Object.class) {
                        ConfigScreenFactory.open(screen);
                    }
                    return null;
                }
            );

            Object builder = builderMethod.invoke(null, settingsText, settingsAction);

            Method dimMethod = McReflect.findMethod(builder.getClass(),
                new Class[]{int.class, int.class, int.class, int.class},
                "dimensions", "bounds", "pos");
            if (dimMethod != null) {
                builder = dimMethod.invoke(builder, x, y, 20, 20);
            }

            Method buildMethod = McReflect.findMethod(builder.getClass(), "build");
            if (buildMethod != null) {
                Object button = buildMethod.invoke(builder);
                if (addMethod != null) {
                    addMethod.setAccessible(true);
                    addMethod.invoke(screen, button);
                }
            }

            LOGGER.debug("RetroMod settings button added to title screen");
        } catch (Exception e) {
            LOGGER.debug("Could not add settings button: {}", e.getMessage());
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
