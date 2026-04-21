---
title: In-game GUI
nav_order: 3
---

# Using the in-game GUI

RetroMod adds two small buttons to Minecraft's title screen — one opens a file picker for transforming mods, the other opens the settings screen. If you've never touched `config.json`, you can get by with just the GUI.

## The title screen buttons

After you install RetroMod and launch the game, two new buttons appear near the bottom of the title screen:

- **RetroMod** — opens the native OS file picker so you can pick mods to transform.
- **⚙** (gear icon) — opens RetroMod's settings screen.

If the buttons don't show up, the mod probably didn't load. Check the Minecraft log for RetroMod messages, and see [Troubleshooting]({{ '/troubleshooting' | relative_url }}).

## The RetroMod button (file picker)

Clicking **RetroMod** opens your operating system's native file picker — the same one you'd get from any other app. Multi-select is supported: hold Cmd/Ctrl to pick several JARs at once.

What happens next:

1. Selected JARs are copied into `retromod-input/` in your game directory.
2. A popup tells you how many mods were queued and asks you to restart the game.
3. On the next launch, RetroMod transforms each JAR, patches its version metadata, and moves the result to `mods/`.
4. Originals go to `retromod-backups/` so you can roll back if anything goes sideways.

The reason for the restart dance is that Fabric scans `mods/` very early — before RetroMod gets to run. If you drop a 1.20 Fabric mod directly into `mods/`, Fabric will refuse to boot. `retromod-input/` exists to give RetroMod first look at those JARs.

## The gear button (settings)

Clicking **⚙** opens a screen with every config option from `config.json` rendered as a toggle. Click a toggle to flip it; changes save automatically, no "apply" button to hunt for.

### What's on the screen

Each toggle has a short label and a one-line description. The options map 1:1 to keys in `config.json` — see [Config reference]({{ '/config' | relative_url }}) for the full list. Highlights:

- **Use AOT** — precompile transformed mods so subsequent launches are fast.
- **Use Hybrid** — blend AOT and runtime transformation for best-of-both behavior.
- **Transform Mixins** — rewrite mixin targets alongside the mod's regular bytecode.
- **Remap Reflection** — catch calls like `Class.forName("net.minecraft.old.Name")` and redirect them.
- **Polyfills Enabled** — inject replacement implementations for removed APIs.
- **Verify Transforms** — after transforming, scan the output JAR for references that don't exist in the current MC. Reports land in `config/retromod/verify-reports/`.
- **Debug** / **Dump Bytecode** — verbose logs and raw `.class` dumps for when you're chasing a specific bug.

### Authenticity status

Near the bottom of the settings screen is a small line showing your build's signature status: **OFFICIAL**, **UNSIGNED**, **UNOFFICIAL**, **TAMPERED**, or **IMPOSTOR**. This is informational — unsigned and unofficial builds work normally. See [Authenticity]({{ '/authenticity' | relative_url }}) for what each status means.

### Reset to defaults

There's a small **Reset** link at the bottom that wipes `config.json` back to defaults. It asks for confirmation first so you don't lose your settings by accident.

## When do I restart?

- **Changed a toggle?** Most options take effect on the next transformation, so restart when you want the change to apply to a newly-dropped mod.
- **Added mods via the picker?** Always restart — Fabric won't scan `retromod-input/` on its own.
- **Enabled Verify Transforms?** Restart and re-transform the mod you want to check.
