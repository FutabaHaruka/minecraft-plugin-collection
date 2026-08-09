#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
BUILD="$ROOT/build-offline"
rm -rf "$BUILD"
mkdir -p "$BUILD/stubs" "$BUILD/classes"
find "$ROOT/src/offlineStubs/java" -name '*.java' -print0 | xargs -0 javac -source 8 -target 8 -encoding UTF-8 -d "$BUILD/stubs"
find "$ROOT/src/compileOnly/java" -name '*.java' -print0 | xargs -0 javac -source 8 -target 8 -encoding UTF-8 -cp "$BUILD/stubs" -d "$BUILD/stubs"
find "$ROOT/src/main/java" -name '*.java' -print0 | xargs -0 javac -source 8 -target 8 -encoding UTF-8 -cp "$BUILD/stubs" -d "$BUILD/classes"
cp -R "$ROOT/src/main/resources/." "$BUILD/classes/"
printf 'Manifest-Version: 1.0\nImplementation-Title: CrownControl\nImplementation-Version: 1.0.0-rc8-p1\n\n' > "$BUILD/MANIFEST.MF"
jar cfm "$ROOT/CrownControl-1.0.0-rc8-p1.jar" "$BUILD/MANIFEST.MF" -C "$BUILD/classes" .
echo "Built: $ROOT/CrownControl-1.0.0-rc8-p1.jar"
