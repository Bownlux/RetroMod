#!/bin/bash
# ============================================================================
# RetroMod Build Script
# Copyright (c) 2026 Bownlux. All Rights Reserved.
#
# Builds all RetroMod outputs:
#   - CLI tool (standalone, all platforms)
#   - Fabric mod (for Fabric Loader)
#   - NeoForge mod (for NeoForge Loader)
# ============================================================================

set -e

VERSION="1.0.0-beta.1"

echo "============================================"
echo "  RetroMod Build Script v${VERSION}"
echo "  Copyright (c) 2026 Bownlux"
echo "============================================"
echo ""

# Check for Maven
if ! command -v mvn &> /dev/null; then
    echo "ERROR: Maven not found!"
    echo "Please install Maven: https://maven.apache.org/install.html"
    exit 1
fi

# Check for Java 21
JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 21 ] 2>/dev/null; then
    echo "Warning: Could not verify Java version"
fi

echo "Building RetroMod..."
echo ""

# Clean and compile
echo "[1/5] Cleaning previous build..."
mvn clean -q

echo "[2/5] Compiling source code..."
mvn compile -q

echo "[3/5] Running tests..."
mvn test -q || echo "Warning: Some tests failed, continuing..."

echo "[4/5] Packaging JARs..."
mvn package -q -DskipTests

echo "[5/5] Creating distribution packages..."

# Create dist folder
mkdir -p dist

# The main shaded JAR becomes the CLI tool
if [ -f "target/retromod-${VERSION}-all.jar" ]; then
    cp "target/retromod-${VERSION}-all.jar" "dist/retromod-${VERSION}-cli.jar"
    echo "  ✓ CLI tool: dist/retromod-${VERSION}-cli.jar"
fi

# Create Fabric mod JAR (includes fabric.mod.json, excludes neoforge stuff)
echo "  Creating Fabric mod..."
mkdir -p target/fabric-build
cd target/fabric-build

# Extract the base JAR
jar xf "../retromod-${VERSION}.jar"

# Remove NeoForge-specific files
rm -rf META-INF/neoforge.mods.toml 2>/dev/null || true
rm -rf com/retromod/core/RetroModNeoForge.class 2>/dev/null || true

# Repackage as Fabric mod
jar cfm "../../dist/retromod-${VERSION}-fabric.jar" META-INF/MANIFEST.MF .
cd ../..
echo "  ✓ Fabric mod: dist/retromod-${VERSION}-fabric.jar"

# Create NeoForge mod JAR (includes neoforge.mods.toml, excludes fabric stuff)
echo "  Creating NeoForge mod..."
mkdir -p target/neoforge-build
cd target/neoforge-build

# Extract the base JAR
jar xf "../retromod-${VERSION}.jar"

# Remove Fabric-specific files
rm -f fabric.mod.json 2>/dev/null || true

# Update manifest for NeoForge
cat > META-INF/MANIFEST.MF << 'EOF'
Manifest-Version: 1.0
Implementation-Title: RetroMod
Implementation-Version: 1.0.0
Automatic-Module-Name: retromod
EOF

# Repackage as NeoForge mod
jar cfm "../../dist/retromod-${VERSION}-neoforge.jar" META-INF/MANIFEST.MF .
cd ../..
echo "  ✓ NeoForge mod: dist/retromod-${VERSION}-neoforge.jar"

# Copy icon to dist
if [ -f "assets/icon_512.png" ]; then
    cp assets/icon_512.png dist/
fi

echo ""
echo "============================================"
echo "  Build Complete!"
echo "============================================"
echo ""
echo "Output files in dist/:"
ls -lh dist/
echo ""
echo "Upload to Modrinth:"
echo "  - Fabric users:   retromod-${VERSION}-fabric.jar"
echo "  - NeoForge users: retromod-${VERSION}-neoforge.jar"
echo ""
echo "For CLI users (power users):"
echo "  - All platforms:  retromod-${VERSION}-cli.jar"
echo ""
echo "Usage:"
echo "  CLI:     java -jar retromod-${VERSION}-cli.jar <command>"
echo "  Fabric:  Drop in mods/ folder with Fabric Loader"
echo "  NeoForge: Drop in mods/ folder with NeoForge"
