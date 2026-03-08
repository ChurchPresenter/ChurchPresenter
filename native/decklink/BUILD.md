# Building the DeckLink JNI Library

Native JNI bridge between ChurchPresenter (Java/Kotlin) and the BlackMagic DeckLink SDK.

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
   cd C:\Users\Sanya\Documents\GitHub\ChurchPresenter\native\decklink
   ```

3. Create a build directory and run CMake:
   ```
   mkdir build
   cd build
   cmake .. -G "Visual Studio 17 2022" -A x64 -DDECKLINK_SDK_DIR="C:\Users\Sanya\Downloads\Blackmagic-DeckLink-SDK-15.3\Win\include"
   ```

4. Build:
   ```
   cmake --build . --config Release
   ```

5. The output DLL will be at:
   ```
   build\lib\Release\decklink_jni.dll
   ```

### Alternative (no CMake)

From **x64 Native Tools Command Prompt for VS 2022**:

```
cd C:\Users\Sanya\Documents\GitHub\ChurchPresenter\native\decklink

REM 1. Generate headers from IDL
midl /h DeckLinkAPI_h.h /iid DeckLinkAPI_i.c /env x64 /W1 /char signed "C:\Users\Sanya\Downloads\Blackmagic-DeckLink-SDK-15.3\Win\include\DeckLinkAPI.idl"

REM 2. Compile
cl /LD /EHsc /std:c++17 /O2 ^
   /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
   /I"C:\Users\Sanya\Downloads\Blackmagic-DeckLink-SDK-15.3\Win\include" ^
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

2. Run the build script:
   ```
   chmod +x build_macos.sh
   ./build_macos.sh
   ```

3. The output dylib will be at:
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

| Platform | Where to place it |
|----------|-------------------|
| **Dev (all)** | Add to JVM arg: `-Djava.library.path=/path/to/dir` |
| **Windows** | Next to the app `.exe`, or in `C:\Windows\System32` |
| **macOS** | `/usr/local/lib/` or inside the `.app` bundle's `lib/` |
| **Linux** | `/usr/local/lib/` then run `sudo ldconfig` |

The app handles the library being absent gracefully — DeckLink options simply won't appear in the settings.

---

## JNI Header Regeneration

If you change native method signatures in `DeckLinkOutput.kt`, update the Java mirror and regenerate:
```
javac -h . DeckLinkManager.java
```
