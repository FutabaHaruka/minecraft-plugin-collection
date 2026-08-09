#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
PIXELMON_JAR="${1:?Usage: $0 /path/to/pixelmon-8.4.2-server.jar}"
BUILD="$ROOT/build"
rm -rf "$BUILD"
mkdir -p "$BUILD/stubs" "$BUILD/classes"
find "$ROOT/src/offlineStubs/java" "$ROOT/src/compileOnly/java" -name '*.java' -print0 \
  | xargs -0 javac --release 8 -encoding UTF-8 -d "$BUILD/stubs"
find "$ROOT/src/main/java" -name '*.java' -print0 \
  | xargs -0 javac --release 8 -encoding UTF-8 -cp "$BUILD/stubs:$PIXELMON_JAR" -d "$BUILD/classes"
cp -R "$ROOT/src/main/resources/." "$BUILD/classes/"
cat > "$BUILD/MANIFEST.MF" <<'MANIFEST'
Manifest-Version: 1.0
Implementation-Title: BreedConsumeControl
Implementation-Version: 1.8.5

MANIFEST
jar cfm "$ROOT/BreedConsumeControl-1.8.5.jar" "$BUILD/MANIFEST.MF" -C "$BUILD/classes" .
echo "Built: $ROOT/BreedConsumeControl-1.8.5.jar"
