/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux. Licensed under MIT License.
 */
package com.retromod.shim.neoforge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * NeoForge 1.21.1 to 1.21.2 shim - Major API renames.
 * Significant changes: ShaderInstance renamed to CompiledShaderProgram,
 * InteractionResult types consolidated, Registry method renames,
 * Attribute field prefix removals (GENERIC_ dropped).
 */
public class NeoForge_1_21_1_to_1_21_2 implements VersionShim {
    @Override public String getShimName() { return "NeoForge 1.21.1 to 1.21.2"; }
    @Override public String getSourceVersion() { return "1.21.1"; }
    @Override public String getTargetVersion() { return "1.21.2"; }
    @Override public String getModLoaderType() { return "neoforge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ShaderInstance renamed to CompiledShaderProgram in 1.21.2
        transformer.registerClassRedirect(
            "net/minecraft/client/renderer/ShaderInstance",
            "net/minecraft/client/renderer/CompiledShaderProgram"
        );

        // InteractionResultHolder merged into InteractionResult
        transformer.registerClassRedirect(
            "net/minecraft/world/InteractionResultHolder",
            "net/minecraft/world/InteractionResult"
        );

        // ItemInteractionResult merged into InteractionResult
        transformer.registerClassRedirect(
            "net/minecraft/world/ItemInteractionResult",
            "net/minecraft/world/InteractionResult"
        );

        // RegistryAccess.registryOrThrow renamed to lookupOrThrow
        transformer.registerMethodRedirect(
            "net/minecraft/core/RegistryAccess", "registryOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;",
            "net/minecraft/core/RegistryAccess", "lookupOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;"
        );

        // Registry.getHolderOrThrow renamed to getOrThrow
        transformer.registerMethodRedirect(
            "net/minecraft/core/Registry", "getHolderOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;",
            "net/minecraft/core/Registry", "getOrThrow",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Holder$Reference;"
        );

        // Registry.getOptional renamed to getOptionalValue
        transformer.registerMethodRedirect(
            "net/minecraft/core/Registry", "getOptional",
            "(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;",
            "net/minecraft/core/Registry", "getOptionalValue",
            "(Lnet/minecraft/resources/ResourceLocation;)Ljava/util/Optional;"
        );

        // Item.getDescription renamed to getName
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getDescription",
            "()Lnet/minecraft/network/chat/Component;",
            "net/minecraft/world/item/Item", "getName",
            "()Lnet/minecraft/network/chat/Component;"
        );

        // Item.getCraftingRemainingItem renamed to getCraftingRemainder
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getCraftingRemainingItem",
            "()Lnet/minecraft/world/item/Item;",
            "net/minecraft/world/item/Item", "getCraftingRemainder",
            "()Lnet/minecraft/world/item/Item;"
        );

        // Attributes: GENERIC_ prefix removed in 1.21.2
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MAX_HEALTH",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MAX_HEALTH",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_MOVEMENT_SPEED",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "MOVEMENT_SPEED",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ATTACK_DAMAGE",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ATTACK_DAMAGE",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_ARMOR",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "ARMOR",
            "Lnet/minecraft/core/Holder;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/ai/attributes/Attributes", "GENERIC_KNOCKBACK_RESISTANCE",
            "Lnet/minecraft/core/Holder;",
            "net/minecraft/world/entity/ai/attributes/Attributes", "KNOCKBACK_RESISTANCE",
            "Lnet/minecraft/core/Holder;"
        );
    }

    @Override public String[] getShimClasses() { return new String[0]; }
}
