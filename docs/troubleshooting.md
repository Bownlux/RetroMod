---
title: Troubleshooting
nav_order: 8
---

# Troubleshooting

This page covers the most common ways Retromod surprises people, and how to fix them. If your problem isn't here, [open an issue](https://github.com/Bownlux/Retromod/issues) — attach your `latest.log` and the contents of `config/retromod/verify-reports/` for the mod you're struggling with.

## My mod doesn't load

1. **Check `retromod-input/` and `retromod-backups/`.** If your mod is sitting in `retromod-input/` after a restart, Retromod didn't process it — check the log for a Retromod error on that filename. If it's in `mods/` but also in `retromod-backups/`, transformation ran at least once; look for runtime errors instead.

2. **Check `config/retromod/verify-reports/<modid>.txt`.** If the report lists missing classes or methods, Retromod didn't find a mapping or shim for something the mod uses. See [Verify Transforms]({{ '/verify-transforms' | relative_url }}) for how to read the report, then file an issue or add the mapping yourself.

3. **Check your Fabric Loader version.** Retromod targets the version of Fabric Loader bundled with the current target MC. If your loader is older, the mod's patched metadata (which declares `fabricloader: "*"`) is fine, but Fabric might still reject something else. Update to the latest loader for your MC version.

4. **Confirm the mod is actually in the right folder.** On macOS, instance-based launchers (Prism, MultiMC, ATLauncher) each have their own `mods/` folder. Make sure you dropped the JAR in the right one. See [Installation]({{ '/installation' | relative_url }}).

## My mod loads but crashes immediately

1. **Enable debug logging.** Set `"debug": true` and `"log_level": "DEBUG"` in `config/retromod/config.json`. Restart. The next crash will have a much more detailed Retromod trace in `latest.log`.

