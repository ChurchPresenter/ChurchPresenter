# Windows

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_now.bat` (not the CMake flow — CMake regenerates headers that lack legacy device support). See `native/decklink/BUILD.md` for full instructions. After building, copy `native/decklink/decklink_jni.dll` to `composeApp/src/jvmMain/resources/win-x64/decklink_jni.dll` and `composeApp/src/jvmMain/appResources/windows/decklink_jni.dll`.

# macOS

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_macos.sh`. See `native/decklink/BUILD.md` for full instructions. After building, copy `native/decklink/build/lib/libdecklink_jni.dylib` to `composeApp/src/jvmMain/resources/macos-arm64/libdecklink_jni.dylib` and `composeApp/src/jvmMain/appResources/macos/libdecklink_jni.dylib`.

# Linux

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_linux.sh`. See `native/decklink/BUILD.md` for full instructions. After building, copy `native/decklink/build/lib/libdecklink_jni.so` to `composeApp/src/jvmMain/resources/linux-x64/libdecklink_jni.so` and `composeApp/src/jvmMain/appResources/linux/libdecklink_jni.so`.