# Windows

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_now.bat` (not the CMake flow — CMake regenerates headers that lack legacy device support). The script auto-detects Visual Studio, prompts for the DeckLink SDK path, and copies the built DLL to both resource locations automatically. See `native/decklink/BUILD.md` for full instructions.

# macOS

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_macos.sh`. The script defaults to the installed DeckLinkAPI.framework headers, or prompts for the SDK path. It copies the built dylib to both resource locations automatically. See `native/decklink/BUILD.md` for full instructions.

# Linux

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_linux.sh`. The script defaults to `/usr/include` if DeckLink headers are installed there, or prompts for the SDK path. It copies the built .so to both resource locations automatically. See `native/decklink/BUILD.md` for full instructions.
