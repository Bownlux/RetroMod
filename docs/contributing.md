---
title: Contributing
nav_order: 12
---

# Contributing

Thanks for your interest in contributing. Retromod is a solo project that's quickly turning into a community effort, and the kinds of things contributors tend to work on — adding shims, writing polyfills, filling mapping gaps — directly improve the tool for every user. Here's how to get set up and get something merged.

## Ground rules

- **MIT license.** By contributing, you agree your changes can be distributed under MIT.
- **Be kind.** Standard modding-community etiquette. Don't be a jerk in issues or PRs.
- **Small changes are welcome.** A single missing mapping entry is a perfectly good PR.
- **Big changes need a discussion first.** Open a [GitHub discussion](https://github.com/Bownlux/Retromod/discussions) or draft issue before spending a weekend on something huge.

## Setup

```bash
git clone https://github.com/Bownlux/Retromod.git
cd Retromod
mvn package -q -DskipTests -Dexec.skip=true
```

If that produces `target/retromod-1.0.0-beta.7.jar`, you're good. Java 25 is required — see [Troubleshooting]({{ '/troubleshooting' | relative_url }}) if the build complains about class file versions.

## The skills

Most contributions fall into a handful of well-trodden workflows. Each has a skill file under `.claude/skills/` that walks through the exact steps: which files to touch, what tests to add, common pitfalls.


If you're using Claude Code, these skills activate automatically based on the task. Otherwise, open the corresponding file under `.claude/skills/` and follow it by hand — they're written to be readable as plain Markdown.

## Easy first PRs

The easiest way to start contributing is filling gaps in the SRG → Mojang mapping dictionary. Every entry directly unblocks a real Forge mod, the change is one line in a TSV file, and the verification loop is fast. See [Adding SRG Mappings]({{ '/srg-mappings' | relative_url }}) for the format and workflow.

## Git flow

Standard fork-and-PR workflow:

1. Fork the repo on GitHub.
2. Clone your fork, add the upstream remote:
   ```bash
   git remote add upstream https://github.com/Bownlux/Retromod.git
   ```
3. Create a branch:
   ```bash
   git checkout -b add-1.21.5-shim
   ```
4. Make your change. Commit with a descriptive message.
5. Push and open a PR against `main`.

Keep PRs focused — one logical change per PR. If you fix a bug and notice unrelated cleanup while you're there, split it into a second PR.

## Build and test

```bash
# Quick build (no tests)
mvn package -q -DskipTests -Dexec.skip=true

# Full build with tests
mvn package -Dexec.skip=true

# Tests only
mvn test -Dexec.skip=true

# Lite build (1.20+ only, no legacy polyfills — smaller JAR)
mvn package -P lite -DskipTests -Dexec.skip=true
```

Always pass `-Dexec.skip=true` so Maven doesn't run the CLI entrypoint mid-build.

Tests use JUnit 5. When adding a new shim or polyfill, add a test for it — even a simple "this class loads and registers" test catches a lot of wiring bugs.

## Test on a real install

After building:

```bash
cp target/retromod-1.0.0-beta.7.jar \
  ~/Library/Application\ Support/minecraft/mods/retromod-1.0.0-beta.7+26.1.jar
```

Adjust the path for your OS (see [Installation]({{ '/installation' | relative_url }})). Drop a test mod in `retromod-input/`, launch, see what happens. For repeated test cycles, the `build-and-deploy` skill automates this.

## CI and the break-glass bypass

The repo's CI (`.github/workflows/ci.yml`) runs tests on every push and PR, auto-reverts failed pushes, and auto-closes failed PRs.

**Break-glass bypass:** if you push within 5 minutes of a revert, CI skips tests. This is for unsticking the tree when CI itself is broken — don't use it to land untested code. Use it, document why in the commit message, and fix CI in a follow-up PR.

If CI reverts a change you think was correct, the first thing to check is whether the linter pass disagreed with it — sometimes the linter rewrites things the test suite doesn't like. The CLAUDE.md has notes on common gotchas.

## Code style

- Java 25. Use modern features where they genuinely help; don't force records/sealed types where a plain class is clearer.
- No tabs, 4-space indent, standard Java conventions.
- Keep methods focused — if a method is getting long enough to need internal section comments, it probably wants to be two methods.
- Log messages: `logger.info(...)` for things users should see, `logger.debug(...)` for things only contributors care about.
- Don't hardcode `"1.21.11"` or any other MC version string. Use `Retromod.TARGET_MC_VERSION` — the linter has reverted this specific mistake more than once.

## Preserving old shims

**Don't delete shims.** Even ones from 1.12.2 → 1.13. Some people are still translating ancient mods, and the shim chain relies on those old hops being present to route long version jumps. If an old shim has a bug, fix the bug; don't remove the shim.

## Commit messages

No strict convention. Prefer descriptive imperatives:

```
Add 1.21.5 → 1.21.6 shim with 18 redirects

Closes #N.
```

If a commit is a revert, say what was reverted and why. If a commit is a break-glass bypass, mention that explicitly.

## Questions?

- Something's unclear in a skill? Open an issue — skills are documentation too.
- Not sure if a change is wanted? Open a discussion first.
- Stuck on a specific transformation problem? Attach `latest.log`, the verify report, and the mod JAR to an issue.

Welcome aboard.
