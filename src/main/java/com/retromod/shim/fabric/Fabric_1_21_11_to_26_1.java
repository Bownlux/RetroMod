/*
 * RetroMod - Backwards Compatibility Layer for Minecraft Mods
 * Copyright (c) 2026 Bownlux
 *
 * Based on actual Fabric API changes documented at:
 * https://fabricmc.net/2026/03/14/261.html
 * https://docs.fabricmc.net/develop/porting/26.1/fabric-api
 *
 * This is the biggest shim in the chain — MC 26.1 removed ALL obfuscation
 * and Fabric API renamed hundreds of classes/methods to match Mojang's names.
 *
 * NOTE: Vanilla MC class moves (587 entries) are handled separately by
 * mojang-class-moves-26.1.tsv loaded via IntermediaryToMojangMapper.
 * This shim focuses on FABRIC API renames and method signature changes.
 */
package com.retromod.shim.fabric;

import com.retromod.core.RetroModTransformer;
import com.retromod.core.VersionShim;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Compatibility shim for Fabric mods built for 1.21.11 to run on 26.1+.
 *
 * MC 26.1 is the first unobfuscated Minecraft version. Major changes:
 * - ALL code obfuscation removed (Mojang official names used directly)
 * - Intermediary names are dead — class_XXXX / method_XXXX / field_XXXX gone
 * - Yarn mappings discontinued
 * - Java 25 required (class file major version 69)
 * - Fabric API renamed hundreds of classes to match Mojang naming
 * - Many Fabric API packages reorganized
 * - Several deprecated APIs removed
 *
 * Vanilla MC class/package moves are handled by mojang-class-moves-26.1.tsv
 * and intermediary→Mojang remapping (87K entries in intermediary-to-mojang.tsv).
 *
 * This shim handles Fabric API-specific renames and method changes.
 */
public class Fabric_1_21_11_to_26_1 implements VersionShim {

    @Override
    public String getShimName() {
        return "Fabric 1.21.11 to 26.1";
    }

    @Override
    public String getSourceVersion() {
        return "1.21.11";
    }

    @Override
    public String getTargetVersion() {
        return "26.1";
    }

    @Override
    public String getModLoaderType() {
        return "fabric";
    }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {

        // ============================================================
        // FABRIC API PACKAGE RENAMES
        // Fabric API 26.1 renamed many packages to match Mojang naming
        // ============================================================

        // --- Networking: S2C/C2S → Clientbound/Serverbound ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/C2SConfigurationChannelEvents",
            "net/fabricmc/fabric/api/networking/v1/ServerboundConfigurationChannelEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/C2SPlayChannelEvents",
            "net/fabricmc/fabric/api/networking/v1/ServerboundPlayChannelEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/S2CConfigurationChannelEvents",
            "net/fabricmc/fabric/api/networking/v1/ClientboundConfigurationChannelEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/S2CPlayChannelEvents",
            "net/fabricmc/fabric/api/networking/v1/ClientboundPlayChannelEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/PacketByteBufs",
            "net/fabricmc/fabric/api/networking/v1/FriendlyByteBufs"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/FabricServerConfigurationNetworkHandler",
            "net/fabricmc/fabric/api/networking/v1/FabricServerConfigurationPacketListenerImpl"
        );

