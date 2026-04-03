@echo off
setlocal

REM === Auto-detect Visual Studio ===
set "VCVARS="
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if exist "%VSWHERE%" (
    for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do (
        if exist "%%i\VC\Auxiliary\Build\vcvars64.bat" set "VCVARS=%%i\VC\Auxiliary\Build\vcvars64.bat"
    )
)
if not defined VCVARS (
    for %%V in (
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
        "%ProgramFiles%\Microsoft Visual Studio\2022\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2022\Community\VC\Auxiliary\Build\vcvars64.bat"
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2022\Professional\VC\Auxiliary\Build\vcvars64.bat"
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2022\Enterprise\VC\Auxiliary\Build\vcvars64.bat"
        "%ProgramFiles(x86)%\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat"
    ) do (
        if exist %%V if not defined VCVARS set "VCVARS=%%~V"
    )
)
if not defined VCVARS (
    echo ERROR: Could not find vcvars64.bat. Install Visual Studio 2022 or 2019 Build Tools.
    exit /b 1
)
echo Using: %VCVARS%
call "%VCVARS%" > nul 2>&1

REM === Resolve script directory ===
cd /d "%~dp0"

REM === DeckLink SDK path ===
if not "%~1"=="" (
    set "SDK_DIR=%~1"
) else (
    set /p "SDK_DIR=Enter DeckLink SDK include path (e.g. C:\path\to\Blackmagic DeckLink SDK 15.3\Win\include): "
)
if not exist "%SDK_DIR%\DeckLinkAPI.idl" (
    echo ERROR: DeckLinkAPI.idl not found in "%SDK_DIR%"
    exit /b 1
)

cl /EHsc /std:c++17 /O2 /I"%SDK_DIR%" /I"." decklink_info.cpp DeckLinkAPI_i.c /Fe:decklink_info.exe /link ole32.lib oleaut32.lib
if errorlevel 1 (
    echo COMPILE FAILED
    exit /b 1
)
echo === BUILD SUCCESS ===
echo Run: decklink_info.exe
