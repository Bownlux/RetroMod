---
title: Config reference
nav_order: 4
---

# Config reference

RetroMod's config file lives at `config/retromod/config.json` inside your Minecraft game directory. It's created with defaults on first launch. You can edit it by hand or flip toggles in the [in-game settings screen]({{ '/gui' | relative_url }}) — they edit the same file.

## Sample config.json

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
  "dump_bytecode": false,
  "force_translate_complex": false,
  "polyfills_enabled": true,
  "verify_transforms": true
}
```

## Keys

### Transformation behavior

#### `use_aot` — boolean, default `true`

Precompile transformed mods and cache the output in `config/retromod/aot-cache/`. Subsequent launches skip the transformation step entirely and load cached JARs directly, which is significantly faster.

Turn off if you're debugging transforms and want fresh output on every launch, or if you're low on disk space (the cache can get chunky for large modpacks).

#### `use_hybrid` — boolean, default `true`

Blend AOT with runtime transformation: cached classes are used when available, uncached ones are transformed on demand. Gives you AOT's speed without AOT's all-or-nothing cache semantics.

Only meaningful when `use_aot` is also `true`.

#### `instruction_level_granularity` — boolean, default `true`

Transform at the instruction level instead of the method level. Lets RetroMod rewrite individual `INVOKEVIRTUAL`s and field accesses without replacing whole methods. More precise, slightly slower.

Disable only if you're debugging a bug you suspect is caused by granular rewrites.

#### `transform_mixins` — boolean, default `true`

Rewrite mixin targets inside the mod JAR. If a mixin targets `net.minecraft.client.gui.GuiScreen#drawScreen`, RetroMod rewrites the annotation to target the modern equivalent. Disable only if a specific mixin framework is misbehaving and you'd rather the mod fail cleanly than be rewritten.

#### `transform_refmaps` — boolean, default `true`

Mixins ship with a refmap — a JSON file mapping intermediary names to SRG names. RetroMod rewrites the refmap so it matches post-transformation targets. Leave on unless you know you don't want this.

#### `remap_reflection` — boolean, default `true`

Catch calls like `Class.forName("net.minecraft.some.OldName")` and `ObfuscationReflectionHelper.getPrivateValue(...)`, and rewrite the string arguments to the new names. Reflection is common in older mods; without this, reflective access to MC internals fails at runtime.

#### `polyfills_enabled` — boolean, default `true`

Inject replacement implementations for APIs that were removed outright. Without polyfills, mods that call deleted classes get `NoClassDefFoundError` at runtime. Disable only if you want to see what breaks without the safety net.

### Verification

#### `verify_transforms` — boolean, default `true`

**New.** After RetroMod finishes transforming a mod, it scans the output bytecode for references (class names, method descriptors, field signatures) that don't exist in the current Minecraft JAR. Any misses are written to `config/retromod/verify-reports/<modid>.txt`.

This catches un-remapped names and missing polyfills *before* the mod blows up at runtime. See [Verify Transforms]({{ '/verify-transforms' | relative_url }}).

### Targeting

#### `target_mc_version` — string, default `"auto"`

The Minecraft version to target. `"auto"` detects it from the running loader, which is what you want 99% of the time.

Set to an explicit version string like `"26.1.2"` if you're using the CLI outside a game context and want to pin the target. Don't hardcode this in-game unless you know what you're doing.

#### `force_translate_complex` — boolean, default `false`

RetroMod normally refuses to transform mods its complexity analyzer flags as likely-to-break (deep MC internals hooks, custom rendering pipelines, etc.). Flip this on to make it transform anyway. Expect crashes. Useful for experimenting.

### Logging

#### `log_level` — string, default `"INFO"`

Standard SLF4J levels: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR`. Bump to `DEBUG` or `TRACE` when chasing a bug.

#### `log_transformations` — boolean, default `false`

Log every class, method, and field redirect as it happens. Noisy — expect thousands of lines per mod. Useful when diagnosing "why did it rewrite *that*?"

#### `debug` — boolean, default `false`

Umbrella debug flag. Enables extra sanity checks inside the transformer, prints more context on errors, and tightens several assertions. Leave off for normal use.

#### `dump_bytecode` — boolean, default `false`

Write raw `.class` files to `config/retromod/bytecode-dumps/` for each transformed class. Useful with a decompiler (CFR, Vineflower) when you want to see exactly what RetroMod produced. Eats disk fast.

## Editing safely

- The file is plain JSON — no comments allowed.
- If you write a syntactically invalid file, RetroMod logs a warning and falls back to defaults for that launch. Your broken file is left alone so you can fix it.
- Delete the file to regenerate defaults. All values reset.
- The in-game settings screen always wins: if you open it and click a toggle, the file is rewritten with the new value plus whatever was already there.
