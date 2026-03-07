# RetroMod

> Run older Minecraft mods on newer versions through bytecode transformation and API shimming.

[![Java 21+](https://img.shields.io/badge/Java-21+-blue.svg)](https://adoptium.net/)
[![Minecraft 1.21.x](https://img.shields.io/badge/Minecraft-1.21.x-green.svg)](https://minecraft.net/)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Build](https://img.shields.io/badge/Build-Maven-C71A36.svg)](https://maven.apache.org/)

**Made by the developers of [RevivalSMP.net](https://revivalsmp.net)**

---

**RetroMod** is a drop-in Minecraft mod that lets you run older mods on newer Minecraft versions. It works by transforming mod bytecode at load time -- rewriting renamed methods, redirecting removed APIs, and patching Mixin targets -- so old mods just work, no changes needed. Supports **Fabric**, **NeoForge**, and **Forge**, with 33 built-in version shims covering every 1.21.x transition. Future-proofed for Vulkan, Metal, and ARM/Apple Silicon.

---

## What is RetroMod?

RetroMod is a backwards compatibility layer for Minecraft mods. When Minecraft or mod loaders update, they often rename methods, remove APIs, or change class hierarchies -- breaking older mods. RetroMod automatically transforms old mod bytecode at load time so they work on newer Minecraft versions without any changes to the original mod.

### Key Features

- **Hybrid AOT/JIT Compilation** -- Pre-transforms mods at first launch for near-zero runtime overhead, with JIT fallback for edge cases
- **Instruction-Level Granularity** -- Only specific problematic bytecode instructions get transformed, not entire classes
- **API Embedding** -- Removed mod loader APIs are bundled directly into mod JARs as shim classes
- **Mixin Compatibility** -- Transforms `@Inject`, `@Redirect`, `@Shadow`, and `@Accessor` annotations to target renamed methods
- **Reflection Remapping** -- Intercepts `Class.forName()` and `Method.invoke()` calls to use correct names at runtime
- **Rendering Backend Support** -- Future-proofed for Vulkan, Metal, and DirectX rendering transitions
- **Multi-Architecture** -- Works on x86_64, ARM64 (Apple Silicon), and other architectures
- **Multi-Loader** -- Supports Fabric, NeoForge, and Forge (including Forge-to-NeoForge migration)

## Supported Version Transitions

Shims are **chainable** -- a mod built for 1.21 can run on 1.21.11 by applying each shim in sequence.

### Fabric (11 shims)

| From | To | Status |
|------|------|--------|
| 1.21 | 1.21.1 | Supported |
| 1.21.1 | 1.21.2 | Supported |
| 1.21.2 | 1.21.3 | Supported |
| 1.21.3 | 1.21.4 | Supported |
| 1.21.4 | 1.21.5 | Supported |
| 1.21.5 | 1.21.6 | Supported |
| 1.21.6 | 1.21.7 | Supported |
| 1.21.7 | 1.21.8 | Supported |
| 1.21.8 | 1.21.9 | Supported |
| 1.21.9 | 1.21.10 | Supported |
| 1.21.10 | 1.21.11 | Supported |

### NeoForge (11 shims)

| From | To | Status |
|------|------|--------|
| 1.21 | 1.21.1 | Supported |
| 1.21.1 | 1.21.2 | Supported |
| 1.21.2 | 1.21.3 | Supported |
| 1.21.3 | 1.21.4 | Supported |
| 1.21.4 | 1.21.5 | Supported |
| 1.21.5 | 1.21.6 | Supported |
| 1.21.6 | 1.21.7 | Supported |
| 1.21.7 | 1.21.8 | Supported |
| 1.21.8 | 1.21.9 | Supported |
| 1.21.9 | 1.21.10 | Supported |
| 1.21.10 | 1.21.11 | Supported |

### Forge (11 shims)

| From | To | Status |
|------|------|--------|
| 1.20 (Forge) | 1.21 (NeoForge) | Supported (cross-loader migration) |
| 1.21 | 1.21.1 | Supported |
| 1.21.1 | 1.21.2 | Supported |
| 1.21.2 | 1.21.3 | Supported |
| 1.21.3 | 1.21.4 | Supported |
| 1.21.4 | 1.21.5 | Supported |
| 1.21.5 | 1.21.6 | Supported |
| 1.21.6 | 1.21.7 | Supported |
| 1.21.7 | 1.21.8 | Supported |
| 1.21.8 | 1.21.9 | Supported |
| 1.21.9 | 1.21.10 | Supported |

> **Note:** Forge is less active than NeoForge for 1.21.x, but it does have releases up to 1.21.11 (Forge 61.1.3). RetroMod supports both. The Forge 1.20 to NeoForge 1.21 shim also handles migrating old Forge mods to NeoForge.

## Quick Start

### Fabric

1. Download `retromod-1.0.0-beta.1.jar`
2. Place **only** RetroMod in your `mods/` folder (no other mods yet)
3. Launch Minecraft once, then close it
4. Add your old mods to the `mods/` folder
5. Launch again -- your old mods should work!

> **Why the two-step process?** Fabric blocks mods targeting older Minecraft versions. RetroMod's first launch creates a config that bypasses this check so old mods can load and be transformed.

### Forge / NeoForge

1. Install RetroMod in your `mods/` folder
2. Add your old mods (even Forge mods on NeoForge!)
3. Launch the game
4. RetroMod auto-transforms incompatible mods
5. Restart when prompted -- done!

> RetroMod backs up originals to `mods/retromod-backups/` before transforming.

### Uninstalling

- **Fabric:** Also delete `config/fabric_loader_dependencies.json`
- **Forge/NeoForge:** Restore mods from `mods/retromod-backups/` if needed

### Java Agent Mode (Advanced)

```bash
java -javaagent:retromod-1.0.0-beta.1-agent.jar -jar minecraft.jar
```

### CLI Tool (Advanced)

```bash
# Analyze a mod for compatibility issues
java -jar retromod-1.0.0-beta.1-all.jar analyze mymod.jar

# AOT compile a mod manually
java -jar retromod-1.0.0-beta.1-all.jar aot mymod.jar

# Batch process all mods in a folder
java -jar retromod-1.0.0-beta.1-all.jar batch mods/ --aot

# List all registered shims
java -jar retromod-1.0.0-beta.1-all.jar shims
```

## Building from Source

Requires **Java 21+** and **Maven 3.8+**.

```bash
# Clone the repository
git clone https://github.com/Bownlux/MC-RetroMod.git
cd MC-RetroMod

# Build all artifacts
mvn clean package

# Build outputs:
#   target/retromod-1.0.0-beta.1.jar          - Main JAR
#   target/retromod-1.0.0-beta.1-all.jar      - Shaded JAR (all dependencies bundled)
#   target/retromod-1.0.0-beta.1-agent.jar    - Java Agent JAR
#   target/retromod-1.0.0-beta.1-sources.jar  - Source JAR
```

Build profiles:

```bash
mvn clean package -Pcli       # CLI-only build
mvn clean package -Pfabric    # Fabric mod build
mvn clean package -Pneoforge  # NeoForge mod build
```

## Architecture

### How It Works

```
┌─────────────────────────────────────────────────────────┐
│                   MOD JAR INPUT                         │
│           (built for older MC version)                  │
└────────────────────────┬────────────────────────────────┘
                         │
                         v
┌─────────────────────────────────────────────────────────┐
│                  CLASS ANALYSIS                         │
│  - Detect mod loader type (Fabric / NeoForge / Forge)  │
│  - Detect target MC version from metadata              │
│  - Analyze bytecode instruction-by-instruction         │
└────────────────────────┬────────────────────────────────┘
                         │
                         v
┌─────────────────────────────────────────────────────────┐
│             SHIM CHAIN RESOLUTION                       │
│                                                         │
│  ShimRegistry finds the shortest path:                  │
│  e.g. 1.21 → 1.21.1 → 1.21.2 → ... → 1.21.11          │
│  Each shim registers method/class/field redirects       │
└────────────────────────┬────────────────────────────────┘
                         │
                         v
┌─────────────────────────────────────────────────────────┐
│             BYTECODE TRANSFORMATION                     │
│                                                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │   FULL AOT   │  │ PARTIAL AOT  │  │  JIT ONLY    │  │
│  │ Method/class │  │ Most insns   │  │ Reflection,  │  │
│  │ redirects,   │  │ transformed, │  │ dynamic      │  │
│  │ field renames │  │ some marked  │  │ dispatch,    │  │
│  │              │  │ for JIT      │  │ obfuscated   │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────────┬────────────────────────────────┘
                         │
                         v
┌─────────────────────────────────────────────────────────┐
│                 TRANSFORMED OUTPUT                      │
│  - Rewritten bytecode with correct method/class refs    │
│  - Embedded API shims for removed functionality         │
│  - Updated Mixin annotation targets                     │
│  - Valid StackMapTable frames (SafeClassWriter)          │
└─────────────────────────────────────────────────────────┘
```

### Core Components

| Component | Description |
|-----------|-------------|
| `RetroModTransformer` | Core ASM-based bytecode transformer with SafeClassWriter for modded classloader compatibility |
| `HybridTransformationEngine` | Smart AOT/JIT engine -- pre-compiles where possible, falls back to JIT |
| `ShimRegistry` | BFS-based shim chain finder for multi-version transitions |
| `VersionShim` | Interface for version-specific compatibility shims (ServiceLoader discoverable) |
| `MixinCompatibilityTransformer` | Transforms Mixin annotations to target renamed methods |
| `EnvironmentDetector` | Detects OS, CPU arch, rendering backend, client/server mode |
| `RenderingBackendShim` | Future-proof rendering compat (OpenGL / Vulkan / Metal) |
| `SafeCrashHandler` | Graceful error handling that only intercepts RetroMod-transformed class errors |
| `ModVersionDetector` | Reads mod metadata from `fabric.mod.json`, `mods.toml`, `neoforge.mods.toml` |

### Rendering Backend Compatibility

RetroMod is future-proofed for Minecraft's eventual transition away from OpenGL:

| Backend | Status | Notes |
|---------|--------|-------|
| **OpenGL** | Current default | Full support, no redirects needed |
| **Vulkan** | Ready | GlStateManager redirects, GLSL-to-SPIR-V shader compat, render pass abstraction |
| **Metal** | Ready | macOS/Apple Silicon, includes Vulkan redirects + texture format compat |
| **DirectX** | Planned | Windows-only, detection in place |

When Minecraft switches rendering backends, mods that call low-level APIs (`GlStateManager`, direct LWJGL, custom shaders) will break. RetroMod intercepts those calls and redirects them to backend-appropriate implementations.

### Multi-Architecture Support

| Architecture | Status |
|-------------|--------|
| x86_64 (Intel/AMD) | Fully supported |
| ARM64 / AArch64 (Apple Silicon, RPi 4+) | Fully supported |
| x86 (32-bit legacy) | Supported |
| ARM (32-bit) | Supported |
| RISC-V 64 | Detected, experimental |

## Configuration

Place `config.properties` in `config/retromod/`:

```properties
# Enable AOT compilation (faster subsequent launches)
use_aot=true

# Use hybrid compilation (partial AOT + JIT fallback)
use_hybrid=true

# Instruction-level granularity for partial AOT
instruction_level_granularity=true

# Transform Mixin targets
transform_mixins=true

# Enable reflection remapping
remap_reflection=true

# Log level: ERROR, WARN, INFO, DEBUG, TRACE
log_level=INFO
```

## Major API Changes Handled

### Fabric API

| Transition | Change | How RetroMod Handles It |
|-----------|--------|------------------------|
| 1.21 to 1.21.2 | `Identifier` constructor made protected | Redirect to `Identifier.of()` |
| 1.21 to 1.21.2 | `FabricDimensions.teleport` removed | Shim bridges to `Entity.teleportTo()` |
| 1.21.5 to 1.21.6 | HUD API completely rewritten | `HudRenderCallbackShim` adapts old callbacks |
| 1.21.5 to 1.21.6 | Material API removed | No-op shim |
| 1.21.8 to 1.21.9 | `Entity.getWorld()` renamed to `getEntityWorld()` | Direct method redirect |
| 1.21.8 to 1.21.9 | `ResourceManagerHelper` replaced by `ResourceLoader` | API bridge shim |

### NeoForge

| Transition | Change | How RetroMod Handles It |
|-----------|--------|------------------------|
| 1.21.8 to 1.21.9 | `IItemHandler` replaced by `ResourceHandler` | Compatibility wrappers |
| 1.21.8 to 1.21.9 | `KeyMapping.Category` now a record | `KeyMappingShim` creates categories |
| 1.21.10 to 1.21.11 | `ResourceLocation` renamed to `Identifier` | Class redirect |
| 1.21.10 to 1.21.11 | `javax.annotation.Nullable` replaced by `org.jspecify` | Annotation redirect |

### Forge

| Transition | Change | How RetroMod Handles It |
|-----------|--------|------------------------|
| 1.20 to 1.21 (NeoForge) | Massive package/API rename (`MinecraftForge` to `NeoForge`) | Cross-loader migration shim |
| 1.21.8 to 1.21.9 | `Entity.getWorld()` renamed | Direct method redirect |

## API for Mod Developers

Check if RetroMod is present and query transformation info:

```java
boolean retroModPresent = FabricLoader.getInstance()
    .isModLoaded("retromod");

if (retroModPresent) {
    RetroMod.getTransformationInfo("yourmodid")
        .ifPresent(info -> {
            logger.info("RetroMod transformation active:");
            logger.info("  Source: " + info.sourceVersion());
            logger.info("  Target: " + info.targetVersion());
            logger.info("  Shims: " + info.shimsApplied());
        });
}
```

## Troubleshooting

### Mod crashes with `NoSuchMethodError`

The method was likely renamed in a newer version. Use the CLI to check:

```bash
java -jar retromod-1.0.0-beta.1-all.jar analyze yourmod.jar
```

### Mod loads but features don't work

Some APIs were removed without replacement. Check the compatibility tables above.

### Clear the AOT cache

If transforms seem stale or broken:

```bash
java -jar retromod-1.0.0-beta.1-all.jar clean
```

### Enable debug logging

```properties
# In config/retromod/config.properties
log_level=DEBUG
log_transformations=true
dump_bytecode=true
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add your shim in `src/main/java/com/retromod/shim/`
4. Register it in `META-INF/services/com.retromod.core.VersionShim`
5. Add tests in `src/test/java/com/retromod/`
6. Submit a pull request

### Adding a New Version Shim

```java
package com.retromod.shim.fabric;

import com.retromod.core.*;

public class Fabric_X_to_Y implements VersionShim {
    @Override public String getShimName() { return "Fabric X to Y"; }
    @Override public String getSourceVersion() { return "X"; }
    @Override public String getTargetVersion() { return "Y"; }
    @Override public String getModLoaderType() { return "fabric"; }

    @Override
    public void registerRedirects(RetroModTransformer transformer) {
        // Redirect a renamed method
        transformer.registerMethodRedirect(
            "old/Owner", "oldMethod", "(args)return",
            "new/Owner", "newMethod", "(args)return"
        );

        // Redirect a renamed class
        transformer.registerClassRedirect(
            "old/ClassName",
            "new/ClassName"
        );
    }
}
```

## Known Limitations

1. **Cannot transform already-loaded classes** without Java Agent mode
2. **Complex Mixins** may need manual shim updates for non-standard patterns
3. **Only mod loader APIs** are shimmed (not removed Minecraft internal classes)
4. **Cross-loader mods** (running a Forge mod on Fabric) are not supported
5. **Rendering backend shims** activate only when Minecraft actually switches backends

## License

[MIT License](LICENSE) -- Copyright (c) 2026 RevivalSMP

## Credits

- **Bownlux** -- Original author
- **[RevivalSMP.net](https://revivalsmp.net)** -- Development and hosting
- **[ASM](https://asm.ow2.io/)** -- Bytecode manipulation library
- **[FabricMC](https://fabricmc.net/)** -- Fabric API and mod loader
- **[NeoForged](https://neoforged.net/)** -- NeoForge API and mod loader
- **[MinecraftForge](https://minecraftforge.net/)** -- Forge mod loader

---

*RetroMod is not affiliated with Mojang, Microsoft, FabricMC, NeoForged, or MinecraftForge.*
