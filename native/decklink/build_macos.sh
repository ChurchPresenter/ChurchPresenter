#!/bin/bash
# Build libdecklink_jni.dylib on macOS
# Prerequisites:
#   - Xcode command-line tools
#   - CMake (brew install cmake)
#   - BlackMagic Desktop Video drivers installed (provides DeckLinkAPI.framework)
#   - JDK 21 installed
#
# Usage: ./build_macos.sh [path-to-decklink-sdk-headers]

SDK_DIR="${1:-/Library/Frameworks/DeckLinkAPI.framework/Headers}"

echo "Building libdecklink_jni.dylib..."
echo "DeckLink SDK: $SDK_DIR"

mkdir -p build && cd build

cmake .. -DDECKLINK_SDK_DIR="$SDK_DIR" -DCMAKE_BUILD_TYPE=Release
cmake --build .

if [ -f lib/libdecklink_jni.dylib ]; then
    echo ""
    echo "SUCCESS: lib/libdecklink_jni.dylib"
    echo "Copy this dylib to your app's library path or /usr/local/lib/"
else
    echo ""
    echo "BUILD FAILED - check errors above"
fi

cd ..
