# RetroMod API Compatibility

RetroMod includes compatibility shims for popular modding APIs, allowing mods built with older API versions to work on newer Minecraft versions.

## Supported APIs

### Fabric APIs (6 shims)

| API | Old Version | New Version | Coverage |
|-----|-------------|-------------|----------|
| **Fabric API** | 0.50.0+ | 0.100.0+ | Networking, Registries, Rendering, Resources, Events, Biomes |
| **Mod Menu** | 1.x | 7.x | Config screen factory, badges |
| **Cloth Config** | 4.x | 11.x | ConfigBuilder, entry builders, tooltips |
| **REI** | 3.x | 12.x | Plugins, displays, widgets, entries |
| **Trinkets** | 2.x | 3.7+ | Trinket interface, components, slots |
| **Cardinal Components** | 2.x | 6.x | Component registry, NBT serialization |

### Forge/NeoForge APIs (3 shims)

| API | Old Version | New Version | Coverage |
|-----|-------------|-------------|----------|
| **JEI** | 7.x | 15.x | Plugins, categories, ingredients, displays |
| **Curios** | 1.x | 5.x | ICurio interface, slot context, rendering |
| **Forge Capabilities** | 1.20.x | NeoForge 1.21+ | LazyOptional, ICapabilityProvider, handlers |

### Cross-Loader APIs (2 shims)

| API | Old Version | New Version | Coverage |
|-----|-------------|-------------|----------|
| **GeckoLib** | 3.x | 4.4+ | IAnimatable, controllers, renderers, models |
| **Architectury** | 1.x | 9.x | Registries, events, networking, platform |

## How It Works

When RetroMod loads an old mod, it:

1. **Detects API usage** - Scans for imports/calls to old APIs
2. **Selects appropriate shims** - Based on the APIs used
3. **Transforms bytecode** - Redirects old API calls to shim methods
4. **Embeds compatibility classes** - Adds shim implementations to the mod

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

## Adding More API Support

To request support for additional APIs, please open an issue with:
- API name and versions
- Links to API documentation
- Example mods that use the API
