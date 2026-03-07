/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Baubles API -> Curios API Compatibility Shim
 */
package com.retromod.shim.api.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * Baubles API compatibility shim.
 *
 * Baubles was the original accessory/trinket slot mod for Forge (1.7.10 - 1.12.2).
 * It was never updated past 1.12.2 and was effectively replaced by Curios API
 * (by C4 / TheIllusiveC4) starting in 1.13+.
 *
 * This shim redirects Baubles API calls to their Curios equivalents so that
 * old mods relying on Baubles can work with the modern Curios system.
 *
 * Key mappings:
 * - BaublesApi.getBaubles(player) -> CuriosApi.getCuriosInventory(player)
 * - IBauble interface -> ICurio interface
 * - IBaublesItemHandler -> ICuriosItemHandler / IItemHandlerModifiable
 * - BaubleType enum -> Curios slot type strings ("ring", "amulet", "belt", etc.)
 */
public class BaublesApiShim implements VersionShim {

    @Override
    public String getShimName() {
        return "Baubles -> Curios API Compatibility";
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
        // CORE API CLASS REDIRECTS
        // ============================================================

        // Old: baubles.api.BaublesApi (main entry point)
        // New: top.theillusivec4.curios.api.CuriosApi
        transformer.registerClassRedirect(
            "baubles/api/BaublesApi",
            "com/retromod/shim/api/forge/embedded/BaublesShim"
        );

        // Old: BaublesApi.getBaubles(player) returns IBaublesItemHandler
        // New: CuriosApi.getCuriosInventory(player) returns LazyOptional<ICuriosItemHandler>
        transformer.registerMethodRedirect(
            "baubles/api/BaublesApi",
            "getBaubles",
            "(Lnet/minecraft/entity/player/EntityPlayer;)Lbaubles/api/cap/IBaublesItemHandler;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getBaubles",
            "(Lnet/minecraft/world/entity/player/Player;)Ljava/lang/Object;"
        );

        // Old: BaublesApi.getBaublesHandler(player) - alternate accessor
        // New: redirect to CuriosApi.getCuriosInventory(player)
        transformer.registerMethodRedirect(
            "baubles/api/BaublesApi",
            "getBaublesHandler",
            "(Lnet/minecraft/entity/player/EntityPlayer;)Lbaubles/api/cap/IBaublesItemHandler;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getBaublesHandler",
            "(Lnet/minecraft/world/entity/player/Player;)Ljava/lang/Object;"
        );

        // ============================================================
        // IBAUBLE INTERFACE -> ICURIO INTERFACE
        // ============================================================

        // Old: baubles.api.IBauble (main item interface)
        // New: top.theillusivec4.curios.api.type.capability.ICurio
        transformer.registerClassRedirect(
            "baubles/api/IBauble",
            "top/theillusivec4/curios/api/type/capability/ICurio"
        );

        // Old: IBauble.onWornTick(stack, player) - called every tick when worn
        // New: ICurio.curioTick(slotContext)
        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "onWornTick",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "onWornTick",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"
        );

