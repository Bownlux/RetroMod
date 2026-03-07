/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * WAILA (What Am I Looking At) API -> Jade/HWYLA Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * WAILA API compatibility shim.
 *
 * WAILA (What Am I Looking At) by ProfMobius was the original tooltip overlay
 * mod for Forge 1.5 through 1.12.2. It showed information about the block/entity
 * the player was looking at.
 *
 * WAILA's lineage:
 * - WAILA (1.5 - 1.12.2) by ProfMobius (mcp.mobius.waila)
 * - HWYLA (Here's What You're Looking At, 1.9 - 1.16.5) by TehNut - fork of WAILA
 * - Jade (1.16+ to present) by Snownee - spiritual successor, modern API
 *
 * This shim redirects WAILA API calls to Jade equivalents. Many popular mods
 * from 1.7.10/1.12.2 (Tinkers, Thermal, EnderIO) used the WAILA API to add
 * tooltip information for their blocks.
 *
 * Key mappings:
 * - IWailaDataProvider -> IBlockComponentProvider / IEntityComponentProvider (Jade)
 * - IWailaPlugin -> IWailaPlugin (Jade keeps the name but changes the package)
 * - IWailaRegistrar -> IWailaCommonRegistration / IWailaClientRegistration (Jade)
 * - ITaggedList / tooltip list -> ITooltip (Jade)
 */
