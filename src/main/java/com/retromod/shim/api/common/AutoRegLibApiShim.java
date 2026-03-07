/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * AutoRegLib API -> Modern Registry Methods Compatibility Shim
 */
package com.retromod.shim.api.common;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * AutoRegLib API compatibility shim.
 *
 * AutoRegLib (Automatic Registration Library) by Vazkii was a utility library
 * for Forge 1.10 through 1.16 that provided automatic block/item registration,
 * automatic model/texture loading, and automatic creative tab population.
 * It was the foundation library for Quark, Psi, Botania, and other Vazkii mods.
 *
 * AutoRegLib was deprecated in 1.16+ as Vazkii moved to Zeta (for Quark)
 * and direct Forge/NeoForge DeferredRegister patterns. The automatic
 * registration approach became less relevant as Minecraft's registry system
 * matured and mod loaders added their own deferred registration APIs.
 *
 * Key mappings:
 * - IForgeRegistryEntry (extended by ARL items/blocks) -> direct Registry
 * - RegistryHelper -> DeferredRegister (Forge) / Registry.register (Fabric)
 * - IModBlock / IModItem -> standard Block/Item subclassing
 * - ModCreativeTab -> CreativeModeTab.Builder
 * - RecipeHandler -> RecipeSerializer registration
 */
