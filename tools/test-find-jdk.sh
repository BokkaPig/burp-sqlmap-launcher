#!/usr/bin/env bash
# Tests for tools/find-jdk.sh, which has been the source of two build failures
# and is awkward to verify by hand because it depends on what is installed.
#
#   tools/test-find-jdk.sh
#
# Fixtures are built in a temp directory; nothing on this machine is touched.
set -uo pipefail
cd "$(dirname "$0")/.."

PASS=0; FAIL=0
ok()   { PASS=$((PASS+1)); printf '  ok   %s\n' "$1"; }
bad()  { FAIL=$((FAIL+1)); printf '  FAIL %s\n       %s\n' "$1" "${2:-}"; }

FIX=$(mktemp -d); trap 'rm -rf "$FIX"' EXIT

# A real JDK to point the fixtures at. Without one there is nothing to test.
. tools/find-jdk.sh >/dev/null 2>&1 || { echo "no JDK on this machine; cannot run these tests" >&2; exit 2; }
REAL_JDK="$JAVA_HOME"
echo "Using real JDK: $REAL_JDK"

# selected <JAVA_HOME value> -> prints what find-jdk.sh settles on, or "none"
selected() {
    env JAVA_HOME="$1" bash -c '. tools/find-jdk.sh >/dev/null 2>&1 && printf "%s" "$JAVA_HOME"' 2>/dev/null \
        || printf 'none'
}

# 1. A genuine JDK is kept as-is.
[ "$(selected "$REAL_JDK")" = "$REAL_JDK" ] \
    && ok "a real JDK on JAVA_HOME is honoured" \
    || bad "a real JDK on JAVA_HOME is honoured" "got $(selected "$REAL_JDK")"

# 2. A path that does not exist falls through. This broke the documented build
#    command on every machine whose JDK directory was named differently.
[ "$(selected /usr/lib/jvm/java-21-openjdk-does-not-exist)" = "$REAL_JDK" ] \
    && ok "a nonexistent JAVA_HOME falls through to a real JDK" \
    || bad "a nonexistent JAVA_HOME falls through to a real JDK"

# 3. A JRE whose javac is an alternatives symlink into another JDK. It runs, so
#    a naive check passes, but Gradle rejects the installation for having no
#    compiler of its own.
mkdir -p "$FIX/jre-with-borrowed-javac/bin"
ln -s "$REAL_JDK/bin/java"  "$FIX/jre-with-borrowed-javac/bin/java"
ln -s "$REAL_JDK/bin/javac" "$FIX/jre-with-borrowed-javac/bin/javac"
[ "$(selected "$FIX/jre-with-borrowed-javac")" = "$REAL_JDK" ] \
    && ok "a JRE borrowing another JDK's javac is rejected" \
    || bad "a JRE borrowing another JDK's javac is rejected" "got $(selected "$FIX/jre-with-borrowed-javac")"

# 4. A javac that exists but fails, as macOS ships when no JDK is installed.
mkdir -p "$FIX/stub/bin"
printf '#!/bin/sh\necho "Unable to find any JVMs matching version." >&2\nexit 1\n' > "$FIX/stub/bin/javac"
chmod +x "$FIX/stub/bin/javac"
[ "$(selected "$FIX/stub")" = "$REAL_JDK" ] \
    && ok "a javac stub that fails to run is rejected" \
    || bad "a javac stub that fails to run is rejected"

# 5. A JDK older than the project's release target.
mkdir -p "$FIX/old/bin"
printf '#!/bin/sh\necho "javac 11.0.21"\n' > "$FIX/old/bin/javac"
chmod +x "$FIX/old/bin/javac"
[ "$(selected "$FIX/old")" = "$REAL_JDK" ] \
    && ok "a JDK older than 17 is rejected" \
    || bad "a JDK older than 17 is rejected"

# 6. Nothing installed at all: must explain itself and exit non-zero rather
#    than silently handing Gradle something unusable.
NOJDK_BIN="$FIX/nojavac-bin"; mkdir -p "$NOJDK_BIN"
for b in bash sh sed dirname readlink cat head printf; do
    src=$(command -v "$b") && ln -sf "$src" "$NOJDK_BIN/$b"
done
sed -e "s#/usr/lib/jvm/\*/#$FIX/empty/*/#" \
    -e "s#/usr/java/\*/#$FIX/empty/*/#" \
    -e "s#/opt/java/\*/#$FIX/empty/*/#" \
    -e "s#/Library/Java/JavaVirtualMachines/\*/Contents/Home/#$FIX/empty/*/#" \
    -e 's#"$HOME"/.sdkman/candidates/java/\*/#'"$FIX"'/empty/*/#' \
    -e 's#"$HOME"/.jdks/\*/#'"$FIX"'/empty/*/#' \
    -e 's#\[ -x /usr/libexec/java_home \]#false#' \
    tools/find-jdk.sh > "$FIX/find-jdk-nojdk.sh"
out=$(env -u JAVA_HOME PATH="$NOJDK_BIN" bash -c ". $FIX/find-jdk-nojdk.sh" 2>&1); rc=$?
{ [ "$rc" -ne 0 ] && printf '%s' "$out" | grep -q 'No usable JDK found'; } \
    && ok "no JDK at all: explains itself and fails" \
    || bad "no JDK at all: explains itself and fails" "rc=$rc"

