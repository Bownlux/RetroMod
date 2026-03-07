/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Fabric Shield Lib API -> Modern Rendering Compatibility Shim
 */
package com.retromod.shim.api.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Fabric Shield Lib API compatibility shim.
 *
 * FabricShieldLib by CrimsonDawn45 was a library for Fabric (1.16 - 1.19)
 * that provided an easy API for creating custom shields with custom models
 * and banner patterns. It filled a gap that Fabric API did not cover for
 * custom shield rendering.
 *
 * The library became unmaintained after 1.19 as Minecraft's rendering pipeline
 * underwent significant changes (PoseStack -> GuiGraphics, new model loading
 * system, etc.). Modern Fabric mods now use direct rendering hooks or
 * Fabric Rendering API v1 for custom shield models.
 *
 * Key mappings:
 * - FabricShieldLib.registerShield() -> manual registry + model registration
 * - FabricShieldItem -> custom Item subclass with shield behavior
 * - FabricBannerShieldItem -> custom Item with banner rendering
 * - ShieldEntityModel -> vanilla ShieldModel with custom geometry
 * - Shield render layer registration -> Fabric Rendering API
 */
public class FabricShieldLibApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "Fabric Shield Lib -> Modern Rendering Compatibility";
    }

    @Override
    public String getSourceVersion() {
        return "*";
    }

    @Override
    public String getTargetVersion() {
        return "*";
    }

    @Override
    public String getModLoaderType() {
        return "fabric";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // SHIELD ITEM CLASSES
        // ============================================================

        // Old: com.github.crimsondawn45.fabricshieldlib.lib.object.FabricShieldItem
        // Base class for custom shield items; extended Item with shield durability,
        // cooldown, and blocking behavior
        // New: redirect to shim that creates a proper shield Item for modern MC
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldItem",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$FabricShieldItemCompat"
        );

        // Old: FabricShieldItem constructor (settings, cooldownTicks, durability, repairItem)
        // New: construct modern shield item with equivalent properties
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldItem",
            "<init>",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "createShieldItem",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)Ljava/lang/Object;"
        );

        // Old: com.github.crimsondawn45.fabricshieldlib.lib.object.FabricBannerShieldItem
        // Extended FabricShieldItem with banner pattern rendering support
        // New: redirect to shim with banner support
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricBannerShieldItem",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$FabricBannerShieldItemCompat"
        );

        // Old: FabricBannerShieldItem constructor (settings, cooldownTicks, durability, repairItem)
        // New: construct modern banner shield item
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricBannerShieldItem",
            "<init>",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "createBannerShieldItem",
            "(Lnet/minecraft/item/Item$Settings;IILnet/minecraft/item/Item;)Ljava/lang/Object;"
        );

        // ============================================================
        // SHIELD ENTITY MODEL (rendering)
        // ============================================================

        // Old: com.github.crimsondawn45.fabricshieldlib.lib.object.FabricShieldEntityModel
        // Custom entity model class for shield rendering with Fabric's model system
        // New: net.minecraft.client.model.ShieldModel (vanilla 1.20+)
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldEntityModel",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ShieldModelCompat"
        );

        // Old: FabricShieldEntityModel constructor (modelPart)
        // New: ShieldModel(root) in modern MC
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/object/FabricShieldEntityModel",
            "<init>",
            "(Lnet/minecraft/client/model/ModelPart;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "createShieldModel",
            "(Lnet/minecraft/client/model/ModelPart;)Ljava/lang/Object;"
        );

        // ============================================================
        // MAIN LIB REGISTRATION CLASS
        // ============================================================

        // Old: com.github.crimsondawn45.fabricshieldlib.initializers.FabricShieldLibClient
        // Client-side initializer that set up shield rendering
        // New: redirect to shim that handles modern render registration
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLibClient",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ClientInitCompat"
        );

        // Old: FabricShieldLib (common initializer)
        // New: redirect to shim
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLib",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$CommonInitCompat"
        );

        // ============================================================
        // SHIELD RENDERING / MODEL LAYER REGISTRATION
        // ============================================================

        // Old: FabricShieldLibClient.registerShieldModelLayer(shieldItem, modelLayer)
        // Registered a custom model layer for the shield's rendering
        // New: EntityModelLayerRegistry from Fabric Rendering API
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLibClient",
            "registerShieldModelLayer",
            "(Lnet/minecraft/item/Item;Lnet/minecraft/client/render/entity/model/EntityModelLayer;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "registerShieldModelLayer",
            "(Lnet/minecraft/item/Item;Ljava/lang/Object;)V"
        );

        // Old: FabricShieldLibClient.registerShieldTexture(shieldItem, texture)
        // Registered the texture used for shield rendering
        // New: handled via modern SpriteAtlas or direct texture reference
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLibClient",
            "registerShieldTexture",
            "(Lnet/minecraft/item/Item;Lnet/minecraft/util/Identifier;)V",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "registerShieldTexture",
            "(Lnet/minecraft/item/Item;Lnet/minecraft/util/Identifier;)V"
        );

        // ============================================================
        // SHIELD ENCHANTMENT HELPERS
        // ============================================================

        // Old: FabricShieldLib.isShield(stack) - check if an item is a registered shield
        // New: check via item properties / tags
        transformer.registerMethodRedirect(
            "com/github/crimsondawn45/fabricshieldlib/initializers/FabricShieldLib",
            "isShield",
            "(Lnet/minecraft/item/ItemStack;)Z",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "isShield",
            "(Lnet/minecraft/item/ItemStack;)Z"
        );

        // ============================================================
        // SHIELD EVENT CALLBACKS
        // ============================================================

        // Old: com.github.crimsondawn45.fabricshieldlib.lib.event.ShieldBlockCallback
        // Custom event fired when a shield blocks an attack
        // New: redirect to Fabric API attack entity / damage events
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldBlockCallback",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ShieldBlockCallbackCompat"
        );

        // Old: ShieldBlockCallback.EVENT - the event instance
        // New: redirect to Fabric API equivalent
        transformer.registerFieldRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldBlockCallback",
            "EVENT",
            "Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "SHIELD_BLOCK_EVENT",
            "Ljava/lang/Object;"
        );

        // Old: com.github.crimsondawn45.fabricshieldlib.lib.event.ShieldDisabledCallback
        // Custom event fired when shield is disabled (cooldown from axe)
        // New: redirect to vanilla/Fabric damage events
        transformer.registerClassRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldDisabledCallback",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim$ShieldDisabledCallbackCompat"
        );

        transformer.registerFieldRedirect(
            "com/github/crimsondawn45/fabricshieldlib/lib/event/ShieldDisabledCallback",
            "EVENT",
            "Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/api/fabric/embedded/FabricShieldLibShim",
            "SHIELD_DISABLED_EVENT",
            "Ljava/lang/Object;"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.fabric.embedded.FabricShieldLibShim"
        };
    }
}
