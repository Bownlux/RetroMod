/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Bridge for Font.draw/drawShadow methods removed in MC 26.1.
 *
 * Old mods call:
 *   Font.draw(PoseStack, String, float, float, int) -> int
 *   Font.draw(PoseStack, Component, float, float, int) -> int
 *   Font.drawShadow(PoseStack, String, float, float, int) -> int
 *   Font.drawShadow(PoseStack, FormattedCharSequence, float, float, int) -> int
 *
 * In 26.1, these are replaced by:
 *   Font.drawInBatch(String, float, float, int, boolean, Matrix4f,
 *                    MultiBufferSource, DisplayMode, int, int) -> int
 *
 * This bridge devirtualizes the old instance methods into static methods,
 * extracts the Matrix4f from PoseStack, obtains a MultiBufferSource from
 * the Minecraft client, and delegates to drawInBatch.
 */
package com.retromod.shim.fabric.embedded;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Bridge for Font text rendering methods removed in MC 26.1.
 *
 * ~70% of mods render text using the old Font.draw/drawShadow methods.
 * This bridge translates those calls to the new drawInBatch API.
 *
 * All methods are static (devirtualized): the Font instance is the first parameter.
 * PoseStack is accepted as Object and the Matrix4f is extracted via reflection.
 * Returns 0 as the int width (old mods rarely use the return value).
 */
