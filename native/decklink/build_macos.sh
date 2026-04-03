#!/bin/bash
# Build libdecklink_jni.dylib on macOS (no-CMake quick build)
# Prerequisites:
#   - Xcode command-line tools
#   - BlackMagic Desktop Video drivers installed (provides DeckLinkAPI.framework)
#   - JDK 21 installed (JAVA_HOME set)
#
# Usage: ./build_macos.sh [path-to-decklink-sdk-headers]

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
REPO_DIR="$SCRIPT_DIR/../.."

# SDK path: argument or default framework location, or prompt
if [ -n "$1" ]; then
    SDK_DIR="$1"
elif [ -d "/Library/Frameworks/DeckLinkAPI.framework/Headers" ]; then
    SDK_DIR="/Library/Frameworks/DeckLinkAPI.framework/Headers"
else
    read -rp "Enter DeckLink SDK headers path: " SDK_DIR
fi

if [ ! -d "$SDK_DIR" ]; then
    echo "ERROR: SDK directory not found: $SDK_DIR"
    exit 1
fi

# Detect JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    JAVA_HOME=$(/usr/libexec/java_home 2>/dev/null || true)
    if [ -z "$JAVA_HOME" ]; then
        echo "ERROR: JAVA_HOME is not set and could not be auto-detected."
        exit 1
    fi
    echo "Auto-detected JAVA_HOME: $JAVA_HOME"
fi

echo "Building libdecklink_jni.dylib..."
echo "DeckLink SDK: $SDK_DIR"
echo "JAVA_HOME: $JAVA_HOME"

clang++ -std=c++17 -shared -o libdecklink_jni.dylib \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
    -I"$SDK_DIR" \
    -framework CoreFoundation \
    -framework DeckLinkAPI \
    -F/Library/Frameworks \
    decklink_jni.cpp

if [ -f libdecklink_jni.dylib ]; then
    echo ""
    echo "=== BUILD SUCCESS ==="
    ls -la libdecklink_jni.dylib

    # Detect architecture for resource folder
    ARCH=$(uname -m)
    if [ "$ARCH" = "arm64" ]; then
        RES_DIR="macos-arm64"
    else
        RES_DIR="macos-x64"
    fi

    # Copy to resource locations
    cp -v libdecklink_jni.dylib "$REPO_DIR/composeApp/src/jvmMain/resources/$RES_DIR/libdecklink_jni.dylib"
    cp -v libdecklink_jni.dylib "$REPO_DIR/composeApp/src/jvmMain/appResources/macos/libdecklink_jni.dylib"
    echo "=== Copied to resources and appResources ==="
else
    echo ""
    echo "BUILD FAILED - check errors above"
    exit 1
fi
