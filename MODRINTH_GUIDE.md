# RetroMod

**Run your old Minecraft mods on newer versions -- no changes needed.**

RetroMod automatically rewrites mod bytecode at load time so older mods just work on modern Minecraft. Drop it in your mods folder, add your old mods, and go. Works on Fabric, NeoForge, and Forge, on both clients and servers.

> This is a **beta release** (v1.0.0-beta.1). If something breaks, please [open an issue on GitHub](https://github.com/Bownlux/MC-RetroMod/issues) so we can fix it.

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

## What It Supports

**Mod loaders:** Fabric, NeoForge, Forge (including old Forge mods on NeoForge)

**Version transitions:** Every step from 1.21 through 1.21.11, with 33 built-in shims across all three loaders. Shims chain together, so a mod built for 1.21 can run on 1.21.11.

**What gets transformed:**
- Renamed/removed methods and classes
- Mixin annotations (`@Inject`, `@Redirect`, `@Shadow`, etc.)
- Reflection calls
- Mod loader API changes (Identifier, HUD API, Entity methods, Transfer API, etc.)

**What doesn't need transformation:**
- Mods already built for your Minecraft version pass through untouched
- RetroMod checks Modrinth first and will suggest downloading a native version if one exists

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
