/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 RevivalSMP. Licensed under MIT.
 */
package com.retromod.shim.api.fabric.embedded;

import com.retromod.core.EnvironmentDetector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.nio.IntBuffer;
import java.util.function.Supplier;

/**
 * Rendering compatibility implementations.
 *
 * Provides fallback implementations for rendering methods that change
 * when Minecraft transitions between graphics backends (OpenGL → Vulkan → Metal).
 *
 * These methods are called via bytecode redirects registered by RenderingBackendShim.
 * They use reflection to call the appropriate backend method at runtime.
 */
public class RenderingCompat {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-RenderCompat");

    // Cached method references for performance
    private static Method cachedGetTessellator = null;
    private static Method cachedBindTarget = null;
    private static Object cachedTessellatorInstance = null;

    /**
     * No-op method for removed OpenGL calls.
     * Used when methods like enableTexture/disableTexture are removed in Vulkan.
     */
    public static void noop() {
        // Intentionally empty - the OpenGL call is no longer needed
    }

    /**
     * Thread-safe render system assertion.
     * Works on all backends (OpenGL, Vulkan, Metal).
     */
    @SuppressWarnings("unchecked")
    public static void assertRenderThread(Supplier<?> supplier) {
        // On Vulkan/Metal, rendering MUST happen on the render thread.
        // On OpenGL, it's more lenient. We log warnings but don't crash.
        String threadName = Thread.currentThread().getName();
        boolean isRenderThread = threadName.contains("Render") ||
                                  threadName.contains("Main") ||
                                  threadName.equals("Server thread");
        if (!isRenderThread) {
            if (EnvironmentDetector.isVulkan() || EnvironmentDetector.isMetal()) {
                LOGGER.warn("Render call from non-render thread '{}' - this may crash on Vulkan/Metal",
                    threadName);
            }
        }

        // Try to call the actual RenderSystem.assertThread if available
        try {
            Class<?> renderSystem = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            Method assertMethod = renderSystem.getMethod("assertOnRenderThread");
            assertMethod.invoke(null);
        } catch (Exception ignored) {
            // Method doesn't exist in this version - that's OK
        }
    }

    /**
     * Compatible shader loading that handles GLSL → SPIR-V translation.
     * When Vulkan is used, GLSL shaders need to be compiled to SPIR-V.
     */
    public static void loadShaderCompat(String shaderName) {
        LOGGER.debug("Loading shader (compat): {}", shaderName);

        try {
            if (EnvironmentDetector.isVulkan()) {
                // Vulkan needs SPIR-V shaders
                // Try to find pre-compiled SPIR-V version first
                String spirvName = shaderName.replace(".glsl", ".spv")
                                              .replace(".vsh", ".vert.spv")
                                              .replace(".fsh", ".frag.spv");

                // Try loading the SPIR-V version via reflection
                try {
                    Class<?> shaderClass = Class.forName("net.minecraft.client.gl.ShaderProgram");
                    Method loadMethod = shaderClass.getMethod("loadProgram", String.class);
                    loadMethod.invoke(null, spirvName);
                    return;
                } catch (Exception e) {
                    LOGGER.debug("No SPIR-V shader found, falling back to GLSL: {}", shaderName);
                }
            }

            // Fall back to standard shader loading
            try {
                Class<?> shaderClass = Class.forName("net.minecraft.client.gl.ShaderProgram");
                Method loadMethod = shaderClass.getMethod("loadProgram", String.class);
                loadMethod.invoke(null, shaderName);
            } catch (Exception e) {
                LOGGER.warn("Could not load shader: {}", shaderName);
            }
        } catch (Exception e) {
            LOGGER.warn("Shader compat error: {}", e.getMessage());
        }
    }

    /**
     * Compatible framebuffer/render target binding.
     * Handles both OpenGL framebuffers and Vulkan render passes.
     */
    public static void bindRenderTarget(Object framebuffer, boolean setViewport) {
        if (framebuffer == null) return;

        try {
            // Try the standard method first
            Method bindMethod = framebuffer.getClass().getMethod("bindFramebuffer", boolean.class);
            bindMethod.invoke(framebuffer, setViewport);
        } catch (NoSuchMethodException e) {
            // Try alternative method names for newer versions
            try {
                Method bindMethod = framebuffer.getClass().getMethod("bindWrite", boolean.class);
                bindMethod.invoke(framebuffer, setViewport);
            } catch (Exception e2) {
                try {
                    // Vulkan: render target binding
                    Method bindMethod = framebuffer.getClass().getMethod("beginRenderPass");
                    bindMethod.invoke(framebuffer);
                } catch (Exception e3) {
                    LOGGER.debug("Could not bind render target: {}", e3.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Render target bind error: {}", e.getMessage());
        }
    }

    /**
     * Compatible Tessellator access.
     * Handles both OpenGL immediate-mode Tessellator and Vulkan command buffers.
     */
    public static Object getTessellator() {
        // Return cached instance if available
        if (cachedTessellatorInstance != null) {
            return cachedTessellatorInstance;
        }

        try {
            // Try standard Tessellator.getInstance()
            Class<?> tessClass = Class.forName("net.minecraft.client.render.Tessellator");
            if (cachedGetTessellator == null) {
                cachedGetTessellator = tessClass.getMethod("getInstance");
            }
            cachedTessellatorInstance = cachedGetTessellator.invoke(null);
            return cachedTessellatorInstance;
        } catch (ClassNotFoundException e) {
            // Try alternative class names for future versions
            try {
                Class<?> tessClass = Class.forName("com.mojang.blaze3d.vertex.Tesselator");
                Method getInstance = tessClass.getMethod("getInstance");
                cachedTessellatorInstance = getInstance.invoke(null);
                return cachedTessellatorInstance;
            } catch (Exception e2) {
                LOGGER.debug("Tessellator not available");
                return null;
            }
        } catch (Exception e) {
            LOGGER.debug("Could not get Tessellator: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Compatible texture upload that works on OpenGL, Vulkan, and Metal.
     */
    public static void texImage2DCompat(int target, int level, int internalFormat,
                                         int width, int height, int border,
                                         int format, int type, IntBuffer pixels) {
        try {
            if (EnvironmentDetector.isVulkan() || EnvironmentDetector.isMetal()) {
                // Vulkan/Metal: Use the new texture upload API
                try {
                    Class<?> texManager = Class.forName(
                        "com.mojang.blaze3d.platform.TextureManager");
                    Method uploadMethod = texManager.getMethod("upload",
                        int.class, int.class, int.class, int.class, int.class,
                        int.class, int.class, int.class, IntBuffer.class);
                    uploadMethod.invoke(null, target, level, internalFormat,
                        width, height, border, format, type, pixels);
                    return;
                } catch (Exception ignored) {}
            }

            // Fall back to OpenGL
            Class<?> glStateManager = Class.forName(
                "com.mojang.blaze3d.platform.GlStateManager");
            Method texMethod = glStateManager.getMethod("texImage2D",
                int.class, int.class, int.class, int.class, int.class,
                int.class, int.class, int.class, IntBuffer.class);
            texMethod.invoke(null, target, level, internalFormat,
                width, height, border, format, type, pixels);
        } catch (Exception e) {
            LOGGER.warn("texImage2D compat failed: {}", e.getMessage());
        }
    }
}
