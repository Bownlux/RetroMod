---
title: Mods That Can't Be Translated
nav_order: 14
---

# Mods That Can't Be Translated

Retromod can shim a renamed method, redirect a moved class, polyfill a removed API, and patch a mod's metadata so a stricter loader stops rejecting it. What it cannot do is rewrite something that isn't there to be rewritten — or run a translation pass on bytes that aren't Java bytecode in the first place.

This page is the realistic "no" list. If your mod is here, or matches one of the general patterns at the bottom, Retromod is not the right tool — only the mod's original author can ship a working port.

## The general rule

If a mod uses any of the following, **no automated bytecode tool — Retromod or otherwise — can translate it**. There's no realistic path to making it work without the mod author writing a manual port:

- **Native code.** JNI/JNA bindings, `.so` / `.dll` / `.dylib` files bundled in the JAR, or shared libraries downloaded on first launch. Bytecode translation can't touch native binaries — they target a fixed ABI built against a fixed C/C++/Rust API on the other side.
- **A custom rendering pipeline.** Mods that replace MC's renderer with their own (Flywheel, Iris's shader pipeline, mods that ship custom Vulkan/Metal/OpenGL code). The rendering surface changes shape between MC versions in ways that aren't expressible as a redirect — entire pipeline stages get rewritten.
- **Their own bytecode transformer.** Coremods that ship an ASM transformer of their own, applied at class-load time, against bytecode positions in MC that have since moved. Retromod can't second-guess another transformer's intent — and even if it could, the moved positions wouldn't match anymore.
- **A custom or modified Mixin framework.** Standard Mixin we can work with. Forks of Mixin, or proprietary mixin-like systems, depend on private internals that aren't part of any compatibility surface.
- **Direct GPU buffer manipulation tied to a specific MC version.** Mods that reach into MC's vertex buffers, command queues, or shader programs at byte-level offsets. Any of those that change between versions break in ways no transformer can patch.
- **Closed-source / proprietary distribution.** Mods that ship obfuscated and forbid redistribution (OptiFine being the canonical example) can't be transformed without violating their license — and most of them are also coremods, so even setting the license aside, see point 3.

If a mod hits any of those, the answer is "you need the original author to port it." There is no faster path.

## Mixins into reworked vanilla methods (feature won't work, but the mod loads)

This one isn't a property of the mod so much as of the gap you're crossing, and it's a *degraded-feature* problem, not a hard incompatibility. A mod that uses `@Inject` / `@Redirect` / `@ModifyArg` to hook a **vanilla** Minecraft method only works if that method still has the shape the mod compiled against. Retromod can rename a method and move a class, but it cannot re-point an injector when Mojang **reworked the method itself** — changed its parameter count, reordered its arguments, or swapped a parameter type — because the injection target the mod describes no longer exists to be matched.

When this happens the loader reports `InvalidInjectionException: Invalid descriptor` and skips that one injection. Retromod treats these as non-fatal, so the mod still loads — the specific feature that mixin implemented just doesn't run.

