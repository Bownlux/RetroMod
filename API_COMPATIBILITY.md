# RetroMod API Compatibility

RetroMod includes compatibility shims for popular modding APIs, allowing mods built with older API versions to work on newer Minecraft versions. This includes both actively maintained APIs and legacy/unmaintained APIs that many old mods still depend on.

## Supported APIs

### Fabric APIs (13 shims)

| API | Old Version | New Version | Coverage |
|-----|-------------|-------------|----------|
| **Fabric API** | 0.50.0+ | 0.100.0+ | Networking, Registries, Rendering, Resources, Events, Biomes |
| **Mod Menu** | 1.x | 7.x | Config screen factory, badges |
| **Cloth Config** | 4.x | 11.x | ConfigBuilder, entry builders, tooltips |
| **REI** | 3.x | 12.x | Plugins, displays, widgets, entries |
| **Trinkets** | 2.x | 3.7+ | Trinket interface, components, slots |
| **Cardinal Components** | 2.x | 6.x | Component registry, NBT serialization |
| **Sodium/Iris** | Various | Latest | Renderer API, shader compat |
| **owo-lib** | Various | Latest | UI components, config, networking |
| **LibGui** | Various | Latest | Screen handlers, widget system |
| **EMI** | Various | Latest | Recipe viewer integration |
| **Forge Config API Port** | Various | Latest | Forge-style configs on Fabric |
| **LibBlockAttributes** | 0.6+ (1.14-1.18) | Fabric Transfer API v2 | Item/fluid storage, insertable/extractable, FluidAmount conversion |
| **FabricShieldLib** | 1.x (1.16-1.19) | Modern rendering | Shield items, models, banner shields, block/disabled callbacks |

### Forge/NeoForge APIs (11 shims)

| API | Old Version | New Version | Coverage |
|-----|-------------|-------------|----------|
| **JEI** | 7.x | 15.x | Plugins, categories, ingredients, displays |
| **Curios** | 1.x | 5.x | ICurio interface, slot context, rendering |
| **Forge Capabilities** | 1.20.x | NeoForge 1.21+ | LazyOptional, ICapabilityProvider, handlers |
| **Mekanism** | Various | Latest | Gas API, chemical handling |
| **Forge Config** | Various | Latest | Config spec, value types |
| **Forge Events** | Various | Latest | Event bus, lifecycle events |
| **Forge Registry** | Various | Latest | IForgeRegistry, DeferredRegister |
| **Baubles** | 1.x (1.7-1.12) | Curios 5.x | IBauble→ICurio, BaubleType→slot strings, capabilities, rendering |
| **NEI (Not Enough Items)** | 1.x (1.7-1.12) | JEI 15.x | IRecipeHandler→IRecipeCategory, API→plugin registration, GuiDraw→GuiGraphics |
| **Thermal/RF API** | 1.x (1.7-1.12) | Forge Energy | IEnergyHandler→IEnergyStorage, RF→FE conversion, sided→unsided |
| **WAILA** | 1.x (1.7-1.12) | Jade 13.x | IWailaDataProvider→IBlockComponentProvider, registrar split, accessor updates |

### Cross-Loader APIs (10 shims)

| API | Old Version | New Version | Coverage |
|-----|-------------|-------------|----------|
| **GeckoLib** | 3.x | 4.4+ | IAnimatable, controllers, renderers, models |
| **Architectury** | 1.x | 9.x | Registries, events, networking, platform |
| **Patchouli** | Various | Latest | Book system, template pages |
| **Create** | Various | Latest | Contraption API, processing |
| **YACL** | Various | Latest | Config screens, options |
| **Jade/WAILA** | Various | Latest | Tooltip providers, data accessors |
| **AE2** | Various | Latest | Grid system, part API |
| **Botania** | Various | Latest | Mana system, subtile API |
| **MixinExtras** | 0.0.x-0.2.x | 0.3.x+ (bundled) | Bootstrap→no-op, injector package moves, annotation relocations |
| **AutoRegLib** | 1.x (1.10-1.16) | Modern registry | RegistryHelper→DeferredRegister, IModBlock/IModItem, creative tabs, recipes |

## Legacy/Unmaintained API Handling

