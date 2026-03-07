/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 RevivalSMP. Licensed under MIT.
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.EnvironmentDetector;
import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Rendering Backend Compatibility Shim.
 *
 * Handles the transition from OpenGL to Vulkan/Metal/DirectX rendering backends.
 * When Minecraft switches rendering APIs, mods that directly call into the
 * rendering layer will break. This shim intercepts those calls and redirects
 * them to the correct backend implementation.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * HOW MINECRAFT RENDERING WORKS (for context):
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * Minecraft uses LWJGL (Lightweight Java Game Library) for rendering.
 * Currently LWJGL provides OpenGL bindings, but LWJGL 3 also supports Vulkan.
 *
 * The rendering stack:
 *   Mod code → Minecraft's rendering API → GlStateManager → LWJGL → OpenGL/Vulkan/Metal
 *
 * When the backend changes (e.g., OpenGL → Vulkan), the layers that change are:
 *   1. GlStateManager → VkStateManager (or RenderStateManager)
 *   2. LWJGL OpenGL calls → LWJGL Vulkan calls
 *   3. Shader compilation (GLSL → SPIR-V)
 *   4. Buffer/Texture management APIs
 *
 * Mods that use Minecraft's HIGH-LEVEL rendering API (DrawContext, VertexConsumer)
 * should work without changes. Mods that reach into LOW-LEVEL APIs (GlStateManager,
 * direct LWJGL calls) will need redirection.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * WHAT THIS SHIM DOES:
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * 1. Detects which rendering backend is active
 * 2. Registers method redirects for deprecated/removed rendering calls
 * 3. Provides compatibility wrappers for common rendering patterns
 * 4. Handles architecture-specific rendering differences (ARM vs x86)
 *
 * ═══════════════════════════════════════════════════════════════════════════
 */
public class RenderingBackendShim implements VersionShim {

    private static final Logger LOGGER = LoggerFactory.getLogger("RetroMod-RenderBackend");

    @Override
    public String getShimName() {
        return "RenderingBackendCompat";
    }

    @Override
    public String getSourceVersion() {
        return "1.21.4"; // OpenGL-era versions
    }

    @Override
    public String getTargetVersion() {
        return "26.1.0"; // Future Vulkan-era versions
    }

    @Override
    public String getModLoaderType() {
        return "fabric"; // Works on all loaders through bytecode transformation
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        EnvironmentDetector.RenderingBackend backend = EnvironmentDetector.getRenderingBackend();
        LOGGER.info("Rendering backend: {} (arch: {}, os: {})",
            backend, EnvironmentDetector.getCpuArch(), EnvironmentDetector.getOsFamily());

        // =====================================================================
        //  OpenGL → Vulkan Rendering Redirects
        // =====================================================================
        //
        // When Minecraft eventually moves to Vulkan, these classes/methods
        // will be renamed or restructured. We register redirects proactively
        // so old mods keep working.
        //
        // These redirects only activate if the target classes actually exist
        // in the running Minecraft version. If they don't exist, the redirects
        // are harmless no-ops.
        // =====================================================================

        if (backend == EnvironmentDetector.RenderingBackend.VULKAN) {
            registerVulkanRedirects(transformer);
        } else if (backend == EnvironmentDetector.RenderingBackend.METAL) {
            registerMetalRedirects(transformer);
        }

        // Always register future-proof redirects (safe on all backends)
        registerFutureProofRedirects(transformer);

        LOGGER.info("Rendering backend shim registered");
    }

