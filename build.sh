#!/bin/sh
# Builds CompileQuest.jar from src/ and res/ (macOS / Linux). Needs a JDK 17 or newer.
set -e
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes
find src -name "*.java" > build/sources.txt
javac --release 17 -encoding UTF-8 -d build/classes @build/sources.txt
cp -R res/. build/classes/
jar --create --file CompileQuest.jar --main-class com.compilequest.Main -C build/classes .
echo "Build OK: CompileQuest.jar"