Many popular mods from older Minecraft versions depend on APIs that are no longer maintained. RetroMod handles these by embedding compatibility shims that bridge old API calls to their modern equivalents:

| Old API | Era | Replaced By | How RetroMod Handles It |
|---------|-----|-------------|------------------------|
| **Baubles** | 1.7-1.12 | Curios | Maps IBauble→ICurio, BaubleType enum→slot strings |
| **NEI** | 1.7-1.12 | JEI | Maps IRecipeHandler→IRecipeCategory, API registration→plugin system |
| **Thermal/RF** | 1.7-1.12 | Forge Energy | Maps IEnergyHandler→IEnergyStorage, RF units→FE units (1:1) |
| **WAILA** | 1.7-1.12 | Jade | Maps IWailaDataProvider→IBlockComponentProvider, single registrar→split registration |
| **LibBlockAttributes** | 1.14-1.18 | Fabric Transfer API | Maps ItemInsertable/Extractable→Storage, FluidAmount→droplets |
| **AutoRegLib** | 1.10-1.16 | DeferredRegister | Maps RegistryHelper→DeferredRegister, removes recipe helpers (now JSON) |
| **MixinExtras** (old) | Pre-0.3 | Bundled MixinExtras | Redirects old package paths, makes bootstrap calls no-op |

### What Happens When an Old API Has No Modern Version?

When a mod uses an API that doesn't have a version for your current Minecraft client:

1. **RetroMod identifies the API** — Scans the mod's bytecode for known API package references
2. **Selects the best shim** — Picks the embedded shim that bridges the old API to its modern replacement
3. **Embeds compatibility classes** — Adds shim implementation classes directly into the transformed mod JAR
4. **Redirects at bytecode level** — Old API calls are rewritten to call the shim, which internally uses the closest available modern API

The mod never knows the difference — it thinks it's calling the old API, but the shim translates everything to the modern equivalent. This works even if the old API jar is completely missing from the modpack, because the shim provides all the bridge classes the mod needs.

## How It Works

When RetroMod loads an old mod, it:

1. **Detects API usage** — Scans for imports/calls to old APIs
2. **Selects appropriate shims** — Based on the APIs used
3. **Transforms bytecode** — Redirects old API calls to shim methods
4. **Embeds compatibility classes** — Adds shim implementations to the mod

## Example Transformations

### Fabric API Networking (1.20.1 → 1.21.11)
```java
// Old code (1.20.1)
ServerPlayNetworking.send(player, channelId, buf);

// Transformed to call shim
NetworkingShim.sendLegacy(player, channelId, buf);
// Shim wraps buf in new CustomPayload system
```

### GeckoLib Animation (3.x → 4.x)
```java
// Old code (GeckoLib 3)
new AnimationController(entity, "controller", 5, predicate);

// Transformed
GeckoLibShim.createController(entity, "controller", 5, predicate);
// Shim handles new constructor signature
```

### Forge Capabilities (1.20 → NeoForge 1.21)
```java
// Old code (Forge 1.20)
LazyOptional<IItemHandler> handler = tile.getCapability(ForgeCapabilities.ITEM_HANDLER);

// Transformed
LazyOptionalShim<IItemHandler> handler = tile.getCapability(CapabilityShim.getItemHandler());
// Shim bridges to NeoForge's new capability system
```

### Baubles → Curios (1.12 → 1.21)
```java
// Old code (Baubles, 1.12)
BaublesApi.getBaublesHandler(player).getStackInSlot(BaubleType.AMULET.getValidSlots()[0]);

// Transformed
BaublesShim.getBaublesHandler(player).getStackInSlot("necklace");
// Shim maps BaubleType enum values to Curios slot identifiers
```

### Thermal RF → Forge Energy (1.12 → 1.21)
```java
// Old code (RF API, 1.12)
int received = ((IEnergyReceiver) tile).receiveEnergy(EnumFacing.NORTH, 1000, false);

// Transformed
int received = ThermalEnergyShim.receiveEnergy(tile, Direction.NORTH, 1000, false);
// Shim wraps modern IEnergyStorage, ignores side parameter
```

## Adding More API Support

To request support for additional APIs, please open an issue with:
- API name and versions
- Links to API documentation
- Example mods that use the API
