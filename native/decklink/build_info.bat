@echo off
call "C:\Program Files (x86)\Microsoft Visual Studio\2019\BuildTools\VC\Auxiliary\Build\vcvars64.bat" > nul 2>&1

cd /d C:\Users\SBC\Documents\GitHub\ChurchPresenter\native\decklink

set SDK_DIR=C:\Users\SBC\Downloads\Blackmagic_DeckLink_SDK_15.3\Blackmagic DeckLink SDK 15.3\Win\include

cl /EHsc /std:c++17 /O2 /I"%SDK_DIR%" /I"." decklink_info.cpp DeckLinkAPI_i.c /Fe:decklink_info.exe /link ole32.lib oleaut32.lib
