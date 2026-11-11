#!/usr/bin/env bash
# Builds the extension on any architecture, finding a JDK for you.
#
#   ./build.sh            compile, test, and produce the jar
#   ./build.sh test       tests only
#   ./build.sh clean build
#
# Equivalent to ./gradlew with JAVA_HOME sorted out first. If you already have a
# JDK on JAVA_HOME or javac on PATH, ./gradlew works just as well.
set -uo pipefail

cd "$(dirname "$0")"
# shellcheck source=tools/find-jdk.sh
. tools/find-jdk.sh

echo "Building with JDK at $JAVA_HOME"

LOG=$(mktemp)
trap 'rm -f "$LOG"' EXIT

run_gradle() {
    ./gradlew "$@" 2>&1 | tee "$LOG"
    return "${PIPESTATUS[0]}"
}

run_gradle "${@:-build}"
status=$?

# A long-lived Gradle daemon caches what it decided about the JVM it runs on.
# If it ever probed this JVM while the JDK was part-installed, it keeps serving
# that verdict - so the build fails with "does not provide the required
# capabilities: [JAVA_COMPILER]" even after the JDK is fixed, and keeps failing
# however many times the packages are reinstalled. The giveaway is that it
# fails in milliseconds. Stopping the daemon forces a fresh probe.
if [ "$status" -ne 0 ] && grep -q 'JAVA_COMPILER' "$LOG"; then
    cat <<'MSG'

------------------------------------------------------------------
That failure usually means a stale Gradle daemon is serving a cached
verdict about this JVM, not that anything is wrong with the JDK.
Stopping the daemon and retrying once...
------------------------------------------------------------------
MSG
    ./gradlew --stop >/dev/null 2>&1 || true
    run_gradle "${@:-build}"
    status=$?

    if [ "$status" -ne 0 ] && grep -q 'JAVA_COMPILER' "$LOG"; then
        cat <<'MSG'

Still failing after a fresh daemon, so the JDK itself is the problem.
Ask Gradle what it sees:

  ./gradlew -q javaToolchains

Look for this installation and its "Is JDK:" line. If that says false,
the installation is missing javac, javadoc or jar. On Debian, Kali and
Ubuntu those live in the -jdk-headless package:

  sudo apt install openjdk-21-jdk

MSG
    fi
fi

exit "$status"