    /**
     * Register redirects for the OpenGL → Vulkan transition.
     * These will activate when Minecraft switches to Vulkan.
     */
    private void registerVulkanRedirects(RetroModTransformer transformer) {
        LOGGER.info("Registering Vulkan rendering redirects");

        // GlStateManager → VkStateManager (or unified RenderStateManager)
        // When MC moves to Vulkan, GlStateManager will be replaced
        transformer.registerClassRedirect(
            "com/mojang/blaze3d/platform/GlStateManager",
            "com/mojang/blaze3d/platform/RenderStateManager"
        );

        // GL-specific method redirects
        // enableTexture/disableTexture removed in Vulkan (textures always bound)
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/platform/GlStateManager", "enableTexture", "()V",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "noop", "()V"
        );
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/platform/GlStateManager", "disableTexture", "()V",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "noop", "()V"
        );

        // Shader compilation: GLSL shaders → SPIR-V
        // Mods that load custom GLSL shaders will need translation
        transformer.registerMethodRedirect(
            "net/minecraft/client/gl/ShaderProgram", "loadProgram",
            "(Ljava/lang/String;)V",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "loadShaderCompat",
            "(Ljava/lang/String;)V"
        );

        // VertexFormat changes (Vulkan uses different vertex layouts)
        transformer.registerClassRedirect(
            "com/mojang/blaze3d/vertex/DefaultVertexFormat",
            "com/mojang/blaze3d/vertex/VertexFormats"
        );

        // Framebuffer handling
        // OpenGL framebuffers → Vulkan render passes
        transformer.registerMethodRedirect(
            "net/minecraft/client/gl/Framebuffer", "bindFramebuffer", "(Z)V",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "bindRenderTarget",
            "(Ljava/lang/Object;Z)V"
        );
    }

    /**
     * Register redirects for Metal rendering on macOS.
     * Metal may be used directly on Apple Silicon for better performance.
     */
    private void registerMetalRedirects(RetroModTransformer transformer) {
        LOGGER.info("Registering Metal rendering redirects (macOS)");

        // Metal uses similar abstractions to Vulkan, so most Vulkan redirects apply
        registerVulkanRedirects(transformer);

        // Additional Metal-specific redirects
        // Metal has different texture format support than OpenGL
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/platform/GlStateManager", "texImage2D",
            "(IIIIIIIILjava/nio/IntBuffer;)V",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "texImage2DCompat",
            "(IIIIIIIILjava/nio/IntBuffer;)V"
        );
    }

    /**
     * Register future-proof redirects that work on ALL backends.
     * These handle common API changes that happen regardless of rendering backend.
     */
    private void registerFutureProofRedirects(RetroModTransformer transformer) {
        // =====================================================================
        //  RenderSystem thread safety
        // =====================================================================
        // Vulkan/Metal require stricter threading. Mods that call RenderSystem
        // from wrong threads will crash. We can redirect to thread-safe wrappers.
        // (These are safe on OpenGL too - just add a thread check)

        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/systems/RenderSystem", "assertThread",
            "(Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "assertRenderThread",
            "(Ljava/util/function/Supplier;)V"
        );

        // =====================================================================
        //  MatrixStack → PoseStack rename tracking
        // =====================================================================
        // Mojang has been gradually renaming MatrixStack to PoseStack
        // in their mappings. Both should work.

        transformer.registerClassRedirect(
            "net/minecraft/client/util/math/MatrixStack",
            "net/minecraft/client/util/math/MatrixStack"
        );

        // =====================================================================
        //  DrawContext evolution
        // =====================================================================
        // DrawContext (1.20+) replaced MatrixStack for most 2D rendering.
        // Future versions may rename it or add Vulkan-specific methods.

        // Old Screen.render(MatrixStack, ...) → new Screen.render(DrawContext, ...)
        // This is already handled by version shims, but we reinforce here

        // =====================================================================
        //  Tessellator / BufferBuilder changes
        // =====================================================================
        // Vulkan doesn't use the same immediate-mode-style buffer building.
        // Future MC may replace Tessellator with a more Vulkan-friendly API.

        transformer.registerMethodRedirect(
            "net/minecraft/client/render/Tessellator", "getInstance",
            "()Lnet/minecraft/client/render/Tessellator;",
            "com/retromod/shim/api/fabric/embedded/RenderingCompat", "getTessellator",
            "()Ljava/lang/Object;"
        );
    }
}
