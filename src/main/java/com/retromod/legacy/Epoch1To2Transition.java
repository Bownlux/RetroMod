/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 * 
 * EPOCH 1 → 2: Legacy (1.8-1.12) to Flattening (1.13)
 * This is the MOST COMPLEX transition - "The Flattening" changed everything
 */
package com.retromod.legacy;

public class Epoch1To2Transition extends BaseEpochTransition {
    
    @Override public String name() { return "Legacy 1.8-1.12 → Flattening 1.13"; }
    @Override public int sourceEpoch() { return 1; }
    @Override public int targetEpoch() { return 2; }
    
    public Epoch1To2Transition() {
        // Block class hierarchy
        addClass("net/minecraft/block/Block", "net/minecraft/block/Block");
        addClass("net/minecraft/block/BlockContainer", "net/minecraft/block/BlockWithEntity");
        addClass("net/minecraft/block/BlockBush", "net/minecraft/block/PlantBlock");
        addClass("net/minecraft/block/BlockCrops", "net/minecraft/block/CropBlock");
        addClass("net/minecraft/block/BlockDoor", "net/minecraft/block/DoorBlock");
        addClass("net/minecraft/block/BlockFence", "net/minecraft/block/FenceBlock");
        addClass("net/minecraft/block/BlockFenceGate", "net/minecraft/block/FenceGateBlock");
        addClass("net/minecraft/block/BlockFlower", "net/minecraft/block/FlowerBlock");
        addClass("net/minecraft/block/BlockGlass", "net/minecraft/block/GlassBlock");
        addClass("net/minecraft/block/BlockGrass", "net/minecraft/block/GrassBlock");
        addClass("net/minecraft/block/BlockHopper", "net/minecraft/block/HopperBlock");
        addClass("net/minecraft/block/BlockLeaves", "net/minecraft/block/LeavesBlock");
        addClass("net/minecraft/block/BlockLog", "net/minecraft/block/PillarBlock");
        addClass("net/minecraft/block/BlockOre", "net/minecraft/block/OreBlock");
        addClass("net/minecraft/block/BlockPane", "net/minecraft/block/PaneBlock");
        addClass("net/minecraft/block/BlockPressurePlate", "net/minecraft/block/PressurePlateBlock");
        addClass("net/minecraft/block/BlockSlab", "net/minecraft/block/SlabBlock");
        addClass("net/minecraft/block/BlockStairs", "net/minecraft/block/StairsBlock");
        addClass("net/minecraft/block/BlockTorch", "net/minecraft/block/TorchBlock");
        addClass("net/minecraft/block/BlockWall", "net/minecraft/block/WallBlock");
        
        // Item classes
        addClass("net/minecraft/item/Item", "net/minecraft/item/Item");
        addClass("net/minecraft/item/ItemBlock", "net/minecraft/item/BlockItem");
        addClass("net/minecraft/item/ItemFood", "net/minecraft/item/Item");
        addClass("net/minecraft/item/ItemTool", "net/minecraft/item/ToolItem");
        addClass("net/minecraft/item/ItemSword", "net/minecraft/item/SwordItem");
        addClass("net/minecraft/item/ItemPickaxe", "net/minecraft/item/PickaxeItem");
        addClass("net/minecraft/item/ItemAxe", "net/minecraft/item/AxeItem");
        addClass("net/minecraft/item/ItemSpade", "net/minecraft/item/ShovelItem");
        addClass("net/minecraft/item/ItemHoe", "net/minecraft/item/HoeItem");
        addClass("net/minecraft/item/ItemArmor", "net/minecraft/item/ArmorItem");
        addClass("net/minecraft/item/ItemBow", "net/minecraft/item/BowItem");
        addClass("net/minecraft/item/ItemBucket", "net/minecraft/item/BucketItem");
        
        // Entity classes
        addClass("net/minecraft/entity/Entity", "net/minecraft/entity/Entity");
        addClass("net/minecraft/entity/EntityLiving", "net/minecraft/entity/LivingEntity");
        addClass("net/minecraft/entity/EntityLivingBase", "net/minecraft/entity/LivingEntity");
        addClass("net/minecraft/entity/EntityCreature", "net/minecraft/entity/mob/PathAwareEntity");
        addClass("net/minecraft/entity/EntityAgeable", "net/minecraft/entity/passive/PassiveEntity");
        addClass("net/minecraft/entity/monster/EntityMob", "net/minecraft/entity/mob/HostileEntity");
        addClass("net/minecraft/entity/monster/EntityZombie", "net/minecraft/entity/mob/ZombieEntity");
        addClass("net/minecraft/entity/monster/EntitySkeleton", "net/minecraft/entity/mob/SkeletonEntity");
        addClass("net/minecraft/entity/monster/EntityCreeper", "net/minecraft/entity/mob/CreeperEntity");
        addClass("net/minecraft/entity/passive/EntityPig", "net/minecraft/entity/passive/PigEntity");
        addClass("net/minecraft/entity/passive/EntityCow", "net/minecraft/entity/passive/CowEntity");
        addClass("net/minecraft/entity/passive/EntitySheep", "net/minecraft/entity/passive/SheepEntity");
        addClass("net/minecraft/entity/player/EntityPlayer", "net/minecraft/entity/player/PlayerEntity");
        addClass("net/minecraft/entity/player/EntityPlayerMP", "net/minecraft/server/network/ServerPlayerEntity");
        
        // Tile Entity → Block Entity
        addClass("net/minecraft/tileentity/TileEntity", "net/minecraft/block/entity/BlockEntity");
        addClass("net/minecraft/tileentity/TileEntityChest", "net/minecraft/block/entity/ChestBlockEntity");
        addClass("net/minecraft/tileentity/TileEntityFurnace", "net/minecraft/block/entity/FurnaceBlockEntity");
        addClass("net/minecraft/tileentity/TileEntityHopper", "net/minecraft/block/entity/HopperBlockEntity");
        addClass("net/minecraft/tileentity/TileEntitySign", "net/minecraft/block/entity/SignBlockEntity");
        addClass("net/minecraft/tileentity/TileEntityBeacon", "net/minecraft/block/entity/BeaconBlockEntity");
        addClass("net/minecraft/tileentity/TileEntityBanner", "net/minecraft/block/entity/BannerBlockEntity");
        
        // World classes
        addClass("net/minecraft/world/World", "net/minecraft/world/World");
        addClass("net/minecraft/world/WorldServer", "net/minecraft/server/world/ServerWorld");
        addClass("net/minecraft/world/WorldProvider", "net/minecraft/world/dimension/Dimension");
        addClass("net/minecraft/world/chunk/Chunk", "net/minecraft/world/chunk/WorldChunk");
        addClass("net/minecraft/world/biome/Biome", "net/minecraft/world/biome/Biome");
        
        // Inventory & GUI
        addClass("net/minecraft/inventory/Container", "net/minecraft/screen/ScreenHandler");
        addClass("net/minecraft/inventory/Slot", "net/minecraft/screen/slot/Slot");
        addClass("net/minecraft/inventory/IInventory", "net/minecraft/inventory/Inventory");
        addClass("net/minecraft/client/gui/GuiScreen", "net/minecraft/client/gui/screen/Screen");
        
        // Utility classes
        addClass("net/minecraft/util/BlockPos", "net/minecraft/util/math/BlockPos");
        addClass("net/minecraft/util/Vec3", "net/minecraft/util/math/Vec3d");
        addClass("net/minecraft/util/Vec3i", "net/minecraft/util/math/Vec3i");
        addClass("net/minecraft/util/EnumFacing", "net/minecraft/util/math/Direction");
        addClass("net/minecraft/util/EnumHand", "net/minecraft/util/Hand");
        addClass("net/minecraft/util/ResourceLocation", "net/minecraft/util/Identifier");
        addClass("net/minecraft/util/text/ITextComponent", "net/minecraft/text/Text");
        addClass("net/minecraft/util/text/TextComponentString", "net/minecraft/text/LiteralText");
        addClass("net/minecraft/util/text/TextComponentTranslation", "net/minecraft/text/TranslatableText");
        addClass("net/minecraft/util/DamageSource", "net/minecraft/entity/damage/DamageSource");
        addClass("net/minecraft/util/NonNullList", "net/minecraft/util/collection/DefaultedList");
        
        // NBT classes
        addClass("net/minecraft/nbt/NBTTagCompound", "net/minecraft/nbt/NbtCompound");
        addClass("net/minecraft/nbt/NBTTagList", "net/minecraft/nbt/NbtList");
        addClass("net/minecraft/nbt/NBTTagString", "net/minecraft/nbt/NbtString");
        addClass("net/minecraft/nbt/NBTTagInt", "net/minecraft/nbt/NbtInt");
        addClass("net/minecraft/nbt/NBTBase", "net/minecraft/nbt/NbtElement");
        
        // Forge-specific → Virtual Mod Loader
        addClass("net/minecraftforge/fml/common/Mod", "com/retromod/virtual/VirtualMod");
        addClass("net/minecraftforge/fml/common/event/FMLPreInitializationEvent", 
                 "com/retromod/virtual/VirtualPreInitEvent");
        addClass("net/minecraftforge/fml/common/event/FMLInitializationEvent",
                 "com/retromod/virtual/VirtualInitEvent");
        addClass("net/minecraftforge/fml/common/event/FMLPostInitializationEvent",
                 "com/retromod/virtual/VirtualPostInitEvent");
        addClass("net/minecraftforge/fml/common/eventhandler/SubscribeEvent",
                 "com/retromod/virtual/VirtualSubscribeEvent");
        addClass("net/minecraftforge/fml/common/registry/GameRegistry",
                 "com/retromod/virtual/VirtualGameRegistry");
        addClass("net/minecraftforge/common/MinecraftForge",
                 "com/retromod/virtual/VirtualMinecraftForge");
        addClass("net/minecraftforge/client/model/ModelLoader",
                 "com/retromod/virtual/VirtualModelLoader");
        addClass("net/minecraftforge/oredict/OreDictionary",
                 "com/retromod/virtual/VirtualOreDictionary");
        
        // Shim classes
        addShim("com.retromod.virtual.VirtualMod");
        addShim("com.retromod.virtual.VirtualMinecraftForge");
        addShim("com.retromod.virtual.VirtualGameRegistry");
        addShim("com.retromod.virtual.VirtualOreDictionary");
        addShim("com.retromod.virtual.LegacyBlockShim");
        addShim("com.retromod.virtual.LegacyItemShim");
    }
}
