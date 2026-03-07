#!/bin/bash
# ============================================================================
# RetroMod Multi-Version Build Script
# Copyright (c) 2026 RevivalSMP. MIT License.
#
# Builds RetroMod for ALL loaders and ALL supported MC versions:
#   - Fabric (1.14 through 1.21.11)
#   - Forge (1.12.2 through 1.21.11)
#   - NeoForge (1.20.1 through 1.21.11)
#   - CLI tool (standalone)
# ============================================================================

# Don't exit on error - we'll handle errors ourselves
# set -e

VERSION="1.0.0-beta.1"
MC_VERSIONS=("1.12.2" "1.13" "1.13.1" "1.13.2" "1.14" "1.14.1" "1.14.2" "1.14.3" "1.14.4" "1.15" "1.15.1" "1.15.2" "1.16" "1.16.1" "1.16.2" "1.16.3" "1.16.4" "1.16.5" "1.17" "1.17.1" "1.18" "1.18.1" "1.18.2" "1.19" "1.19.1" "1.19.2" "1.19.3" "1.19.4" "1.20" "1.20.1" "1.20.2" "1.20.3" "1.20.4" "1.20.5" "1.20.6" "1.21" "1.21.1" "1.21.2" "1.21.3" "1.21.4" "1.21.5" "1.21.6" "1.21.7" "1.21.8" "1.21.9" "1.21.10" "1.21.11")
LOADERS=("fabric" "forge" "neoforge")

echo "============================================"
echo "  RetroMod Multi-Version Build Script"
echo "  Version: ${VERSION}"
echo "  MIT License - RevivalSMP"
echo "============================================"
echo ""
echo "Building for:"
echo "  - ${#MC_VERSIONS[@]} Minecraft versions (1.12.2 - 1.21.11)"
echo "  - ${#LOADERS[@]} mod loaders (Fabric, Forge, NeoForge)"
echo ""

# Check for Maven
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found!"
    echo "Install: https://maven.apache.org/install.html"
    exit 1
fi

# Create output directories
mkdir -p dist/Fabric
mkdir -p dist/Forge
mkdir -p dist/NeoForge
mkdir -p dist/CLI

# Check for --skip-build flag (used when called from Maven)
SKIP_BUILD=false
for arg in "$@"; do
    if [ "$arg" = "--skip-build" ]; then
        SKIP_BUILD=true
    fi
done

if [ "$SKIP_BUILD" = false ]; then
    # Build the base JAR first
    echo "[Step 1/4] Building base JAR with Maven..."
    mvn clean package -DskipTests
else
    echo "[Step 1/4] Skipping Maven build (--skip-build flag)"
fi

# Find the built JAR (handle different naming)
BASE_JAR=""
if [ -f "target/retromod-${VERSION}.jar" ]; then
    BASE_JAR="target/retromod-${VERSION}.jar"
elif [ -f "target/retromod.jar" ]; then
    BASE_JAR="target/retromod.jar"
else
    # Find any jar that's not sources/javadoc/agent/all
    BASE_JAR=$(find target -maxdepth 1 -name "retromod*.jar" -not -name "*sources*" -not -name "*javadoc*" -not -name "*agent*" -not -name "*all*" | head -1)
fi

if [ -z "$BASE_JAR" ] || [ ! -f "$BASE_JAR" ]; then
    echo "ERROR: Base build failed! No JAR found in target/"
    echo "Build failed"
    exit 1
fi

echo "  Base JAR: $BASE_JAR"

echo ""
echo "[Step 2/4] Creating CLI tool..."

# Find the shaded/all JAR for CLI, or use base JAR
CLI_JAR=""
if [ -f "target/retromod-${VERSION}-all.jar" ]; then
    CLI_JAR="target/retromod-${VERSION}-all.jar"
elif [ -f "target/retromod-${VERSION}-shaded.jar" ]; then
    CLI_JAR="target/retromod-${VERSION}-shaded.jar"
else
    CLI_JAR="$BASE_JAR"
fi

cp "$CLI_JAR" "dist/CLI/retromod-${VERSION}-cli.jar"
echo "  dist/CLI/retromod-${VERSION}-cli.jar"

echo ""
echo "[Step 3/4] Creating loader-specific JARs..."

# Function to check if a loader supports a given MC version
loader_supports_version() {
    local loader=$1
    local ver=$2
    case $loader in
        neoforge)
            # NeoForge started at 1.20.1
            case $ver in
                1.12*|1.13*|1.14*|1.15*|1.16*|1.17*|1.18*|1.19*|1.20) return 1 ;;
                *) return 0 ;;
            esac
            ;;
        fabric)
            # Fabric started at 1.14
            case $ver in
                1.12*|1.13*) return 1 ;;
                *) return 0 ;;
            esac
            ;;
        *) return 0 ;;  # Forge supports all versions
    esac
}