public class WailaApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "WAILA -> Jade/HWYLA API Compatibility";
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
        return "forge";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // CORE PLUGIN INTERFACE
        // ============================================================

        // Old: mcp.mobius.waila.api.IWailaPlugin - plugin entry point
        // Mods implemented this to register their data providers
        // New: snownee.jade.api.IWailaPlugin (Jade keeps the interface name)
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaPlugin",
            "snownee/jade/api/IWailaPlugin"
        );

        // Old: @WailaPlugin annotation on plugin class
        // New: @WailaPlugin annotation (Jade, same name different package)
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/WailaPlugin",
            "snownee/jade/api/WailaPlugin"
        );

        // ============================================================
        // REGISTRAR INTERFACE CHANGES
        // ============================================================

        // Old: mcp.mobius.waila.api.IWailaRegistrar - single registrar for everything
        // Mods used this in IWailaPlugin.register(registrar) to add providers
        // New: Jade splits into IWailaClientRegistration and IWailaCommonRegistration
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "com/retromod/shim/api/forge/embedded/WailaShim$RegistrarCompat"
        );

        // Old: IWailaRegistrar.registerBodyProvider(provider, blockClass)
        // Added tooltip lines to the body section of the overlay
        // New: IWailaClientRegistration.registerBlockComponent(provider, blockClass)
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerBodyProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerBodyProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        // Old: IWailaRegistrar.registerHeadProvider(provider, blockClass)
        // Added tooltip lines to the head (top) section
        // New: IWailaClientRegistration.registerBlockComponent with priority
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerHeadProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerHeadProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        // Old: IWailaRegistrar.registerTailProvider(provider, blockClass)
        // Added tooltip lines to the tail (bottom) section
        // New: IWailaClientRegistration.registerBlockComponent with priority
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerTailProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerTailProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        // Old: IWailaRegistrar.registerNBTProvider(provider, blockClass)
        // Used to sync server-side NBT data for the tooltip
        // New: IWailaCommonRegistration.registerBlockDataProvider(provider, blockClass)
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerNBTProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerNBTProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        // Old: IWailaRegistrar.registerStackProvider(provider, blockClass)
        // Used to override the displayed ItemStack in the overlay
        // New: IWailaClientRegistration.registerBlockIcon(provider, blockClass)
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaRegistrar",
            "registerStackProvider",
            "(Lmcp/mobius/waila/api/IWailaDataProvider;Ljava/lang/Class;)V",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "registerStackProvider",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Class;)V"
        );

        // ============================================================
        // DATA PROVIDER INTERFACE (the main provider mods implement)
        // ============================================================

        // Old: mcp.mobius.waila.api.IWailaDataProvider - block/tile data provider
        // This was THE interface all WAILA addons implemented
        // New: snownee.jade.api.IBlockComponentProvider (Jade)
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "com/retromod/shim/api/forge/embedded/WailaShim$DataProviderCompat"
        );

        // Old: IWailaDataProvider.getWailaBody(stack, tooltip, accessor, config)
        // Main method for adding tooltip lines
        // New: IBlockComponentProvider.appendTooltip(tooltip, accessor, config)
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaBody",
            "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Ljava/util/List;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaBody",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
        );

        // Old: IWailaDataProvider.getWailaHead(stack, tooltip, accessor, config)
        // Method for the header line (usually block name)
        // New: IBlockComponentProvider.appendTooltip with appropriate priority
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaHead",
            "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Ljava/util/List;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaHead",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
        );

        // Old: IWailaDataProvider.getWailaTail(stack, tooltip, accessor, config)
        // Method for the footer line (usually mod name)
        // New: IBlockComponentProvider.appendTooltip with appropriate priority
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaTail",
            "(Lnet/minecraft/item/ItemStack;Ljava/util/List;Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Ljava/util/List;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaTail",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Ljava/util/List;Ljava/lang/Object;Ljava/lang/Object;)Ljava/util/List;"
        );

        // Old: IWailaDataProvider.getWailaStack(accessor, config)
        // Returns the ItemStack to display as the icon
        // New: IBlockComponentProvider.getBlockIcon(accessor, config)
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getWailaStack",
            "(Lmcp/mobius/waila/api/IWailaDataAccessor;Lmcp/mobius/waila/api/IWailaConfigHandler;)Lnet/minecraft/item/ItemStack;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getWailaStack",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Lnet/minecraft/world/item/ItemStack;"
        );

        // Old: IWailaDataProvider.getNBTData(player, te, tag, world, pos)
        // Server-side method to add NBT data for client-side tooltip
        // New: IServerDataProvider.appendServerData(data, accessor)
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataProvider",
            "getNBTData",
            "(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/tileentity/TileEntity;Lnet/minecraft/nbt/NBTTagCompound;Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/nbt/NBTTagCompound;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getNBTData",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // ============================================================
        // DATA ACCESSOR (read-only access to block/entity being looked at)
        // ============================================================

        // Old: mcp.mobius.waila.api.IWailaDataAccessor
        // Provided context about the block being looked at
        // New: snownee.jade.api.BlockAccessor
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "snownee/jade/api/BlockAccessor"
        );

        // Old: IWailaDataAccessor.getTileEntity()
        // New: BlockAccessor.getBlockEntity()
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getTileEntity",
            "()Lnet/minecraft/tileentity/TileEntity;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getBlockEntity",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Old: IWailaDataAccessor.getBlock()
        // New: BlockAccessor.getBlock()
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getBlock",
            "()Lnet/minecraft/block/Block;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getBlock",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Old: IWailaDataAccessor.getPosition()
        // New: BlockAccessor.getPosition()
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getPosition",
            "()Lnet/minecraft/util/math/BlockPos;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getPosition",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // Old: IWailaDataAccessor.getNBTData()
        // New: BlockAccessor.getServerData()
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaDataAccessor",
            "getNBTData",
            "()Lnet/minecraft/nbt/NBTTagCompound;",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getServerData",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // ============================================================
        // CONFIG HANDLER
        // ============================================================

        // Old: mcp.mobius.waila.api.IWailaConfigHandler
        // New: snownee.jade.api.config.IWailaConfig (Jade)
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaConfigHandler",
            "com/retromod/shim/api/forge/embedded/WailaShim$ConfigHandlerCompat"
        );

        // Old: IWailaConfigHandler.getConfig(key) -> boolean
        // New: Jade config system
        transformer.registerMethodRedirect(
            "mcp/mobius/waila/api/IWailaConfigHandler",
            "getConfig",
            "(Ljava/lang/String;)Z",
            "com/retromod/shim/api/forge/embedded/WailaShim",
            "getConfig",
            "(Ljava/lang/Object;Ljava/lang/String;)Z"
        );

        // ============================================================
        // ENTITY DATA PROVIDER (for looking at entities)
        // ============================================================

        // Old: mcp.mobius.waila.api.IWailaEntityProvider
        // New: snownee.jade.api.IEntityComponentProvider (Jade)
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaEntityProvider",
            "snownee/jade/api/IEntityComponentProvider"
        );

        // Old: mcp.mobius.waila.api.IWailaEntityAccessor
        // New: snownee.jade.api.EntityAccessor (Jade)
        transformer.registerClassRedirect(
            "mcp/mobius/waila/api/IWailaEntityAccessor",
            "snownee/jade/api/EntityAccessor"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.WailaShim"
        };
    }
}
