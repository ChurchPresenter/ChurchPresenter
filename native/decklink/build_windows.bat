@echo off
REM Build decklink_jni.dll on Windows
REM Prerequisites:
REM   - Visual Studio 2022 (or Build Tools) with C++ workload
REM   - CMake on PATH
REM   - BlackMagic DeckLink SDK downloaded
REM   - JDK 21 installed (JAVA_HOME set)
REM
REM Usage: build_windows.bat [path-to-decklink-sdk-Win-include]

set SDK_DIR=%~1
if "%SDK_DIR%"=="" (
    echo ERROR: DeckLink SDK path not provided.
    echo Usage: build_windows.bat "C:\path\to\Blackmagic-DeckLink-SDK\Win\include"
    exit /b 1
)

REM Set up VS environment (needed for MIDL compiler)
REM Try to find VS using vswhere first, then fall back to known paths
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
    for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VS_PATH=%%i"
)

if defined VS_PATH (
    call "%VS_PATH%\Common7\Tools\VsDevCmd.bat" -arch=amd64
) else if exist "%ProgramFiles%\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" (
    call "%ProgramFiles%\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" -arch=amd64
) else if exist "%ProgramFiles%\Microsoft Visual Studio\2022\Professional\Common7\Tools\VsDevCmd.bat" (
    call "%ProgramFiles%\Microsoft Visual Studio\2022\Professional\Common7\Tools\VsDevCmd.bat" -arch=amd64
) else if exist "%ProgramFiles%\Microsoft Visual Studio\2022\Enterprise\Common7\Tools\VsDevCmd.bat" (
    call "%ProgramFiles%\Microsoft Visual Studio\2022\Enterprise\Common7\Tools\VsDevCmd.bat" -arch=amd64
) else if exist "%ProgramFiles(x86)%\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" (
    call "%ProgramFiles(x86)%\Microsoft Visual Studio\2022\BuildTools\Common7\Tools\VsDevCmd.bat" -arch=amd64
) else (
    echo ERROR: Visual Studio 2022 not found. Install VS or Build Tools with C++ workload.
    exit /b 1
)

echo Building decklink_jni.dll...
echo DeckLink SDK: %SDK_DIR%

if not exist build mkdir build
cd build

cmake .. -G "Visual Studio 17 2022" -A x64 -DDECKLINK_SDK_DIR="%SDK_DIR%"
cmake --build . --config Release

echo.
if exist lib\Release\decklink_jni.dll (
    echo SUCCESS: build\lib\Release\decklink_jni.dll
    echo Copy this DLL to your app's library path or system PATH.
) else (
    echo BUILD FAILED - check errors above
)

cd ..
