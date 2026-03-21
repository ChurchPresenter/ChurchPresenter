@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat" > nul 2>&1

cd /d C:\Users\SBC\Documents\GitHub\ChurchPresenter\native\decklink

set SDK_DIR=C:\Users\SBC\Downloads\Blackmagic_DeckLink_SDK_15.3\Blackmagic DeckLink SDK 15.3\Win\include

echo === Step 1: MIDL ===
midl /h DeckLinkAPI_h.h /iid DeckLinkAPI_i.c /env x64 /W1 /char signed "%SDK_DIR%\DeckLinkAPI.idl"
if errorlevel 1 (
    echo MIDL FAILED
    exit /b 1
)
echo === MIDL OK ===

echo === Step 2: Compile ===
cl /LD /EHsc /std:c++17 /O2 /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" /I"%SDK_DIR%" /I"." decklink_jni.cpp DeckLinkAPI_i.c /Fe:decklink_jni.dll /link ole32.lib oleaut32.lib
if errorlevel 1 (
    echo COMPILE FAILED
    exit /b 1
)
echo === BUILD SUCCESS ===
dir decklink_jni.dll