public class AutoRegLibApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "AutoRegLib -> Modern Registry Compatibility";
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
        return "common"; // AutoRegLib was Forge-only but the redirect pattern is universal
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // CORE REGISTRATION HELPER
        // ============================================================

        // Old: vazkii.autoreglib.RegistryHelper - main automatic registration class
        // Mods called RegistryHelper.register(block/item) and it auto-generated
        // registry names, model JSON paths, and creative tab entries
        // New: redirect to shim that uses modern DeferredRegister pattern
        transformer.registerClassRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$RegistryHelperCompat"
        );

        // Old: RegistryHelper.registerBlock(block, name) - register block + auto item
        // Automatically created a BlockItem, registered both, set up models
        // New: DeferredRegister<Block>.register(name, supplier) + item separately
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerBlock",
            "(Lnet/minecraft/block/Block;Ljava/lang/String;)Lnet/minecraft/block/Block;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerBlock",
            "(Ljava/lang/Object;Lnet/minecraft/world/level/block/Block;Ljava/lang/String;)Lnet/minecraft/world/level/block/Block;"
        );

        // Old: RegistryHelper.registerBlock(block, name, itemBlock)
        // Register block with a custom BlockItem
        // New: register both through DeferredRegister
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerBlock",
            "(Lnet/minecraft/block/Block;Ljava/lang/String;Lnet/minecraft/item/BlockItem;)Lnet/minecraft/block/Block;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerBlockWithItem",
            "(Ljava/lang/Object;Lnet/minecraft/world/level/block/Block;Ljava/lang/String;Lnet/minecraft/world/item/BlockItem;)Lnet/minecraft/world/level/block/Block;"
        );

        // Old: RegistryHelper.registerItem(item, name) - register item
        // Automatically set registry name and creative tab
        // New: DeferredRegister<Item>.register(name, supplier)
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerItem",
            "(Lnet/minecraft/item/Item;Ljava/lang/String;)Lnet/minecraft/item/Item;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerItem",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/Item;Ljava/lang/String;)Lnet/minecraft/world/item/Item;"
        );

        // Old: RegistryHelper.registerEntity(entityType, name, mod)
        // Auto-register entity types with tracking settings
        // New: DeferredRegister<EntityType> / Registry.register
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/api/RegistryHelper",
            "registerEntity",
            "(Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;III)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerEntity",
            "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/String;Ljava/lang/Object;III)V"
        );

        // ============================================================
        // IMOD BLOCK / IMOD ITEM INTERFACES
        // ============================================================

        // Old: vazkii.autoreglib.block.IModBlock - blocks implementing this get
        // automatic model loading, item registration, and creative tab placement
        // New: standard Block subclass (no auto-registration needed)
        transformer.registerClassRedirect(
            "vazkii/autoreglib/block/IModBlock",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$IModBlockCompat"
        );

        // Old: IModBlock.getPrefix() - return a prefix for the registry name
        // AutoRegLib would combine prefix + name for the registry path
        // New: no-op, modern mods use explicit registry names
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/block/IModBlock",
            "getPrefix",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "getBlockPrefix",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        // Old: IModBlock.createItemBlock() - auto-create the corresponding BlockItem
        // New: mods create BlockItems explicitly
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/block/IModBlock",
            "createItemBlock",
            "()Lnet/minecraft/item/BlockItem;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "createItemBlock",
            "(Ljava/lang/Object;)Lnet/minecraft/world/item/BlockItem;"
        );

        // Old: vazkii.autoreglib.item.IModItem - items implementing this get
        // automatic model loading and creative tab placement
        // New: standard Item subclass
        transformer.registerClassRedirect(
            "vazkii/autoreglib/item/IModItem",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$IModItemCompat"
        );

        // Old: IModItem.getPrefix()
        // New: no-op
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/item/IModItem",
            "getPrefix",
            "()Ljava/lang/String;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "getItemPrefix",
            "(Ljava/lang/Object;)Ljava/lang/String;"
        );

        // ============================================================
        // CREATIVE TAB HELPER
        // ============================================================

        // Old: vazkii.autoreglib.util.ModCreativeTab - auto-populating creative tab
        // Automatically added all registered IModBlock/IModItem to this tab
        // New: CreativeModeTab.Builder (1.19.3+) or manual registration
        transformer.registerClassRedirect(
            "vazkii/autoreglib/util/ModCreativeTab",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$ModCreativeTabCompat"
        );

        // Old: new ModCreativeTab(modId, iconSupplier) - create creative tab
        // New: CreativeModeTab.builder().title(...).icon(...).displayItems(...).build()
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/util/ModCreativeTab",
            "<init>",
            "(Ljava/lang/String;Ljava/util/function/Supplier;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "createCreativeTab",
            "(Ljava/lang/String;Ljava/util/function/Supplier;)Ljava/lang/Object;"
        );

        // ============================================================
        // RECIPE HANDLER
        // ============================================================

        // Old: vazkii.autoreglib.recipe.RecipeHandler - auto recipe serializer registration
        // New: RecipeSerializer registered via DeferredRegister
        transformer.registerClassRedirect(
            "vazkii/autoreglib/recipe/RecipeHandler",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$RecipeHandlerCompat"
        );

        // Old: RecipeHandler.addShapedRecipe(output, params...) - helper to add recipes
        // This was already deprecated in later ARL versions (recipes moved to data packs)
        // New: no-op or log warning (recipes must be JSON data packs now)
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/recipe/RecipeHandler",
            "addShapedRecipe",
            "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "addShapedRecipe",
            "(Lnet/minecraft/world/item/ItemStack;[Ljava/lang/Object;)V"
        );

        // Old: RecipeHandler.addShapelessRecipe(output, inputs...)
        // New: no-op (recipes are data-driven in modern MC)
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/recipe/RecipeHandler",
            "addShapelessRecipe",
            "(Lnet/minecraft/item/ItemStack;[Ljava/lang/Object;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "addShapelessRecipe",
            "(Lnet/minecraft/world/item/ItemStack;[Ljava/lang/Object;)V"
        );

        // ============================================================
        // MODEL HANDLER (automatic model/texture resolution)
        // ============================================================

        // Old: vazkii.autoreglib.client.ModelHandler - auto JSON model generation
        // AutoRegLib would generate simple block/item models at runtime if no
        // JSON was found in the resource pack
        // New: mods provide JSON models or use model datagen
        transformer.registerClassRedirect(
            "vazkii/autoreglib/client/ModelHandler",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$ModelHandlerCompat"
        );

        // Old: ModelHandler.registerModel(item, meta, modelLocation)
        // Used to manually override model locations
        // New: ModelLoadingPlugin (Fabric) or RegisterEvent for models (Forge)
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/client/ModelHandler",
            "registerModel",
            "(Lnet/minecraft/item/Item;ILnet/minecraft/util/ResourceLocation;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerModel",
            "(Lnet/minecraft/world/item/Item;ILjava/lang/Object;)V"
        );

        // ============================================================
        // PROXY SYSTEM (removed in modern MC modding)
        // ============================================================

        // Old: vazkii.autoreglib.base.ModBase - base mod class with proxy support
        // AutoRegLib had its own proxy system wrapping Forge's @SidedProxy
        // New: modern mods use dist-specific initialization instead
        transformer.registerClassRedirect(
            "vazkii/autoreglib/base/ModBase",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$ModBaseCompat"
        );

        // Old: ModBase.proxy field (CommonProxy)
        // New: no-op, side-specific code uses DistExecutor or platform checks
        transformer.registerFieldRedirect(
            "vazkii/autoreglib/base/ModBase",
            "proxy",
            "Lvazkii/autoreglib/base/CommonProxy;",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "PROXY_COMPAT",
            "Ljava/lang/Object;"
        );

        // ============================================================
        // NETWORK HELPER
        // ============================================================

        // Old: vazkii.autoreglib.network.NetworkHandler - simple packet registration
        // Wrapped Forge's SimpleNetworkWrapper with auto-incrementing discriminators
        // New: modern packet registration via Forge/Fabric networking APIs
        transformer.registerClassRedirect(
            "vazkii/autoreglib/network/NetworkHandler",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim$NetworkHandlerCompat"
        );

        // Old: NetworkHandler.register(packetClass, side) - register a packet type
        // New: channel.registerPacket() or PayloadRegistrar (NeoForge)
        transformer.registerMethodRedirect(
            "vazkii/autoreglib/network/NetworkHandler",
            "register",
            "(Ljava/lang/Class;Lnet/minecraftforge/fml/relauncher/Side;)V",
            "com/retromod/shim/api/common/embedded/AutoRegLibShim",
            "registerPacket",
            "(Ljava/lang/Object;Ljava/lang/Class;Ljava/lang/Object;)V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.common.embedded.AutoRegLibShim"
        };
    }
}