        // Old: IBauble.onEquipped(stack, player) - called when equipped
        // New: ICurio.onEquip(slotContext, prevStack)
        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "onEquipped",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "onEquipped",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"
        );

        // Old: IBauble.onUnequipped(stack, player) - called when removed
        // New: ICurio.onUnequip(slotContext, newStack)
        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "onUnequipped",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "onUnequipped",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)V"
        );

        // Old: IBauble.canEquip(stack, player) - check if can be equipped
        // New: ICurio.canEquip(slotContext)
        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "canEquip",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "canEquip",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"
        );

        // Old: IBauble.canUnequip(stack, player)
        // New: ICurio.canUnequip(slotContext)
        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "canUnequip",
            "(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EntityLivingBase;)Z",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "canUnequip",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"
        );

        // Old: IBauble.getBaubleType(stack) -> BaubleType enum
        // New: mapped through ICurioItem slot type system
        transformer.registerMethodRedirect(
            "baubles/api/IBauble",
            "getBaubleType",
            "(Lnet/minecraft/item/ItemStack;)Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getBaubleType",
            "(Ljava/lang/Object;Lnet/minecraft/world/item/ItemStack;)Ljava/lang/Object;"
        );

        // ============================================================
        // BAUBLETYPE ENUM -> CURIOS SLOT IDENTIFIERS
        // ============================================================

        // Old: baubles.api.BaubleType enum (AMULET, RING, BELT, TRINKET, HEAD, BODY, CHARM)
        // New: Curios uses string-based slot types ("necklace", "ring", "belt", etc.)
        transformer.registerClassRedirect(
            "baubles/api/BaubleType",
            "com/retromod/shim/api/forge/embedded/BaublesShim$BaubleTypeCompat"
        );

        // BaubleType.AMULET -> "necklace" slot in Curios
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "AMULET",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "AMULET",
            "Ljava/lang/Object;"
        );

        // BaubleType.RING -> "ring" slot in Curios
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "RING",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "RING",
            "Ljava/lang/Object;"
        );

        // BaubleType.BELT -> "belt" slot in Curios
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "BELT",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "BELT",
            "Ljava/lang/Object;"
        );

        // BaubleType.TRINKET -> "charm" slot in Curios (closest equivalent)
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "TRINKET",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "TRINKET",
            "Ljava/lang/Object;"
        );

        // BaubleType.HEAD -> "head" slot in Curios
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "HEAD",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "HEAD",
            "Ljava/lang/Object;"
        );

        // BaubleType.BODY -> "body" slot in Curios
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "BODY",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "BODY",
            "Ljava/lang/Object;"
        );

        // BaubleType.CHARM -> "charm" slot in Curios
        transformer.registerFieldRedirect(
            "baubles/api/BaubleType",
            "CHARM",
            "Lbaubles/api/BaubleType;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "CHARM",
            "Ljava/lang/Object;"
        );

        // ============================================================
        // CAPABILITY / ITEM HANDLER REDIRECTS
        // ============================================================

        // Old: baubles.api.cap.IBaublesItemHandler
        // New: top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler
        transformer.registerClassRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "com/retromod/shim/api/forge/embedded/BaublesShim$BaublesItemHandlerCompat"
        );

        // Old: IBaublesItemHandler.getStackInSlot(slot)
        // New: ICuriosItemHandler.getStackInSlot(slotType, index)
        transformer.registerMethodRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "getStackInSlot",
            "(I)Lnet/minecraft/item/ItemStack;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getStackInSlot",
            "(Ljava/lang/Object;I)Lnet/minecraft/world/item/ItemStack;"
        );

        // Old: IBaublesItemHandler.setStackInSlot(slot, stack)
        // New: mapped through Curios inventory system
        transformer.registerMethodRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "setStackInSlot",
            "(ILnet/minecraft/item/ItemStack;)V",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "setStackInSlot",
            "(Ljava/lang/Object;ILnet/minecraft/world/item/ItemStack;)V"
        );

        // Old: IBaublesItemHandler.getSlots() - number of bauble slots
        // New: mapped through Curios slot count
        transformer.registerMethodRedirect(
            "baubles/api/cap/IBaublesItemHandler",
            "getSlots",
            "()I",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "getSlots",
            "(Ljava/lang/Object;)I"
        );

        // ============================================================
        // BAUBLE CAPABILITY PROVIDER
        // ============================================================

        // Old: baubles.api.cap.BaublesCapabilities.CAPABILITY_BAUBLES
        // New: CuriosCapability (registered via AttachCapabilitiesEvent or CuriosApi)
        transformer.registerFieldRedirect(
            "baubles/api/cap/BaublesCapabilities",
            "CAPABILITY_BAUBLES",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "CAPABILITY_CURIOS",
            "Ljava/lang/Object;"
        );

        // Old: baubles.api.cap.BaublesCapabilities.CAPABILITY_ITEM_BAUBLE
        // New: CuriosCapability.ITEM
        transformer.registerFieldRedirect(
            "baubles/api/cap/BaublesCapabilities",
            "CAPABILITY_ITEM_BAUBLE",
            "Lnet/minecraftforge/common/capabilities/Capability;",
            "com/retromod/shim/api/forge/embedded/BaublesShim",
            "CAPABILITY_CURIO_ITEM",
            "Ljava/lang/Object;"
        );

        // ============================================================
        // RENDER HANDLER REDIRECT
        // ============================================================

        // Old: baubles.api.render.IRenderBauble - custom rendering for equipped baubles
        // New: ICurioRenderer in Curios API
        transformer.registerClassRedirect(
            "baubles/api/render/IRenderBauble",
            "top/theillusivec4/curios/api/client/ICurioRenderer"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.api.forge.embedded.BaublesShim"
        };
    }
}