# Function to create a mod JAR for specific loader and version
create_mod_jar() {
    local LOADER=$1
    local MC_VERSION=$2
    local LOADER_DIR=""

    # Skip unsupported loader/version combinations
    if ! loader_supports_version "$LOADER" "$MC_VERSION"; then
        return 0  # Silently skip
    fi

    # Map to correct directory names
    case $LOADER in
        fabric) LOADER_DIR="Fabric" ;;
        forge) LOADER_DIR="Forge" ;;
        neoforge) LOADER_DIR="NeoForge" ;;
    esac

    local OUTPUT_NAME="retromod-${VERSION}+${MC_VERSION}.jar"
    local ORIG_DIR="$(pwd)"
    local OUTPUT_PATH="${ORIG_DIR}/dist/${LOADER_DIR}/${MC_VERSION}/${OUTPUT_NAME}"

    # Create version subdirectory
    mkdir -p "${ORIG_DIR}/dist/${LOADER_DIR}/${MC_VERSION}"

    # Create temp directory
    local TEMP_DIR=$(mktemp -d)

    # Extract base JAR
    unzip -q "$BASE_JAR" -d "$TEMP_DIR" 2>/dev/null || {
        echo "ERROR: Failed to extract base JAR"
        rm -rf "$TEMP_DIR"
        return 1
    }

    # Remove other loaders' files
    case $LOADER in
        fabric)
            rm -f "$TEMP_DIR/META-INF/neoforge.mods.toml" 2>/dev/null
            rm -f "$TEMP_DIR/META-INF/mods.toml" 2>/dev/null
            rm -f "$TEMP_DIR/pack.mcmeta" 2>/dev/null
            # Update fabric.mod.json with correct MC version
            if [ -f "$TEMP_DIR/fabric.mod.json" ]; then
                if command -v python3 &> /dev/null; then
                    python3 -c "
import json
try:
    with open('$TEMP_DIR/fabric.mod.json', 'r') as f:
        data = json.load(f)
    data['depends']['minecraft'] = '${MC_VERSION}'
    data['version'] = '${VERSION}'
    with open('$TEMP_DIR/fabric.mod.json', 'w') as f:
        json.dump(data, f, indent=2)
except Exception as e:
    print(f'Warning: Could not update fabric.mod.json: {e}')
"
                else
                    # Fallback: use sed (less reliable but works without Python)
                    sed -i.bak "s/\"minecraft\": \"[^\"]*\"/\"minecraft\": \"${MC_VERSION}\"/" "$TEMP_DIR/fabric.mod.json" 2>/dev/null || true
                    rm -f "$TEMP_DIR/fabric.mod.json.bak" 2>/dev/null
                fi
            fi
            ;;
        forge)
            rm -f "$TEMP_DIR/fabric.mod.json" 2>/dev/null
            rm -f "$TEMP_DIR/META-INF/neoforge.mods.toml" 2>/dev/null
            # Create Forge mods.toml
            mkdir -p "$TEMP_DIR/META-INF"
            cat > "$TEMP_DIR/META-INF/mods.toml" << TOML
modLoader = "javafml"
loaderVersion = "[52,)"
license = "MIT"
issueTrackerURL = "https://github.com/Bownlux/MC-RetroMod/issues"

[[mods]]
modId = "retromod"
version = "${VERSION}"
displayName = "RetroMod"
description = '''
Made by the Developers of revivalsmp.net

Backwards compatibility layer for Minecraft mods.
Run older Forge mods on Minecraft ${MC_VERSION}!

Made by the Developers of revivalsmp.net
'''
authors = "Bownlux"
logoFile = "assets/retromod/icon.png"

[[dependencies.retromod]]
modId = "forge"
mandatory = true
versionRange = "[52,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.retromod]]
modId = "minecraft"
mandatory = true
versionRange = "[${MC_VERSION}]"
ordering = "NONE"
side = "BOTH"
TOML
            ;;
        neoforge)
            rm -f "$TEMP_DIR/fabric.mod.json" 2>/dev/null
            rm -f "$TEMP_DIR/META-INF/mods.toml" 2>/dev/null
            # Create NeoForge mods.toml
            mkdir -p "$TEMP_DIR/META-INF"
            cat > "$TEMP_DIR/META-INF/neoforge.mods.toml" << TOML
