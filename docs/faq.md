---
title: FAQ
nav_order: 10
---

# FAQ

Frequently asked questions about RetroMod. If your question isn't here, check the other docs or [open an issue](https://github.com/Bownlux/RetroMod/issues).

## Is RetroMod safe?

Yes, within the usual caveats for running any third-party mod:

- **The code is open source** under the [MIT license](https://github.com/Bownlux/RetroMod/blob/main/LICENSE). You can read every line before running it.
- **Releases are signed.** RetroMod includes a [signature verifier]({{ '/authenticity' | relative_url }}) that distinguishes official builds from forks or tampered copies.
- **It modifies bytecode, nothing else.** RetroMod doesn't talk to the network, doesn't touch files outside your game directory, and doesn't collect telemetry.
- **Transformation is in-process.** The transformer runs inside Minecraft's JVM at mod load time; there's no separate daemon, no autostart, no system integration.

The one meaningful caveat: RetroMod *does* modify other mods' bytecode. If one of those mods does something shady, RetroMod can't know — it just rewrites the names. Install mods from sources you trust.

## Why MIT license?

A few reasons:

- **It's what modding culture expects.** Most Fabric and NeoForge mods are MIT or similarly permissive. Making RetroMod compatible with the ecosystem was the priority.
- **Modpack authors can ship it without asking.** No attribution clause to trip up, no viral license to worry about.
- **Forks and derivatives are fine.** If somebody wants to build a different mod-compatibility layer on top of RetroMod's shim engine, go for it.

The signature verifier exists precisely because the license is permissive — it lets users tell the original from derivatives without restricting anyone's right to make derivatives.

## Does it work on servers?

Yes. Server-side-only mods work exactly the same way client-side mods do — RetroMod transforms their bytecode, patches their metadata, and the server loads them.

One nicety: **if a mod is server-only, clients connecting to the server don't need RetroMod themselves.** The server handles the transformation; clients just see a vanilla-looking server that happens to have some extra behavior. Install RetroMod on the server, leave clients alone.

Mods that have both client and server components need RetroMod on both sides.

## Does it work with modpacks?

Yes, and modpacks are actually one of the best use cases. Drop RetroMod into the pack's `mods/` folder (or configure your pack launcher to include it), put your old mods in `retromod-input/`, and on first launch everything gets transformed and moved to `mods/`.

For CurseForge / Modrinth / ATLauncher packs specifically: include RetroMod as a regular mod dependency, and add a post-install step (if your launcher supports it) that copies old mods into `retromod-input/` instead of `mods/`. Or skip the auto-install and tell users to use the in-game file picker.

## Can I commercialize a modpack using RetroMod?

Yes. MIT license, no strings attached. You can:

- Bundle RetroMod in a paid modpack.
- Include it in a donation-required server pack.
- Redistribute it under whatever branding your pack uses.
- Modify it for your needs.

You have to preserve the copyright notice and MIT license text somewhere in the distribution — that's the only requirement. A `LICENSE` file alongside the JAR, or a mention in the pack's credits, is enough.

## What Minecraft versions are supported?

- **Target:** Minecraft 26.1.2. This is the version your transformed mods run *on*.
- **Source:** mods built for 1.12.2 and newer. That's the version the mod was originally released for.

So: you can take a Fabric mod built for 1.20.1 and run it on 26.1.2. You can take a mod built for 1.16.5 and run it on 26.1.2. 1.12.2 is the oldest source we currently support.

Older source versions (pre-1.12.2) aren't supported because the mod ecosystem shifts fundamentally around that point — Forge API differences, mixin maturity, etc. That's future work.

Newer target versions will be added as Minecraft releases them. RetroMod is built around 26.1 specifically because it's the first fully unobfuscated MC release, which makes the mapping work straightforward.

## Why does verification say "unsigned"?

Because the current beta builds aren't signed. Signing infrastructure is on the roadmap for the 1.0 release. In the meantime, UNSIGNED is expected on every build you see — it's not an error or a warning about your JAR specifically, it's just the current state of the project.

See [Authenticity]({{ '/authenticity' | relative_url }}) for the full breakdown of what each status means.

## Does RetroMod work with OptiFine / Sodium / Iris?

Partial compatibility. Sodium and Iris are mostly fine — they target modern MC APIs, which RetroMod doesn't interfere with. OptiFine is trickier because it transforms the game in ways that overlap with what RetroMod does; there's a dedicated `compat/OptiFineCompat.java` that handles some of the friction, but YMMV. Test with your specific rendering mod combo before committing to it.

## Will it work for [specific mod]?

The honest answer: try it and see. Simple content-only mods (items, blocks, recipes) almost always work. Mods that hook MC's internals for rendering, physics, or networking are hit-or-miss. Mods with extensive reflection or dynamic classloading may need `remap_reflection` enabled (it's on by default).

The best way to find out is to drop the mod in `retromod-input/`, launch, and check `config/retromod/verify-reports/` for that mod. If verification passes, odds are good. If it doesn't, the report tells you what's missing.

## Is there a Discord / Matrix / forum?

Not yet. Right now all discussion happens on the [GitHub issue tracker](https://github.com/Bownlux/RetroMod/issues). For bugs and feature requests, open an issue. For general questions, open a [discussion](https://github.com/Bownlux/RetroMod/discussions).

## Can I contribute?

Please do. See [Contributing]({{ '/contributing' | relative_url }}) for the workflow and the set of skills (under `.claude/skills/`) that walk you through common tasks like adding a shim or a polyfill.
