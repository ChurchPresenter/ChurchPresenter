# Windows

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_now.bat` (not the CMake flow — CMake regenerates headers that lack legacy device support). The script auto-detects Visual Studio, prompts for the DeckLink SDK path, and copies the built DLL to both resource locations automatically. See `native/decklink/BUILD.md` for full instructions.

# macOS

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_macos.sh [sdk-path]`. The script prompts for the SDK path if not passed as an argument. You can pass either the SDK root (e.g. `/path/to/Blackmagic-DeckLink-SDK-15.3/`) or the headers directory directly (`/path/to/SDK/Mac/include`) — the script auto-detects the `Mac/include` subdirectory. The framework does NOT need to be installed — the script compiles DeckLinkAPIDispatch.cpp for runtime dynamic loading. It copies the built dylib to both resource locations automatically. See `native/decklink/BUILD.md` for full instructions.

# Linux

The DeckLink JNI native library needs to be rebuilt. Run `native/decklink/build_linux.sh [sdk-path]`. The script prompts for the SDK path if not passed as an argument. You can pass either the SDK root (e.g. `/path/to/Blackmagic-DeckLink-SDK-15.3/`) or the headers directory directly (`/path/to/SDK/Linux/include`) — the script auto-detects the `Linux/include` subdirectory. It copies the built .so to both resource locations automatically. See `native/decklink/BUILD.md` for full instructions.
