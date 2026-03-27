/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Compatibility shim for Fabric mods built for 1.16.5 to run on 1.17.
 * Handles Java 16 transition, tag system restructuring, and ore generation changes.
 */
public class Fabric_1_16_5_to_1_17 implements VersionShim {

    @Override public String getShimName() { return "Fabric 1.16.5 to 1.17"; }
    @Override public String getSourceVersion() { return "1.16.5"; }
    @Override public String getTargetVersion() { return "1.17"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Tag system restructured
        transformer.registerClassRedirect(
            "net/minecraft/tag/ServerTagManagerHolder",
            "com/retromod/shim/fabric/embedded/TagShim"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/BlockTags",
            "net/minecraft/registry/tag/BlockTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/ItemTags",
            "net/minecraft/registry/tag/ItemTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/EntityTypeTags",
            "net/minecraft/registry/tag/EntityTypeTags"
        );
        transformer.registerClassRedirect(
            "net/minecraft/tag/FluidTags",
            "net/minecraft/registry/tag/FluidTags"
        );
        // Ore feature config changed
        transformer.registerMethodRedirect(
            "net/minecraft/world/gen/feature/OreFeatureConfig", "<init>",
            "(Lnet/minecraft/structure/rule/RuleTest;Lnet/minecraft/block/BlockState;I)V",
            "com/retromod/shim/fabric/embedded/OreFeatureShim", "create",
            "(Ljava/lang/Object;Ljava/lang/Object;I)Ljava/lang/Object;"
        );

        // ============================================================
        // MCP → Mojang method renames (1.16.5 → 1.17)
        // The biggest rename wave: these are the "alphabet of modding"
        // methods that 99% of mods use. By 1.17, Mojang names are used.
        // ============================================================

        // Entity.onUpdate() → tick()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "onUpdate",
            "()V",
            "net/minecraft/world/entity/Entity", "tick",
            "()V"
        );
        // Entity.attackEntityFrom() → hurt()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/Entity", "attackEntityFrom",
            "(Lnet/minecraft/world/damagesource/DamageSource;F)Z",
            "net/minecraft/world/entity/Entity", "hurt",
            "(Lnet/minecraft/world/damagesource/DamageSource;F)Z"
        );
        // LivingEntity.onDeath() → die()
        transformer.registerMethodRedirect(
            "net/minecraft/world/entity/LivingEntity", "onDeath",
            "(Lnet/minecraft/world/damagesource/DamageSource;)V",
            "net/minecraft/world/entity/LivingEntity", "die",
            "(Lnet/minecraft/world/damagesource/DamageSource;)V"
        );
        // Level.setBlockState() → setBlock() (3-param version: BlockPos, BlockState, int flags)
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/Level", "setBlockState",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z",
            "net/minecraft/world/level/Level", "setBlock",
            "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;I)Z"
        );
        // Level.setBlockToAir() → removeBlock()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/Level", "setBlockToAir",
            "(Lnet/minecraft/core/BlockPos;)Z",
            "net/minecraft/world/level/Level", "removeBlock",
            "(Lnet/minecraft/core/BlockPos;Z)Z"
        );
        // Block.onBlockPlacedBy() → setPlacedBy()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/Block", "onBlockPlacedBy",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V",
            "net/minecraft/world/level/block/Block", "setPlacedBy",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/item/ItemStack;)V"
        );
        // Item.onItemRightClick() → use()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "onItemRightClick",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;",
            "net/minecraft/world/item/Item", "use",
            "(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;)Lnet/minecraft/world/InteractionResultHolder;"
        );
        // Item.onItemUseFinish() → finishUsingItem()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "onItemUseFinish",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;",
            "net/minecraft/world/item/Item", "finishUsingItem",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/LivingEntity;)Lnet/minecraft/world/item/ItemStack;"
        );
        // Item.onUpdate() → inventoryTick()
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "onUpdate",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V",
            "net/minecraft/world/item/Item", "inventoryTick",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;IZ)V"
        );
        // Item.addInformation() → appendHoverText()
        // NOTE: descriptor needs verification - Component vs Text type depends on mappings
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "addInformation",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V",
            "net/minecraft/world/item/Item", "appendHoverText",
            "(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Ljava/util/List;Lnet/minecraft/world/item/TooltipFlag;)V"
        );
        // BlockEntity.readFromNBT() → load()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/BlockEntity", "readFromNBT",
            "(Lnet/minecraft/nbt/CompoundTag;)V",
            "net/minecraft/world/level/block/entity/BlockEntity", "load",
            "(Lnet/minecraft/nbt/CompoundTag;)V"
        );
        // BlockEntity.writeToNBT() → save()
        transformer.registerMethodRedirect(
            "net/minecraft/world/level/block/entity/BlockEntity", "writeToNBT",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;",
            "net/minecraft/world/level/block/entity/BlockEntity", "save",
            "(Lnet/minecraft/nbt/CompoundTag;)Lnet/minecraft/nbt/CompoundTag;"
        );
        // Screen.drawScreen() → render()
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/Screen", "drawScreen",
            "(IIF)V",
            "net/minecraft/client/gui/screens/Screen", "render",
            "(IIF)V"
        );
        // Screen.initGui() → init()
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/Screen", "initGui",
            "()V",
            "net/minecraft/client/gui/screens/Screen", "init",
            "()V"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[0];
    }
}
