# Retromod Compatibility List

> **Retromod v1.0.0-beta.4** | **Target: Minecraft 26.1** | **Fabric Loader 0.18.4**
>
> Last updated: 2026-03-10

Retromod enables older Fabric mods to run on newer Minecraft versions by applying bytecode transformations.
It covers MC **1.14.4 through 1.21.11** with 31 Fabric shims, 317+ method redirects, and 486+ class redirects.

All mods listed here were downloaded exclusively from **[Modrinth](https://modrinth.com)** (a curated, reviewed mod repository).

> **About the `*` on entries below:** "Successfully transformed" in this document means **the mod's bytecode passed Retromod's pipeline and the mod loader accepts it on the target MC version** — i.e. it loads. It does **not** mean every in-game feature works. In practice **most mods on this list only get to the loading stage** — Minecraft starts, the mod registers, log lines for it appear — but specific features (rendering hooks, mixin injections, registry-keyed accessors, NBT round-trips, etc.) commonly fail at runtime once they're actually exercised. Each entry below is marked with `*` to make that limitation explicit. Closing those runtime gaps is what Retromod's beta phase is for; if you hit a broken feature in a listed mod, please [file an issue with the log](https://github.com/Bownlux/Retromod/issues).

---

## Mods That Load After Transformation*

All mods below were transformed using the Retromod CLI (`retromod batch`) and have their version constraints
relaxed to load on MC 1.21.11. They are sorted by original MC version (oldest first).

> **`*` (only to the loading stage):** every entry here passes the load, but in-game functionality varies and is **not guaranteed** for any specific mod. See the note above the section.

### MC 1.16.5 (10 version hops to 1.21.11)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **Cloth Config** | 4.17.101 | Config Library | [Link](https://modrinth.com/mod/cloth-config) |
| **Lithium** | 0.6.6 | Server Optimization | [Link](https://modrinth.com/mod/lithium) |
| **LambDynamicLights** | 1.3.4 | Dynamic Lighting | [Link](https://modrinth.com/mod/lambdynamiclights) |

> **Note:** LambDynamicLights requires SpruceUI (not included). Lithium & Cloth Config are standalone.

### MC 1.17.1 (9 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **Lithium** | 0.7.5 | Server Optimization | [Link](https://modrinth.com/mod/lithium) |
| **Mod Menu** | 2.0.17 | Mod List UI | [Link](https://modrinth.com/mod/modmenu) |
| **LambDynamicLights** | 2.1.0 | Dynamic Lighting | [Link](https://modrinth.com/mod/lambdynamiclights) |

### MC 1.18.2 (8 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **Sodium** | 0.4.1 | Rendering Optimization | [Link](https://modrinth.com/mod/sodium) |
| **Lithium** | 0.10.3 | Server Optimization | [Link](https://modrinth.com/mod/lithium) |
| **Mod Menu** | 3.2.5 | Mod List UI | [Link](https://modrinth.com/mod/modmenu) |
| **AppleSkin** | 2.5.1 | Food Info HUD | [Link](https://modrinth.com/mod/appleskin) |
| **Iris Shaders** | 1.6.11 | Shader Support | [Link](https://modrinth.com/mod/iris) |

### MC 1.19 (7 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **Not Enough Crashes** | 4.1.6 | Crash Recovery | [Link](https://modrinth.com/mod/notenoughcrashes) |
| **Continuity** | 2.0.2 | Connected Textures | [Link](https://modrinth.com/mod/continuity) |
| **AppleSkin** | 2.4.1 | Food Info HUD | [Link](https://modrinth.com/mod/appleskin) |
| **LambDynamicLights** | 2.2.0 | Dynamic Lighting | [Link](https://modrinth.com/mod/lambdynamiclights) |

### MC 1.19.2 (6 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **CIT Resewn** | 1.1.2 | Custom Item Textures | [Link](https://modrinth.com/mod/cit-resewn) |
| **Architectury API** | 6.6.92 | Cross-loader API | [Link](https://modrinth.com/mod/architectury-api) |
| **Cloth Config** | 8.3.134 | Config Library | [Link](https://modrinth.com/mod/cloth-config) |
| **Not Enough Crashes** | 5.0.0 | Crash Recovery | [Link](https://modrinth.com/mod/notenoughcrashes) |
| **AppleSkin** | 2.5.1 | Food Info HUD | [Link](https://modrinth.com/mod/appleskin) |

### MC 1.19.4 (5 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **BetterF3** | 6.0.3 | Debug Screen | [Link](https://modrinth.com/mod/betterf3) |
| **Mouse Tweaks** | 2.24 | Inventory QoL | [Link](https://modrinth.com/mod/mouse-tweaks) |
| **Light Overlay** | 7.0.3 | Light Level Display | [Link](https://modrinth.com/mod/light-overlay) |
| **Continuity** | 3.0.0 | Connected Textures | [Link](https://modrinth.com/mod/continuity) |

> **Note:** Light Overlay requires Architectury API (not included in default setup).

### MC 1.20.1 (4 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **Sodium** | 0.5.13 | Rendering Optimization | [Link](https://modrinth.com/mod/sodium) |
| **Iris Shaders** | 1.7.6 | Shader Support | [Link](https://modrinth.com/mod/iris) |
| **Jade** | 11.13.1 | Tooltip Info (WAILA) | [Link](https://modrinth.com/mod/jade) |
| **FerriteCore** | 6.0.1 | Memory Optimization | [Link](https://modrinth.com/mod/ferrite-core) |
| **Debugify** | 2.0 | Bug Fixes | [Link](https://modrinth.com/mod/debugify) |
| **Orbital Railgun** | 1.1 | Weapon Mod | [Link](https://modrinth.com/mod/orbital-railgun) |

### MC 1.20.4 (3 version hops)

| Mod | Version | Type | Modrinth |
|-----|---------|------|----------|
| **Sodium** | 0.5.8 | Rendering Optimization | [Link](https://modrinth.com/mod/sodium) |
| **No Chat Reports** | 2.6.1 | Privacy | [Link](https://modrinth.com/mod/no-chat-reports) |
| **e4mc** | 6.0.6 | LAN Over Internet | [Link](https://modrinth.com/mod/e4mc) |
| **Simple Voice Chat** | 2.5.22 | Voice Chat | [Link](https://modrinth.com/mod/simple-voice-chat) |
| **Zoomify** | 2.15.2 | Zoom | [Link](https://modrinth.com/mod/zoomify) |

### MC 1.20.5-1.20.6 (2 version hops)

| Mod | Version | Original MC | Type | Modrinth |
|-----|---------|-------------|------|----------|
| **Dynamic FPS** | 3.11.4 | 1.20.5 | FPS Management | [Link](https://modrinth.com/mod/dynamic-fps) |
| **Iris Shaders** | 1.7.2 | 1.20.6 | Shader Support | [Link](https://modrinth.com/mod/iris) |
| **Jade** | 14.2.4 | 1.20.6 | Tooltip Info | [Link](https://modrinth.com/mod/jade) |
| **REI** | 15.0.787 | 1.20.6 | Recipe Viewer | [Link](https://modrinth.com/mod/rei) |

### MC 1.21-1.21.4 (1-2 version hops)

| Mod | Version | Original MC | Type | Modrinth |
|-----|---------|-------------|------|----------|
| **Sodium** | 0.6.13 | 1.21.1 | Rendering Optimization | [Link](https://modrinth.com/mod/sodium) |
| **FerriteCore** | 7.0.0 | 1.21 | Memory Optimization | [Link](https://modrinth.com/mod/ferrite-core) |
| **Debugify** | 1.0 | 1.21 | Bug Fixes | [Link](https://modrinth.com/mod/debugify) |
| **Simple Voice Chat** | 2.6.12 | 1.21.1 | Voice Chat | [Link](https://modrinth.com/mod/simple-voice-chat) |
| **Capes** | 1.5.5 | 1.21 | Cosmetic Capes | [Link](https://modrinth.com/mod/capes) |
| **Carry On** | 2.3.0.29 | 1.21.4 | Pick Up Blocks | [Link](https://modrinth.com/mod/carry-on) |

---

## Recommended Test Setup (21 mods simultaneously)*

These mods were selected to run together (one per unique mod, picking the oldest version). Same `*` caveat as above — the set loads, but individual feature-level functionality isn't guaranteed for any of them yet.

| # | Mod | From MC | Hops | Notes |
|---|-----|---------|------|-------|
| 1 | Cloth Config | 1.16.5 | 10 | Config library, standalone |
| 2 | Lithium | 1.17.1 | 9 | Server perf, minimal API surface |
| 3 | AppleSkin | 1.18.2 | 8 | HUD overlay, standalone |
| 4 | Mod Menu | 1.18.2 | 8 | Uses Fabric Screen API |
| 5 | Not Enough Crashes | 1.19 | 7 | Crash recovery |
| 6 | Continuity | 1.19 | 7 | Connected textures |
| 7 | CIT Resewn | 1.19.2 | 6 | Custom item textures |
| 8 | BetterF3 | 1.19.4 | 5 | Debug screen |
| 9 | Mouse Tweaks | 1.19.4 | 5 | Inventory QoL |
| 10 | Jade | 1.20.1 | 4 | WAILA/tooltip info |
| 11 | FerriteCore | 1.20.1 | 4 | Memory optimization |
| 12 | Debugify | 1.20.1 | 4 | Bug fixes |
| 13 | Orbital Railgun | 1.20.1 | 4 | Content mod |
| 14 | No Chat Reports | 1.20.4 | 3 | Privacy |
| 15 | e4mc | 1.20.4 | 3 | LAN over internet |
| 16 | Dynamic FPS | 1.20.5 | 2 | FPS management |
| 17 | REI | 1.20.6 | 2 | Recipe viewer |
| 18 | Zoomify | 1.20.4 | 3 | Zoom (needs Kotlin + YACL) |
| 19 | Capes | 1.21 | 2 | Cosmetic capes |
| 20 | Carry On | 1.21.4 | 1 | Pick up blocks/entities |
| 21 | Voice Chat | 1.21.1 | 1 | Simple Voice Chat |

### Required Dependencies

| Dependency | Version | Purpose |
|-----------|---------|---------|
| **Fabric API** | 0.141.3+1.21.11 | Core Fabric APIs |
| **Fabric Language Kotlin** | 1.13.9 | For Zoomify & Kotlin mods |
| **YACL** | 3.8.2+1.21.11 | For Zoomify's config |
| **Retromod** | 1.0.0-beta.4 | The transformation engine |

---

## Mods That Can Never Be Translated

The mods below sit in a category separate from "skipped this run." They can't be made to work by any automated bytecode translator — Retromod or otherwise — and never will be. The full reasoning, the general rules behind the list, and the difference between "won't load" and "loads but features are broken" lives on its own page: **[Mods That Can't Be Translated](https://bownlux.github.io/Retromod/incompatible-mods)**.

The short version:

| Mod | Why it's a hard "no" |
|---|---|
| **Create** | Ships Flywheel (custom GL-level rendering library) and rewrites internals between MC versions. |
| **Applied Energistics 2 (AE2)** | Deep MC integration, custom networking protocol, channel/quantum systems re-architected each version. |
| **Tinkers' Construct** | Tool/material system replaced at JSON-schema *and* bytecode level; schemas change too much to redirect. |
| **IndustrialCraft / IC2** | Coremod heritage, energy network internals tied to MC's tick scheduler. |
| **Thaumcraft** | Aspect/research data tied to MC internals; multiple full redesigns. |
| **OptiFine** | Proprietary, closed-source, Forge coremod — license + technical reasons both block translation. |
| **Sodium / Iris / Embeddium / Flywheel** | Replace MC's rendering pipeline; mixin injections target bytecode offsets that move between versions. They may *load* but full functionality is not realistic. |

**The general rule.** Any mod that uses native code (`.so` / `.dll` / `.dylib` files), ships its own bytecode transformer (coremod), uses a custom or modified Mixin framework, replaces MC's rendering pipeline, or interacts with MC at byte-level GPU buffers will **never** be translatable by any automated tool. See the [Mods That Can't Be Translated](https://bownlux.github.io/Retromod/incompatible-mods) page for the full breakdown.

---

## Skipped Mods

These were skipped in a specific test run for solvable reasons (missing dependencies, deprecated standalone versions, etc.), not because they're permanently incompatible. Different from the "never" list above.

| Mod | Version | MC | Reason |
|-----|---------|-----|--------|
| **LambDynamicLights** | 1.3.4 | 1.16.5 | Needs SpruceUI dependency |
| **Light Overlay** | 7.0.3 | 1.19.4 | Needs Architectury API |
| **Essential** | 1.3.10.6 | 1.20.1 | 50MB, heavily obfuscated, proprietary |
| **Litematica** | 0.19.60 | 1.21 | Needs malilib dependency |
| **Freecam** | 1.3.5 | 1.18.2 | Very old, many API changes |
| **Presence Footsteps** | 1.8.2 | ? | No MC version in metadata |
| **Zoomify** (old) | 1.1.0 | 1.18.0 | Very old, use 2.15.2 instead |

---

## Resource Packs

| Resource Pack | MC Version | Status |
|--------------|-----------|--------|
| **Fresh Moves v3.1.1** (Animated Eyes) | 1.21.3+ | Fixed - added `min_format`/`max_format` fields |
| **Fresh Animations v1** (1.10.3 BETA) | 1.20.3+ | Works natively with overlays |

---

## Data Packs

No custom data packs were tested. Retromod can transform data packs in world save `datapacks/` folders.

---

## Batch Transformation Results

Total mods processed via CLI: **45 mods from Modrinth**

| Result | Count |
|--------|-------|
| Bytecode transformed (loaded successfully)* | 36 |
| Copied (already compatible) | 9 |
| Failed at transformation stage | 0 |

**Average transformation time: 209ms per mod**

> `*` "Transformed" here means the bytecode pipeline ran without error and the resulting JAR was accepted by the loader. It does **not** mean the mod runs without runtime errors once gameplay actually exercises its hooks. Most mods on the list have at least one feature that breaks in-game on the new MC version — see the section caveat above.

---

## Version Coverage

Retromod covers every Fabric version from 1.14.4 to 1.21.11:

```
MC 1.14.4 -> 1.15.2 -> 1.16.5 -> 1.17 -> 1.17.1 -> 1.18 -> 1.18.1 -> 1.18.2
-> 1.19 -> 1.19.1 -> 1.19.2 -> 1.19.3 -> 1.19.4 -> 1.20 -> 1.20.1 -> 1.20.2
-> 1.20.3 -> 1.20.4 -> 1.20.5 -> 1.20.6 -> 1.21 -> 1.21.1 -> 1.21.2 -> 1.21.3
-> 1.21.4 -> 1.21.5 -> 1.21.6 -> 1.21.7 -> 1.21.8 -> 1.21.9 -> 1.21.10 -> 1.21.11
```

---

## How to Use

### Method 1: Drop in retromod-input/ (Recommended)
1. Install Retromod in your `mods/` folder
2. Place old `.jar` mods into `mods/retromod-input/`
3. Launch Minecraft - Retromod auto-transforms during pre-launch

### Method 2: CLI Batch Transform
```bash
retromod batch ./old-mods/ --output ./transformed/
```

### Method 3: CLI Single Transform
```bash
retromod transform old-mod.jar --output new-mod-retromod.jar
```

---

## Known Limitations

1. **Loading vs functioning is a real distinction.** The `*` on every entry in this document means "loads, may not function." A mod showing up in the list above is *not* a guarantee that any specific feature of that mod actually works in-game. We're closing the runtime-functionality gap shim by shim during beta.
2. **Mixin-heavy mods** (Sodium, Iris, Lithium) may have runtime issues beyond bytecode redirection — mixins target specific bytecode positions that often move between MC versions, and a transformer can't synthesize a new injection point that didn't exist before.
3. **Mods needing specific Fabric API submodules** that were renamed/removed may need additional shims.
4. **Very old mods** (1.16.5 and earlier) have the most API changes to bridge — expect more rough edges here than on shorter translation distances.
5. **Kotlin mods** need Fabric Language Kotlin installed separately.
6. **Resource packs** for MC 1.21.11 need `min_format`/`max_format` in `pack.mcmeta`.

---

*Generated by Retromod v1.0.0-beta.4 | [Modrinth](https://modrinth.com/mod/retromod) | [GitHub](https://github.com/Bownlux/Retromod)*
*Made by the Developers of [revivalsmp.net](https://revivalsmp.net)*