26.1 reworked a lot of these. Real examples from a single mod (Caverns & Chasms, 1.21.1 → 26.1, [#24](https://github.com/Bownlux/Retromod/issues/24)):

- `AbstractFurnaceBlockEntity.burn` — gained `RegistryAccess`, a `RecipeHolder`, and the block-entity itself as parameters.
- `BlockBehaviour.updateShape` — arguments reordered and `LevelReader` / `ScheduledTickAccess` / `RandomSource` inserted.
- The player NBT-save hook — now writes to a `ValueOutput` instead of a `CompoundTag`.
- `FallingBlockEntity` fall-damage hook — a `float` parameter became a `double`.

There's no general fix for the injection itself: the mod's injector was written for a method body 26.1 deleted, so that hook stays inactive. The shorter the version jump, the less often this bites (1.21.1 → 1.21.8 reworks far fewer methods than 1.21.1 → 26.1).

What *does* hard-crash — and what Retromod fixes — is when the same mod also references a **removed class**. Caverns & Chasms pulled the deleted `DirectionProperty` into `Blocks.<clinit>` during bootstrap, and that `NoClassDefFoundError` killed the game before any of the soft-failed mixins mattered. Retromod polyfills `DirectionProperty` (redirecting it to the surviving `EnumProperty`), so the mod now boots; the reworked-method mixins above still soft-fail, so those particular tweaks won't apply, but the game runs.

### MixinExtras `@Local` captures — and the mixin blocklist

A nastier variant: MixinExtras' `@WrapOperation` / `@ModifyExpressionValue` with a `@Local` capture. Standard Mixin's `@Inject` can be downgraded to fail-soft, but a `@Local` that no longer binds (because the vanilla method's local-variable layout changed) makes MixinExtras emit an **invalid bridge method**, which the JVM rejects with `VerifyError: Bad local variable type` *at class-load* — fatal, before any soft-fail can run. Deeper & Darker's `PaintingItemMixin` hit this on 26.1: it wraps `ItemStack.shrink` inside `HangingEntityItem.useOn` and captures the `Level` local, which moved when 26.1 restructured `useOn` ([#28](https://github.com/Bownlux/Retromod/issues/28)).

This can't be auto-detected safely — the local usually still exists, just at a different slot, so a heuristic would strip working mixins too. Instead Retromod ships a curated **mixin blocklist** (`retromod/mixin-blocklist.json`) that surgically removes the specific crashing handler so the framework never processes it: the mod boots and only that one feature is inert. You can extend it for your own mods via `config/retromod/mixin-blocklist.json`:

```json
{
  "blocked": [
    { "mixin": "com/example/mod/mixin/SomeMixin", "methods": ["handlerThatCrashes"] }
  ]
}
```

Omit `methods` to disable every injector on the class. If a mod crashes with `VerifyError: Bad local variable type` pointing at a `wrapOperation$…$mixinextras$bridge` method, that mixin handler is the culprit — add it here (and please file an issue so it can join the curated list).

## Specific mods that will never work

This is the obvious list. Everything here matches one or more of the general rules above. Other mods that match those rules belong here too — these are just the ones people ask about most.

### Deep-integration / coremod-class

| Mod | Why |
|---|---|
| **Create** | Ships [Flywheel](https://github.com/Jozufozu/Flywheel) — its own GL-level rendering library — and replaces large parts of MC's animation, networking, and contraption simulation. Internal API surface is also rewritten between MC versions, not renamed. |
| **Applied Energistics 2 (AE2)** | Decades of deep MC integration, custom networking protocol, historically a coremod, and the channel/quantum systems get re-architected between versions. |
| **Tinkers' Construct** | Replaces the tool and material system at the JSON-schema level *and* the bytecode level. The schema changes between versions are larger than any redirect table can cover. |
| **IndustrialCraft / IC2** | Very deep MC integration, coremod heritage, energy network internals are tied to MC's tick scheduler in ways that break on every major MC update. |
| **Thaumcraft** | Magic systems tied to MC's internal aspect/research data structures, which change between versions. The mod has been redesigned multiple times and its older incarnations are tied to specific MC internals. |
| **Botania** *(deep mixin variants)* | Heavily mixin-based with injection points tied to specific bytecode offsets. Some Botania features may load, but full-functionality compatibility is the same problem as Sodium/Iris below — only worse. |

### Rendering replacement / shader

| Mod | Why |
|---|---|
| **OptiFine** | Proprietary, closed-source, ships as a Forge coremod. Even ignoring the license, the coremod transforms wouldn't survive translation. |
| **Sodium**, **Iris Shaders**, **Embeddium** | These technically *load* via Retromod (they appear on [COMPATIBILITY.md](../COMPATIBILITY.md) with the `*` caveat), but their mixin injections target specific bytecode offsets in MC's renderer — when those offsets move between versions, no transformer can synthesize an injection point that wasn't there. Expect partial functionality at best. |
| **Flywheel** | The rendering library Create depends on. Same shape problem as above — custom GL pipeline. |
| **ImmediatelyFast** and similar rendering-pipeline replacements | Same pattern — they wrap MC's rendering at a level that doesn't translate. |

### Loaders (these aren't mods, but people ask)

Forge, NeoForge, Fabric Loader, and Quilt Loader are **mod loaders**, not mods. Retromod runs *on top of* them; it doesn't translate them. If you want a different loader, install that loader directly.

## Mods that load but are likely broken in-game

A separate category from the "never works" list above. These mods get through Retromod's pipeline and the loader accepts them, but their actual gameplay features depend on bytecode positions that have moved. They're worth listing because people see them on the [COMPATIBILITY.md](../COMPATIBILITY.md) success list and assume they fully work:

- **Sodium / Iris / Embeddium / other heavy mixin mods.** Load fine, render features may glitch, crash on specific scene complexity, or silently no-op.
- **Mods with `@Inject` on local-variable captures** (`LocalCapture.CAPTURE_FAILHARD` patterns). Standard Mixin will refuse the injection if local types changed; Retromod downgrades these to soft-fail so you don't crash, but the feature the mixin was implementing won't run.
- **Mods that hardcode network packet IDs.** Retromod can rewrite class references but not protocol numbers — version-specific packet handlers usually need an actual port.

If a mod from this category is critical to your setup, file an issue describing what specifically isn't working — that's the kind of feedback that drives the next round of shim/polyfill work.

## "But X works for me!"

Possible. The lines above are about reliability, not impossibility-in-every-individual-case. Some Create features have loaded for individual users; some Sodium configurations work on some hardware; some AE2 networks function until the first quantum bridge. None of those mean the mod is *generally* compatible. They mean you got lucky with the specific subset of the mod you exercised.

The honest framing is: if you depend on one of these mods working correctly across a long play session with friends, treat it as broken until the mod ships a real port for your MC version. Retromod is not going to close that gap.

## Reporting and disagreements

If you think something on the "never works" list actually does work cleanly with Retromod, please [file an issue](https://github.com/Bownlux/Retromod/issues) with:

- The mod name and exact version
- The MC version you transformed *from* and the MC version you transformed *to*
- Your `latest.log` from a launch where the mod loaded successfully
- A short description of which features you actually exercised in-game (not "it loaded" — what did you click, place, or see render correctly?)

Conversely, if a mod *not* on this list keeps failing for you and you suspect it should be, file an issue — adding more mods to this page based on real reports is one of the better forms of contribution.
