---
title: Verify Transforms
nav_order: 6
---

# Verify Transforms

Verify Transforms is RetroMod's safety net: a post-transformation bytecode scan that checks the output JAR for references to classes, methods, and fields that don't actually exist in the current Minecraft. It catches problems *before* the mod blows up at runtime — the kind of problems where you'd otherwise stare at a `NoSuchMethodError` in the crash log wondering what you missed.

## What it does

After RetroMod finishes transforming a mod, the verifier walks every class in the output JAR and checks each reference to `net.minecraft.*` (and a few other watchlisted packages) against the current Minecraft JAR on disk. For each reference it asks one of three questions:

- Does this class exist?
- Does this method exist on that class (with this descriptor)?
- Does this field exist on that class (with this type)?

Any "no" gets logged to a report. If the list is empty, verification passes. If it's not, you've found a gap — an intermediary name RetroMod didn't know how to remap, a method that was removed without a polyfill, a shim chain that's missing a link.

## Why it matters

Without verification, you find out about unresolved references by booting Minecraft and waiting for the crash. Sometimes that's immediate; sometimes it's ten minutes into gameplay when the problematic code path finally runs. Verify Transforms front-loads the discovery: the moment transformation finishes, you know whether the result is load-safe.

It's especially valuable for:

- **Modpack authors** preparing a large pack — run `batch --verify` once and get a single list of what's still broken.
- **RetroMod contributors** working on new shims or polyfills — verification output is the immediate feedback loop for "did my mapping fix what I thought it would?"
- **CI pipelines** — verification is a pass/fail signal that's easy to gate merges on.

## Turning it on and off

Verify Transforms is **on by default** as of the current beta. Toggle it via:

- **GUI:** [in-game settings screen]({{ '/gui' | relative_url }}), **Verify Transforms** toggle.
- **Config:** set `"verify_transforms": false` in `config/retromod/config.json`.
- **CLI:** always runs if you pass `--verify`. If the config has it enabled, it runs automatically even without the flag.

Disable it if you trust your toolchain and want to skip the overhead (verification is fast, but on a huge modpack it's measurable).

## Where reports land

```
<minecraft>/config/retromod/verify-reports/
├── examplemod.txt
├── another-mod.txt
└── ...
```

One report per transformed mod, named after the mod ID. If verification passes the file is still written but is essentially empty (header only).

## Reading a report

A report is plain text. Example for a partially-broken transformation:

```
RetroMod verification report
Mod: examplemod
Source MC version: 1.20.1
Target MC version: 26.1.2
Timestamp: 2026-04-16T14:22:51Z

=== Missing classes (1) ===
  net/minecraft/class_4844
    referenced from: com/example/examplemod/RenderHandler

=== Missing methods (3) ===
  net/minecraft/client/render/RenderLayer.getEntityCutout(Lnet/minecraft/util/Identifier;)Lnet/minecraft/client/render/RenderLayer;
    referenced from: com/example/examplemod/client/EntityRenderer (line 42)
  net/minecraft/entity/LivingEntity.getHealth()F
    referenced from: com/example/examplemod/mixin/LivingEntityMixin
  ...

=== Missing fields (0) ===

Summary: 4 unresolved references across 2 classes.
```

### What to do with a report

- **Missing class `net/minecraft/class_XXXX`** — an intermediary name RetroMod didn't remap. Needs an entry in `IntermediaryToMojangMapper`.
- **Missing method on a class that does exist** — either the method got renamed (needs a shim redirect) or it got removed (needs a polyfill).
- **Missing field** — same deal as methods. Renamed? Add a redirect. Removed? Add a polyfill.

If you're using RetroMod as a user, you probably want to [file an issue](https://github.com/Bownlux/RetroMod/issues) with the report attached. If you're contributing, the [`mapping-work`](https://github.com/Bownlux/RetroMod/tree/main/.claude/skills/mapping-work), [`add-version-shim`](https://github.com/Bownlux/RetroMod/tree/main/.claude/skills/add-version-shim), and [`add-polyfill`](https://github.com/Bownlux/RetroMod/tree/main/.claude/skills/add-polyfill) skills walk you through the fix.

## Does verification guarantee the mod works?

No. It guarantees **every bytecode reference resolves** against the current Minecraft. It doesn't guarantee semantics — a method can exist with the same signature and do something completely different now. A clean verification means "the mod will load without linkage errors", not "the mod will behave correctly." For the latter, nothing beats actually running it.

That said, in practice the vast majority of transform failures are linkage errors, and verification catches them all.

## Performance

Verification scans the output JAR in-process using the same ASM reader RetroMod already has warmed up. Expect it to add somewhere in the low single-digit percent of transformation time for a typical mod. For a 500-mod pack, that's negligible compared to transformation itself.
