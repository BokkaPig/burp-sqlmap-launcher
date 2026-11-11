#!/usr/bin/env bash
# Builds the extension on any architecture, finding a JDK for you.
#
#   ./build.sh            compile, test, and produce the jar
#   ./build.sh test       tests only
#   ./build.sh clean build
#
# Equivalent to ./gradlew with JAVA_HOME sorted out first. If you already have a
# JDK on JAVA_HOME or javac on PATH, ./gradlew works just as well.
set -euo pipefail

cd "$(dirname "$0")"
# shellcheck source=tools/find-jdk.sh
. tools/find-jdk.sh

echo "Building with JDK at $JAVA_HOME"
./gradlew "${@:-build}"
