---
name: build-and-deploy
description: Build Retromod from source and deploy it to a Minecraft installation for testing. Use when you need to rebuild after code changes and test in-game.
argument-hint: "--skip-tests or --deploy"
---

# Build and Deploy

Build Retromod and optionally deploy to the local Minecraft installation.

## Build

```bash
# Full build with tests
mvn package

# Quick build (skip tests)
mvn package -DskipTests -Dexec.skip=true -q
```

Output: `target/retromod-1.0.0-beta.4.jar`

## Deploy

```bash
MODS="$HOME/Library/Application Support/minecraft/mods"
cp target/retromod-1.0.0-beta.4.jar "$MODS/retromod-1.0.0-beta.4+26.1.jar"
```

## Run CLI Commands

Since the JAR doesn't include dependencies, use Maven exec:

```bash
mvn -f pom.xml exec:java \
  -Dexec.mainClass="com.retromod.cli.RetromodCli" \
  -Dexec.args="<command> [args]" -q
```

### Common CLI Commands
```bash
# Analyze a mod
-Dexec.args="analyze '/path/to/mod.jar'"

# Batch transform
-Dexec.args="batch '/path/to/mods-folder' --aot"

# List shims
-Dexec.args="shims"

# Show help
-Dexec.args="--help"
```

## Project Structure

```
Retromod/
├── pom.xml                          # Maven build config (Java 21 target, ASM 9.7)
├── src/main/java/com/retromod/
│   ├── core/                        # Core runtime (Retromod, Transformer, Detectors)
│   ├── cli/                         # CLI tool (RetromodCli)
│   ├── aot/                         # AOT compiler (AotCompiler, FullAotCompiler)
│   ├── shim/                        # Version shims by loader
│   │   ├── fabric/                  # Fabric shims (1.14→...→26.1)
│   │   ├── neoforge/                # NeoForge shims (1.20.1→...→26.1)
│   │   ├── forge/                   # Forge shims (1.12.2→...→1.20)
│   │   ├── api/                     # API-specific shims (JEI, Curios, etc.)
│   │   └── ShimRegistry.java        # BFS chain finder
│   ├── mapping/                     # Name mapping (IntermediaryToMojang, MappingComposer)
│   ├── mixin/                       # Mixin compatibility
│   ├── polyfill/                    # Removed API polyfills
│   ├── embedder/                    # API embedding into mod JARs
│   ├── resources/                   # Resource/data pack transforms
│   ├── gui/                         # In-game GUI
│   └── agent/                       # Java Agent mode
├── src/main/resources/
│   ├── fabric.mod.json              # Fabric metadata
│   ├── META-INF/mods.toml           # Forge metadata
│   ├── META-INF/neoforge.mods.toml  # NeoForge metadata
│   ├── META-INF/services/           # ServiceLoader registrations
│   └── mappings/                    # Bundled mapping files
├── src/test/java/                   # Tests
├── .github/workflows/ci.yml        # GitHub Actions CI
└── .claude/skills/                  # Claude development skills
```

## Build Requirements
- Java 21+ (compiles to Java 21 bytecode, runs on Java 25)
- Maven 3.8+
- ASM 9.7 (bytecode manipulation)
- Gson (JSON parsing)
- SLF4J (logging)

## Important Notes
- The mod compiles with Java 21 target but runs on both Java 21 (1.21.x) and Java 25 (26.1)
- `pom.xml` has `<maven.compiler.source>21</maven.compiler.source>`
- The `-Dexec.skip=true` flag prevents Maven from running the CLI entrypoint during build
- CI uses Java 25 to ensure compatibility
- The deployed JAR filename should include the target MC version (e.g. `+26.1.jar`)
