---
title: Home
layout: default
nav_order: 1
description: "Run old Minecraft mods on new Minecraft versions. Bytecode-level compatibility layer for Fabric, NeoForge, and Forge."
permalink: /
---

<div class="retromod-hero">
  <div class="retromod-hero-inner">
    <h1 class="retromod-hero-title">RetroMod</h1>
    <p class="retromod-hero-tagline">Run old Minecraft mods on new Minecraft versions.</p>
    <p class="retromod-hero-subtitle">
      A bytecode-level compatibility layer for Fabric, NeoForge, and Forge. Transforms old mods at load time so they target the current Minecraft API — no source changes, no recompilation.
    </p>
    <div class="retromod-hero-cta">
      <a class="retromod-btn retromod-btn-primary" href="https://github.com/Bownlux/RetroMod/releases">
        Download
      </a>
      <a class="retromod-btn retromod-btn-secondary" href="https://github.com/Bownlux/RetroMod">
        View on GitHub
      </a>
      <a class="retromod-btn retromod-btn-ghost" href="{{ '/installation' | relative_url }}">
        Get started →
      </a>
    </div>
    <div class="retromod-hero-stats">
      <div class="retromod-stat">
        <span class="retromod-stat-value">145</span>
        <span class="retromod-stat-label">version shims</span>
      </div>
      <div class="retromod-stat">
        <span class="retromod-stat-value">30</span>
        <span class="retromod-stat-label">polyfill providers</span>
      </div>
      <div class="retromod-stat">
        <span class="retromod-stat-value">328</span>
        <span class="retromod-stat-label">API redirects</span>
      </div>
      <div class="retromod-stat">
        <span class="retromod-stat-value">MIT</span>
        <span class="retromod-stat-label">open source</span>
      </div>
    </div>
  </div>
</div>

<div class="retromod-features">

  <div class="retromod-feature-card">
    <div class="retromod-feature-icon">⚡</div>
    <h3>AOT + JIT</h3>
    <p>Transform mods at startup (JIT) for fast iteration, or pre-compile once (AOT) for instant subsequent launches.</p>
  </div>

  <div class="retromod-feature-card">
    <div class="retromod-feature-icon">🧩</div>
    <h3>Cross-loader</h3>
    <p>Works with Fabric, NeoForge, and Forge. The same mod transformed for any loader's target version.</p>
  </div>

  <div class="retromod-feature-card">
    <div class="retromod-feature-icon">🔍</div>
    <h3>Verify transforms</h3>
    <p>Optional post-transformation verification catches unmapped names and missing APIs before the game loads.</p>
  </div>

  <div class="retromod-feature-card">
    <div class="retromod-feature-icon">🎛️</div>
    <h3>In-game config</h3>
    <p>Toggle features from the title screen. No config-file hunting, no restarts for most options.</p>
  </div>

  <div class="retromod-feature-card">
    <div class="retromod-feature-icon">🛡️</div>
    <h3>Hardened I/O</h3>
    <p>Zip-slip safe, zip-bomb capped, signed official releases. Transforming JARs shouldn't be a liability.</p>
  </div>

  <div class="retromod-feature-card">
    <div class="retromod-feature-icon">📚</div>
    <h3>Open source</h3>
    <p>MIT licensed. Fork it, modify it, redistribute it. Contribute shims and polyfills for the mods you care about.</p>
  </div>

</div>

<div class="retromod-explore">
  <h2>Explore the wiki</h2>
  <div class="retromod-card-grid">

    <a class="retromod-link-card" href="{{ '/installation' | relative_url }}">
      <h3>Installation</h3>
      <p>Get RetroMod running on your launcher of choice.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/gui' | relative_url }}">
      <h3>In-game GUI</h3>
      <p>The title-screen button, file picker, and settings screen.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/config' | relative_url }}">
      <h3>Config reference</h3>
      <p>Every config option, defaults, and when to change it.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/cli' | relative_url }}">
      <h3>CLI tool</h3>
      <p>Transform, batch, AOT-compile, and verify from the command line.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/verify-transforms' | relative_url }}">
      <h3>Verify transforms</h3>
      <p>Catch broken references before the game tries to load them.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/technical' | relative_url }}">
      <h3>Technical</h3>
      <p>How the transformer works, the security model, and fork policy.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/authenticity' | relative_url }}">
      <h3>Authenticity</h3>
      <p>Telling the official build from a fork or an impostor.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/architecture' | relative_url }}">
      <h3>Architecture</h3>
      <p>Code tour — ASM visitor chain, shim registry, polyfills.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/troubleshooting' | relative_url }}">
      <h3>Troubleshooting</h3>
      <p>Mod won't load, strange crashes, verification warnings.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/faq' | relative_url }}">
      <h3>FAQ</h3>
      <p>Common questions: safety, servers, modpacks, commercial use.</p>
    </a>

    <a class="retromod-link-card" href="{{ '/contributing' | relative_url }}">
      <h3>Contributing</h3>
      <p>Open source, MIT licensed — help build shims and polyfills.</p>
    </a>

  </div>
</div>

<div class="retromod-cta-strip">
  <h2>Ready to try it?</h2>
  <p>Drop the JAR in your mods folder and launch the game. RetroMod picks up old mods from <code>retromod-input/</code>, transforms them, and moves them into place.</p>
  <a class="retromod-btn retromod-btn-primary" href="{{ '/installation' | relative_url }}">Read the install guide</a>
</div>
