/*
 * Retromod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.gui;

import com.retromod.util.McReflect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.function.Consumer;

/**
 * Creates in-game Minecraft screens via reflection.
 *
 * Since Retromod is built with Maven (no Minecraft on classpath), all MC
 * screen classes are accessed via reflection. This factory provides methods
 * to show various dialog types using MC's built-in screen system:
 *
 *   - ConfirmScreen (yes/no dialogs)
 *   - Custom message screens (info/warning/results)
 *   - Progress overlay
 *
 * All screens are rendered inside the game window, not as Swing popups.
 */
public final class InGameScreenFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger("Retromod-GUI");

    // Cached MC classes (resolved lazily)
    private static Class<?> screenClass;
    private static Class<?> textClass;
    private static Class<?> confirmScreenClass;
    private static Class<?> minecraftClientClass;
    private static Class<?> buttonWidgetClass;
    private static boolean resolved = false;

    private InGameScreenFactory() {}

    /**
     * Resolve all needed MC classes via reflection.
     */
    private static synchronized boolean resolveClasses() {
        if (resolved) return screenClass != null;

        resolved = true;

        screenClass = McReflect.findClass(
            "net.minecraft.client.gui.screen.Screen",
            "net.minecraft.client.gui.screens.Screen"
        );

        textClass = McReflect.findClass(
            "net.minecraft.text.Text",
            "net.minecraft.network.chat.Component"
        );

        // The plain Yes/No ConfirmScreen — NOT ConfirmLinkScreen (that's the
        // link-warning dialog with a different constructor). Its ctor is
        // ConfirmScreen(BooleanConsumer, Component title, Component message).
        confirmScreenClass = McReflect.findClass(
            "net.minecraft.client.gui.screen.ConfirmScreen",    // yarn
            "net.minecraft.client.gui.screens.ConfirmScreen"    // mojang
        );

        minecraftClientClass = McReflect.findClass(
            "net.minecraft.client.MinecraftClient",
            "net.minecraft.client.Minecraft"
        );

        buttonWidgetClass = McReflect.findClass(
            "net.minecraft.client.gui.widget.ButtonWidget",
            "net.minecraft.client.gui.components.Button"
        );

        if (screenClass == null || textClass == null || minecraftClientClass == null) {
            LOGGER.warn("Could not resolve MC screen classes for in-game GUI");
            return false;
        }

        return true;
    }

    /**
     * Get the MinecraftClient instance via reflection.
     */
    private static Object getClientInstance() {
        try {
            Method getInstance = McReflect.findMethod(minecraftClientClass, "getInstance");
            return getInstance != null ? getInstance.invoke(null) : null;
        } catch (Exception e) {
            LOGGER.debug("Could not get MC client instance: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Create a Text/Component object from a string.
     */
    private static Object createText(String text) {
        try {
            // Try Text.literal() (1.19.2+) / Component.literal()
            Method literal = McReflect.findMethod(textClass,
                new Class[]{String.class}, "literal");
            if (literal != null) {
                return literal.invoke(null, text);
            }

            // Fallback: try Text.of() / Component.translatable()
            Method of = McReflect.findMethod(textClass,
                new Class[]{String.class}, "of");
            if (of != null) {
                return of.invoke(null, text);
            }

            // Last resort: LiteralText constructor (pre-1.19.2)
            Class<?> literalTextClass = McReflect.findClass(
                "net.minecraft.text.LiteralText",
                "net.minecraft.network.chat.TextComponent"
            );
            if (literalTextClass != null) {
                return literalTextClass.getConstructor(String.class).newInstance(text);
            }
        } catch (Exception e) {
            LOGGER.debug("Could not create Text object: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Set the current screen on MinecraftClient.
     */
    private static void setScreen(Object screen) {
        try {
            Object client = getClientInstance();
            if (client == null) return;

            Method setScreen = McReflect.findMethod(minecraftClientClass,
                new Class[]{screenClass}, "setScreen");
            if (setScreen != null) {
                setScreen.invoke(client, screen);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not set MC screen: {}", e.getMessage());
        }
    }

    // =========================================================================
    // Public API: Show various screen types
    // =========================================================================

    /**
     * Show a confirmation dialog in-game with Yes/No buttons.
     *
     * @param title   the dialog title
     * @param message the dialog message
     * @param onYes   callback when Yes is clicked
     * @param onNo    callback when No is clicked (or null to just close)
     */
    public static void showConfirmScreen(String title, String message,
                                          Runnable onYes, Runnable onNo) {
        if (!resolveClasses()) {
            LOGGER.warn("Cannot show in-game confirm screen (classes not available)");
            return;
        }

        try {
            Object titleText = createText(title);
            Object messageText = createText(message);

            if (titleText == null || messageText == null) return;

            // ConfirmScreen takes a BooleanConsumer callback + title + message
            // BooleanConsumer is it.unimi.dsi.fastutil.booleans.BooleanConsumer
            // or a simple functional interface depending on MC version

            // Try: new ConfirmScreen(callback, title, message)
            // Where callback is (boolean confirmed) -> ...

            // Find the BooleanConsumer class
            Class<?> boolConsumerClass = null;
            try {
                boolConsumerClass = Class.forName("it.unimi.dsi.fastutil.booleans.BooleanConsumer");
            } catch (ClassNotFoundException e) {
                // Try vanilla functional interface
                try {
                    boolConsumerClass = Class.forName("net.minecraft.client.gui.screen.ConfirmScreen$1");
                } catch (ClassNotFoundException e2) {
                    // ignored
                }
            }

            if (boolConsumerClass != null) {
                Object callback = Proxy.newProxyInstance(
                    boolConsumerClass.getClassLoader(),
                    new Class<?>[]{boolConsumerClass},
                    (proxy, method, args) -> {
                        if (method.getParameterCount() == 1) {
                            boolean confirmed = (Boolean) args[0];
                            if (confirmed && onYes != null) {
                                onYes.run();
                            } else if (!confirmed && onNo != null) {
                                onNo.run();
                            }
                            // Close the screen
                            setScreen(null);
                        }
                        return null;
                    }
                );

                Constructor<?> ctor = confirmScreenClass.getConstructor(
                    boolConsumerClass, textClass, textClass);
                Object screen = ctor.newInstance(callback, titleText, messageText);
                setScreen(screen);
                return;
            }

            // Fallback: just use a simple notification
            showNotification(title + "\n" + message);

        } catch (Exception e) {
            LOGGER.warn("Failed to show confirm screen: {}", e.getMessage());
        }
    }

    /**
     * Show a Yes/No confirmation over a known parent screen. Unlike
     * {@link #showConfirmScreen}, declining returns to {@code parentScreen}
     * (e.g. the title screen) instead of clearing to a null screen — so this is
     * safe to invoke from a title-screen init hook. Used by the restart prompt (#33).
     *
     * @param parentScreen the screen to return to if the user clicks No
     * @param title        dialog title
     * @param message      dialog body (supports newlines)
     * @param onYes        run when the user clicks Yes
     */
    public static void showRestartConfirm(Object parentScreen, String title,
                                          String message, Runnable onYes) {
        if (!resolveClasses() || confirmScreenClass == null) {
            LOGGER.warn("Cannot show restart confirm (screen classes unavailable)");
            return;
        }
        try {
            Object titleText = createText(title);
            Object messageText = createText(message);
            if (titleText == null || messageText == null) return;

            Class<?> boolConsumerClass;
            try {
                boolConsumerClass = Class.forName("it.unimi.dsi.fastutil.booleans.BooleanConsumer");
            } catch (ClassNotFoundException e) {
                LOGGER.debug("BooleanConsumer unavailable; cannot build ConfirmScreen");
                return;
            }

            Object callback = Proxy.newProxyInstance(
                boolConsumerClass.getClassLoader(),
                new Class<?>[]{boolConsumerClass},
                (proxy, method, args) -> {
                    if (method.getParameterCount() == 1 && args != null) {
                        boolean confirmed = (Boolean) args[0];
                        if (confirmed) {
                            if (onYes != null) onYes.run(); // stops the client; screen irrelevant
                        } else {
                            // Decline → restore the title screen, never a null screen.
                            setScreen(parentScreen);
                        }
                    }
                    return null;
                }
            );

            Constructor<?> ctor = confirmScreenClass.getConstructor(
                boolConsumerClass, textClass, textClass);
            Object screen = ctor.newInstance(callback, titleText, messageText);
            setScreen(screen);
        } catch (Exception e) {
            LOGGER.warn("Failed to show restart confirm: {}", e.getMessage());
        }
    }

    /**
     * Show a results/info screen in-game.
     * Uses MC's ConfirmScreen with only an "OK" button.
     *
     * @param title   the screen title
     * @param message the message body (supports newlines)
     * @param onClose callback when closed (or null)
     */
    public static void showResultScreen(String title, String message, Runnable onClose) {
        showConfirmScreen(title, message, onClose != null ? onClose : () -> {}, onClose);
    }

    /**
     * Show a simple text notification by setting a screen with a message and "Done" button.
     * Fallback when ConfirmScreen construction fails.
     */
    public static void showNotification(String message) {
        if (!resolveClasses()) return;

        try {
            // Try using NoticeScreen if available (some MC versions)
            Class<?> noticeScreenClass = McReflect.findClass(
                "net.minecraft.client.gui.screen.NoticeScreen",
                "net.minecraft.client.gui.screens.NoticeScreen"
            );

            if (noticeScreenClass != null) {
                Object titleText = createText("Retromod");
                Object messageText = createText(message);
                Object buttonText = createText("Done");

                // NoticeScreen(Runnable onClose, Text title, Text message, Text buttonText, boolean showTimer)
                try {
                    Constructor<?> ctor = noticeScreenClass.getConstructor(
                        Runnable.class, textClass, textClass, textClass, boolean.class);
                    Object screen = ctor.newInstance(
                        (Runnable) () -> setScreen(null),
                        titleText, messageText, buttonText, false);
                    setScreen(screen);
                    return;
                } catch (NoSuchMethodException e) {
                    // Try simpler constructor
                    Constructor<?> ctor = noticeScreenClass.getConstructor(
                        Runnable.class, textClass, textClass);
                    Object screen = ctor.newInstance(
                        (Runnable) () -> setScreen(null),
                        titleText, messageText);
                    setScreen(screen);
                    return;
                }
            }
        } catch (Exception e) {
            LOGGER.debug("NoticeScreen not available: {}", e.getMessage());
        }

        // Last resort — log the message
        LOGGER.info("[Retromod Notification] {}", message);
    }

    /**
     * Show transformation results in-game.
     *
     * @param results list of result strings (e.g., "✓ modname.jar — transformed", "✗ other.jar — failed")
     * @param needsRestart whether to show a "Restart Required" message
     */
    public static void showTransformResults(List<String> results, boolean needsRestart) {
        StringBuilder msg = new StringBuilder();

        for (String result : results) {
            msg.append(result).append("\n");
        }

        if (needsRestart) {
            msg.append("\nPlease RESTART Minecraft for changes to take effect.");
        }

        showResultScreen("Retromod — Transformation Results", msg.toString(), () -> {
            if (needsRestart) {
                // Optionally: trigger a restart by calling mc.scheduleStop()
                try {
                    Object client = getClientInstance();
                    if (client != null) {
                        Method scheduleStop = McReflect.findMethod(minecraftClientClass,
                            "scheduleStop");
                        if (scheduleStop != null) {
                            scheduleStop.invoke(client);
                        }
                    }
                } catch (Exception e) {
                    LOGGER.debug("Could not schedule stop: {}", e.getMessage());
                }
            }
        });
    }

    /**
     * Show a crash/error screen in-game.
     *
     * @param modName   the mod that caused the crash
     * @param errorMsg  the error message
     * @param onSaveQuit callback for "Save & Quit" action
     */
    public static void showCrashScreen(String modName, String errorMsg, Runnable onSaveQuit) {
        String title = "Retromod — Mod Error Detected";
        String message = "A transformed mod caused an error:\n\n"
            + "Mod: " + modName + "\n"
            + "Error: " + errorMsg + "\n\n"
            + "Your world has been saved automatically.\n"
            + "Click 'Save & Quit' to exit safely.";

        showConfirmScreen(title, message, onSaveQuit, onSaveQuit);
    }
}