# 7. Given a choice, a stable LTS beats a newer early-access build. Debian
#    ships Java 25 as "25.0.5-ea", and build tools can mis-probe EA builds.
mkdir -p "$FIX/roots/java-25-ea/bin" "$FIX/roots/java-21/bin"
printf '#!/bin/sh\necho "javac 25.0.5-ea"\n' > "$FIX/roots/java-25-ea/bin/javac"
printf '#!/bin/sh\necho "javac 21.0.8"\n'    > "$FIX/roots/java-21/bin/javac"
# Gradle wants javadoc and jar too, so a fixture must carry them to count.
for _t in javadoc jar; do
    printf '#!/bin/sh\nexit 0\n' > "$FIX/roots/java-25-ea/bin/$_t"
    printf '#!/bin/sh\nexit 0\n' > "$FIX/roots/java-21/bin/$_t"
done
chmod +x "$FIX/roots/java-25-ea/bin/"* "$FIX/roots/java-21/bin/"*

sed -e "s#/usr/lib/jvm/\*/#$FIX/roots/*/#" \
    -e "s#/usr/java/\*/#$FIX/empty/*/#" \
    -e "s#/opt/java/\*/#$FIX/empty/*/#" \
    -e "s#/Library/Java/JavaVirtualMachines/\*/Contents/Home/#$FIX/empty/*/#" \
    -e 's#"$HOME"/.sdkman/candidates/java/\*/#'"$FIX"'/empty/*/#' \
    -e 's#"$HOME"/.jdks/\*/#'"$FIX"'/empty/*/#' \
    -e 's#\[ -x /usr/libexec/java_home \]#false#' \
    tools/find-jdk.sh > "$FIX/find-jdk-roots.sh"
picked=$(env -u JAVA_HOME PATH="$NOJDK_BIN" bash -c ". $FIX/find-jdk-roots.sh >/dev/null 2>&1 && printf '%s' \"\$JAVA_HOME\"")
[ "$picked" = "$FIX/roots/java-21" ] \
    && ok "a stable LTS is preferred over a newer early-access build" \
    || bad "a stable LTS is preferred over a newer early-access build" "picked ${picked:-none}"

# 8. If an early-access build is all there is, use it rather than refusing.
rm -rf "$FIX/roots/java-21"
picked=$(env -u JAVA_HOME PATH="$NOJDK_BIN" bash -c ". $FIX/find-jdk-roots.sh >/dev/null 2>&1 && printf '%s' \"\$JAVA_HOME\"")
[ "$picked" = "$FIX/roots/java-25-ea" ] \
    && ok "an early-access build is still used when nothing else exists" \
    || bad "an early-access build is still used when nothing else exists" "picked ${picked:-none}"

# 9. An installation with javac but no javadoc/jar. Gradle refuses these, so
#    this must too rather than handing Gradle something it will reject.
mkdir -p "$FIX/javac-only/bin"
ln -s "$REAL_JDK/bin/javac" "$FIX/javac-only/bin/javac"
[ "$(selected "$FIX/javac-only")" = "$REAL_JDK" ] \
    && ok "an install with javac but no javadoc/jar is rejected" \
    || bad "an install with javac but no javadoc/jar is rejected" "got $(selected "$FIX/javac-only")"

# 10. When such an install is the only one, say what is missing. Reporting
#     "no JDK found" on a machine that clearly has javac sends people in circles.
mkdir -p "$FIX/onlypartial/java-25/bin"
printf '#!/bin/sh\necho "javac 25.0.5"\n' > "$FIX/onlypartial/java-25/bin/javac"
chmod +x "$FIX/onlypartial/java-25/bin/javac"
sed -e "s#/usr/lib/jvm/\*/#$FIX/onlypartial/*/#" \
    -e "s#/usr/java/\*/#$FIX/empty/*/#" \
    -e "s#/opt/java/\*/#$FIX/empty/*/#" \
    -e "s#/Library/Java/JavaVirtualMachines/\*/Contents/Home/#$FIX/empty/*/#" \
    -e 's#"$HOME"/.sdkman/candidates/java/\*/#'"$FIX"'/empty/*/#' \
    -e 's#"$HOME"/.jdks/\*/#'"$FIX"'/empty/*/#' \
    -e 's#\[ -x /usr/libexec/java_home \]#false#' \
    tools/find-jdk.sh > "$FIX/find-jdk-partial.sh"
out=$(env -u JAVA_HOME PATH="$NOJDK_BIN" bash -c ". $FIX/find-jdk-partial.sh" 2>&1); rc=$?
{ [ "$rc" -ne 0 ] \
  && printf '%s' "$out" | grep -q 'incomplete' \
  && printf '%s' "$out" | grep -q 'javadoc' \
  && printf '%s' "$out" | grep -q 'jar'; } \
    && ok "an incomplete install is named, with the missing tools" \
    || bad "an incomplete install is named, with the missing tools" "rc=$rc out=$(printf '%s' "$out" | head -2 | tr '\n' ' ')"

echo
if [ "$FAIL" -eq 0 ]; then echo "ALL PASS ($PASS)"; else echo "$FAIL FAILURE(S), $PASS passed"; fi
exit $(( FAIL > 0 ))
