# Retromod - Claude Development Guide

Retromod transforms older Minecraft mod bytecode so old mods work on newer MC versions. It supports Fabric, NeoForge, and Forge with 145 version shims and 30 polyfill providers (328 redirects).

**Repository:** https://github.com/Bownlux/Retromod.git

## Critical Context

- **Target MC version:** 26.1 (Mojang removed ALL code obfuscation)
- **Java:** Built WITH Java 25 (we need the modern compiler to use ASM 9.8 features that read MC 26.1's class file format), but bytecode targets `--release 17` so the SAME JAR runs on Java 17, 21, and 25 — broad runtime compat is the entire point. `build-all.sh` sets the per-MC `"java"` constraint in fabric.mod.json based on what each MC version itself needs: `>=17` for MC 1.20–1.20.4, `>=21` for MC 1.20.5–1.21.x, **`>=25` for MC 26.x** (MC 26.1's own class files are Java 25 bytecode, so a Java 21 JVM can't load them). Don't accidentally bump `<release>` higher — that locks out MC 1.20.x users on Java 17. ASM 9.8 itself runs on Java 8+; it just READS class files up to v69 (Java 25), which has nothing to do with what bytecode WE emit.
- **Intermediary names are dead in 26.1+.** All `class_XXXX`, `method_XXXX`, `field_XXXX` must map to Mojang official names.
- **NeoForge already uses Mojang names** since 1.17 — NeoForge mods mainly need metadata patching, not name remapping.
- **Fabric mods use intermediary names** — they need full intermediary→Mojang remapping for 26.1+.
- **ALL old shims (including pre-1.20.1) must stay.** People still translate 1.16.5, 1.14.4 mods. Shims are NOT separate build targets — they're all part of one build.
- **`Retromod.TARGET_MC_VERSION`** is auto-detected at runtime from the mod loader. NEVER hardcode version strings like `"1.21.11"` — always use `Retromod.TARGET_MC_VERSION`.

## Architecture

```
src/main/java/com/retromod/
├── core/           Core runtime: Retromod, RetromodTransformer, version detectors, mod transformers
├── cli/            CLI tool: RetromodCli (analyze, batch, aot, shims, etc.)
├── aot/            AOT compiler: AotCompiler, HybridCompiler, FullAotCompiler
├── shim/           Version shims by loader (fabric/, neoforge/, forge/, api/)
│   └── ShimRegistry.java   BFS-based shim chain finder with version aliases
├── mapping/        IntermediaryToMojangMapper, MappingComposer (26.1 core feature)
├── mixin/          Mixin compatibility: MixinCompatibilityTransformer, MixinTargetRedirector
├── polyfill/       Removed API reimplementations (72+ polyfills across 10 providers)
├── embedder/       API embedding into mod JARs, ModVersionInfo record
├── resources/      Resource/data pack transforms
├── gui/            In-game GUI (title screen button, file picker, restart popup)
├── agent/          Java Agent mode (premain/agentmain)
├── legacy/         Legacy version support utilities
├── compat/         Compatibility layer
├── archive/        Archive handling
├── util/           Utilities
└── virtual/        Virtual filesystem/classes
```

## Build Commands

```bash
# Quick build (skip tests)
mvn package -q -DskipTests -Dexec.skip=true

# Full build with tests
mvn package -Dexec.skip=true

# Run tests only
mvn test -Dexec.skip=true

# Lite build (1.20+ only, smaller JAR, no legacy/third-party polyfills)
mvn package -P lite -DskipTests -Dexec.skip=true

# Run CLI command (deps not bundled in JAR, must use mvn exec)
mvn exec:java -Dexec.mainClass="com.retromod.cli.RetromodCli" -Dexec.args="<command>" -q
```

**Important:** Always pass `-Dexec.skip=true` during build to prevent Maven from running the CLI entrypoint.

Output JAR: `target/retromod-1.0.0-beta.7.jar`

## Deploy to Minecraft

```bash
cp target/retromod-1.0.0-beta.7.jar ~/Library/Application\ Support/minecraft/mods/retromod-1.0.0-beta.7+26.1.jar
```

Game directory (macOS): `~/Library/Application Support/minecraft/`

## Key Files

| File | Purpose |
|------|---------|
| `core/Retromod.java` | Main Fabric ModInitializer, `TARGET_MC_VERSION` auto-detection |
| `core/RetromodPreLaunch.java` | Fabric pre-launch (runs BEFORE mod scan, transforms mods) |
| `core/RetromodNeoForge.java` | NeoForge entry point (transforms at constructor time) |
| `core/RetromodForge.java` | Forge entry point |
| `core/RetromodTransformer.java` | ASM bytecode transformer (class/method/field redirects) |
| `core/FabricModTransformer.java` | Patches fabric.mod.json version constraints |
| `core/ForgeModTransformer.java` | Patches mods.toml/neoforge.mods.toml version constraints |
| `core/ModVersionDetector.java` | Reads mod MC version from loader-specific metadata |
| `mapping/IntermediaryToMojangMapper.java` | ~230 intermediary→Mojang name mappings |
| `mapping/MappingComposer.java` | Generates mapping files from TinyV2 + ProGuard sources |
| `shim/ShimRegistry.java` | BFS chain finder with version aliases |
| `cli/RetromodCli.java` | CLI tool (`TARGET_MC_VERSION = "26.1"`) |
| `embedder/ModVersionInfo.java` | Record with `needsTransformation()` version comparison |
| `aot/AotCompiler.java` | AOT compilation with metadata patching for 26.1+ |

## How Mod Transformation Works

1. **Fabric:** `RetromodPreLaunch` runs before Fabric scans `mods/`. Transforms mods from `retromod-input/`, patches `fabric.mod.json`, moves to `mods/`. User restarts. Old mods CANNOT go directly in `mods/` — Fabric rejects them.

2. **NeoForge/Forge:** `RetromodNeoForge`/`RetromodForge` constructor transforms from `retromod-input/` AND in-place in `mods/`. Patches `mods.toml`. Backs up originals to `retromod-backups/`.

3. **CLI batch:** `RetromodCli.batchCommand()` processes all JARs in a folder. For 26.1+ targets, ALL mods get metadata patching via `patchModMetadata()` post-processing step, even if bytecode doesn't need transformation.

## Version Constraint Patching

**This is critical for 26.1.** Old mods have version ranges like `[1.21,1.21.1)` which reject MC 26.1.

- **Fabric:** Replace `"minecraft"` with exact target version, relax fabricloader/fabric-api to `"*"`
- **NeoForge/Forge:** Widen minecraft `versionRange` to `[<lower>,)`, make non-core deps `type="optional"`, handle both bracket ranges and bare versions
- The `ForgeModTransformer.updateMinecraftVersionRange()` method processes TOML line-by-line tracking `[[dependencies.modid]]` blocks

## ServiceLoader Registration

Shims and polyfills are discovered via ServiceLoader:
- `META-INF/services/com.retromod.core.VersionShim` — 145 entries
- `META-INF/services/com.retromod.polyfill.PolyfillProvider` — 55 entries

When adding a new shim or polyfill, ALWAYS register it in the corresponding services file.

## Testing

- **Framework:** JUnit 5 (Jupiter)
- **Test file:** `src/test/java/com/retromod/RetromodTest.java`
- **Run:** `mvn test -Dexec.skip=true`

## CI/CD

- **File:** `.github/workflows/ci.yml`
- **Java:** 25
- **Features:** Break-glass bypass (push within 5 min of revert skips tests), auto-revert failed pushes, auto-close failed PRs, CI-passed labels
- **Important:** The linter/CI may revert changes it doesn't like. If that happens, check what was reverted and fix accordingly.

## Common Pitfalls

1. **Don't hardcode `"1.21.11"` anywhere.** Use `Retromod.TARGET_MC_VERSION`. The linter has reverted this multiple times.

2. **Don't delete old shims.** Every shim from 1.12.2 onwards must stay — people translate ancient mods.

3. **TOML parsing is fragile.** The simple TOML parser can't handle `[[dependencies.modid]]` array-of-tables properly (entries overwrite each other). Use `extractMcVersionFromToml()` regex approach instead.

4. **AOT cache invalidation.** The AOT cache checks source mod hash but NOT Retromod version. After changing Retromod's transform logic, delete `config/retromod/aot-cache/` to force re-compilation.

5. **`needsTransformation()` returns false for null targetMcVersion.** If `ModVersionDetector` can't read the version, the mod gets skipped. For 26.1+, the batch command has a separate post-processing step to patch metadata even for "compatible" mods.

6. **Fabric is strictest.** Fabric checks mod versions BEFORE Retromod runs. That's why `retromod-input/` exists — mods get transformed there first, then moved to `mods/`.

7. **The JAR doesn't bundle dependencies.** You can't run `java -jar retromod.jar` directly for CLI — use `mvn exec:java` instead.

## Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| ASM | 9.8 | Bytecode manipulation (9.8 required for Java 25) |
| Gson | 2.10.1 | JSON parsing |
| SLF4J | 2.0.9 | Logging |
| JUnit 5 | 5.10.1 | Testing |
| Mixin | 0.8.5 | Mixin API (provided) |
| Fabric Loader | 0.16.10 | Fabric API (provided) |

## Skills

Development skills are in `.claude/skills/`:

| Skill | Use when... |
|-------|-------------|
| `add-version-shim` | Adding support for a new MC version transition |
| `add-polyfill` | Reimplementing a removed API using modern equivalents |
| `mapping-work` | Working with intermediary/Mojang/SRG name mappings |
| `mod-loader-compat` | Fixing Fabric/NeoForge/Forge loading issues |
| `test-mod-transform` | Testing mod transforms end-to-end |
| `debug-crash` | Diagnosing crashes from transformed mods |
| `build-and-deploy` | Building and deploying to Minecraft |
| `modrinth-api` | Downloading test mods from Modrinth |
