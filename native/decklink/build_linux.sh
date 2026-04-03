#!/bin/bash
# Build libdecklink_jni.so on Linux (no-CMake quick build)
# Prerequisites:
#   - GCC/G++ with C++17 support
#   - BlackMagic Desktop Video drivers installed
#   - BlackMagic DeckLink SDK headers (drivers install them to /usr/include)
#   - JDK 21 installed (JAVA_HOME set)
#
# Usage: ./build_linux.sh [path-to-decklink-sdk-headers]

set -e
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"
REPO_DIR="$SCRIPT_DIR/../.."

# SDK path: argument or prompt
if [ -n "$1" ]; then
    SDK_DIR="$1"
else
    read -rp "Enter DeckLink SDK path (root or Linux/include): " SDK_DIR
fi

if [ ! -d "$SDK_DIR" ]; then
    echo "ERROR: SDK directory not found: $SDK_DIR"
    exit 1
fi

# Auto-detect Linux/include subdirectory if given the SDK root
if [ ! -f "$SDK_DIR/DeckLinkAPI.h" ] && [ -f "$SDK_DIR/Linux/include/DeckLinkAPI.h" ]; then
    SDK_DIR="$SDK_DIR/Linux/include"
    echo "Auto-detected headers in: $SDK_DIR"
fi

if [ ! -f "$SDK_DIR/DeckLinkAPI.h" ]; then
    echo "ERROR: DeckLinkAPI.h not found in $SDK_DIR"
    echo "Pass the path containing DeckLinkAPI.h (e.g. /path/to/SDK/Linux/include)"
    exit 1
fi

# Detect JAVA_HOME if not set
if [ -z "$JAVA_HOME" ]; then
    if [ -d "/usr/lib/jvm/java-21" ]; then
        JAVA_HOME="/usr/lib/jvm/java-21"
    elif [ -d "/usr/lib/jvm/default-java" ]; then
        JAVA_HOME="/usr/lib/jvm/default-java"
    else
        echo "ERROR: JAVA_HOME is not set and could not be auto-detected."
        exit 1
    fi
    echo "Auto-detected JAVA_HOME: $JAVA_HOME"
fi

echo "Building libdecklink_jni.so..."
echo "DeckLink SDK: $SDK_DIR"
echo "JAVA_HOME: $JAVA_HOME"

g++ -std=c++17 -shared -fPIC -o libdecklink_jni.so \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    -I"$SDK_DIR" \
    decklink_jni.cpp \
    -ldl

if [ -f libdecklink_jni.so ]; then
    echo ""
    echo "=== BUILD SUCCESS ==="
    ls -la libdecklink_jni.so

    # Copy to resource locations
    cp -v libdecklink_jni.so "$REPO_DIR/composeApp/src/jvmMain/resources/linux-x64/libdecklink_jni.so"
    cp -v libdecklink_jni.so "$REPO_DIR/composeApp/src/jvmMain/appResources/linux/libdecklink_jni.so"
    echo "=== Copied to resources and appResources ==="
else
    echo ""
    echo "BUILD FAILED - check errors above"
    exit 1
fi
