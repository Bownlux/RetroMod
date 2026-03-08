# RetroMod

> Run older Minecraft mods on newer versions through bytecode transformation and API shimming.

[![Java 21+](https://img.shields.io/badge/Java-21+-blue.svg)](https://adoptium.net/)
[![Minecraft 1.21.x](https://img.shields.io/badge/Minecraft-1.21.x-green.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Beta](https://img.shields.io/badge/Status-Beta-orange.svg)]()

**Made by the developers of [RevivalSMP.net](https://revivalsmp.net)**

> **This project is currently in beta.** It works for many mods, but some complex mods may still have issues. Always keep backups of your original mod files. Please report bugs on [GitHub Issues](https://github.com/Bownlux/MC-RetroMod/issues).

RetroMod is a drop-in Minecraft mod that transforms older mod bytecode at load time — rewriting renamed methods, redirecting removed APIs, and patching Mixin targets — so old mods just work. Supports **Fabric**, **NeoForge**, and **Forge** with version shims covering Minecraft 1.12.2 all the way through 1.21.11.

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

- **Hybrid AOT/JIT** — Pre-transforms mods at first launch, JIT fallback for edge cases
- **Instruction-Level Granularity** — Only specific bytecode instructions get transformed
- **API Embedding** — Removed APIs are bundled as shim classes directly into mod JARs
- **Mixin Compatibility** — Transforms `@Inject`, `@Redirect`, `@Shadow`, `@Accessor` targets
- **Reflection Remapping** — Intercepts `Class.forName()` and `Method.invoke()` calls
- **Multi-Loader** — Fabric, NeoForge, and Forge (experimental Forge→NeoForge migration for simple mods)
- **Multi-Architecture** — x86_64, ARM64/Apple Silicon, and more
- **Rendering Future-Proofing** — Ready for Vulkan, Metal, and DirectX transitions

## Supported Versions

Shims are **chainable** — a 1.12.2 Forge mod can run on 1.21.11 by applying each shim in sequence. **All intermediate versions** (1.16.2, 1.14.1, 1.15.1, etc.) are supported via fuzzy version matching — mods targeting any version within a range are automatically handled.

| Loader | Shims | Range | Stability |
|--------|-------|-------|-----------|
| **Fabric** | 31 | 1.14 → ... → 1.21.11 | 1.16.5+: Beta, 1.14–1.15: Alpha |
| **NeoForge** | 17 | 1.20.1 → ... → 1.21.11 | Beta |
| **Forge** | 27 | 1.12.2 → ... → 1.20 → 1.21 (NeoForge*) → ... → 1.21.11 | 1.16.5+: Beta, 1.12–1.15: Alpha |

> **Fuzzy version matching:** If a mod targets an intermediate version like 1.16.2 or 1.14.3, RetroMod automatically resolves it to the nearest milestone shim. This means every MC version from 1.12.2 to 1.21.11 is supported, even versions without their own dedicated shim.

> **Alpha versions (1.12.2–1.15.2):** These cover massive Minecraft changes like "The Flattening" (1.12→1.13) where every block ID, entity name, and NBT class was renamed. Mods from these versions may not fully work and could be unstable. Use at your own risk.
>
> **Beta versions (1.16.5+):** More stable but still being tested. Most mods should work. Always keep backups.

## API Compatibility

RetroMod supports **34+ popular modding APIs** — both actively maintained and legacy/unmaintained. See [API_COMPATIBILITY.md](API_COMPATIBILITY.md) for the full list.

Highlights: Fabric API, Mod Menu, Cloth Config, REI, Trinkets, JEI, Curios, GeckoLib, Architectury, Create, and legacy APIs like Baubles, NEI, Thermal/RF, and old WAILA. When a mod uses an unmaintained API, RetroMod embeds a compatibility shim that bridges old API calls to their modern equivalent.

---

## Building

Requires **Java 21+** and **Maven 3.8+**.

### Easy Build (Recommended)

```bash
git clone https://github.com/Bownlux/MC-RetroMod.git
cd MC-RetroMod
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

RetroMod includes a standalone CLI for transforming mods outside of Minecraft.

```bash
# Analyze a mod's compatibility
java -jar retromod-cli.jar analyze mymod.jar

# AOT compile a mod (recommended)
java -jar retromod-cli.jar aot mymod.jar

# Batch process all mods in a folder
java -jar retromod-cli.jar batch mods/ --aot

# Full prep: overrides + transform everything
java -jar retromod-cli.jar prepare ~/.minecraft --aot

# Transform a legacy mod (1.8-1.20.x) to 1.21.x
java -jar retromod-cli.jar legacy oldmod-1.12.2.jar

# Show API differences between versions
java -jar retromod-cli.jar diff fabric 1.21.8 1.21.9

# Developer migration helper — shows what API changes to make
java -jar retromod-cli.jar devhelp mymod-1.21.4.jar 1.21.11
```

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
  "log_level": "INFO",
  "log_transformations": false,
  "target_mc_version": "auto",
  "debug": false,
  "dump_bytecode": false
}
```

---

## How It Works

```
MOD JAR (old version)
  ↓
CLASS ANALYSIS — detect loader type, MC version, scan bytecode
  ↓
SHIM CHAIN — find path: e.g. 1.21 → 1.21.1 → ... → 1.21.11
  ↓
BYTECODE TRANSFORMATION — AOT, partial AOT, or JIT
  ↓
OUTPUT — rewritten bytecode, embedded API shims, updated Mixins
```

### Core Components

| Component | Description |
|-----------|-------------|
| `RetroModTransformer` | ASM-based bytecode transformer with SafeClassWriter |
| `HybridTransformationEngine` | AOT/JIT engine with fallback |
| `ShimRegistry` | BFS-based shim chain finder |
| `MixinCompatibilityTransformer` | Transforms Mixin annotation targets |
| `ModVersionDetector` | Reads mod metadata from loader-specific files |
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
java -jar retromod-cli.jar devhelp your-mod.jar 1.21.11
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

## Contributing

Fork → add shim in `src/main/java/com/retromod/shim/` → register in `META-INF/services/com.retromod.core.VersionShim` → add tests → PR.

## License

[MIT License](LICENSE) — Copyright (c) 2026 RevivalSMP

## Credits

**Bownlux** (author) · **[RevivalSMP.net](https://revivalsmp.net)** (development) · **[ASM](https://asm.ow2.io/)** · **[FabricMC](https://fabricmc.net/)** · **[NeoForged](https://neoforged.net/)** · **[MinecraftForge](https://minecraftforge.net/)**

*RetroMod is not affiliated with Mojang, Microsoft, FabricMC, NeoForged, or MinecraftForge.*
