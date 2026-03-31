# Building the DeckLink JNI Library

Native JNI bridge between ChurchPresenter (Java/Kotlin) and the BlackMagic DeckLink SDK.

**Current SDK version: 15.3** (`BLACKMAGIC_DECKLINK_API_VERSION 0x0f030000`)

End users do NOT need the SDK — only the compiled library (`.dll` / `.dylib`) is bundled with the app.
Users with DeckLink hardware just need the BlackMagic Desktop Video drivers (which they already have).

---

## Windows

### Prerequisites

1. **BlackMagic DeckLink SDK** — [download](https://www.blackmagicdesign.com/support) (free, extract anywhere)
2. **Visual Studio 2022** (or Build Tools) with **C++ Desktop Development** workload
3. **CMake 3.15+** — `winget install Kitware.CMake` or [cmake.org/download](https://cmake.org/download/)
4. **JDK 21** — `JAVA_HOME` must be set

### Steps

1. Open **Developer Command Prompt for VS 2022** (or **x64 Native Tools Command Prompt**)

2. Navigate to this directory:
   ```
   cd path\to\ChurchPresenter\native\decklink
   ```

3. Regenerate JNI headers (required after any native method changes in `DeckLinkIO.kt`):
   ```
   javac -h . DeckLinkManager.java
   ```

4. Create a build directory and run CMake:
   ```
   mkdir build
   cd build
   cmake .. -G "Visual Studio 17 2022" -A x64 -DDECKLINK_SDK_DIR="path\to\Blackmagic-DeckLink-SDK\Win\include"
   ```

5. Build:
   ```
   cmake --build . --config Release
   ```

6. The output DLL will be at:
   ```
   build\lib\Release\decklink_jni.dll
   ```

### Alternative (no CMake)

From **x64 Native Tools Command Prompt for VS 2022**:

```
cd path\to\ChurchPresenter\native\decklink

REM 1. Generate headers from IDL
midl /h DeckLinkAPI_h.h /iid DeckLinkAPI_i.c /env x64 /W1 /char signed "path\to\Blackmagic-DeckLink-SDK\Win\include\DeckLinkAPI.idl"

REM 2. Compile
cl /LD /EHsc /std:c++17 /O2 ^
   /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
   /I"path\to\Blackmagic-DeckLink-SDK\Win\include" ^
   /I"." ^
   decklink_jni.cpp DeckLinkAPI_i.c ^
   /Fe:decklink_jni.dll ^
   /link ole32.lib oleaut32.lib
```

Output: `decklink_jni.dll` in the current directory.

---

## macOS

### Prerequisites

1. **BlackMagic Desktop Video drivers** — [download](https://www.blackmagicdesign.com/support) (installs `DeckLinkAPI.framework`)
2. **Xcode command-line tools** — `xcode-select --install`
3. **CMake** — `brew install cmake`
4. **JDK 21** — `JAVA_HOME` must be set

### Steps

1. Navigate to this directory:
   ```
   cd ~/Documents/GitHub/ChurchPresenter/native/decklink
   ```

2. Regenerate JNI headers:
   ```
   javac -h . DeckLinkManager.java
   ```

3. Run the build script:
   ```
   chmod +x build_macos.sh
   ./build_macos.sh
   ```

4. The output dylib will be at:
   ```
   build/lib/libdecklink_jni.dylib
   ```

### Alternative (no CMake)

```
cd ~/Documents/GitHub/ChurchPresenter/native/decklink

clang++ -std=c++17 -shared -o libdecklink_jni.dylib \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/darwin" \
    -I/Library/Frameworks/DeckLinkAPI.framework/Headers \
    -framework CoreFoundation \
    -framework DeckLinkAPI \
    -F/Library/Frameworks \
    decklink_jni.cpp
```

Output: `libdecklink_jni.dylib` in the current directory.

---

## Linux

### Prerequisites

1. **BlackMagic Desktop Video drivers** — [download](https://www.blackmagicdesign.com/support) (`.deb` or `.rpm`)
2. **Build tools** — `sudo apt install build-essential cmake`
3. **JDK 21** — `JAVA_HOME` must be set

### Steps

```
cd ~/Documents/GitHub/ChurchPresenter/native/decklink
javac -h . DeckLinkManager.java
mkdir -p build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release -DDECKLINK_SDK_DIR=/usr/include
cmake --build .
```

Output: `build/lib/libdecklink_jni.so`

### Alternative (no CMake)

```
g++ -std=c++17 -shared -fPIC -o libdecklink_jni.so \
    -I"$JAVA_HOME/include" -I"$JAVA_HOME/include/linux" \
    -I/usr/include \
    decklink_jni.cpp \
    -ldl
```

---

## Installing the Built Library

### During development (running via Gradle)

Copy the compiled library into the project so Gradle can find it at runtime:

```
# Windows — copy decklink_jni.dll to:
composeApp/src/jvmMain/resources/win-x64/decklink_jni.dll

# macOS — copy libdecklink_jni.dylib to:
composeApp/src/jvmMain/resources/macos-x64/libdecklink_jni.dylib
# (or macos-arm64/ for Apple Silicon)

# Linux — copy libdecklink_jni.so to:
composeApp/src/jvmMain/resources/linux-x64/libdecklink_jni.so
```

Then add this JVM argument in `build.gradle.kts` under `jvmArgs(...)`:
```
"-Djava.library.path=src/jvmMain/resources/win-x64"   // adjust per platform
```

### Packaged app (installed via MSI/DMG)

| Platform | Where to place it |
|----------|-------------------|
| **Windows** | Next to `ChurchPresenter.exe` inside the install directory (e.g. `C:\Program Files\ChurchPresenter\`) |
| **macOS** | Inside `ChurchPresenter.app/Contents/app/lib/` |
| **Linux** | `/usr/local/lib/` then run `sudo ldconfig` |

To auto-bundle with the installer, place the compiled binary in:
```
composeApp/src/jvmMain/appResources/windows/decklink_jni.dll
composeApp/src/jvmMain/appResources/macos/libdecklink_jni.dylib
composeApp/src/jvmMain/appResources/linux/libdecklink_jni.so
```
These get included in the packaged app via the `appResourcesRootDir` setting in `build.gradle.kts`.

The app handles the library being absent gracefully — DeckLink options simply won't appear in the settings.

---

## Updating to a Newer DeckLink SDK

BlackMagic releases updated SDKs at [blackmagicdesign.com/support](https://www.blackmagicdesign.com/support). To update:

1. Download the new SDK and extract it
2. Point the build at the new SDK path:
   ```
   # CMake
   cmake .. -DDECKLINK_SDK_DIR="C:\path\to\new-sdk\Win\include"

   # No-CMake (update the -I path in the midl/cl commands)
   ```
3. Rebuild — the IDL files in the new SDK will generate updated headers automatically
4. Update the version at the top of this file (`BUILD.md`)
5. Test with your DeckLink hardware
6. Replace the compiled DLL/dylib in `appResources/`

**When do you need to update?**
- New DeckLink hardware models not detected → update SDK + drivers
- BlackMagic drops support for old SDK versions → update SDK
- You don't need to update just because a new SDK is released — the API is backward-compatible

**What changes between SDK versions?**
- The `.idl` files may add new interfaces/methods, but existing ones stay the same
- Our C++ code uses core interfaces (`IDeckLinkIterator`, `IDeckLinkOutput`, `IDeckLinkInput`, `IDeckLinkDisplayMode`, `IDeckLinkConfiguration`, `IDeckLinkProfileAttributes`) which have been stable since SDK v10
- If a new SDK changes these interfaces (rare), you'll get compile errors — fix by updating `decklink_jni.cpp` to match

---

## JNI Header Regeneration

If you change native method signatures in `DeckLinkIO.kt`, update the Java mirror and regenerate:
```
javac -h . DeckLinkManager.java
```

---

## What the Native Library Provides

### Output (existing)
- `nativeListDevices()` — enumerate all DeckLink devices
- `nativeOpen(deviceIndex, width, height)` — open device for video output
- `nativeGetOutputInfo(deviceIndex)` — get output resolution/fps
- `nativeSendFrame(deviceIndex, pixels, width, height)` — send a single frame
- `nativeStartScheduledPlayback(deviceIndex, fps)` — start timed playback
- `nativeScheduleFrame(deviceIndex, pixels, width, height)` — schedule a frame
- `nativeStopPlayback(deviceIndex)` — stop scheduled playback
- `nativeClose(deviceIndex)` — close output

### Input capture (added March 2026)
- `nativeListInputModes(deviceIndex)` — list available input display modes (returns `"name|WxH@fps_scan"` strings)
- `nativeListVideoConnections(deviceIndex)` — list available video input connections (SDI, HDMI, etc.) via `IDeckLinkProfileAttributes`
- `nativeOpenInput(deviceIndex, modeStr, connectionType)` — open device for video capture:
  - Sets video input connection via `IDeckLinkConfiguration` if `connectionType > 0`
  - Enables format auto-detection (`bmdVideoInputEnableFormatDetection`)
  - Uses `IDeckLinkInputCallback` to receive frames asynchronously
  - Converts UYVY (8-bit YUV 4:2:2) and BGRA input to ARGB pixel data
  - `VideoInputFormatChanged` callback handles auto-mode signal changes
- `nativeGetInputFrame(deviceIndex)` — poll for latest frame (returns `IntArray` with `[width, height, ...pixels]` or null)
- `nativeCloseInput(deviceIndex)` — stop streams, clean up callback and state

### Input callback pixel format handling
The `DeckLinkInputCallback::VideoInputFrameArrived` method handles:
- **bmdFormat8BitBGRA** — direct BGRA→ARGB byte swap
- **bmdFormat8BitYUV** — UYVY packed YUV 4:2:2 → ARGB (BT.601 conversion)
- **bmdFormat10BitYUV** — v210 format (not yet supported, outputs black)
- Unknown formats — attempts raw BGRA copy
