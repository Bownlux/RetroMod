/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 */
package com.retromod.shim.forge;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;

/**
 * The Flattening - Minecraft 1.13 completely overhauled the block/item ID system,
 * registry system, and command system. This is the most complex shim in RetroMod.
 */
public class Forge_1_12_2_to_1_13_2 implements VersionShim {

    @Override public String getShimName() { return "Forge 1.12.2 to 1.13.2"; }
    @Override public String getSourceVersion() { return "1.12.2"; }
    @Override public String getTargetVersion() { return "1.13.2"; }
    @Override public String getModLoaderType() { return "forge"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // ============================================================
        // THE FLATTENING - Block/Item registry overhaul
        // ============================================================

        // Init classes moved
        transformer.registerClassRedirect(
            "net/minecraft/init/Blocks",
            "net/minecraft/block/Blocks"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/Items",
            "net/minecraft/item/Items"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/SoundEvents",
            "net/minecraft/util/SoundEvents"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/Enchantments",
            "net/minecraft/enchantment/Enchantments"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/Biomes",
            "net/minecraft/world/biome/Biomes"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/PotionTypes",
            "net/minecraft/potion/Potions"
        );
        transformer.registerClassRedirect(
            "net/minecraft/init/MobEffects",
            "net/minecraft/potion/Effects"
        );

        // ============================================================
        // Block state system overhaul
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/block/state/IBlockState",
            "net/minecraft/block/BlockState"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/state/BlockStateContainer",
            "net/minecraft/state/StateContainer"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyInteger",
            "net/minecraft/state/IntegerProperty"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyBool",
            "net/minecraft/state/BooleanProperty"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyEnum",
            "net/minecraft/state/EnumProperty"
        );
        transformer.registerClassRedirect(
            "net/minecraft/block/properties/PropertyDirection",
            "net/minecraft/state/DirectionProperty"
        );

        // ============================================================
        // Entity renames
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayer",
            "net/minecraft/entity/player/PlayerEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/player/EntityPlayerMP",
            "net/minecraft/entity/player/ServerPlayerEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityLivingBase",
            "net/minecraft/entity/LivingEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityCreature",
            "net/minecraft/entity/CreatureEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/monster/EntityMob",
            "net/minecraft/entity/monster/MonsterEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/passive/EntityAnimal",
            "net/minecraft/entity/passive/AnimalEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/item/EntityItem",
            "net/minecraft/entity/item/ItemEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/projectile/EntityArrow",
            "net/minecraft/entity/projectile/ArrowEntity"
        );
        transformer.registerClassRedirect(
            "net/minecraft/entity/EntityLiving",
            "net/minecraft/entity/MobEntity"
        );

        // ============================================================
        // NBT renames
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagCompound",
            "net/minecraft/nbt/CompoundNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagList",
            "net/minecraft/nbt/ListNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagString",
            "net/minecraft/nbt/StringNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagInt",
            "net/minecraft/nbt/IntNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTTagByte",
            "net/minecraft/nbt/ByteNBT"
        );
        transformer.registerClassRedirect(
            "net/minecraft/nbt/NBTBase",
            "net/minecraft/nbt/INBT"
        );

        // ============================================================
        // GUI / Screen renames
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiScreen",
            "net/minecraft/client/gui/screen/Screen"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiButton",
            "net/minecraft/client/gui/widget/button/Button"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiTextField",
            "net/minecraft/client/gui/widget/TextFieldWidget"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiContainer",
            "net/minecraft/client/gui/screen/inventory/ContainerScreen"
        );
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/inventory/GuiChest",
            "net/minecraft/client/gui/screen/inventory/ChestScreen"
        );

        // ============================================================
        // Container / Inventory renames
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/inventory/Container",
            "net/minecraft/inventory/container/Container"
        );
        transformer.registerClassRedirect(
            "net/minecraft/inventory/Slot",
            "net/minecraft/inventory/container/Slot"
        );
        transformer.registerClassRedirect(
            "net/minecraft/creativetab/CreativeTabs",
            "net/minecraft/item/ItemGroup"
        );

        // ============================================================
        // World renames
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/world/WorldServer",
            "net/minecraft/world/server/ServerWorld"
        );
        transformer.registerClassRedirect(
            "net/minecraft/world/chunk/Chunk",
            "net/minecraft/world/chunk/Chunk"
        );

        // ============================================================
        // Network renames
        // ============================================================
        transformer.registerClassRedirect(
            "net/minecraft/network/play/server/SPacketChat",
            "net/minecraft/network/play/server/SChatPacket"
        );

        // ============================================================
        // Forge API changes
        // ============================================================
        transformer.registerMethodRedirect(
            "net/minecraftforge/fml/common/registry/GameRegistry", "register",
            "(Lnet/minecraft/item/Item;)V",
            "com/retromod/shim/forge/embedded/FlatteningShim", "registerItem",
            "(Ljava/lang/Object;)V"
        );
        transformer.registerMethodRedirect(
            "net/minecraftforge/fml/common/registry/GameRegistry", "register",
            "(Lnet/minecraft/block/Block;)V",
            "com/retromod/shim/forge/embedded/FlatteningShim", "registerBlock",
            "(Ljava/lang/Object;)V"
        );

        // Mod annotation moved
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Mod",
            "net/minecraftforge/fml/common/Mod"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/Mod$EventHandler",
            "net/minecraftforge/fml/common/Mod$EventBusSubscriber"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLInitializationEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLPreInitializationEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLCommonSetupEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLPostInitializationEvent",
            "net/minecraftforge/fml/event/lifecycle/FMLLoadCompleteEvent"
        );
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/event/FMLServerStartingEvent",
            "net/minecraftforge/fml/event/server/FMLServerStartingEvent"
        );

        // Proxy system removed in 1.13+
        transformer.registerClassRedirect(
            "net/minecraftforge/fml/common/SidedProxy",
            "com/retromod/shim/forge/embedded/SidedProxyShim"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            "com.retromod.shim.forge.embedded.FlatteningShim",
            "com.retromod.shim.forge.embedded.SidedProxyShim"
        };
    }
}
