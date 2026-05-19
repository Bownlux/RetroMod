---
title: Authenticity
nav_order: 7
---

# Authenticity / signed builds

Retromod includes a `SignatureVerifier` that checks whether the running JAR is an official build signed by me. It's **purely informational** — Retromod still runs regardless of what it finds. No feature is gated on it. This page explains why it exists, what the statuses mean, and where to see them.

## Why this exists (and what it isn't)

Retromod modifies other mods' bytecode. That's a lot of trust. If somebody distributed a malicious fork under the name "Retromod" — same icon, same title-screen button, same everything — users might install it without realizing it's not the real thing. The signature check is a simple way to answer one question: "is this the JAR that was built and signed by the actual Retromod project, or is it something else?"

It is **not**:

- **DRM.** The MIT license is preserved. You can fork, rebuild, redistribute, sell, sublicense — everything the license grants. Unsigned and modified builds work identically to signed ones.
- **A whitelist.** There's no phone-home, no server check, no list of approved forks. The verification is fully local; it compares a signature embedded in the JAR against a public key baked into the runtime.
- **A blocker.** Every status below still lets Retromod start, load, and transform mods.

Think of it like a package manager showing a "maintainer: Bownlux" line in the install output. Nice to know, not load-bearing.

## The statuses

`SignatureVerifier` produces one of five statuses at startup. The status is written to the launcher log — not to the in-game UI, because we don't want the warning to scare users away from legitimate forks. You can also check any JAR manually by running `jarsigner -verify -verbose -certs retromod-*.jar`.

### OFFICIAL

The JAR contains a valid signature that matches my public key. This is what you get when you download a release straight from the [GitHub releases page](https://github.com/Bownlux/Retromod/releases) and don't modify it afterward.

### UNSIGNED

The JAR has no signature at all — for example, a build you compiled yourself from source, or an early beta that was released before signing was added. **Completely normal.** The rest of Retromod works the same.

### UNOFFICIAL

The JAR has a signature, but it doesn't match my public key. This is a third-party build signed by somebody else — a fork, a modpack's bundled copy, a platform's re-signed distribution. Not inherently malicious; just not an official build.

### TAMPERED

The JAR claims to be signed with my key, but its contents don't match the signature — i.e. somebody modified an official build after it was signed. This is worth paying attention to. It means the JAR is no longer what I released, but the signature is still there as if it were. If you didn't modify the JAR yourself, consider re-downloading from the [official releases page](https://github.com/Bownlux/Retromod/releases).

### IMPOSTOR

The JAR is signed with a signature structure that mimics the official format but using a different key, packaged in a way clearly intended to look official. This is the status to actually worry about — it's the fingerprint of a deliberate attempt to impersonate Retromod. If you see this, **do not run the JAR**. Get rid of it, grab a fresh copy from GitHub, and think about where you downloaded the suspicious one from.

## Where to check

- **Log file:** Retromod logs the status on startup. Search your Minecraft log for `[Retromod] Authenticity:`.
- **Manually with `jarsigner`:** run `jarsigner -verify -verbose -certs retromod-1.0.0-beta.4.jar` in a terminal. Java's built-in verifier will print the cert and whether the contents are intact.

The authenticity status is intentionally **not** shown in the in-game settings screen. We don't want users to see an "UNOFFICIAL" warning in a prominent UI and assume legitimate forks are malicious — the log line is enough information for anyone who needs it.

## "I got an UNSIGNED warning, is my game going to explode?"

No. Beta builds haven't all been signed yet, and lots of users build from source or install through third-party launchers that re-bundle mods. UNSIGNED is the most common non-OFFICIAL status and there's nothing wrong with it. The only status worth immediate action is IMPOSTOR, and even TAMPERED is more "huh, that's odd" than "run for the hills" unless you didn't intentionally modify the JAR.

## For fork maintainers

If you're redistributing Retromod as part of a modpack or fork, you don't need to strip the signature check — it'll simply report UNOFFICIAL (or UNSIGNED if you didn't sign your build), and everything keeps working. If you'd like your fork to show its own identity, you can sign with your own key and modify the displayed name; again, MIT license, do what you want.

**Please keep the fork-warning log message.** When the verifier detects anything other than OFFICIAL, it prints:

> `You are using a Retromod Fork. If this was advertised as the official Retromod, this is NOT official! Check github.com/Bownlux/Retromod for the real thing.`

This is what protects users from malware that impersonates Retromod — if everyone keeps the warning, a JAR that doesn't print it is an obvious red flag. If you'd like your fork to be granted permission to remove or replace that line, email [Bownlux@revivalsmp.net](mailto:Bownlux@revivalsmp.net). See the [Technical]({{ '/technical' | relative_url }}) page for the full fork policy.

## For contributors

The verifier lives in `src/main/java/com/retromod/security/SignatureVerifier.java`. It's a standalone module — disabling or replacing it is a self-contained change. PRs to improve it are welcome, especially around clearer user messaging when a status is concerning.
