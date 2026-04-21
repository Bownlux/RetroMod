/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 * 
 * Sodium/Iris Rendering API Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Sodium and Iris rendering API compatibility shim.
 * 
 * Sodium is the most popular performance mod for Fabric.
 * Iris adds shader support on top of Sodium.
 * Many mods need compatibility with these.
 * 
 * API changes:
 * - Sodium 0.4.x -> 0.5.x: Major renderer rewrite
 * - Sodium 0.5.x -> 0.6.x: Further API changes for 1.21
 * - Iris 1.6.x -> 1.7.x: Shader pipeline changes
 */
public class SodiumIrisApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "Sodium/Iris API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "0.4.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "0.6.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "fabric";
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // SODIUM RENDERER API CHANGES
        // ============================================================
        
        // Old: me.jellysquid.mods.sodium
        // New: net.caffeinemc.mods.sodium (package rename in 0.5+)
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder",
            "net/caffeinemc/mods/sodium/client/render/chunk/compile/buffers/ChunkModelBuilder"
        );
        
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/model/vertex/type/ChunkVertexType",
            "net/caffeinemc/mods/sodium/client/render/chunk/vertex/format/ChunkVertexType"
        );
        
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/render/chunk/data/ChunkRenderData",
            "net/caffeinemc/mods/sodium/client/render/chunk/data/BuiltSectionInfo"
        );
        
        // ============================================================
        // SODIUM BLOCK RENDERER CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/render/pipeline/BlockRenderer",
            "net/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderer"
        );
        
        // Old: BlockRenderContext
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/render/pipeline/context/BlockRenderContext",
            "net/caffeinemc/mods/sodium/client/render/chunk/compile/pipeline/BlockRenderContext"
        );
        
        // ============================================================
        // SODIUM OPTIONS API
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/gui/SodiumGameOptions",
            "net/caffeinemc/mods/sodium/client/gui/SodiumGameOptions"
        );
        
        transformer.registerMethodRedirect(
            "me/jellysquid/mods/sodium/client/SodiumClientMod",
            "options",
            "()Lme/jellysquid/mods/sodium/client/gui/SodiumGameOptions;",
            "net/caffeinemc/mods/sodium/client/SodiumClientMod",
            "options",
            "()Lnet/caffeinemc/mods/sodium/client/gui/SodiumGameOptions;"
        );
        
        // ============================================================
        // FABRIC RENDERING API (FRAPI) CHANGES
        // ============================================================
        
        // Indium compatibility - FRAPI on Sodium
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/renderer/v1/Renderer",
            "net/fabricmc/fabric/api/renderer/v1/Renderer"
        );
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/renderer/v1/mesh/MeshBuilder",
            "net/fabricmc/fabric/api/renderer/v1/mesh/MeshBuilder"
        );
        
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/renderer/v1/mesh/QuadEmitter",
            "net/fabricmc/fabric/api/renderer/v1/mesh/QuadEmitter"
        );
        
        // ============================================================
        // IRIS SHADER API CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "net/coderbot/iris/api/v0/IrisApi",
            "net/irisshaders/iris/api/v0/IrisApi"
        );
        
        transformer.registerMethodRedirect(
            "net/coderbot/iris/api/v0/IrisApi",
            "getInstance",
            "()Lnet/coderbot/iris/api/v0/IrisApi;",
            "net/irisshaders/iris/api/v0/IrisApi",
            "getInstance",
            "()Lnet/irisshaders/iris/api/v0/IrisApi;"
        );
        
        transformer.registerMethodRedirect(
            "net/coderbot/iris/api/v0/IrisApi",
            "isShaderPackInUse",
            "()Z",
            "net/irisshaders/iris/api/v0/IrisApi",
            "isShaderPackInUse",
            "()Z"
        );
        
        // ============================================================
        // VERTEX FORMAT CHANGES
        // ============================================================
        
        transformer.registerClassRedirect(
            "me/jellysquid/mods/sodium/client/model/vertex/formats/ModelVertexSink",
            "net/caffeinemc/mods/sodium/client/render/vertex/VertexConsumer"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.SodiumShim"
        };
    }
}
