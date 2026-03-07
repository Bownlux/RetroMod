# RetroMod

> Run older Minecraft mods on newer versions through bytecode transformation and API shimming.

[![Java 21+](https://img.shields.io/badge/Java-21+-blue.svg)](https://adoptium.net/)
[![Minecraft 1.21.x](https://img.shields.io/badge/Minecraft-1.21.x-green.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

**Made by the developers of [RevivalSMP.net](https://revivalsmp.net)**

RetroMod is a drop-in Minecraft mod that transforms older mod bytecode at load time — rewriting renamed methods, redirecting removed APIs, and patching Mixin targets — so old mods just work. Supports **Fabric**, **NeoForge**, and **Forge** with 33 version shims covering every 1.21.x transition.

---

## Quick Start

### Fabric

1. Download `retromod-1.0.0-beta.1.jar` and put it in `mods/`
2. Launch Minecraft once, then close it
3. Add your old mods to `mods/`
4. Launch again — they should work!

> **Why two launches?** Fabric blocks mods targeting older versions. RetroMod's first launch creates a config that bypasses this so old mods can load.

### Forge / NeoForge

1. Put RetroMod in `mods/`, add your old mods too (even Forge mods on NeoForge!)
2. Launch the game — RetroMod auto-transforms incompatible mods
3. Restart when prompted — done!

Originals are backed up to `mods/retromod-backups/` before transforming.

---

## Key Features

- **Hybrid AOT/JIT** — Pre-transforms mods at first launch, JIT fallback for edge cases
- **Instruction-Level Granularity** — Only specific bytecode instructions get transformed
- **API Embedding** — Removed APIs are bundled as shim classes directly into mod JARs
- **Mixin Compatibility** — Transforms `@Inject`, `@Redirect`, `@Shadow`, `@Accessor` targets
- **Reflection Remapping** — Intercepts `Class.forName()` and `Method.invoke()` calls
- **Multi-Loader** — Fabric, NeoForge, and Forge (including Forge→NeoForge migration)
- **Multi-Architecture** — x86_64, ARM64/Apple Silicon, and more
- **Rendering Future-Proofing** — Ready for Vulkan, Metal, and DirectX transitions

## Supported Versions

Shims are **chainable** — a 1.21 mod can run on 1.21.11 by applying each shim in sequence.

| Loader | Shims | Range |
|--------|-------|-------|
| **Fabric** | 11 | 1.21 → 1.21.1 → ... → 1.21.11 |
| **NeoForge** | 11 | 1.21 → 1.21.1 → ... → 1.21.11 |
| **Forge** | 11 | 1.20 (Forge) → 1.21 (NeoForge) → ... → 1.21.10 |

> Forge is less active than NeoForge for 1.21.x but does have releases. The Forge 1.20→NeoForge 1.21 shim handles cross-loader migration.

---

## Building

Requires **Java 21+** and **Maven 3.8+**.

### Easy Build (Recommended)

```bash
git clone https://github.com/Bownlux/MC-RetroMod.git
cd MC-RetroMod
./build.sh
```

This builds everything and puts the output in `dist/`:

| File | What it's for |
|------|---------------|
| `retromod-*-cli.jar` | CLI tool (standalone, all dependencies included) |
| `retromod-*-fabric.jar` | Fabric mod — drop in `mods/` |
| `retromod-*-neoforge.jar` | NeoForge mod — drop in `mods/` |

### Maven Build

```bash
mvn clean package          # Build everything
mvn clean package -Pcli    # CLI-only
mvn clean package -Pfabric # Fabric mod only
```

Maven output goes to `target/` with 4 JARs: plain `.jar` (mod), `-all.jar` (shaded CLI), `-agent.jar` (Java agent), `-sources.jar`.

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

1. Cannot transform already-loaded classes without Java Agent mode
2. Complex Mixins may need manual shim updates for non-standard patterns
3. Only mod loader APIs are shimmed (not Minecraft internals)
4. Cross-loader mods (Forge mod on Fabric) are not supported
5. Rendering backend shims activate only when MC actually switches backends

## Contributing

Fork → add shim in `src/main/java/com/retromod/shim/` → register in `META-INF/services/com.retromod.core.VersionShim` → add tests → PR.

## License

[MIT License](LICENSE) — Copyright (c) 2026 RevivalSMP

## Credits

**Bownlux** (author) · **[RevivalSMP.net](https://revivalsmp.net)** (development) · **[ASM](https://asm.ow2.io/)** · **[FabricMC](https://fabricmc.net/)** · **[NeoForged](https://neoforged.net/)** · **[MinecraftForge](https://minecraftforge.net/)**

*RetroMod is not affiliated with Mojang, Microsoft, FabricMC, NeoForged, or MinecraftForge.*