2. **Enable bytecode dumps.** Set `"dump_bytecode": true`. This writes every transformed class to `config/retromod/bytecode-dumps/`. Open the class the crash points to (usually in the stack trace) with a decompiler like [Vineflower](https://github.com/Vineflower/vineflower) or CFR and see what actually came out of the transformer.

3. **Check the mod's mixins.** If the crash mentions `org.spongepowered.asm.mixin`, a mixin probably failed to apply. Disable `transform_mixins` temporarily to see whether the mod even attempts to load without mixin rewriting — if it does, you know the issue's in the mixin pass.

4. **Look for `NoSuchMethodError` / `NoClassDefFoundError`.** These mean a reference didn't get remapped or polyfilled. If the verify report for this mod is empty, it likely means the reference is inside a mixin target or a reflective call — try turning on `remap_reflection` if it's off.

## "unofficial build" or similar authenticity warning

Harmless in most cases. Retromod doesn't block anything based on authenticity status — see [Authenticity]({{ '/authenticity' | relative_url }}) for the full breakdown. The short version:

- **UNSIGNED** — totally normal, especially for beta builds. Ignore.
- **UNOFFICIAL** — you're running a fork or a re-signed build. Fine if you trust where you got it.
- **TAMPERED** / **IMPOSTOR** — if you didn't modify the JAR yourself, re-download from the [official releases page](https://github.com/Bownlux/Retromod/releases).

## Verify reports missing methods or classes

The verifier found references in the transformed mod that don't resolve against current Minecraft. Two things to try:

1. **Look at the referenced class.** If it starts with `net/minecraft/class_`, `method_`, or `field_`, it's an intermediary name that Retromod doesn't know how to remap. This needs an entry in `mapping/IntermediaryToMojangMapper.java`.

2. **Look at the source MC version in the report header.** If the mod is 1.16.5 and the missing class is very old, a shim between 1.16 and 1.20 might be absent. Run `shims` in the [CLI]({{ '/cli' | relative_url }}) to see what's registered.

Either way, [file an issue](https://github.com/Bownlux/Retromod/issues) with the report attached. These reports are exactly the input needed to add the missing mapping or shim, so they're genuinely useful to the project.

## Java version errors

The Java version you need depends on which MC version you're running, **not** on Retromod itself. Retromod's bytecode targets Java 17, so the same JAR runs on Java 17, 21, and 25 — but MC itself has its own Java floor that grows over time:

| Your Minecraft version | Java you need |
|---|---|
| MC 1.20 – 1.20.4 | Java 17 |
| MC 1.20.5 – 1.21.x | Java 21 |
| MC 26.1+ | Java 25 |

Retromod's per-MC build declares the matching Java requirement, so the loader will refuse with a friendly error if you have the wrong one.

### "Mod 'Retromod' requires version 21 or later of 'OpenJDK 64-Bit Server VM' (java)"

(Or `version 25 or later`.) Fabric Loader is enforcing the Java requirement for your MC version — you have an older Java than your MC needs. Install the right Java for your MC version from [Adoptium](https://adoptium.net/), then:

- **Prism / MultiMC / ATLauncher:** Right-click the instance → **Edit** → **Settings** → **Java** tab → uncheck "Use system Java" → point at the new Java install
- **Vanilla launcher:** Edit the installation profile → **More Options** → **Java Executable** → point at the new install

**A common misconception:** MC 1.20.1 *can* run on Java 21 or 25. Vanilla MC is forward-compatible — it was built against Java 17 but loads fine on later versions. The Fabric error message is about Retromod's declared requirement, not a Minecraft limitation.

### "UnsupportedClassVersionError: class file version 69.0"

This is the JVM rejecting bytecode that's newer than it knows how to load. Class file version 69 = Java 25, so this means a class file built for Java 25 is being loaded by a Java 21 (or older) JVM.

If you're on MC 26.1+ and seeing this, you need to install **Java 25**. MC 26.1's own class files are Java 25 bytecode, so even though Retromod itself is Java 17 bytecode, MC won't load on Java 21.

If you're on MC 1.20.x or 1.21.x and seeing this, something on your classpath is Java 25 bytecode that shouldn't be — usually a mod that requires a newer MC and got installed by mistake. Check `latest.log` for the class name in the error; it'll tell you which mod.

## "I'm on Quilt — which build do I install?"

The **Fabric** build. Drop `retromod-<version>+<MC>-fabric.jar` into your Quilt instance's `mods/` folder. There's no separate "Quilt build" — Quilt Loader runs Fabric mods natively, so the Fabric JAR works on Quilt unchanged. You'll need Quilted Fabric API (QFAPI) installed, which Quilt instances typically already have.

Retromod has a `quilt.mod.json` baked into the Fabric JAR specifically so Quilt's mod list shows it with a proper icon and metadata instead of as "a Fabric mod loaded via compat." Same JAR, no extra step. See the [Quilt FAQ entry]({{ '/faq#does-it-work-with-quilt' | relative_url }}) for more.

## "NoSuchFieldError: f_NNNNN_" or "NoSuchMethodError: m_NNNNNN_" on a Forge mod

These are Forge SRG names — short numeric identifiers that old Forge mods (anything built with ForgeGradle's `reobfJar` task, basically every Forge mod for MC < 1.20.5) use to reference Minecraft members. Forge's runtime used to remap them back to real names; Forge 64.x for MC 26.1+ dropped that remap layer, so reobf'd mods crash on first reference.

Retromod ships a dictionary that handles the common ones (~120 entries covering Block/Item statics and the highest-frequency Component/ResourceLocation methods), but it's an explicitly-incomplete starter set — the full SRG namespace has tens of thousands of entries.

If you hit one of these errors:

1. **Confirm it's an SRG-name pattern:** field starts with `f_`, method starts with `m_`, both end with an underscore and contain only digits in the middle. If yours doesn't match that pattern, it's a different problem — see "Verify reports missing methods" above.
2. **Note the exact name** from the crash log (e.g. `f_220832_`).
3. **Add it to Retromod's dictionary** — see [Adding SRG Mappings]({{ '/srg-mappings' | relative_url }}) for the one-line PR workflow. Looking up the Mojang name via [Linkie](https://linkie.shedaniel.dev/) takes ~30 seconds.
4. **Or file an issue with the name** if you'd rather not PR — we'll add it.

## Mixin failures: "@ModifyExpressionValue ... target class 'X' is not supported" / "@Shadow field gui was not located"

Common with mods built for MC versions close to but not exactly matching your host (e.g. a 1.21.9 mod on 26.1.2). What's happening:

Retromod rewrites the *outer* `@Mixin(targets = ...)` annotation to point at the renamed class — that part works, you'll see the rewritten target in the error message (e.g. `target net.minecraft.client.gui.GuiGraphicsExtractor`). But the mod also uses MixinExtras-style inner annotations like `@ModifyExpressionValue(target = "net/minecraft/client/gui/Gui")` and `@Shadow` field references against the old class — and Retromod doesn't yet rewrite the `target = "..."` strings nested inside those, or the field-name on `@Shadow`. So the mixin processor sees a target spec that's now invalid and refuses to apply the injection.

This is a **known Retromod gap** (the mixin-extras inner-target rewriter is incomplete) — please file an issue with your `latest.log` if you hit it. We're tracking it for a future beta.

**Workarounds in the meantime:**

1. **Use a more recent version of the mod, if one exists for your MC version.** A mod built natively against 26.1+ won't have this problem at all — there's nothing for Retromod to rewrite.
2. **Use an older host MC version** that's closer to the mod's source version. A 1.21.9 mod will translate to 1.21.10 or 1.21.11 much more reliably than to 26.1.2 (small hop = fewer renamed internals to bridge).
3. **Some affected mods still partially work.** The failed mixin only disables one feature, not the whole mod — check whether the missing feature is something you actually use. The `Mixin apply for mod X failed` line in the log is a WARN, not always fatal; the fatal crash comes when the mod's `@Inject` was load-bearing for its `<clinit>`.

The relevant known-broken mods so far: anything with a name like "CustomHUD" / "BetterHUD" / "BetterF3" on MC versions much newer than the mod was built for. Anything that ships its own mixins into MC's GUI internals is in this category — the GUI surface changes shape often.

## Forge: "needs language provider javafml:X or above" (e.g. javafml:52)

`javafml` is Forge's Java-mod language provider — the number after the colon is the **Forge loader version**, not a separate library. So `javafml:52` means "Forge 52.x or later"; `javafml:47` means "Forge 47.x or later". Each MC version ships a specific Forge loader version: MC 1.20.1 → Forge 47, MC 1.21 → Forge 51, MC 1.21.1 → Forge 52, MC 26.1+ → Forge 64+.

**If you saw "javafml:52 or above to load We have found 47" on MC 1.20.1 with Retromod 1.0.0-beta.1:** that's a real bug in beta.1 — the Forge `mods.toml` was hardcoded to demand Forge 52+ regardless of MC version, so the MC 1.20.x Forge builds couldn't actually load on the Forge that ships with those MC versions. **Fixed in beta.2.** Update to beta.2 (or later) and the error goes away.

**If you saw this on beta.2+ for a different MC version:** that's unexpected — the per-MC loaderVersion table in `build-all.sh` should match every Forge release. Please [open an issue](https://github.com/Bownlux/Retromod/issues) with your exact MC version and Forge loader version so we can fix the table.

## "java.util.zip.ZipException: duplicate entry"

Some mods (especially older Forge mods that JIJ-bundled Fabric API modules into a Forge package, or mods built with legacy bundler toolchains) ship JARs whose central directory lists the same entry twice. Retromod's transformer used to crash mid-write on the second occurrence — fixed in **beta.2**.

If you see this on beta.1, the workaround is to update to beta.2 (or later). On beta.2+, Retromod logs a `Skipping duplicate JAR entry from source: ...` warning, keeps the first copy, and continues — the transform succeeds.

## Retromod button is missing from the title screen

The mod didn't finish initializing. Check `latest.log` for `Retromod` messages — there'll usually be an exception explaining what went wrong. Common causes:

- **Conflicting mod** that also modifies the title screen. Retromod uses a mixin for the button; if another mod clobbers the same spot, one of them loses.
- **Wrong game directory** — the JAR is in the right folder for a different Minecraft instance than the one you're launching.
- **Fabric API missing** — Retromod requires Fabric API on Fabric installs.

## Game loads but transformed mods aren't being picked up

Make sure they're in `mods/`, not `retromod-input/`. `retromod-input/` is the inbox — after Retromod runs, successfully-transformed mods move to `mods/`. If your mod is stuck in `retromod-input/`, transformation failed — check the log.

## AOT cache seems stale

After updating Retromod, delete `config/retromod/aot-cache/`. The cache's hash includes the source mod but not the Retromod version, so an old cache can produce old output even with a new Retromod JAR. Deleting the folder forces a fresh compile.

## Nothing helps — time to file a bug

Collect:

1. `latest.log` from your Minecraft game directory.
2. The relevant verify report (if any) from `config/retromod/verify-reports/`.
3. The mod JAR you're trying to load, or a link to it.
4. Your `config/retromod/config.json`.
5. Your Minecraft version, loader (Fabric / NeoForge / Forge), and loader version.

Attach to a [new issue](https://github.com/Bownlux/Retromod/issues/new). That set of files is enough to reproduce almost every bug.
