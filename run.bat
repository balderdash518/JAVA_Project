@echo off
rem Starts the game. Builds CompileQuest.jar first if it does not exist yet.
cd /d "%~dp0"
if not exist CompileQuest.jar (
    call build.bat
    if errorlevel 1 (
        pause
        exit /b 1
    )
)
java -jar CompileQuest.jar %*
if errorlevel 1 pause