        // --- World → Level renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientWorldEvents",
            "net/fabricmc/fabric/api/client/event/lifecycle/v1/ClientLevelEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerWorldEvents",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerLevelEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderEvents",
            "net/fabricmc/fabric/api/client/rendering/v1/LevelRenderEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/WorldRenderContext",
            "net/fabricmc/fabric/api/client/rendering/v1/LevelRenderContext"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerEntityWorldChangeEvents",
            "net/fabricmc/fabric/api/event/lifecycle/v1/ServerEntityLevelChangeEvents"
        );

        // --- ScreenHandler → Menu renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/screenhandler/v1/ExtendedScreenHandlerFactory",
            "net/fabricmc/fabric/api/menu/v1/ExtendedMenuProvider"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/screenhandler/v1/ExtendedScreenHandlerType",
            "net/fabricmc/fabric/api/menu/v1/ExtendedMenuType"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/screenhandler/v1/FabricScreenHandlerFactory",
            "net/fabricmc/fabric/api/menu/v1/FabricMenuProvider"
        );

        // --- ItemGroup → CreativeTab renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/itemgroup/v1/ItemGroupEvents",
            "net/fabricmc/fabric/api/creativetab/v1/CreativeModeTabEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/itemgroup/v1/FabricItemGroup",
            "net/fabricmc/fabric/api/creativetab/v1/FabricCreativeModeTab"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/itemgroup/v1/FabricItemGroupEntries",
            "net/fabricmc/fabric/api/creativetab/v1/FabricCreativeModeTabOutput"
        );
        // Client-side itemgroup package
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/itemgroup/v1/ItemGroupEvents",
            "net/fabricmc/fabric/api/client/creativetab/v1/CreativeModeTabEvents"
        );

        // --- Rendering renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/BlockRenderLayerMap",
            "net/fabricmc/fabric/api/client/rendering/v1/ChunkSectionLayerMap"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/EntityModelLayerRegistry",
            "net/fabricmc/fabric/api/client/rendering/v1/ModelLayerRegistry"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/LivingEntityFeatureRendererRegistrationCallback",
            "net/fabricmc/fabric/api/client/rendering/v1/LivingEntityRenderLayerRegistrationCallback"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/DrawItemStackOverlayCallback",
            "net/fabricmc/fabric/api/client/rendering/v1/RenderItemDecorationsCallback"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/model/BakedModelManager",
            "net/fabricmc/fabric/api/client/model/FabricModelManager"
        );

        // --- Registry renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/event/registry/FuelRegistryEvents",
            "net/fabricmc/fabric/api/event/registry/FuelValueEvents"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/registry/CompostingChanceRegistry",
            "net/fabricmc/fabric/api/registry/CompostableRegistry"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/registry/SculkSensorFrequencyRegistry",
            "net/fabricmc/fabric/api/registry/VibrationFrequencyRegistry"
        );

        // --- Data generation renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/datagen/v1/FabricDataOutput",
            "net/fabricmc/fabric/api/datagen/v1/FabricPackOutput"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/datagen/v1/provider/FabricBlockLootTableGenerator",
            "net/fabricmc/fabric/api/datagen/v1/provider/FabricBlockLootSubProvider"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/datagen/v1/provider/FabricTagProvider",
            "net/fabricmc/fabric/api/datagen/v1/provider/FabricTagsProvider"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/datagen/v1/provider/FabricLootTableProvider",
            "net/fabricmc/fabric/api/datagen/v1/provider/FabricLootTableSubProvider"
        );

        // --- Entity renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/object/builder/v1/entity/FabricEntityTypeBuilder",
            "net/fabricmc/fabric/api/object/builder/v1/entity/FabricEntityType$Builder"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/entity/FabricTrackedDataRegistry",
            "net/fabricmc/fabric/api/entity/FabricEntityDataRegistry"
        );

        // --- Transfer API renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/transfer/v1/storage/base/FabricReadView",
            "net/fabricmc/fabric/api/transfer/v1/storage/base/FabricValueInput"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/transfer/v1/storage/base/FabricWriteView",
            "net/fabricmc/fabric/api/transfer/v1/storage/base/FabricValueOutput"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/transfer/v1/item/InventoryStorage",
            "net/fabricmc/fabric/api/transfer/v1/item/ContainerStorage"
        );

        // --- KeyBinding → KeyMapping ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/keybinding/v1/KeyBindingHelper",
            "net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper"
        );

        // KeyMapping constructor: String category → KeyMapping.Category record (changed in 1.21.9)
        // Old: new KeyMapping(String, InputConstants.Type, int, String)
        // New: new KeyMapping(String, InputConstants.Type, int, KeyMapping.Category)
        // Uses Mojang names since intermediary→Mojang remapping has already resolved class names.
        // The descriptor is also resolved through classRedirects, so this matches both
        // intermediary and Mojang-named bytecode.
        transformer.registerConstructorRedirect(
            "net/minecraft/client/KeyMapping",
            "(Ljava/lang/String;Lcom/mojang/blaze3d/platform/InputConstants$Type;ILjava/lang/String;)V",
            "com/retromod/shim/fabric/embedded/KeyBindingShim", "create",
            "(Ljava/lang/String;Ljava/lang/Object;ILjava/lang/String;)Ljava/lang/Object;"
        );

        // Button 6-param constructor removed in 26.1.
        // Old: Button(int x, int y, int w, int h, Component text, OnPress onPress)
        // New: Button(int x, int y, int w, int h, Component text, OnPress onPress, CreateNarration narration)
        // For `new Button(...)` calls → redirect to ButtonShim factory:
        transformer.registerConstructorRedirect(
            "net/minecraft/client/gui/components/Button",
            "(IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)V",
            "com/retromod/shim/fabric/embedded/ButtonShim", "create",
            "(IIIILjava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;"
        );
        // For `super(...)` calls in Button subclasses (e.g. ModMenuButtonWidget) →
        // augment descriptor to add DEFAULT_NARRATION as 7th parameter:
        transformer.registerSuperConstructorRedirect(
            "net/minecraft/client/gui/components/Button",
            "(IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;)V",
            "(IIIILnet/minecraft/network/chat/Component;Lnet/minecraft/client/gui/components/Button$OnPress;Lnet/minecraft/client/gui/components/Button$CreateNarration;)V",
            "net/minecraft/client/gui/components/Button", "DEFAULT_NARRATION",
            "Lnet/minecraft/client/gui/components/Button$CreateNarration;"
        );

        // TranslatableContents constructor descriptor change.
        // Old: TranslatableContents(String key) — 1-arg, removed in 26.1
        // New: TranslatableContents(String key, String fallback, Object[] args) — 3-arg
        // We use super constructor redirect to transform the INVOKESPECIAL <init> call
        // by inserting the missing parameters (null fallback, empty args array).
        // This keeps the NEW/DUP/INVOKESPECIAL pattern intact so the verifier is happy
        // — the result IS a real TranslatableContents, not a wrapped MutableComponent.
        transformer.registerSuperConstructorRedirect(
            "net/minecraft/network/chat/contents/TranslatableContents",
            "(Ljava/lang/String;)V",
            "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V"
        );
        transformer.registerSuperConstructorRedirect(
            "net/minecraft/network/chat/contents/TranslatableContents",
            "(Ljava/lang/String;[Ljava/lang/Object;)V",
            "(Ljava/lang/String;Ljava/lang/String;[Ljava/lang/Object;)V"
        );

        // --- Window: getWindow() → handle() ---
        // MC 26.1 renamed getter methods to record-style accessors
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/platform/Window", "getWindow",
            "()J",
            "com/mojang/blaze3d/platform/Window", "handle",
            "()J"
        );

        // --- Screen.render → Screen.extractRenderState ---
        // In 26.1, Screen rendering was split into extract + render phases.
        // Old mods override render(PoseStack/GuiGraphics, int, int, float).
        // New: extractRenderState(GuiGraphicsExtractor, int, int, float)
        // This redirect handles direct method CALLS (not overrides — those need mixin handling).
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/Screen", "render",
            "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            "net/minecraft/client/gui/screens/Screen", "extractRenderState",
            "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        );

        // AbstractContainerScreen.render → extractRenderState
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "render",
            "(Lnet/minecraft/client/gui/GuiGraphics;IIF)V",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "extractRenderState",
            "(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"
        );

        // GuiGraphics class redirect → GuiGraphicsExtractor
        transformer.registerClassRedirect(
            "net/minecraft/client/gui/GuiGraphics",
            "net/minecraft/client/gui/GuiGraphicsExtractor"
        );

        // Tesselator.getBuilder() → Tesselator.begin() — signature changed but name redirect helps
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/vertex/Tesselator", "getBuilder",
            "()Lcom/mojang/blaze3d/vertex/BufferBuilder;",
            "com/mojang/blaze3d/vertex/Tesselator", "begin",
            "(Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/mojang/blaze3d/vertex/VertexFormat;)Lcom/mojang/blaze3d/vertex/BufferBuilder;"
        );

        // Tesselator.end() → Tesselator.clear()
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/vertex/Tesselator", "end",
            "()V",
            "com/mojang/blaze3d/vertex/Tesselator", "clear",
            "()V"
        );

        // VertexConsumer.endVertex() → removed (auto-ends in 26.1, no-op)
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/vertex/VertexConsumer", "endVertex",
            "()V",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "noOpVoid",
            "(Ljava/lang/Object;)V",
            true  // devirtualize
        );

        // --- GUI/Screen renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/SpecialGuiElementRegistry",
            "net/fabricmc/fabric/api/client/rendering/v1/PictureInPictureRendererRegistry"
        );

        // --- Command API renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/command/v2/ClientCommandManager",
            "net/fabricmc/fabric/api/client/command/v2/ClientCommands"
        );

        // --- Brewing renames ---
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/registry/FabricBrewingRecipeRegistryBuilder",
            "net/fabricmc/fabric/api/registry/FabricPotionBrewingBuilder"
        );


        // ============================================================
        // FABRIC API METHOD RENAMES
        // Methods renamed within classes that still exist
        // ============================================================

        // ScreenEvents: render → extract renames
        // The inner interfaces were renamed (AfterRender → AfterExtract, etc.)
        // AND the SAM method name changed (afterRender → afterExtract).
        // We generate synthetic stub interfaces with the OLD method names so that
        // old mod lambdas can be created without NoClassDefFoundError.
        // The callbacks won't actually fire (wrong method name in Event), but the mod loads.
        transformer.registerSyntheticClass(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents$AfterRender",
            generateScreenEventInterface("AfterRender", "afterRender"));
        transformer.registerSyntheticClass(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents$BeforeRender",
            generateScreenEventInterface("BeforeRender", "beforeRender"));
        // Method renames (for static calls to ScreenEvents.beforeRender/afterRender)
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents", "beforeRender",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents", "beforeExtract",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;"
        );
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents", "afterRender",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents", "afterExtract",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;"
        );

        // KeyBindingHelper.registerKeyBinding → KeyMappingHelper.registerKeyMapping
        // Register with OLD owner name AND resolved Mojang descriptor
        // (bytecode has old Yarn owner but descriptors get resolved to Mojang names)
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/keybinding/v1/KeyBindingHelper", "registerKeyBinding",
            "(Lnet/minecraft/client/KeyMapping;)Lnet/minecraft/client/KeyMapping;",
            "net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper", "registerKeyMapping",
            "(Lnet/minecraft/client/KeyMapping;)Lnet/minecraft/client/KeyMapping;"
        );
        // Also register with post-redirect owner name for mods using new Fabric API names
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper", "registerKeyBinding",
            "(Lnet/minecraft/client/KeyMapping;)Lnet/minecraft/client/KeyMapping;",
            "net/fabricmc/fabric/api/client/keymapping/v1/KeyMappingHelper", "registerKeyMapping",
            "(Lnet/minecraft/client/KeyMapping;)Lnet/minecraft/client/KeyMapping;"
        );

        // Screens utility method renames
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/Screens", "getButtons",
            "(Lnet/minecraft/client/gui/screens/Screen;)Ljava/util/List;",
            "net/fabricmc/fabric/api/client/screen/v1/Screens", "getWidgets",
            "(Lnet/minecraft/client/gui/screens/Screen;)Ljava/util/List;"
        );
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/Screens", "getTextRenderer",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/minecraft/client/gui/Font;",
            "net/fabricmc/fabric/api/client/screen/v1/Screens", "getFont",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/minecraft/client/gui/Font;"
        );
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/Screens", "getClient",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/minecraft/client/Minecraft;",
            "net/fabricmc/fabric/api/client/screen/v1/Screens", "getMinecraft",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/minecraft/client/Minecraft;"
        );

        // FabricRegistryBuilder.createSimple() → create()
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/event/registry/FabricRegistryBuilder", "createSimple",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/fabricmc/fabric/api/event/registry/FabricRegistryBuilder;",
            "net/fabricmc/fabric/api/event/registry/FabricRegistryBuilder", "create",
            "(Lnet/minecraft/resources/ResourceKey;)Lnet/fabricmc/fabric/api/event/registry/FabricRegistryBuilder;"
        );

        // Item.Settings → Item.Properties (already a Mojang name, may already work)
        // FabricItem.Settings → FabricItem.Properties
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/item/v1/FabricItem$Settings",
            "net/fabricmc/fabric/api/item/v1/FabricItem$Properties"
        );

        // Networking method renames: createC2SPacket → createServerboundPacket
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking", "createC2SPacket",
            "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/Packet;",
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking", "createServerboundPacket",
            "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/Packet;"
        );
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking", "createS2CPacket",
            "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/Packet;",
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking", "createClientboundPacket",
            "(Lnet/minecraft/resources/Identifier;Lnet/minecraft/network/FriendlyByteBuf;)Lnet/minecraft/network/protocol/Packet;"
        );

        // PlayChannelHandler → PlayPayloadHandler (already shimmed in 1.21.5→1.21.6)
        // But re-register here for mods that skip the 1.21.6 shim
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$PlayChannelHandler",
            "com/retromod/shim/fabric/embedded/HudRenderCallbackShim" // Placeholder — real bridge in FabricNetworkingPolyfill
        );

        // ============================================================
        // DFU (DataFixerUpper) API CHANGES
        // DataResult changed from class to interface in DFU 9.x
        // ============================================================

        // DataResult.get() removed — redirect to polyfill (instance → static)
        // Uses Object types since DFU classes aren't available at compile time
        transformer.registerMethodRedirect(
            "com/mojang/serialization/DataResult", "get",
            "()Lcom/mojang/datafixers/util/Either;",
            "com/retromod/polyfill/minecraft/DataResultPolyfill", "get",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            true  // devirtualize: instance method → static method
        );

        // ============================================================
        // MINECRAFT VANILLA METHOD/FIELD RENAMES (1.21.x → 26.1)
        // These affect mixin accessors and direct field/method references
        // ============================================================

        // AbstractWidget: x/y/width/height became private in 26.1
        // Old mods access fields directly; new MC has getX()/setX(), getY()/setY() etc.
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "x",
            "getX", "()I", "setX", "(I)V"
        );
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "y",
            "getY", "()I", "setY", "(I)V"
        );
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "width",
            "getWidth", "()I", "setWidth", "(I)V"
        );
        transformer.registerFieldAccessorRedirect(
            "net/minecraft/client/gui/components/AbstractWidget", "height",
            "getHeight", "()I", "setHeight", "(I)V"
        );
        // Note: 'active' and 'visible' are still public fields in 26.1, no accessor needed

        // KeyMapping: boundKey → key
        transformer.registerFieldRedirect(
            "net/minecraft/client/KeyMapping", "boundKey",
            "Lcom/mojang/blaze3d/platform/InputConstants$Key;",
            "net/minecraft/client/KeyMapping", "key",
            "Lcom/mojang/blaze3d/platform/InputConstants$Key;"
        );

        // AbstractContainerScreen: findSlot(DD)Slot → getHoveredSlot(DD)Slot
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "findSlot",
            "(DD)Lnet/minecraft/world/inventory/Slot;",
            "net/minecraft/client/gui/screens/inventory/AbstractContainerScreen", "getHoveredSlot",
            "(DD)Lnet/minecraft/world/inventory/Slot;"
        );

        // Item.getDefaultInstance() → safe wrapper that handles "Components not bound yet"
        // In 26.1, item components are data-driven and bound during data pack loading.
        // Old mods that create ItemStacks during class init or early callbacks crash.
        // This devirtualized redirect wraps the call to catch the NPE and return EMPTY.
        transformer.registerMethodRedirect(
            "net/minecraft/world/item/Item", "getDefaultInstance",
            "()Lnet/minecraft/world/item/ItemStack;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "safeGetDefaultInstance",
            "(Ljava/lang/Object;)Ljava/lang/Object;",
            true  // devirtualize: instance method → static method
        );

        // CommandSourceStack.hasPermission(int) removed in 26.1 — permission system reworked.
        // Old int levels (0-4) map to PermissionLevel enum (ALL, MODERATORS, GAMEMASTERS, ADMINS, OWNERS).
        // Bridge uses reflection to check the new PermissionSet system.
        transformer.registerMethodRedirect(
            "net/minecraft/commands/CommandSourceStack", "hasPermission",
            "(I)Z",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "hasPermission",
            "(Ljava/lang/Object;I)Z",
            true  // devirtualize
        );

        // Listener.setGain(float) removed in 26.1 — volume control moved to per-source
        // Dynamic FPS calls this to mute/unmute sounds. No-op redirect prevents crash.
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/audio/Listener", "setGain",
            "(F)V",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "noOp",
            "(Ljava/lang/Object;F)V",
            true  // devirtualize: instance method → static method
        );

        // EntityType.BOAT/CHEST_BOAT split into per-wood types in 26.1
        // Old: EntityType.BOAT → New: EntityType.OAK_BOAT (most common default)
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/EntityType", "BOAT",
            "Lnet/minecraft/world/entity/EntityType;",
            "net/minecraft/world/entity/EntityType", "OAK_BOAT",
            "Lnet/minecraft/world/entity/EntityType;"
        );
        transformer.registerFieldRedirect(
            "net/minecraft/world/entity/EntityType", "CHEST_BOAT",
            "Lnet/minecraft/world/entity/EntityType;",
            "net/minecraft/world/entity/EntityType", "OAK_CHEST_BOAT",
            "Lnet/minecraft/world/entity/EntityType;"
        );

        // ============================================================
        // ITEM TOOLTIP CALLBACK — redirect EVENT field to dummy event
        // ============================================================
        // ItemTooltipCallback.getTooltip changed from 3 to 4 params in 26.1.
        // Old mods register lambdas with 3 params → AbstractMethodError when hovering.
        // Use field-to-method redirect: GETSTATIC EVENT → INVOKESTATIC getDummyTooltipEvent()
        transformer.registerFieldRedirect(
            "net/fabricmc/fabric/api/client/item/v1/ItemTooltipCallback", "EVENT",
            "Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "getDummyTooltipEvent",
            "()Ljava/lang/Object;"
        );

        // ============================================================
        // SCREEN MOUSE EVENTS — redirect broken callbacks to dummy events
        // ============================================================
        // NOTE: We do NOT redirect Event.register() globally — that breaks
        // ALL event registrations because our shim class can't access
        // Fabric's ArrayBackedEvent due to module/classloader restrictions.
        // Instead, we only redirect the specific ScreenMouseEvents methods
        // that return Events with changed callback signatures.

        // ScreenMouseEvents: allowMouseClick/allowMouseRelease API changed
        // Old: allowMouseClick(Screen, double x, double y, int button) -> boolean
        // New: allowMouseClick(Screen, MouseButtonEvent) -> boolean
        // Old mods register lambdas with old signature → AbstractMethodError on every click.
        // Redirect to dummy events that accept registrations but never fire.
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "allowMouseClick",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "dummyAllowMouseClick",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "allowMouseRelease",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "dummyAllowMouseRelease",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        // afterMouseScroll/beforeMouseScroll also changed signature in 26.1
        // Each needs its OWN dummy event typed for the correct listener interface
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "afterMouseScroll",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "dummyAfterMouseScroll",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );
        transformer.registerMethodRedirect(
            "net/fabricmc/fabric/api/client/screen/v1/ScreenMouseEvents", "beforeMouseScroll",
            "(Lnet/minecraft/client/gui/screens/Screen;)Lnet/fabricmc/fabric/api/event/Event;",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "dummyBeforeMouseScroll",
            "(Ljava/lang/Object;)Ljava/lang/Object;"
        );

        // ============================================================
        // FONT TEXT RENDERING — draw/drawShadow removed in 26.1
        // ~70% of mods render text using these old methods.
        // New API: Font.drawInBatch(String, float, float, int, boolean,
        //          Matrix4f, MultiBufferSource, DisplayMode, int, int)
        // ============================================================

        // Font.draw(PoseStack, String, float, float, int) -> int
        // Intermediary: method_1729 (mapped to "draw" in TSV)
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/Font", "draw",
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
            "com/retromod/shim/fabric/embedded/FontBridge", "drawString",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;FFI)I",
            true  // devirtualize: instance method → static method
        );

        // Font.draw(PoseStack, Component, float, float, int) -> int
        // Intermediary: method_27528 (mapped to "draw" in TSV)
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/Font", "draw",
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
            "com/retromod/shim/fabric/embedded/FontBridge", "drawComponent",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
            true  // devirtualize
        );

        // Font.drawShadow(PoseStack, String, float, float, int) -> int
        // Intermediary: method_27517 (mapped to "drawShadow" in TSV)
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/Font", "drawShadow",
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Ljava/lang/String;FFI)I",
            "com/retromod/shim/fabric/embedded/FontBridge", "drawShadowString",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/String;FFI)I",
            true  // devirtualize
        );

        // Font.drawShadow(PoseStack, Component, float, float, int) -> int
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/Font", "drawShadow",
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/network/chat/Component;FFI)I",
            "com/retromod/shim/fabric/embedded/FontBridge", "drawShadowComponent",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
            true  // devirtualize
        );

        // Font.drawShadow(PoseStack, FormattedCharSequence, float, float, int) -> int
        transformer.registerMethodRedirect(
            "net/minecraft/client/gui/Font", "drawShadow",
            "(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/util/FormattedCharSequence;FFI)I",
            "com/retromod/shim/fabric/embedded/FontBridge", "drawShadowFCS",
            "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;FFI)I",
            true  // devirtualize
        );

        // ============================================================
        // RENDERSYSTEM — removed static methods in 26.1
        // ============================================================

        // RenderSystem.enableTexture() → no-op (textures always enabled)
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/systems/RenderSystem", "enableTexture",
            "()V",
            "com/retromod/shim/fabric/embedded/FontBridge", "noOpVoid",
            "()V"
        );

        // RenderSystem.disableTexture() → no-op
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/systems/RenderSystem", "disableTexture",
            "()V",
            "com/retromod/shim/fabric/embedded/FontBridge", "noOpVoid",
            "()V"
        );

        // RenderSystem.setShaderTexture(int, ResourceLocation) → no-op (removed in 26.1)
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/systems/RenderSystem", "setShaderTexture",
            "(ILnet/minecraft/resources/ResourceLocation;)V",
            "com/retromod/shim/fabric/embedded/FontBridge", "noOpSetShaderTexture",
            "(ILjava/lang/Object;)V"
        );

        // RenderSystem.enableBlend/disableBlend/enableDepthTest/disableDepthTest/setShaderColor
        // ALL removed in 26.1. Rendering is now pipeline-based, not GL state machine.
        for (String method : new String[]{"enableBlend", "disableBlend",
                "enableDepthTest", "disableDepthTest", "defaultBlendFunc"}) {
            transformer.registerMethodRedirect(
                "com/mojang/blaze3d/systems/RenderSystem", method,
                "()V",
                "com/retromod/shim/fabric/embedded/ItemSafetyShim", "noOpStatic",
                "()V"
            );
        }
        // setShaderColor(float, float, float, float) → no-op
        transformer.registerMethodRedirect(
            "com/mojang/blaze3d/systems/RenderSystem", "setShaderColor",
            "(FFFF)V",
            "com/retromod/shim/fabric/embedded/ItemSafetyShim", "noOpColor",
            "(FFFF)V"
        );

        // AbstractContainerScreen.slotClicked: descriptor changed in 26.1
        // Old: slotClicked(Slot, int, int, ClickAction)V
        // New: slotClicked(Slot, int, int, ContainerInput)V
        // The method name didn't change — the 4th parameter type changed.
        // MouseTweaks' @Invoker targets the old descriptor, which won't match.
        // This is a non-fatal mixin failure — MouseTweaks still initializes without it.

        // ============================================================
        // REMOVED DEPRECATED MODULES
        // These were deprecated earlier and removed in 26.1
        // ============================================================

        // fabric-convention-tags-v1 removed (use v2)
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/tag/convention/v1/ConventionalBlockTags",
            "net/fabricmc/fabric/api/tag/convention/v2/ConventionalBlockTags"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/tag/convention/v1/ConventionalItemTags",
            "net/fabricmc/fabric/api/tag/convention/v2/ConventionalItemTags"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/tag/convention/v1/ConventionalBiomeTags",
            "net/fabricmc/fabric/api/tag/convention/v2/ConventionalBiomeTags"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/tag/convention/v1/ConventionalFluidTags",
            "net/fabricmc/fabric/api/tag/convention/v2/ConventionalFluidTags"
        );
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/tag/convention/v1/ConventionalEnchantmentTags",
            "net/fabricmc/fabric/api/tag/convention/v2/ConventionalEnchantmentTags"
        );

        // fabric-rendering-api-v1 world subpackage → level
        transformer.registerClassRedirect(
            "net/fabricmc/fabric/api/client/rendering/v1/world/WorldRenderContext",
            "net/fabricmc/fabric/api/client/rendering/v1/LevelRenderContext"
        );
    }

    @Override
    public String[] getShimClasses() {
        return new String[] {
            // Embedded shim classes needed for 26.1 compatibility
            // HudRenderCallbackShim already declared in 1.21.5→1.21.6 shim
            "com.retromod.shim.fabric.embedded.KeyBindingShim",
            "com.retromod.shim.fabric.embedded.ItemSafetyShim",
            "com.retromod.shim.fabric.embedded.ButtonShim",
            "com.retromod.shim.fabric.embedded.FontBridge"
        };
    }

    /**
     * Generate a synthetic ScreenEvents inner interface (AfterRender or BeforeRender).
     *
     * These interfaces were renamed to AfterExtract/BeforeExtract in 26.1 Fabric API.
     * The SAM method was also renamed (afterRender→afterExtract) and the parameter
     * type changed (GuiGraphics→GuiGraphicsExtractor).
     *
     * We generate the old interface EXTENDING the new one, with a default method
     * bridge so that:
     *   1. Lambdas implementing AfterRender can be stored in AfterExtract[] arrays
     *      (no ArrayStoreException)
     *   2. When the event fires afterExtract(), our default bridge calls afterRender()
     *      on the lambda (the old mod's actual implementation)
     *
     * The generated interface looks like:
     *   interface AfterRender extends AfterExtract {
     *       void afterRender(Screen, GuiGraphicsExtractor, int, int, float);
     *       default void afterExtract(Screen, GuiGraphicsExtractor, int, int, float) {
     *           afterRender(screen, extractor, mouseX, mouseY, tickDelta);
     *       }
     *   }
     */
    private static byte[] generateScreenEventInterface(String simpleName, String methodName) {
        String pkg = "net/fabricmc/fabric/api/client/screen/v1/ScreenEvents$";
        String className = pkg + simpleName;

        // Determine parent interface (AfterRender→AfterExtract, BeforeRender→BeforeExtract)
        String parentSimple = simpleName.replace("Render", "Extract");
        String parentClass = pkg + parentSimple;
        String parentMethod = methodName.replace("Render", "Extract")
                                        .replace("render", "extract");

        // Descriptor uses GuiGraphicsExtractor (26.1 name, GuiGraphics was renamed)
        String desc = "(Lnet/minecraft/client/gui/screens/Screen;"
                + "Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V";

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21,
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE,
            className, null, "java/lang/Object",
            new String[]{ parentClass });  // extends AfterExtract/BeforeExtract

        // Abstract SAM method: void afterRender/beforeRender(Screen, GuiGraphicsExtractor, int, int, float)
        cw.visitMethod(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT,
            methodName, desc, null, null
        ).visitEnd();

        // Default bridge: afterExtract/beforeExtract delegates to afterRender/beforeRender
        MethodVisitor mv = cw.visitMethod(
            Opcodes.ACC_PUBLIC,
            parentMethod, desc, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);  // this
        mv.visitVarInsn(Opcodes.ALOAD, 1);  // screen
        mv.visitVarInsn(Opcodes.ALOAD, 2);  // extractor
        mv.visitVarInsn(Opcodes.ILOAD, 3);  // mouseX
        mv.visitVarInsn(Opcodes.ILOAD, 4);  // mouseY
        mv.visitVarInsn(Opcodes.FLOAD, 5);  // tickDelta
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, className, methodName, desc, true);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(6, 6);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}
