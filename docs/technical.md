---
title: Technical
nav_order: 9
---

# How RetroMod Works

RetroMod is a bytecode transformer. When the game launches, RetroMod rewrites old mod JARs so they target the current Minecraft API instead of the one they were built for. No source changes, no recompilation — the mod author never has to do anything.

This page explains the technical side at a level that should be useful if you're curious, want to contribute, or are evaluating whether RetroMod is safe to run.

## The pipeline

```
┌──────────────────┐
│  Old mod JAR     │   e.g. mymod-1.21.1.jar, targets MC 1.21.1
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ ModVersionDetect │   reads fabric.mod.json / mods.toml
│                  │   extracts modId + target MC version
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  ShimRegistry    │   BFS search: find a chain of version shims
│                  │   1.21.1 → 1.21.2 → … → 26.1
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  RetroModXformer │   For each class in the JAR:
│                  │   - apply each shim's class/method/field redirects
│                  │   - IntermediaryToMojangMapper renames method_XXXX etc.
│                  │   - inject polyfills for removed APIs
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Metadata patcher │   Relax version constraints in fabric.mod.json /
│                  │   mods.toml so the mod is no longer rejected by
│                  │   Fabric/Forge version checks
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ TransformVerifier│   (optional) Scan the output JAR for references
│                  │   that don't exist in the current MC; write a
│                  │   report to config/retromod/verify-reports/
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│ Transformed JAR  │   mymod-1.21.1-retromod.jar, targets MC 26.1
└──────────────────┘
```

## The ASM visitor chain

