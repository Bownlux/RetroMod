# RetroMod

> Run older Minecraft mods on newer versions through bytecode transformation and API shimming.

[![Java 25+](https://img.shields.io/badge/Java-25+-blue.svg)](https://adoptium.net/)
[![Minecraft 26.1](https://img.shields.io/badge/Minecraft-26.1-green.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Beta](https://img.shields.io/badge/Status-Beta-orange.svg)]()

**Made by the developers of [RevivalSMP.net](https://revivalsmp.net)**

> **This project is currently in beta.** It works for many mods, but some complex mods may still have issues. Always keep backups of your original mod files. Please report bugs on [GitHub Issues](https://github.com/Bownlux/RetroMod/issues).

RetroMod is a drop-in Minecraft mod that transforms older mod bytecode at load time — rewriting renamed methods, redirecting removed APIs, and patching Mixin targets — so old mods just work. Supports **Fabric**, **NeoForge**, and **Forge** with version shims covering Minecraft 1.12.2 all the way through 26.1.

> **26.1 Update:** Mojang removed all code obfuscation in Minecraft 26.1. RetroMod automatically maps old intermediary names (`class_XXXX`, `method_XXXX`, `field_XXXX`) to Mojang's official human-readable names, so mods built for 1.21.x and earlier work seamlessly on 26.1+.

---

## Quick Start

### Fabric

1. Download `retromod-1.0.0-beta.1.jar` and put it in `mods/`
2. Launch Minecraft once, then close it — this creates the `retromod-input/` folder and a config that lets old mods load
3. Put your old mods in the `retromod-input/` folder (in your `.minecraft` directory)
4. Launch again — RetroMod transforms them and shows a restart popup
5. Restart one more time — done, your old mods work!

> **Why not just drop old mods in `mods/`?** Fabric checks mod versions before RetroMod can run. If you put an old mod directly in `mods/`, Fabric rejects it and crashes (exit code 255). The `retromod-input/` folder lets RetroMod transform the mod first, then move it to `mods/` with the correct version info.
>
> **Alternative:** Use the CLI to prep everything in one step: `java -jar retromod-cli.jar prepare ~/.minecraft --aot`

### Forge / NeoForge

1. Put RetroMod in `mods/`
2. Drop your old mods in `mods/` too (or in `retromod-input/` if you prefer)
3. Launch the game — RetroMod transforms incompatible mods and updates their `mods.toml`
4. Restart when prompted — done!

> Originals are backed up to `mods/retromod-backups/`.

### Uninstalling

1. Remove `retromod-*.jar` from `mods/`
2. If you want original (untransformed) mods back, restore them from `mods/retromod-backups/` or `retromod-input/processed/`

> Transformed mods have updated bytecode and version info, so they may still work without RetroMod. But if anything breaks, restore the originals.

---

## Key Features

- **534+ Bytecode Redirects** — Class, method, field, constructor, and field-to-accessor redirects across all three loaders
- **Polyfill System** — 72+ reimplementations of removed APIs across Fabric, Forge, NeoForge, vanilla MC, and third-party mods
- **Font & Rendering Bridge** — Old Font.draw/drawShadow and RenderSystem calls bridged to 26.1's new rendering APIs
- **Compatibility Score** — CLI tool (`retromod score <mod.jar>`) analyzes any Fabric/Forge/NeoForge mod and shows a compatibility percentage
- **Mixin Compatibility** — Transforms `@Inject`, `@Redirect`, `@Shadow`, `@Accessor` targets to match renamed classes
- **Hybrid AOT/JIT** — Pre-transforms mods at first launch, JIT fallback for edge cases
- **API Embedding** — Removed APIs are bundled as shim classes directly into mod JARs
- **Reflection Remapping** — Intercepts `Class.forName()` and `Method.invoke()` calls
- **API Version Relaxation** — Updates version constraints so mods requiring old API versions work with newer ones
- **Multi-Loader** — Fabric, NeoForge, and Forge (experimental Forge→NeoForge migration for simple mods)
- **Multi-Architecture** — x86_64, ARM64/Apple Silicon, and more
- **Rendering Future-Proofing** — Ready for Vulkan, Metal, and DirectX transitions

## Supported Versions

Shims are **chainable** — a 1.12.2 Forge mod can run on 26.1 by applying each shim in sequence. **All intermediate versions** (1.16.2, 1.14.1, 1.15.1, etc.) are supported via fuzzy version matching — mods targeting any version within a range are automatically handled.

| Loader | Shims | Range | Stability |
|--------|-------|-------|-----------|
| **Fabric** | 32 | 1.14 → ... → 26.1 → 26.1 | 1.16.5+: Beta, 1.14–1.15: Alpha |
| **NeoForge** | 18 | 1.20.1 → ... → 26.1 → 26.1 | Beta |
| **Forge** | 28 | 1.12.2 → ... → 1.20 → 1.21 (NeoForge*) → ... → 26.1 → 26.1 | 1.16.5+: Beta, 1.12–1.15: Alpha |

> **Fuzzy version matching:** If a mod targets an intermediate version like 1.16.2 or 1.14.3, RetroMod automatically resolves it to the nearest milestone shim. This means every MC version from 1.12.2 to 26.1 is supported, even versions without their own dedicated shim.

> **Alpha versions (1.12.2–1.15.2):** These cover massive Minecraft changes like "The Flattening" (1.12→1.13) where every block ID, entity name, and NBT class was renamed. Mods from these versions may not fully work and could be unstable. Use at your own risk.
>
> **Beta versions (1.16.5+):** More stable but still being tested. Most mods should work. Always keep backups.

## API Compatibility

RetroMod supports **34+ popular modding APIs** — both actively maintained and legacy/unmaintained. See [API_COMPATIBILITY.md](API_COMPATIBILITY.md) for the full list.

Highlights: Fabric API, Mod Menu, Cloth Config, REI, Trinkets, JEI, Curios, GeckoLib, Architectury, Create, and legacy APIs like Baubles, NEI, Thermal/RF, and old WAILA. When a mod uses an unmaintained API, RetroMod embeds a compatibility shim that bridges old API calls to their modern equivalent.

### API Version Relaxation

When a mod declares it requires a specific API version — for example, `"cloth-config2": ">=6.0.0 <7.0.0"` — the mod loader (Fabric/Forge/NeoForge) will block the mod from loading if the installed API version doesn't match, even though RetroMod can handle the API differences. RetroMod fixes this by automatically relaxing those version constraints during transformation.

This covers **60+ known API mod IDs** including all variants of Cloth Config, Mod Menu, REI, Trinkets, Cardinal Components, GeckoLib, JEI, Curios, and many more. For unknown API dependencies with strict upper-bound version ranges, RetroMod also relaxes those as a safety net.

> **Example:** Your friend's mod requires Cloth Config 6.x, but you have Cloth Config 11.x installed. Without RetroMod, Fabric immediately crashes with a dependency error. With RetroMod, the version constraint is relaxed to `"*"` and the bytecode is transformed to work with the newer API — the mod loads and runs normally.

## Resource Pack & Data Pack Conversion (Alpha)

RetroMod can also transform **resource packs** and **data packs** for version compatibility. It automatically:
- Updates `pack.mcmeta` pack format numbers
- Renames old texture paths (e.g., `textures/blocks/` → `textures/block/` from The Flattening)
- Adjusts data pack namespaces for version changes

> **This feature is in alpha.** It works for simple resource/data packs, but complex packs with custom shaders, model overrides, or heavy use of predicates may not convert correctly. Always keep backups of your original packs.

---

## Building

Requires **Java 25+** and **Maven 3.8+**.

### Easy Build (Recommended)

```bash
git clone https://github.com/Bownlux/RetroMod.git
cd RetroMod
./build.sh          # macOS / Linux
build.bat           # Windows
```

This builds everything and puts the output in `dist/`:

| File | What it's for |
|------|---------------|
| `retromod-*-cli.jar` | CLI tool (standalone, all dependencies included) |
| `retromod-*-fabric.jar` | Fabric mod — drop in `mods/` |
| `retromod-*-neoforge.jar` | NeoForge mod — drop in `mods/` |

> There's also `build-all.sh` / `build-all.bat` which builds JARs for **every** MC version × every loader.

### Maven Build

```bash
mvn clean package    # Build everything + create dist/ with all JARs
```

This is what `build.sh` / `build.bat` use internally. Output goes to both `target/` (raw JARs) and `dist/` (ready-to-use JARs).

---

## CLI Tool

RetroMod includes a standalone CLI for transforming mods outside of Minecraft. The build outputs a `retromod` wrapper script so you don't need to type `java -jar ...` every time.

### Setup

After building, the `dist/` folder contains `retromod` (macOS/Linux) and `retromod.bat` (Windows). Add it to your PATH:

```bash
# macOS / Linux
export PATH="$PATH:/path/to/retromod/dist"

# Windows (PowerShell)
$env:PATH += ";C:\path\to\retromod\dist"
```

Or just run it from the dist directory directly.

### Commands

```bash
# Analyze a mod's compatibility
retromod analyze mymod.jar

# AOT compile a mod (recommended)
retromod aot mymod.jar

# Batch process all mods in a folder
retromod batch mods/ --aot

# Full prep: overrides + transform everything
retromod prepare ~/.minecraft --aot

# Transform a legacy mod (1.8-1.20.x) to 1.21.x
retromod legacy oldmod-1.12.2.jar

# Show API differences between versions
retromod diff fabric 1.21.8 1.21.9

# Developer migration helper — shows what API changes to make
retromod devhelp mymod-1.21.4.jar 26.1
```

> You can also use `java -jar retromod-*-cli.jar <command>` directly if you prefer.

### Java Agent Mode

Transform classes at runtime instead of pre-transforming JARs:

```bash
java -javaagent:retromod-agent.jar -jar minecraft.jar
```

---

## CLI vs Mod — Which Should You Use?

RetroMod comes in two forms: a **standalone CLI tool** and an **in-game mod**. Both do the same bytecode transformations, but they work differently.

| | CLI Tool | In-Game Mod |
|---|---------|-------------|
| **How it works** | You run it manually before launching Minecraft. Transforms mods ahead-of-time (AOT). | Drops into your `mods/` folder. Transforms mods automatically on first launch. |
| **When transforms happen** | Before Minecraft starts | During Minecraft's loading phase |
| **Runtime overhead** | None — mods are already transformed | Minimal — AOT on first launch, JIT fallback for edge cases |
| **Ease of use** | Requires command-line knowledge | Just drop it in `mods/` |
| **Server-friendly** | Great for prepping mods before deploying to a server | Works on servers too, but first launch takes longer |
| **Batch processing** | Yes — `batch` and `prepare` commands | No — one instance at a time |
| **Debugging** | `analyze`, `diff`, `devhelp` commands | Config-based (`log_level`, `dump_bytecode`) |

### When to use the CLI

- You want **zero runtime overhead** — all transforms happen before the game starts
- You're setting up a **server** and want everything pre-transformed
- You want to **analyze or debug** a mod before loading it
- You're a **mod developer** using `devhelp` to see what API changes to make

### When to use the Mod

- You want a **simple drop-in experience** — no command line needed
- You want RetroMod to **automatically detect and transform** old mods
- You don't mind a slightly longer first launch while transforms are compiled
- You want the **JIT fallback** for edge cases that AOT might miss

> **You can use both.** Pre-transform heavy mods with the CLI, then use the in-game mod as a safety net for anything the CLI missed.

---

## Performance Impact

RetroMod's bytecode transformations have a cost. Here's what to expect:

### During Transformation (First Launch / CLI)

| What | Impact | Duration |
|------|--------|----------|
| **AOT compilation** | High CPU, moderate RAM (+200–500 MB) | One-time — 5–30 seconds per mod depending on size |
| **Shim embedding** | Moderate CPU | One-time — adds shim classes to mod JARs |
| **Mixin target rewriting** | Moderate CPU | One-time — scans and rewrites annotation targets |

The first launch with the in-game mod will be noticeably slower (30 seconds to a few minutes depending on how many mods you have). After that, **everything is cached** and future launches are normal speed.

> **Tip:** Use the CLI's `batch` or `prepare` command to pre-transform all mods. This way, Minecraft launches at normal speed from the first boot.

### During Gameplay

| Mode | Impact | Notes |
|------|--------|-------|
| **AOT (pre-transformed)** | No impact | Mods are already rewritten — same as running native mods |
| **JIT fallback** | Minor CPU spikes | Only triggers when a class is loaded that AOT missed. Rare — typically <1% of classes. Each JIT transform takes 1–10 ms. |
| **Reflection remapping** | Negligible | Intercepts `Class.forName()` / `Method.invoke()` calls — adds microseconds per call |

For most users, **gameplay performance is identical to running native mods.** The JIT fallback is rare and brief. If you notice stutters, pre-transform with the CLI to eliminate all JIT hits.

### Memory Usage

- **RetroMod itself:** ~10–20 MB of additional memory for the shim registry and transformation engine
- **Embedded shims:** Each transformed mod grows by 50–200 KB (the shim classes added to the JAR)
- **JIT cache:** If JIT triggers, transformed classes are cached in memory — typically <5 MB total

> **Bottom line:** After the first launch, RetroMod has virtually no performance impact. If you want zero overhead, use the CLI to pre-transform everything.

---

## Polyfill System (New)

RetroMod's shims handle API **renames and relocations** — when a method or class was moved or renamed between versions. But sometimes APIs are **completely removed** with no direct replacement. When that happens, mods crash with `ClassNotFoundException`, `NoSuchMethodError`, or mixin hierarchy failures.

The **polyfill system** solves this by re-implementing removed APIs using their modern equivalents. These polyfills provide the original API surface but delegate to the new code under the hood, so mods that depend on removed APIs still **work correctly** — not just load. Polyfills are loaded automatically via ServiceLoader and can be toggled per-category in config.

### What's Covered

RetroMod ships with **72+ polyfill reimplementations** across 10 providers covering every major modding ecosystem:

| Category | Examples | Fixes |
|----------|----------|-------|
| **Fabric Loader** | `TinyVisitor`, `TinyV2Visitor`, `TinyMappingFactory`, `TinyMetadata` | Not Enough Crashes crash (`ClassNotFoundException`) |
| **Fabric API** | Removed Fabric API modules and classes | Mods using deprecated Fabric API surfaces |
| **Minecraft Vanilla** | `Material`, `LiteralText`, removed screen classes (`class_5500`) | No Chat Reports mixin failure, mods using removed vanilla APIs |
| **Mixin Targets** | Removed MC classes used as mixin targets | Mixin hierarchy crashes when target classes are deleted |
| **Forge** | `SidedProxy`, `RegistryObject`, `MinecraftForge`, `ICapabilityProvider`, `LazyOptional` | Legacy Forge mods referencing the old capability/registry system |
| **NeoForge** | `ItemStackHandler`, `ComponentItemHandler`, `RenderHighlightEvent`, `javax.annotation.*` | NeoForge mods using removed transfer API and annotation classes |
| **Baubles** | `IBauble`, `BaubleType`, `BaublesApi`, capability classes | 1.7.10–1.12.2 mods using Baubles (replaced by Curios) |
| **NEI** | `API`, `IRecipeHandler`, `TemplateRecipeHandler`, `ItemList` | 1.4.7–1.12.2 mods using Not Enough Items (replaced by JEI) |
| **CoFH / Redstone Flux** | `IEnergyConnection`, `IEnergyHandler`, `IEnergyReceiver`, `IEnergyProvider` | Mods using the old Thermal/RF energy API (replaced by Forge Energy) |
| **WAILA** | `IWailaPlugin`, `IWailaRegistrar`, `IWailaDataProvider` | Mods using What Am I Looking At (replaced by Jade) |

### Superclass Redirects

When Minecraft changes a **class to an interface** (e.g., `Explosion` became an interface in newer versions), mods that `extend Explosion` break because you can't extend an interface. RetroMod's polyfill system includes a **superclass redirect** mechanism that rewrites the class hierarchy at load time — changing the superclass to a bridge class and adding the new interface, so the mod loads correctly.

### Configuration

Polyfills are **enabled by default**. You can toggle the entire system or individual categories in `config/retromod/config.json`:

```json
{
  "polyfills_enabled": true,
  "polyfill_categories": {
    "fabric_api": true,
    "rendering": true,
    "entity": true,
    "mixin_targets": true,
    "minecraft_vanilla": true,
    "forge": true,
    "neoforge": true,
    "thirdparty": true
  }
}
```

> **Note:** Polyfills reimplement removed APIs by delegating to their modern equivalents. Old API calls are intercepted and bridged to the new code, so mods that depend on them still function correctly. In rare cases where an API was removed with no replacement at all, a minimal fallback is provided.

---

## Configuration

Auto-generated on first launch at `config/retromod/config.json`:

```json
{
  "use_aot": true,
  "use_hybrid": true,
  "instruction_level_granularity": true,
  "transform_mixins": true,
  "transform_refmaps": true,
  "remap_reflection": true,
  "polyfills_enabled": true,
  "log_level": "INFO",
  "log_transformations": false,
  "target_mc_version": "auto",
  "debug": false,
  "dump_bytecode": false,
  "force_translate_complex": false
}
```

| Option | Default | Description |
|--------|---------|-------------|
| `use_aot` | `true` | Pre-transform mods ahead-of-time on first launch |
| `use_hybrid` | `true` | Use hybrid AOT/JIT mode (JIT fills in what AOT misses) |
| `transform_mixins` | `true` | Rewrite Mixin annotation targets for version compatibility |
| `remap_reflection` | `true` | Intercept reflection calls (Class.forName, Method.invoke) and remap class names |
| `polyfills_enabled` | `true` | Enable the polyfill system — reimplements removed APIs using modern equivalents so old mods work correctly |
| `force_translate_complex` | `false` | Force-translate mods that RetroMod deems "unlikely to work" (high complexity score). Enable this if a mod was skipped and you want to try it anyway. |
| `log_level` | `"INFO"` | Logging verbosity: `ERROR`, `WARN`, `INFO`, `DEBUG` |
| `dump_bytecode` | `false` | Dump transformed bytecode to disk for debugging |

---

## How It Works

```
MOD JAR (old version)
  ↓
CLASS ANALYSIS — detect loader type, MC version, scan bytecode
  ↓
SHIM CHAIN — find path: e.g. 1.21 → 1.21.1 → ... → 26.1
  ↓
BYTECODE TRANSFORMATION — AOT, partial AOT, or JIT
  ↓
POLYFILL INJECTION — reimplement removed APIs (Fabric, Forge, NeoForge, vanilla, third-party)
  ↓
SUPERCLASS REDIRECTS — rewrite class hierarchy for class→interface migrations
  ↓
OUTPUT — rewritten bytecode, embedded API shims, polyfill reimplementations, updated Mixins
```

### Core Components

| Component | Description |
|-----------|-------------|
| `RetroModTransformer` | ASM-based bytecode transformer with SafeClassWriter |
| `HybridTransformationEngine` | AOT/JIT engine with fallback |
| `ShimRegistry` | BFS-based shim chain finder |
| `MixinCompatibilityTransformer` | Transforms Mixin annotation targets |
| `ModVersionDetector` | Reads mod metadata from loader-specific files |
| `PolyfillRegistry` | ServiceLoader-based registry for removed API polyfills |
| `RenderingBackendShim` | Future-proof rendering compat (OpenGL/Vulkan/Metal) |

---

## For Mod Developers

### Detecting RetroMod

```java
boolean retroModPresent = FabricLoader.getInstance().isModLoaded("retromod");
```

### Migration Helper

Use `devhelp` to see exactly what API changes you need when updating your mod:

```bash
java -jar retromod-cli.jar devhelp your-mod.jar 26.1
```

This scans your mod, finds the version gap, and prints a list of every class rename, method rename, and field change you need to make.

### Adding a Version Shim

```java
public class Fabric_X_to_Y implements VersionShim {
    @Override public String getSourceVersion() { return "X"; }
    @Override public String getTargetVersion() { return "Y"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        transformer.registerMethodRedirect(
            "old/Owner", "oldMethod", "(args)return",
            "new/Owner", "newMethod", "(args)return"
        );
    }
}
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| `NoSuchMethodError` at runtime | Run `retromod analyze yourmod.jar` to check compatibility |
| Mod loads but features broken | Some APIs were removed without replacement — check shim tables |
| Stale/broken transforms | Run `retromod clean` to clear AOT cache |
| Crash with "error code 1" and no text | Check `config/retromod/crash-log.txt` — RetroMod now writes crash details there. Also check your launcher's logs (`logs/latest.log`). |
| Fabric crashes with "mod requires cloth-config 6.x" (or similar API version) | RetroMod handles this — put the mod in `retromod-input/` so it gets transformed. If dropped directly in `mods/`, Fabric checks versions before RetroMod can fix them. |
| Mod was skipped as "unlikely to work" | Set `"force_translate_complex": true` in config.json to override |
| Debug logging | Set `"log_level": "DEBUG"` and `"dump_bytecode": true` in config.json |

## Known Limitations

> **Beta Notice:** RetroMod is in beta. While it works for many mods, some complex mods may still have issues. Always keep backups.
>
> **Alpha Notice (1.12.2–1.15.2):** Support for these very old versions is in alpha. The API changes between these versions were enormous (The Flattening alone renamed hundreds of classes). Expect instability, especially with complex mods. Simple mods have the best chance of working.

1. Cannot transform already-loaded classes without Java Agent mode
2. Complex Mixins may need manual shim updates for non-standard patterns
3. **Alpha:** Legacy mods (1.12.2–1.15.2) may be unstable — especially mods crossing "The Flattening" (1.12→1.13) which renamed every block, entity, and NBT class
4. **Forge→NeoForge migration is experimental.** RetroMod can remap basic package names (`net.minecraftforge.*` → `net.neoforged.*`), but NeoForge has diverged significantly from Forge — the capability system, networking, and many internal APIs were completely rewritten, not just renamed. Simple Forge mods may work on NeoForge, but complex mods that deeply use Forge's systems will likely not work.
5. Cross-loader mods (Forge mod on Fabric) are not supported
6. Rendering backend shims activate only when MC actually switches backends
7. Very old mods using Java 8 reflection patterns may need additional shim work
8. **RetroMod cannot fix Java version mismatches.** If a mod was compiled for a newer Java version than you have installed (e.g., a mod requiring Java 25 on a Java 21 system), the JVM will refuse to load it with `UnsupportedClassVersionError` before RetroMod can do anything. RetroMod transforms Minecraft API changes, not Java version differences. You need the correct Java version installed.
9. **Mod configs** generally work, but if a mod's config system changed between versions (e.g., old Forge `.cfg` → new `.toml`), you may need to delete the old config file and let the mod regenerate it
10. **Complex mods may be skipped.** RetroMod analyzes each mod's complexity (reflection usage, ASM manipulation, coremods, NMS access, etc.) and warns you if a mod is "unlikely to work." If a mod was skipped, set `"force_translate_complex": true` in `config/retromod/config.json` to force it. The CLI's `aot` command also supports `--force` for this.
11. **Resource/data pack conversion is alpha.** Simple packs work, but packs with custom shaders, model predicates, or heavy overlays may not convert correctly.

## Contributing

**Adding a shim:** Fork → add shim in `src/main/java/com/retromod/shim/` → register in `META-INF/services/com.retromod.core.VersionShim` → add tests → PR.

**Adding a polyfill:** Fork → create a `PolyfillProvider` in `src/main/java/com/retromod/polyfill/` → add reimplementation classes at the original package path that delegate to modern APIs → register in `META-INF/services/com.retromod.polyfill.PolyfillProvider` → PR.

## Badge for Translated Mods

If you used RetroMod to translate your mod to a newer version, you can optionally add this badge to your mod page:

[![Translated with RetroMod](https://img.shields.io/badge/Translated_with-RetroMod-blue?style=for-the-badge&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAOCAYAAAAfSC3RAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADqADAAQAAAABAAAADgAAAAC98Dn6AAACM0lEQVQoFY1Sy27TQBQ9Hs94HNd5NE3SpA8gRRQkqBBCZYNQs+EhYI8EEkJ8A/wDCH6DFRKrQtfAlgrRiqKi0iatmoaGPJw0Dye2hxlKIl4LriVrPPec8dxzjoZ/VBzxiMmTGdXquuW9KqqNP2Harxvj5uzMCE89YMy8ruk0rXrC90r9fmep5ZaffO1+3hzgh8Qpe27BMtPPOA9PeoEH7WdHCIASip7bLDrd0u3SweprRdbVa9w8mY2MZJYMHp7oeS4SsSRyF67i+JFZVJwyDtoNMMMKM8Ku0YC9aHmV+iExfOqRacZzAgHmz1xEbv4K4vYYwiMRzEydkEcLlCpFUBqytUDwmrvzkighqG7dCEQAzkzEWAI76Q7WFwI0jA52C5v4tlOEurmQD2XWzVGMRqlpJCaIrqcOt4Fm3UFHcAQhHXm2ger5Nnr7cuaiRBABhQ0ZyQxRMw5KBAE8X4IMHW4EcPIlkOwo+N2z0Obk2f1gAIXOfNELhdL3dMojvlSz2a2DStfYxxac1S20nn9Ar90BOZ2CWNmHL/p7jeaXx0SZ6/ntRQ0EQmrfcB1srSxj9+0yOvs1kFn5x0vHIHYbck4NXr+9qDg/VDVgrXFq3bKtmK1JA5UIPeFC0zXwc5PQ1A0/1eBzr+SU8/eHdqiFbcTfUZ1dJsSIKLItrYhFE6ivFRBs1NAXraI/ze4U1t+8V4MOk6M+ojiaHYtOP/w9cr6K3KuKs/3UwfbfkVPEQf1PyL8D1zPyAOjcRY4AAAAASUVORK5CYII=)](https://modrinth.com/mod/retromod)

**Markdown:**
```
[![Translated with RetroMod](https://img.shields.io/badge/Translated_with-RetroMod-blue?style=for-the-badge&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAOCAYAAAAfSC3RAAAAAXNSR0IArs4c6QAAAERlWElmTU0AKgAAAAgAAYdpAAQAAAABAAAAGgAAAAAAA6ABAAMAAAABAAEAAKACAAQAAAABAAAADqADAAQAAAABAAAADgAAAAC98Dn6AAACM0lEQVQoFY1Sy27TQBQ9Hs94HNd5NE3SpA8gRRQkqBBCZYNQs+EhYI8EEkJ8A/wDCH6DFRKrQtfAlgrRiqKi0iatmoaGPJw0Dye2hxlKIl4LriVrPPec8dxzjoZ/VBzxiMmTGdXquuW9KqqNP2Harxvj5uzMCE89YMy8ruk0rXrC90r9fmep5ZaffO1+3hzgh8Qpe27BMtPPOA9PeoEH7WdHCIASip7bLDrd0u3SweprRdbVa9w8mY2MZJYMHp7oeS4SsSRyF67i+JFZVJwyDtoNMMMKM8Ku0YC9aHmV+iExfOqRacZzAgHmz1xEbv4K4vYYwiMRzEydkEcLlCpFUBqytUDwmrvzkighqG7dCEQAzkzEWAI76Q7WFwI0jA52C5v4tlOEurmQD2XWzVGMRqlpJCaIrqcOt4Fm3UFHcAQhHXm2ger5Nnr7cuaiRBABhQ0ZyQxRMw5KBAE8X4IMHW4EcPIlkOwo+N2z0Obk2f1gAIXOfNELhdL3dMojvlSz2a2DStfYxxac1S20nn9Ar90BOZ2CWNmHL/p7jeaXx0SZ6/ntRQ0EQmrfcB1srSxj9+0yOvs1kFn5x0vHIHYbck4NXr+9qDg/VDVgrXFq3bKtmK1JA5UIPeFC0zXwc5PQ1A0/1eBzr+SU8/eHdqiFbcTfUZ1dJsSIKLItrYhFE6ivFRBs1NAXraI/ze4U1t+8V4MOk6M+ojiaHYtOP/w9cr6K3KuKs/3UwfbfkVPEQf1PyL8D1zPyAOjcRY4AAAAASUVORK5CYII=)](https://modrinth.com/mod/retromod)
```

**HTML:**
```html
<a href="https://modrinth.com/mod/retromod"><img alt="Translated with RetroMod" height="56" src="https://img.shields.io/badge/Translated_with-RetroMod-blue?style=for-the-badge"></a>
```

## License

[MIT License](LICENSE) — Copyright (c) 2026 RevivalSMP

## Credits

**Bownlux** (author) · **[RevivalSMP.net](https://revivalsmp.net)** (development) · **[ASM](https://asm.ow2.io/)** · **[FabricMC](https://fabricmc.net/)** · **[NeoForged](https://neoforged.net/)** · **[MinecraftForge](https://minecraftforge.net/)**

*RetroMod is not affiliated with Mojang, Microsoft, FabricMC, NeoForged, or MinecraftForge.*
