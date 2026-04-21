---
title: Installation
nav_order: 2
---

# Installation

RetroMod installs like any other Minecraft mod — drop the JAR in your `mods/` folder and launch the game. There's no installer, no wizard, no account to make.

## Requirements

- **Minecraft:** 26.1.2 (the current target; older targets are in the works)
- **Mod loader:** Fabric, NeoForge, or Forge
- **Java:** 25 or newer (Minecraft 26.1 ships its own Java runtime, so this is usually taken care of automatically)

## Get the JAR

Grab the latest release from GitHub:

> [github.com/Bownlux/RetroMod/releases](https://github.com/Bownlux/RetroMod/releases)

The file you want is named something like `retromod-1.0.0-beta.1.jar`. Download it somewhere you'll remember — your Downloads folder is fine.

## Find your Minecraft game directory

This is where your `mods/` folder lives. The exact path depends on your OS:

| OS | Path |
|----|------|
| **macOS** | `~/Library/Application Support/minecraft/` |
| **Windows** | `%APPDATA%\.minecraft\` |
| **Linux** | `~/.minecraft/` |

If you're using a launcher like Prism, MultiMC, or ATLauncher, each instance has its own game directory — open the instance's folder in the launcher instead.

## Install the mod

1. Open your game directory.
2. If there's no `mods/` folder, create one.
3. Drop `retromod-1.0.0-beta.1.jar` into `mods/`.
4. Launch Minecraft.

That's it. On macOS, the command-line version of step 3 looks like:

```bash
cp ~/Downloads/retromod-1.0.0-beta.1.jar \
  ~/Library/Application\ Support/minecraft/mods/
```

## First launch

The first time RetroMod runs, it creates two folders inside your game directory:

- **`config/retromod/`** — holds `config.json` and subfolders like `verify-reports/` and `aot-cache/`. Safe to edit, safe to delete (RetroMod will regenerate defaults).
- **`retromod-input/`** — the inbox. Drop old mods here and RetroMod will transform them on the next launch, then move them to `mods/`. This is the only way to install old Fabric mods — Fabric rejects them before RetroMod gets a chance to help if they're placed directly in `mods/`.

After first launch, you'll see a **RetroMod** button on the title screen. That's your sign everything's wired up.

## Updating

Delete the old `retromod-*.jar` from `mods/` and drop in the new one. Your config is kept. If you've been using the AOT cache and RetroMod's transform logic has changed, clear `config/retromod/aot-cache/` so mods are re-compiled with the new logic.

## Uninstalling

Remove the JAR from `mods/`. Your transformed mods stay transformed — they live in `mods/` as regular JARs now, not linked to RetroMod anymore. To revert a mod, copy the original from `retromod-backups/` back into `mods/`.