public final class FontBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-FontBridge");

    // Cached reflection lookups (thread-safe via volatile)
    private static volatile boolean initialized = false;
    private static volatile boolean initFailed = false;

    // PoseStack → Matrix4f extraction (volatile for thread-safety in DCL pattern)
    private static volatile Method poseStackLast;       // PoseStack.last() -> Pose
    private static volatile Method posePose;            // Pose.pose() -> Matrix4f

    // Minecraft.getInstance().renderBuffers().bufferSource()
    private static volatile Method minecraftGetInstance;
    private static volatile Method renderBuffers;
    private static volatile Method bufferSource;
    private static volatile Method bufferSourceEndBatch; // to flush after drawing

    // Font.drawInBatch (the new 26.1 method)
    private static volatile Method drawInBatchString;   // String variant
    private static volatile Method drawInBatchComponent; // Component variant
    private static volatile Method drawInBatchFCSeq;    // FormattedCharSequence variant

    // Font.DisplayMode enum constants
    private static volatile Object displayModeNormal;
    private static Object displayModeSeeThrough;

    // Component.getString() for Component→String fallback
    private static Method componentGetString;

    // FormattedCharSequence handling — we convert to String via reflection
    private static boolean hasFCSeqMethod = false;

    private FontBridge() {
        // Utility class
    }

    private static void ensureInitialized() {
        if (initialized || initFailed) return;
        synchronized (FontBridge.class) {
            if (initialized || initFailed) return;
            try {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();

                // PoseStack.last().pose() chain
                Class<?> poseStackClass = cl.loadClass("com.mojang.blaze3d.vertex.PoseStack");
                poseStackLast = poseStackClass.getMethod("last");
                Class<?> poseClass = poseStackLast.getReturnType();
                posePose = poseClass.getMethod("pose");

                // Minecraft.getInstance()
                Class<?> minecraftClass = cl.loadClass("net.minecraft.client.Minecraft");
                minecraftGetInstance = minecraftClass.getMethod("getInstance");

                // renderBuffers().bufferSource()
                Object mc = minecraftGetInstance.invoke(null);
                renderBuffers = minecraftClass.getMethod("renderBuffers");
                Object rb = renderBuffers.invoke(mc);
                bufferSource = rb.getClass().getMethod("bufferSource");

                // Find endBatch() on the buffer source for flushing
                Object bs = bufferSource.invoke(rb);
                try {
                    bufferSourceEndBatch = bs.getClass().getMethod("endBatch");
                } catch (NoSuchMethodException e) {
                    // Try alternative name
                    try {
                        bufferSourceEndBatch = bs.getClass().getMethod("endLastBatch");
                    } catch (NoSuchMethodException e2) {
                        // Non-fatal: text will still render, just may not flush immediately
                    }
                }

                // Font class and drawInBatch method
                Class<?> fontClass = cl.loadClass("net.minecraft.client.gui.Font");
                Class<?> matrix4fClass = cl.loadClass("org.joml.Matrix4f");
                Class<?> multiBufferSourceClass = cl.loadClass(
                    "net.minecraft.client.renderer.MultiBufferSource");
                Class<?> displayModeClass = cl.loadClass(
                    "net.minecraft.client.gui.Font$DisplayMode");

                // Get DisplayMode enum constants
                Object[] displayModes = displayModeClass.getEnumConstants();
                for (Object dm : displayModes) {
                    String name = ((Enum<?>) dm).name();
                    if ("NORMAL".equals(name)) {
                        displayModeNormal = dm;
                    } else if ("SEE_THROUGH".equals(name)) {
                        displayModeSeeThrough = dm;
                    }
                }
                if (displayModeNormal == null && displayModes.length > 0) {
                    displayModeNormal = displayModes[0];
                }
                if (displayModeSeeThrough == null && displayModes.length > 1) {
                    displayModeSeeThrough = displayModes[displayModes.length > 2 ? 2 : 1];
                }

                // drawInBatch(String, float, float, int, boolean, Matrix4f,
                //             MultiBufferSource, DisplayMode, int, int) -> int
                drawInBatchString = fontClass.getMethod("drawInBatch",
                    String.class, float.class, float.class, int.class, boolean.class,
                    matrix4fClass, multiBufferSourceClass, displayModeClass,
                    int.class, int.class);

                // Try Component variant
                try {
                    Class<?> componentClass = cl.loadClass("net.minecraft.network.chat.Component");
                    drawInBatchComponent = fontClass.getMethod("drawInBatch",
                        componentClass, float.class, float.class, int.class, boolean.class,
                        matrix4fClass, multiBufferSourceClass, displayModeClass,
                        int.class, int.class);
                    componentGetString = componentClass.getMethod("getString");
                } catch (Exception e) {
                    LOGGER.debug("Font.drawInBatch(Component,...) not found, will use String fallback");
                }

                // Try FormattedCharSequence variant
                try {
                    Class<?> fcsClass = cl.loadClass(
                        "net.minecraft.util.FormattedCharSequence");
                    drawInBatchFCSeq = fontClass.getMethod("drawInBatch",
                        fcsClass, float.class, float.class, int.class, boolean.class,
                        matrix4fClass, multiBufferSourceClass, displayModeClass,
                        int.class, int.class);
                    hasFCSeqMethod = true;
                } catch (Exception e) {
                    LOGGER.debug("Font.drawInBatch(FormattedCharSequence,...) not found, will use fallback");
                }

                initialized = true;
                LOGGER.debug("FontBridge initialized successfully");

            } catch (Exception e) {
                initFailed = true;
                LOGGER.warn("FontBridge initialization failed: {}. " +
                    "Text rendering from old mods may not work.", e.getMessage());
            }
        }
    }

    // ================================================================
    // Font.draw(PoseStack, String, float, float, int) -> int
    // ================================================================

    /**
     * Bridge for Font.draw(PoseStack, String, float, float, int).
     * Devirtualized: Font is the first parameter.
     *
     * @param font      the Font instance
     * @param poseStack the PoseStack (Matrix4f extracted from it)
     * @param text      the text to draw
     * @param x         x position
     * @param y         y position
     * @param color     ARGB color
     * @return 0 (old return was text width; rarely used)
     */
    public static int drawString(Object font, Object poseStack, String text,
                                  float x, float y, int color) {
        return drawInternal(font, poseStack, text, null, null, x, y, color, false);
    }

    // ================================================================
    // Font.draw(PoseStack, Component, float, float, int) -> int
    // ================================================================

    /**
     * Bridge for Font.draw(PoseStack, Component, float, float, int).
     * Devirtualized: Font is the first parameter.
     */
    public static int drawComponent(Object font, Object poseStack, Object component,
                                     float x, float y, int color) {
        return drawInternal(font, poseStack, null, component, null, x, y, color, false);
    }

    // ================================================================
    // Font.drawShadow(PoseStack, String, float, float, int) -> int
    // ================================================================

    /**
     * Bridge for Font.drawShadow(PoseStack, String, float, float, int).
     * Devirtualized: Font is the first parameter.
     */
    public static int drawShadowString(Object font, Object poseStack, String text,
                                        float x, float y, int color) {
        return drawInternal(font, poseStack, text, null, null, x, y, color, true);
    }

    // ================================================================
    // Font.drawShadow(PoseStack, Component, float, float, int) -> int
    // ================================================================

    /**
     * Bridge for Font.drawShadow(PoseStack, Component, float, float, int).
     * Devirtualized: Font is the first parameter.
     */
    public static int drawShadowComponent(Object font, Object poseStack, Object component,
                                           float x, float y, int color) {
        return drawInternal(font, poseStack, null, component, null, x, y, color, true);
    }

    // ================================================================
    // Font.drawShadow(PoseStack, FormattedCharSequence, float, float, int) -> int
    // ================================================================

    /**
     * Bridge for Font.drawShadow(PoseStack, FormattedCharSequence, float, float, int).
     * Devirtualized: Font is the first parameter.
     */
    public static int drawShadowFCS(Object font, Object poseStack, Object formattedCharSeq,
                                     float x, float y, int color) {
        return drawInternal(font, poseStack, null, null, formattedCharSeq, x, y, color, true);
    }

    // ================================================================
    // Internal implementation
    // ================================================================

    /**
     * Core implementation: delegates to Font.drawInBatch with appropriate parameters.
     *
     * @param font             Font instance
     * @param poseStack        PoseStack (Matrix4f extracted via last().pose())
     * @param text             String text (or null if component/FCS is used)
     * @param component        Component (or null)
     * @param formattedCharSeq FormattedCharSequence (or null)
     * @param x                x position
     * @param y                y position
     * @param color            ARGB color
     * @param shadow           true for drawShadow (uses SEE_THROUGH display mode)
     * @return 0
     */
    private static int drawInternal(Object font, Object poseStack,
                                     String text, Object component, Object formattedCharSeq,
                                     float x, float y, int color, boolean shadow) {
        ensureInitialized();
        if (initFailed) return 0;

        try {
            // Extract Matrix4f from PoseStack
            Object pose = poseStackLast.invoke(poseStack);
            Object matrix4f = posePose.invoke(pose);

            // Get MultiBufferSource from Minecraft client
            Object mc = minecraftGetInstance.invoke(null);
            Object rb = renderBuffers.invoke(mc);
            Object bs = bufferSource.invoke(rb);

            // Select display mode
            Object displayMode = shadow ? displayModeSeeThrough : displayModeNormal;
            if (displayMode == null) displayMode = displayModeNormal;

            // Light level: 15728880 = full brightness (LightTexture.FULL_BRIGHT)
            int packedLight = 15728880;
            // Background color: 0 = transparent
            int backgroundColor = 0;

            // Call drawInBatch
            if (text != null && drawInBatchString != null) {
                drawInBatchString.invoke(font, text, x, y, color, shadow,
                    matrix4f, bs, displayMode, backgroundColor, packedLight);
            } else if (component != null && drawInBatchComponent != null) {
                drawInBatchComponent.invoke(font, component, x, y, color, shadow,
                    matrix4f, bs, displayMode, backgroundColor, packedLight);
            } else if (component != null && componentGetString != null) {
                // Fallback: convert Component to String
                String str = (String) componentGetString.invoke(component);
                if (drawInBatchString != null) {
                    drawInBatchString.invoke(font, str, x, y, color, shadow,
                        matrix4f, bs, displayMode, backgroundColor, packedLight);
                }
            } else if (formattedCharSeq != null && drawInBatchFCSeq != null) {
                drawInBatchFCSeq.invoke(font, formattedCharSeq, x, y, color, shadow,
                    matrix4f, bs, displayMode, backgroundColor, packedLight);
            } else if (formattedCharSeq != null && drawInBatchString != null) {
                // Last resort: try toString on the FormattedCharSequence
                String str = formattedCharSeq.toString();
                drawInBatchString.invoke(font, str, x, y, color, shadow,
                    matrix4f, bs, displayMode, backgroundColor, packedLight);
            }

            // Flush the buffer so text actually appears
            if (bufferSourceEndBatch != null) {
                bufferSourceEndBatch.invoke(bs);
            }

        } catch (Exception e) {
            // Log once and continue — better to silently skip text than crash
            LOGGER.debug("FontBridge.drawInternal failed: {}", e.getMessage());
        }

        return 0;
    }

    // ================================================================
    // RenderSystem no-ops for removed methods
    // ================================================================

    /**
     * No-op replacement for RenderSystem.enableTexture().
     * Removed in 26.1 (textures are always enabled in modern rendering pipeline).
     */
    public static void noOpVoid() {
        // Intentionally empty
    }

    /**
     * No-op replacement for RenderSystem.setShaderTexture(int, ResourceLocation).
     * Removed in 26.1 (shader textures managed differently).
     */
    public static void noOpSetShaderTexture(int slot, Object resourceLocation) {
        // Intentionally empty
    }
}