Bytecode transformation uses [ASM](https://asm.ow2.io/). For each `.class` file:

```
ClassReader  →  ClassRemapper  →  RetroModClassVisitor  →  ClassWriter
              (outer — renames)    (inner — redirects)
```

`ClassRemapper` runs first and handles name translation (intermediary → Mojang). `RetroModClassVisitor` sees the already-renamed names and applies shim redirects (method signature changes, constructor deferrals, polyfill injections). The final `ClassWriter` produces the new bytecode.

## Shims and polyfills

A **shim** is a pair of rules: "in mods targeting MC version X, when you see call `Foo.bar()`, rewrite it to `Foo.baz()` (or to `Polyfill.bar(Foo)`)." Shims are chained so a 1.16 mod can be walked up through 1.17, 1.18, 1.19, 1.20, 1.21 to 26.1.

A **polyfill** is a reimplementation of a removed API using current MC primitives. When the shim chain says "this method was removed in 1.20," the polyfill provides a drop-in replacement so old code continues to compile.

RetroMod currently ships 145 version shims and 30 polyfill providers covering 328 redirects.

## Security model

RetroMod reads and writes JARs on disk. That's a privileged operation, so the codebase is careful about:

- **Zip slip / path traversal** — every JAR entry goes through `ZipSecurity.safeEntryName()` before being written to disk or another archive.
- **Zip bombs** — `ZipSecurity.safeReadAllBytes(is, max)` counts actual bytes read, not header-declared sizes, so a crafted archive that lies about entry sizes can't blow up memory.
- **Arbitrary class loading** — `Class.forName()` is only ever called on class names harvested from user JARs with `initialize=false` and RetroMod's own classloader. No user-supplied code is executed during transformation.
- **File deletion** — only within managed directories (`mods/`, `retromod-input/`, `retromod-backups/`, `config/retromod/`). No user files outside those paths are ever touched.

## Authenticity & forks

### This is a security feature, not an anti-fork feature

Before anything else: **I'm a big fan of open source.** Given two apps with the same features — one open source, one closed — I'll pick open source every time, and RetroMod exists as open source for exactly that reason. Forks are welcome. You can rebuild it, modify it, redistribute it, or base a new project on it. The MIT license grants all of that and nothing in this section takes it back.

The signature-check and fork-notice system is here for **one reason only**: some bad actors edit open-source code to add malware and then redistribute it under the original name to trick users into trusting it. That's a real problem that's happened to lots of popular projects. The verifier lets honest users tell "this is the actual RetroMod I downloaded from the official repo" apart from "this is a file labeled RetroMod from a sketchy site." It's security, not an attempt to keep people from forking.

If RetroMod is ever discontinued, all of this comes out. A verifier that fires forever on a dead project would just be noise on every legitimate fork that inherits the codebase. In that scenario the fork-notice text, the cert-check, and the hardened logging come out together, and whatever fork ends up as the community continuation just works.

RetroMod is MIT-licensed. Forks are allowed, encouraged, and legally fine — the license grants that right without any conditions beyond keeping the license text.

There's a practical problem, though: **if a malicious actor publishes a JAR calling itself "RetroMod" and the user downloads it from somewhere other than the official repo, we can't technically tell that apart from a legitimate fork.** Both show up as "unsigned" or "signed with a different key" to our verification code.

### How RetroMod handles this

Official release JARs are signed with my private key. At startup, `SignatureVerifier` checks the JAR's signature:

| Status | Meaning |
|--------|---------|
| `OFFICIAL` | Signed by the official RetroMod key — trusted. |
| `UNSIGNED` | No signature. Either a beta/dev build or a fork. |
| `UNOFFICIAL` | Signed, but with a different key than the official one. |
| `TAMPERED` | Signatures are present but verification failed — the JAR was modified after signing. |
| `IMPOSTOR` | The manifest doesn't even claim to be RetroMod. |

The official build emits a normal startup line like:

> `[RetroMod] ✓ Authenticity: OFFICIAL build — Signed by CN=Bownlux, …`

For any other status (`UNSIGNED`, `UNOFFICIAL`, `TAMPERED`, `IMPOSTOR`) the verifier **automatically** emits:

> `You are using a RetroMod Fork. If this was advertised as the official RetroMod, this is NOT official! Check github.com/Bownlux/RetroMod for the real thing.`

The official build **never** emits this line — if the signature matches, it's the real thing.

### I'm making a fork — what do I do?

Forks are fine, encouraged, and fully MIT-licensed. If you keep the name "RetroMod" in your fork's logs/UI, the fork notice fires automatically — no code you need to add, no flag to flip. That's the safe default: every build that isn't signed with my key announces itself, and a silent "RetroMod" is an obvious red flag for users.

**There's no customizable version on purpose.** If forks could pass arbitrary text, a malicious build could log something like "Official RetroMod v1.2.3 — verified build" and users would have no way to tell. The fixed string plainly says "this is NOT official," which defeats that impersonation regardless of what the attacker calls themselves.

The notice deliberately points users to the GitHub repo rather than an email address — otherwise a single inbox gets flooded with "please verify this JAR" requests from people who could just compare hashes themselves. The contact email below is for fork *maintainers* who want to negotiate dropping the notice, not for end users who want to verify their download.

If you've renamed your project entirely (no "RetroMod" branding in your logs, manifest, or UI), the announcement isn't necessary — the `Implementation-Title` manifest check will report `IMPOSTOR` if your manifest doesn't claim to be RetroMod, which is accurate for "a build that isn't claiming to be RetroMod."

### Can I drop the notice from my fork?

Yes — reach out and we'll approve it. If you have a good reason (renamed project, official modpack partner, platform-verified distribution, etc.), email:

**[Bownlux@revivalsmp.net](mailto:Bownlux@revivalsmp.net)**

Include:
- The fork's name and repository URL
- What you're changing (features added, focus area)
- How users will get your build
- Anything you'd like to credit

**Response time: usually 1–3 days, up to 1 week in the worst case.** I'm not running a support desk — just a person with a normal life and other projects — so you'll get a reply, it just might not be instant.

You'll get a short approval note back. This isn't a legal requirement (MIT doesn't allow adding conditions like this), it's a community norm to keep users safe from impersonators.

### For users

If your logs contain a fork-notice line and you thought you downloaded the official RetroMod:

1. **Check where you downloaded it from.** The official source is [github.com/Bownlux/RetroMod](https://github.com/Bownlux/RetroMod). If the JAR came from somewhere else, delete it and grab a fresh copy from the official repo.
2. **If it came from a reputable modpack or fork you trust**, the notice is expected — it just means the JAR isn't the official upstream build. No action needed.
3. **If it came from a site you don't recognize**, treat it as suspicious. It could be malware using the RetroMod name.

If you don't see any fork notice AND the authenticity log line isn't `OFFICIAL`, that's also a red flag — a legitimate fork maintainer should have added the notice.

## The `Implementation-Title` check

Separately from signature checking, `SignatureVerifier` also reads the JAR's manifest. Official releases set `Implementation-Title: RetroMod`. If a JAR claims a different `Implementation-Title`, we report `IMPOSTOR` — it's not even pretending to be RetroMod.

This catches the trivial case of "someone renamed retromod.jar to something malicious.jar" and prevents some self-inflicted confusion.

## How to verify manually with `jarsigner`

Users who want to independently verify a RetroMod release can run Java's built-in `jarsigner`:

```bash
jarsigner -verify -verbose -certs retromod-1.0.0-beta.1.jar
```

If the JAR is signed by the official key, `jarsigner` will print the certificate subject and a "jar verified" line. If there are any modifications after signing, `jarsigner` will say so.

This doesn't verify *which* key signed it — only that the signature is internally consistent. For fingerprint checking against the known official cert, compare the SHA-256 fingerprint `jarsigner` prints against the one published on the GitHub releases page.

## Further reading

- [Architecture]({{ '/architecture' | relative_url }}) — higher-level walkthrough of the codebase
- [Authenticity]({{ '/authenticity' | relative_url }}) — what the different signature statuses mean
- [Contributing]({{ '/contributing' | relative_url }}) — how to contribute shims, polyfills, and fixes
