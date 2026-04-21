---
title: Troubleshooting
nav_order: 8
---

# Troubleshooting

This page covers the most common ways RetroMod surprises people, and how to fix them. If your problem isn't here, [open an issue](https://github.com/Bownlux/RetroMod/issues) — attach your `latest.log` and the contents of `config/retromod/verify-reports/` for the mod you're struggling with.

## My mod doesn't load

1. **Check `retromod-input/` and `retromod-backups/`.** If your mod is sitting in `retromod-input/` after a restart, RetroMod didn't process it — check the log for a RetroMod error on that filename. If it's in `mods/` but also in `retromod-backups/`, transformation ran at least once; look for runtime errors instead.

2. **Check `config/retromod/verify-reports/<modid>.txt`.** If the report lists missing classes or methods, RetroMod didn't find a mapping or shim for something the mod uses. See [Verify Transforms]({{ '/verify-transforms' | relative_url }}) for how to read the report, then file an issue or add the mapping yourself.

3. **Check your Fabric Loader version.** RetroMod targets the version of Fabric Loader bundled with the current target MC. If your loader is older, the mod's patched metadata (which declares `fabricloader: "*"`) is fine, but Fabric might still reject something else. Update to the latest loader for your MC version.

4. **Confirm the mod is actually in the right folder.** On macOS, instance-based launchers (Prism, MultiMC, ATLauncher) each have their own `mods/` folder. Make sure you dropped the JAR in the right one. See [Installation]({{ '/installation' | relative_url }}).

## My mod loads but crashes immediately

1. **Enable debug logging.** Set `"debug": true` and `"log_level": "DEBUG"` in `config/retromod/config.json`. Restart. The next crash will have a much more detailed RetroMod trace in `latest.log`.

2. **Enable bytecode dumps.** Set `"dump_bytecode": true`. This writes every transformed class to `config/retromod/bytecode-dumps/`. Open the class the crash points to (usually in the stack trace) with a decompiler like [Vineflower](https://github.com/Vineflower/vineflower) or CFR and see what actually came out of the transformer.

3. **Check the mod's mixins.** If the crash mentions `org.spongepowered.asm.mixin`, a mixin probably failed to apply. Disable `transform_mixins` temporarily to see whether the mod even attempts to load without mixin rewriting — if it does, you know the issue's in the mixin pass.

4. **Look for `NoSuchMethodError` / `NoClassDefFoundError`.** These mean a reference didn't get remapped or polyfilled. If the verify report for this mod is empty, it likely means the reference is inside a mixin target or a reflective call — try turning on `remap_reflection` if it's off.

## "unofficial build" or similar authenticity warning

Harmless in most cases. RetroMod doesn't block anything based on authenticity status — see [Authenticity]({{ '/authenticity' | relative_url }}) for the full breakdown. The short version:

- **UNSIGNED** — totally normal, especially for beta builds. Ignore.
- **UNOFFICIAL** — you're running a fork or a re-signed build. Fine if you trust where you got it.
- **TAMPERED** / **IMPOSTOR** — if you didn't modify the JAR yourself, re-download from the [official releases page](https://github.com/Bownlux/RetroMod/releases).

## Verify reports missing methods or classes

The verifier found references in the transformed mod that don't resolve against current Minecraft. Two things to try:

1. **Look at the referenced class.** If it starts with `net/minecraft/class_`, `method_`, or `field_`, it's an intermediary name that RetroMod doesn't know how to remap. This needs an entry in `mapping/IntermediaryToMojangMapper.java`.

2. **Look at the source MC version in the report header.** If the mod is 1.16.5 and the missing class is very old, a shim between 1.16 and 1.20 might be absent. Run `shims` in the [CLI]({{ '/cli' | relative_url }}) to see what's registered.

Either way, [file an issue](https://github.com/Bownlux/RetroMod/issues) with the report attached. These reports are exactly the input needed to add the missing mapping or shim, so they're genuinely useful to the project.

## Java version errors

You need **Java 25 or newer**. If you see something like:

```
UnsupportedClassVersionError: ... class file version 69.0
```

...your Java runtime is too old. ASM 9.8 (which RetroMod uses) only reads class file version 69 on Java 25+. Fixes:

- **Use Minecraft's bundled runtime.** Modern Minecraft launchers install the right Java for you — make sure the profile you're launching is using it rather than a system-wide Java 17 or 21.
- **Install Java 25 manually.** [Adoptium](https://adoptium.net/) has OpenJDK builds. After installing, point your launcher's profile at the new Java executable.

## RetroMod button is missing from the title screen

The mod didn't finish initializing. Check `latest.log` for `RetroMod` messages — there'll usually be an exception explaining what went wrong. Common causes:

- **Conflicting mod** that also modifies the title screen. RetroMod uses a mixin for the button; if another mod clobbers the same spot, one of them loses.
- **Wrong game directory** — the JAR is in the right folder for a different Minecraft instance than the one you're launching.
- **Fabric API missing** — RetroMod requires Fabric API on Fabric installs.

## Game loads but transformed mods aren't being picked up

Make sure they're in `mods/`, not `retromod-input/`. `retromod-input/` is the inbox — after RetroMod runs, successfully-transformed mods move to `mods/`. If your mod is stuck in `retromod-input/`, transformation failed — check the log.

## AOT cache seems stale

After updating RetroMod, delete `config/retromod/aot-cache/`. The cache's hash includes the source mod but not the RetroMod version, so an old cache can produce old output even with a new RetroMod JAR. Deleting the folder forces a fresh compile.

## Nothing helps — time to file a bug

Collect:

1. `latest.log` from your Minecraft game directory.
2. The relevant verify report (if any) from `config/retromod/verify-reports/`.
3. The mod JAR you're trying to load, or a link to it.
4. Your `config/retromod/config.json`.
5. Your Minecraft version, loader (Fabric / NeoForge / Forge), and loader version.

Attach to a [new issue](https://github.com/Bownlux/RetroMod/issues/new). That set of files is enough to reproduce almost every bug.
