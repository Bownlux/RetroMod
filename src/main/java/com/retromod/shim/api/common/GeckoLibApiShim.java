/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under RetroMod Personal Use License.
 * 
 * GeckoLib API Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * GeckoLib API compatibility shim.
 * 
 * GeckoLib is the most popular animation library for Minecraft mods.
 * Used for animated entities, armor, items, and blocks.
 * 
 * Works on both Fabric and Forge/NeoForge.
 * 
 * Major API changes:
 * - v2.x -> v3.x: Complete rewrite
 * - v3.x -> v4.x: Animation system changes
 * - v4.x -> v5.x: Renderer changes, 1.20+ support
 */
public class GeckoLibApiShim implements VersionShim {
    
    @Override
    public String getShimName() {
        return "GeckoLib API Compatibility";
    }
    
    @Override
    public String getSourceVersion() {
        return "3.0.0";
    }
    
    @Override
    public String getTargetVersion() {
        return "4.4.0";
    }
    
    @Override
    public String getModLoaderType() {
        return "common"; // Works for both Fabric and Forge
    }
    
    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // PACKAGE CHANGES (v3 -> v4)
        // ============================================================
        
        // Old: software.bernie.geckolib3
        // New: software.bernie.geckolib
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/core/IAnimatable",
            "software/bernie/geckolib/animatable/GeoAnimatable"
        );
        
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/core/manager/AnimationData",
            "software/bernie/geckolib/animatable/instance/AnimatableInstanceCache"
        );
        
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/core/manager/AnimationFactory",
            "software/bernie/geckolib/util/GeckoLibUtil"
        );
        
        // ============================================================
        // ANIMATABLE INTERFACE CHANGES
        // ============================================================
        
        // Old: IAnimatable.getFactory()
        // New: GeoAnimatable.getAnimatableInstanceCache()
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/core/IAnimatable",
            "getFactory",
            "()Lsoftware/bernie/geckolib3/core/manager/AnimationFactory;",
            "software/bernie/geckolib/animatable/GeoAnimatable",
            "getAnimatableInstanceCache",
            "()Lsoftware/bernie/geckolib/animatable/instance/AnimatableInstanceCache;"
        );
        
        // Old: registerControllers(AnimationData data)
        // New: registerControllers(AnimatableManager.ControllerRegistrar)
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/core/IAnimatable",
            "registerControllers",
            "(Lsoftware/bernie/geckolib3/core/manager/AnimationData;)V",
            "com/retromod/shim/api/common/embedded/GeckoLibShim",
            "registerControllersCompat",
            "(Ljava/lang/Object;Ljava/lang/Object;)V"
        );
        
        // ============================================================
        // ANIMATION CONTROLLER CHANGES
        // ============================================================
        
        // Old: AnimationController<T extends IAnimatable>
        // New: AnimationController<T extends GeoAnimatable>
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/core/controller/AnimationController",
            "software/bernie/geckolib/animation/AnimationController"
        );
        
        // Old: AnimationController constructor
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/core/controller/AnimationController",
            "<init>",
            "(Lsoftware/bernie/geckolib3/core/IAnimatable;Ljava/lang/String;FLsoftware/bernie/geckolib3/core/controller/AnimationController$IAnimationPredicate;)V",
            "com/retromod/shim/api/common/embedded/GeckoLibShim",
            "createController",
            "(Ljava/lang/Object;Ljava/lang/String;FLjava/lang/Object;)Ljava/lang/Object;"
        );
        
        // ============================================================
        // ANIMATION BUILDER CHANGES
        // ============================================================
        
        // Old: AnimationBuilder
        // New: RawAnimation
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/core/builder/AnimationBuilder",
            "software/bernie/geckolib/animation/RawAnimation"
        );
        
        // Old: new AnimationBuilder().addAnimation("name")
        // New: RawAnimation.begin().then("name", loopType)
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/core/builder/AnimationBuilder",
            "addAnimation",
            "(Ljava/lang/String;)Lsoftware/bernie/geckolib3/core/builder/AnimationBuilder;",
            "com/retromod/shim/api/common/embedded/GeckoLibShim",
            "addAnimation",
            "(Ljava/lang/Object;Ljava/lang/String;)Ljava/lang/Object;"
        );
        
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/core/builder/AnimationBuilder",
            "addAnimation",
            "(Ljava/lang/String;Z)Lsoftware/bernie/geckolib3/core/builder/AnimationBuilder;",
            "com/retromod/shim/api/common/embedded/GeckoLibShim",
            "addAnimationWithLoop",
            "(Ljava/lang/Object;Ljava/lang/String;Z)Ljava/lang/Object;"
        );
        
        // ============================================================
        // RENDERER CHANGES
        // ============================================================
        
        // Old: GeoEntityRenderer<T extends LivingEntity & IAnimatable>
        // New: GeoEntityRenderer<T extends LivingEntity & GeoAnimatable>
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/renderers/geo/GeoEntityRenderer",
            "software/bernie/geckolib/renderer/GeoEntityRenderer"
        );
        
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/renderers/geo/GeoItemRenderer",
            "software/bernie/geckolib/renderer/GeoItemRenderer"
        );
        
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/renderers/geo/GeoArmorRenderer",
            "software/bernie/geckolib/renderer/GeoArmorRenderer"
        );
        
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/renderers/geo/GeoBlockRenderer",
            "software/bernie/geckolib/renderer/GeoBlockRenderer"
        );
        
        // ============================================================
        // MODEL PROVIDER CHANGES
        // ============================================================
        
        // Old: GeoModelProvider
        // New: GeoModel
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/model/AnimatedGeoModel",
            "software/bernie/geckolib/model/GeoModel"
        );
        
        // Old: getModelLocation, getTextureLocation, getAnimationFileLocation
        // These method names stayed similar but return types changed
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/model/AnimatedGeoModel",
            "getModelLocation",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;",
            "software/bernie/geckolib/model/GeoModel",
            "getModelResource",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"
        );
        
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/model/AnimatedGeoModel",
            "getTextureLocation",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;",
            "software/bernie/geckolib/model/GeoModel",
            "getTextureResource",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"
        );
        
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/model/AnimatedGeoModel",
            "getAnimationFileLocation",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;",
            "software/bernie/geckolib/model/GeoModel",
            "getAnimationResource",
            "(Ljava/lang/Object;)Lnet/minecraft/resources/ResourceLocation;"
        );
        
        // ============================================================
        // PLAY STATE CHANGES
        // ============================================================
        
        // Old: PlayState enum
        // New: Same but different package
        transformer.registerClassRedirect(
            "software/bernie/geckolib3/core/PlayState",
            "software/bernie/geckolib/animation/PlayState"
        );
        
        // ============================================================
        // GECKOLIB INITIALIZATION
        // ============================================================
        
        // Old: GeckoLib.initialize()
        // New: GeckoLibUtil methods or auto-init
        transformer.registerMethodRedirect(
            "software/bernie/geckolib3/GeckoLib",
            "initialize",
            "()V",
            "com/retromod/shim/api/common/embedded/GeckoLibShim",
            "initialize",
            "()V"
        );
    }
    
    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.GeckoLibShim"
        };
    }
}