modLoader = "javafml"
loaderVersion = "[4,)"
license = "MIT"
issueTrackerURL = "https://github.com/Bownlux/MC-RetroMod/issues"

[[mods]]
modId = "retromod"
version = "${VERSION}"
displayName = "RetroMod"
description = '''
Made by the Developers of revivalsmp.net

Backwards compatibility layer for Minecraft mods.
Run older NeoForge mods on Minecraft ${MC_VERSION}!

Made by the Developers of revivalsmp.net
'''
authors = "Bownlux"
logoFile = "assets/retromod/icon.png"

[[dependencies.retromod]]
modId = "neoforge"
type = "required"
versionRange = "[21.0,)"
ordering = "NONE"
side = "BOTH"

[[dependencies.retromod]]
modId = "minecraft"
type = "required"
versionRange = "[${MC_VERSION}]"
ordering = "NONE"
side = "BOTH"
TOML
            ;;
    esac

    # Update manifest with version info
    mkdir -p "$TEMP_DIR/META-INF"
    cat > "$TEMP_DIR/META-INF/MANIFEST.MF" << MANIFEST
Manifest-Version: 1.0
Implementation-Title: RetroMod
Implementation-Version: ${VERSION}
RetroMod-Target-MC: ${MC_VERSION}
RetroMod-Loader: ${LOADER}
Automatic-Module-Name: retromod

MANIFEST

    # Repackage JAR using zip (more compatible than jar command)
    cd "$TEMP_DIR"
    zip -qr "$OUTPUT_PATH" . 2>/dev/null || {
        # Fallback to jar command
        jar cfm "$OUTPUT_PATH" META-INF/MANIFEST.MF . 2>/dev/null || {
            echo "ERROR: Failed to create JAR"
            cd "$ORIG_DIR"
            rm -rf "$TEMP_DIR"
            return 1
        }
    }
    cd "$ORIG_DIR"

    # Cleanup
    rm -rf "$TEMP_DIR"

    echo "  ${MC_VERSION}"
    return 0
}

# Build all combinations
FAILED=0

for LOADER in "${LOADERS[@]}"; do
    echo ""
    # Simple capitalization for display
    case $LOADER in
        fabric) LOADER_CAP="Fabric" ;;
        forge) LOADER_CAP="Forge" ;;
        neoforge) LOADER_CAP="NeoForge" ;;
        *) LOADER_CAP="$LOADER" ;;
    esac
    echo "Building ${LOADER_CAP} JARs..."
    for MC_VERSION in "${MC_VERSIONS[@]}"; do
        if ! create_mod_jar "$LOADER" "$MC_VERSION"; then
            FAILED=$((FAILED + 1))
        fi
    done
done

echo ""
echo "[Step 4/4] Copying assets..."
cp assets/icon_512.png dist/ 2>/dev/null || echo "  (icon_512.png not found)"
cp assets/icon_128.png dist/ 2>/dev/null || echo "  (icon_128.png not found)"

# Count output files
FABRIC_COUNT=$(find dist/Fabric -name "*.jar" 2>/dev/null | wc -l | tr -d ' ')
FORGE_COUNT=$(find dist/Forge -name "*.jar" 2>/dev/null | wc -l | tr -d ' ')
NEOFORGE_COUNT=$(find dist/NeoForge -name "*.jar" 2>/dev/null | wc -l | tr -d ' ')
CLI_COUNT=$(find dist/CLI -name "*.jar" 2>/dev/null | wc -l | tr -d ' ')
TOTAL_COUNT=$((FABRIC_COUNT + FORGE_COUNT + NEOFORGE_COUNT + CLI_COUNT))

echo ""
echo "============================================"
echo "  Build Complete!"
echo "============================================"
echo ""
echo "Output structure:"
echo "  dist/"
echo "  ├── Fabric/        (1.14 - 1.21.11)"
echo "  ├── Forge/         (1.12.2 - 1.21.11)"
echo "  ├── NeoForge/      (1.20.1 - 1.21.11)"
echo "  └── CLI/"
echo "      └── retromod-${VERSION}-cli.jar"
echo ""
echo "Summary:"
echo "  Fabric:   ${FABRIC_COUNT} JARs"
echo "  Forge:    ${FORGE_COUNT} JARs"
echo "  NeoForge: ${NEOFORGE_COUNT} JARs"
echo "  CLI:      ${CLI_COUNT} JAR"
echo "  ─────────────────────"
echo "  Total:    ${TOTAL_COUNT} JARs"

if [ $FAILED -gt 0 ]; then
    echo ""
    echo "  WARNING: ${FAILED} JAR(s) failed to build"
fi

echo ""
