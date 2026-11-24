@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Build-All.ps1" %*
set "BUILD_EXIT=%ERRORLEVEL%"
pause
exit /b %BUILD_EXIT%
