@echo off
rem Builds CompileQuest.jar from src/ and res/. Needs a JDK 17 or newer (javac and jar on PATH).
setlocal EnableDelayedExpansion
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo javac was not found. Install a JDK 17 or newer and add it to PATH.
    exit /b 1
)

if exist build\classes rmdir /s /q build\classes
mkdir build\classes
if exist build\sources.txt del build\sources.txt
for /r src %%f in (*.java) do (
    set "p=%%f"
    echo "!p:\=/!">>build\sources.txt
)

echo Compiling...
javac --release 17 -encoding UTF-8 -d build\classes @build\sources.txt
if errorlevel 1 goto :fail

xcopy /e /i /q /y res build\classes >nul
jar --create --file CompileQuest.jar --main-class com.compilequest.Main -C build\classes .
if errorlevel 1 goto :fail

echo Build OK: CompileQuest.jar
exit /b 0

:fail
echo Build FAILED.
exit /b 1
