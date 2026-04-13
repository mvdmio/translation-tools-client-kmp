@echo off
setlocal

set SCRIPT_DIR=%~dp0
call "%SCRIPT_DIR%..\..\gradlew.bat" -p "%SCRIPT_DIR%." %*
exit /b %ERRORLEVEL%
