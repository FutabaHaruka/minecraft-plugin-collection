#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
ORIGINAL="${1:-$ROOT/lib/AyCore-1.3.2-BETA.jar}"
OUT="${2:-$ROOT/dist/AyCore-1.3.2-BETA-CacheOptimized.jar}"
[ -f "$ORIGINAL" ] || { echo "Missing original jar: $ORIGINAL" >&2; exit 1; }
rm -rf "$ROOT/build"
mkdir -p "$ROOT/build/stubs" "$ROOT/build/classes" "$(dirname "$OUT")"
javac --release 8 -d "$ROOT/build/stubs" $(find "$ROOT/compile-stubs" -name '*.java')
javac --release 8 -cp "$ROOT/build/stubs:$ORIGINAL" -d "$ROOT/build/classes" $(find "$ROOT/src" -name '*.java')
python3 "$ROOT/tools/overlay.py" "$ORIGINAL" "$ROOT/build/classes" "$OUT"
zip -T "$OUT"
echo "Built: $OUT"
