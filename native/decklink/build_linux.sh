#!/bin/bash
# Build libdecklink_jni.so on Linux
# Prerequisites:
#   - GCC/G++ with C++17 support
#   - CMake (apt install cmake)
#   - BlackMagic Desktop Video drivers installed
#   - BlackMagic DeckLink SDK headers (or drivers which install them to /usr/include)
#   - JDK 21 installed (JAVA_HOME set)
#
# Usage: ./build_linux.sh [path-to-decklink-sdk-headers]

SDK_DIR="${1:-/usr/include}"

echo "Building libdecklink_jni.so..."
echo "DeckLink SDK: $SDK_DIR"

mkdir -p build && cd build

cmake .. -DDECKLINK_SDK_DIR="$SDK_DIR" -DCMAKE_BUILD_TYPE=Release
cmake --build .

if [ -f lib/libdecklink_jni.so ]; then
    echo ""
    echo "SUCCESS: lib/libdecklink_jni.so"
    echo "Copy this .so to your app's library path or /usr/local/lib/"
else
    echo ""
    echo "BUILD FAILED - check errors above"
fi

cd ..
