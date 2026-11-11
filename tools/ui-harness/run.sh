#!/usr/bin/env bash
# Stands the extension's UI up outside Burp for a visual check.
#
#   tools/ui-harness/run.sh [candidates|run|tamper] [--screenshot out.png]
#
# With --screenshot it runs headless under Xvfb and writes a PNG; without it the
# window opens on your display. Needs Xvfb and ImageMagick's `import` for the
# screenshot path.
set -euo pipefail

cd "$(dirname "$0")/../.."
MODE="${1:-candidates}"
SHOT=""
if [[ "${2:-}" == "--screenshot" ]]; then SHOT="${3:?--screenshot needs an output path}"; fi

# shellcheck source=../find-jdk.sh
. tools/find-jdk.sh

echo "Building (main + test classes)..."
./gradlew classes testClasses --quiet

MONTOYA=$(find ~/.gradle/caches -name 'montoya-api-*.jar' | sort | tail -1)
if [[ -z "$MONTOYA" ]]; then echo "Could not find the montoya-api jar in the Gradle cache." >&2; exit 1; fi

OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT
CP="build/classes/java/main:build/classes/java/test:$MONTOYA"

javac -cp "$CP" -d "$OUT" tools/ui-harness/UiHarness.java

if [[ -z "$SHOT" ]]; then
    exec java -cp "$CP:$OUT" UiHarness "$MODE"
fi

echo "Rendering '$MODE' headless -> $SHOT"
Xvfb :99 -screen 0 1500x950x24 >/dev/null 2>&1 &
XVFB=$!
trap 'kill $XVFB 2>/dev/null || true; rm -rf "$OUT"' EXIT
sleep 3
DISPLAY=:99 java -cp "$CP:$OUT" UiHarness "$MODE" >/dev/null 2>&1 &
APP=$!
sleep 8
DISPLAY=:99 import -window root "$SHOT"
kill $APP 2>/dev/null || true
echo "Wrote $SHOT"
