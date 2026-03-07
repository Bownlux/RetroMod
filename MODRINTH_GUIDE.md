# RetroMod

**Run your old Minecraft mods on newer versions -- no changes needed.**

RetroMod automatically rewrites mod bytecode at load time so older mods just work on modern Minecraft. Drop it in your mods folder, add your old mods, and go. Works on Fabric, NeoForge, and Forge, on both clients and servers.

> **BETA RELEASE (v1.0.0-beta.1)** -- RetroMod is currently in beta. It works for many mods, but some complex mods may still have issues. Always keep backups of your original mod files. If something breaks, please [open an issue on GitHub](https://github.com/Bownlux/MC-RetroMod/issues) so we can fix it.

---

## Supported Versions

RetroMod uses **chainable shims** -- a mod built for 1.12.2 can run on 1.21.11 by applying each version shim in sequence. **All intermediate versions are supported** thanks to fuzzy version matching (so a mod targeting 1.16.2, 1.14.1, 1.17.1, etc. will be handled correctly).

| Loader | Version Range | Stability |
|--------|---------------|-----------|
| **Fabric** | 1.14 through 1.21.11 | Alpha for 1.14 - 1.15.2, Beta for 1.16+ |
| **Forge** | 1.12.2 through 1.21.11 | Alpha for 1.12.2 - 1.15.2, Beta for 1.16+ |
| **NeoForge** | 1.20.1 through 1.21.11 | Beta |

> **Alpha versions (1.12.2 - 1.15.2):** These cover massive Minecraft changes like "The Flattening" (1.12 to 1.13) where every block ID, entity name, and NBT class was renamed. Mods from these versions may not fully work. Use at your own risk.
>
> **Beta versions (1.16+):** More stable but still being tested. Most mods should work. Always keep backups.

---

## API Compatibility

RetroMod doesn't just remap vanilla Minecraft methods -- it also understands **19+ popular modding APIs** and handles their version differences automatically.

### Fabric APIs
- **Fabric API** -- core Fabric modules (Transfer API, Rendering, Events, etc.)
- **Mod Menu** -- config screen integration
- **Cloth Config** -- configuration API
- **REI** (Roughly Enough Items) -- recipe viewer
- **Trinkets** -- accessory/trinket slots
- **Cardinal Components** -- component/data attachment API
- **Sodium / Iris** -- rendering optimization and shaders
- **owo-lib** -- UI and config framework
- **LibGui** -- GUI library
- **EMI** -- recipe/item viewer
- **Forge Config API Port** -- Forge config on Fabric

### Forge APIs
- **JEI** (Just Enough Items) -- recipe viewer
- **Curios** -- equipment/accessory slots
- **Forge Capabilities** -- capability system
- **Mekanism** -- energy and chemical APIs
- **Forge Config** -- configuration system
- **Forge Events** -- event bus
- **Forge Registry** -- registry system

### Cross-Loader APIs
- **GeckoLib** -- entity animation
- **Architectury** -- cross-loader abstraction
- **Patchouli** -- in-game documentation books
- **Create** -- mechanical contraption API
- **YACL** (Yet Another Config Lib) -- configuration screens
- **Jade / WAILA** -- block/entity info overlay
- **AE2** (Applied Energistics 2) -- autocrafting/storage API
- **Botania** -- mana and flower APIs

### Legacy / Unmaintained API Handling

If a mod uses an old API that no longer has a version for modern Minecraft, **RetroMod embeds compatibility shims** that bridge the gap. The shim uses the closest available API version internally and adapts the calls so old code still works. No extra downloads needed -- RetroMod bundles these shims directly.

| Old API | Shimmed To | Notes |
|---------|-----------|-------|
| **Baubles** | Curios | Trinket/accessory slot API migration |
| **NEI** (Not Enough Items) | JEI | Recipe viewer migration |
| **Thermal / RF** (Redstone Flux) | Forge Energy | Energy API migration |
| **Old WAILA** | Jade | Block info overlay migration |
| **LibBlockAttributes** | Transfer API | Fabric item/fluid transfer migration |
| **MixinExtras** (old standalone) | Bundled MixinExtras | Old standalone versions redirected to the version now bundled with loaders |

---

## How to Install

### Fabric

1. Put `retromod-1.0.0-beta.1.jar` in your `mods/` folder
2. Launch Minecraft **once** with just RetroMod (no old mods yet), then close it
3. Now add your old mods to `mods/` and launch again -- they should work

The first launch is needed because Fabric normally blocks mods targeting older Minecraft versions. RetroMod's first run sets up a config to bypass that check.

### Forge / NeoForge

1. Put RetroMod in your `mods/` folder
2. Add your old mods to `mods/` (or `retromod-input/`)
3. Launch the game -- RetroMod transforms them automatically
4. Restart when prompted

RetroMod keeps backups of the originals in `mods/retromod-backups/`.

### Servers

Same process as above. Works on Fabric, Forge, and NeoForge dedicated servers. If a mod is server-only (like Lithium), your players don't need RetroMod installed -- only the server does.

---

## FAQ

**Why do I need to launch twice on Fabric?**
Fabric checks mod version requirements before any mods run. RetroMod needs that first launch to set up a config file that tells Fabric to skip those checks.

**Where do transformed mods go?**
They go in your `mods/` folder with a `-retromod` suffix. Originals are backed up.

**Why is the first launch slow?**
RetroMod is compiling transforms for your mods. After that, it caches everything so future launches are fast.

**Does it work with OptiFine?**
Limited support. OptiFine is closed-source and tends to conflict with other mods. We recommend [Sodium](https://modrinth.com/mod/sodium) + [Iris](https://modrinth.com/mod/iris) instead -- both work great with RetroMod.

**Can I use it on a server?**
Yes. Put RetroMod and your old mods on the server. For server-only mods, players don't need RetroMod at all.

**What happens if a mod uses an old API that doesn't exist anymore?**
RetroMod embeds compatibility shims for many legacy APIs. If a mod calls an old API (like Baubles or NEI), RetroMod's built-in shim intercepts the call and redirects it to the modern equivalent (Curios, JEI, etc.). The shim adapts arguments and return values so the old code doesn't know the difference. No extra jars or downloads are needed.

**What about intermediate versions like 1.16.2 or 1.14.1?**
All intermediate versions are supported. RetroMod uses fuzzy version matching, so a mod targeting 1.16.2, 1.17.1, 1.18.1, etc. will be matched to the nearest shim chain and transformed correctly. You don't need to worry about exact version numbers.

---

## Uninstalling

- **Fabric:** Remove RetroMod from `mods/` and delete `config/fabric_loader_dependencies.json`
- **Forge/NeoForge:** Remove RetroMod and restore any mods from `mods/retromod-backups/` if needed

---

## Links

- **Source & Issues:** [github.com/Bownlux/MC-RetroMod](https://github.com/Bownlux/MC-RetroMod)
- **License:** [MIT](LICENSE)

---

Made by **Bownlux** -- part of the team behind **[RevivalSMP.net](https://revivalsmp.net)**, a lifesteal survival server with a player-run economy. Come check it out!

---
---

# Modrinth Upload Guide (For Bownlux)

> Everything below this line is **developer-facing** -- do NOT copy this section to the Modrinth page description. Everything **above** this line is the Modrinth page description.

---

## How to Upload RetroMod to Modrinth

### 1. Files to Upload

Everything you need is in the `dist/` folder after running `build.sh` / `build.bat` or `mvn clean package`:

| File | Upload as | Notes |
|------|-----------|-------|
| `retromod-*-fabric.jar` | Primary file | For Fabric users |
| `retromod-*-neoforge.jar` | Additional file | For NeoForge users |
| `retromod-*-cli.jar` | Additional file | Optional -- standalone CLI tool |

Upload all three files in a single Modrinth version entry.

### 2. Version Settings

When creating a new version on Modrinth:

- **Version number:** `1.0.0-beta.1` (or whatever the current version is)
- **Version type:** Beta
- **Release channel:** Beta

### 3. Game Versions to Select

**Select ALL of these Minecraft versions.** RetroMod supports every intermediate version through fuzzy matching, so check every version in these ranges:

**For the Fabric jar:**
- 1.14, 1.14.1, 1.14.2, 1.14.3, 1.14.4
- 1.15, 1.15.1, 1.15.2
- 1.16, 1.16.1, 1.16.2, 1.16.3, 1.16.4, 1.16.5
- 1.17, 1.17.1
- 1.18, 1.18.1, 1.18.2
- 1.19, 1.19.1, 1.19.2, 1.19.3, 1.19.4
- 1.20, 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6
- 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11

**For the NeoForge jar:**
- 1.20.1, 1.20.2, 1.20.3, 1.20.4, 1.20.5, 1.20.6
- 1.21, 1.21.1, 1.21.2, 1.21.3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11

> **Note:** There is no separate Forge jar. Old Forge mods on NeoForge are handled by RetroMod's Forge-to-NeoForge migration shim. Forge users on 1.12.2-1.20 can use the NeoForge jar with NeoForge, or use the CLI tool to pre-transform their mods.

### 4. Loaders to Select

- Fabric
- NeoForge
- Forge (mark as supported even though there's no separate Forge jar -- RetroMod handles Forge mods via the NeoForge jar)

### 5. Suggested Categories and Tags

**Categories:**
- Utility
- Library

**Tags / Keywords:**
- compatibility
- legacy mods
- old mods
- bytecode
- version compatibility
- mod migration
- retro

### 6. Changelog Template

Use this format for each release:

```markdown
## RetroMod v1.0.0-beta.1

**This is a beta release.** Please report issues on [GitHub](https://github.com/Bownlux/MC-RetroMod/issues).

### Supported Versions
- Fabric: 1.14 - 1.21.11
- Forge: 1.12.2 - 1.21.11 (via NeoForge migration)
- NeoForge: 1.20.1 - 1.21.11

### What's New
- [List changes here]

### Known Issues
- [List known issues here]

### API Compatibility
- 19+ APIs supported (Fabric API, JEI, Curios, GeckoLib, Architectury, and more)
- Legacy API shims for Baubles, NEI, Thermal/RF, old WAILA, and more
```

### 7. Page Description

Copy everything from the **top of this file** down to (but not including) the `---` `---` double separator and the "Modrinth Upload Guide" header. That's your Modrinth page body.

### 8. Project Settings on Modrinth

- **Project type:** Mod
- **License:** MIT
- **Source code:** `https://github.com/Bownlux/MC-RetroMod`
- **Issues:** `https://github.com/Bownlux/MC-RetroMod/issues`
- **Discord:** (add if you have one)
- **Client side:** Required
- **Server side:** Optional (required only if running old mods on the server)
