---
title: CLI tool
nav_order: 5
---

# CLI tool

RetroMod ships with a command-line tool (`RetroModCli`) for transforming mods outside Minecraft. It's handy for scripting, CI pipelines, bulk modpack prep, and poking at what RetroMod would do to a mod without actually booting the game.

## Running the CLI

One quirk: **the published JAR doesn't bundle its dependencies**, so `java -jar retromod.jar ...` won't work. You run the CLI through Maven instead, from a checkout of the repo:

```bash
git clone https://github.com/Bownlux/RetroMod.git
cd RetroMod
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="<command> <args>" \
  -q
```

The `-q` flag keeps Maven quiet so you only see RetroMod's output. Every example below assumes you're in the repo root.

## Commands

### `transform` — transform a single mod

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="transform path/to/mod.jar" -q
```

Transforms `mod.jar` in-place to target the current `TARGET_MC_VERSION` (26.1.2). The original is backed up next to it with a `.bak` suffix.

Output: `path/to/mod.jar` (transformed), `path/to/mod.jar.bak` (original).

### `transform --verify` — transform and verify

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="transform path/to/mod.jar --verify" -q
```

Same as `transform`, but after transformation runs the [verifier]({{ '/verify-transforms' | relative_url }}) and writes a report to `config/retromod/verify-reports/`. Non-zero exit if verification finds unresolved references.

### `batch` — transform every mod in a folder

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="batch /path/to/mods" -q
```

Iterates every `.jar` in the given folder, transforming each one. For targets at 26.1+ this also runs the metadata-only patch on mods that don't need bytecode rewrites, so version constraints get relaxed regardless.

Pass `--verify` to verify each mod after transforming:

```bash
-Dexec.args="batch /path/to/mods --verify"
```

### `aot` — AOT-compile a folder of mods

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="aot /path/to/mods" -q
```

Runs the full AOT compiler over every mod in the folder, writing cache entries into `config/retromod/aot-cache/`. Next time Minecraft launches with RetroMod, those mods load from the cache without being transformed again.

Useful for bulk-preparing a modpack on a faster machine before shipping it out.

### `embed` — bake RetroMod into a mod JAR

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="embed path/to/mod.jar" -q
```

Embeds a minimal RetroMod runtime directly into `mod.jar` so the mod can self-transform at load time without needing RetroMod installed separately. Produces a self-contained JAR that works on a vanilla Fabric/NeoForge/Forge install.

### `diff` — show what transformation *would* do

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="diff path/to/mod.jar" -q
```

Dry-run: prints the class/method/field renames, mixin target rewrites, and metadata changes RetroMod would apply, without actually writing anything. Great for "what is RetroMod going to do to my mod?" before you commit.

### `shims` — list registered shims

```bash
mvn exec:java \
  -Dexec.mainClass="com.retromod.cli.RetroModCli" \
  -Dexec.args="shims" -q
```

Prints every registered version shim, grouped by loader (Fabric / NeoForge / Forge / API), with `fromVersion → toVersion` and redirect count. Useful for confirming a shim is actually loaded.

Abbreviated output:

```
Fabric shims (112):
  1.20.1 → 1.20.2   (18 redirects)
  1.20.2 → 1.20.4   (23 redirects)
  ...

NeoForge shims (21):
  1.20.4 → 1.20.6   (5 redirects)
  ...
```

## Flags

### `--verify`

Works with `transform` and `batch`. Runs the verifier after each transformation and writes a report. Non-zero exit code if any verification misses are found.

### `-q` (Maven quiet mode)

Not a RetroMod flag, but worth mentioning. Pass `-q` to Maven to suppress its progress output. Without it you'll see Maven's standard blah about downloading dependencies and running the exec plugin.

## Scripting tips

- Every command returns a non-zero exit on error, so you can chain with `&&` or check `$?` in shell scripts.
- `batch` logs one line per mod to stdout — easy to `grep` for specific mod IDs or failures.
- Verify reports are plain text; parse them with whatever you like.

## See also

- [Verify Transforms]({{ '/verify-transforms' | relative_url }})
- [Config reference]({{ '/config' | relative_url }})
