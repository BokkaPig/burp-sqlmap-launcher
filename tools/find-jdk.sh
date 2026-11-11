#!/usr/bin/env bash
# Sets JAVA_HOME to a usable JDK, on any architecture and distribution.
#
# Source it, do not run it:  . tools/find-jdk.sh
#
# Gradle compiles with the JVM it runs on, so that JVM has to be a JDK. A JRE
# gives an error that does not say what is wrong, and distributions disagree on
# both where JDKs live and what the directory is called (java-21-openjdk-amd64
# on x86_64, -arm64 on ARM, no suffix elsewhere), so nothing here hardcodes a
# path. This file is the only place in the project allowed to know where JDKs
# are kept.

_resolve_link() {
    # readlink -f is GNU and recent-macOS only; walk the links by hand otherwise.
    if readlink -f "$1" >/dev/null 2>&1; then
        readlink -f "$1"
    else
        local target="$1"
        while [ -L "$target" ]; do target=$(readlink "$target"); done
        printf '%s' "$target"
    fi
}

# A directory is only a JDK if it owns a working javac.
#
# Three ways a directory can look like a JDK and not be one:
#  * macOS ships a /usr/bin/javac stub that exists but exits non-zero when no
#    JDK is installed, which would make JAVA_HOME=/usr look valid.
#  * Debian's update-alternatives can leave a javac symlink inside a JRE that
#    points at a different JDK's compiler. It runs perfectly, so a naive check
#    passes, but Gradle still rejects the JRE with "does not provide the
#    required capabilities: [JAVA_COMPILER]" because the installation itself
#    has no compiler. Gradle keys off the javac binary belonging to the
#    installation, not off the compiler module, which the JRE package ships.
#  * /usr looks like a JDK home because /usr/bin/javac exists, but its javac
#    really lives under /usr/lib/jvm.
#
# Requiring javac to resolve to exactly <home>/bin/javac rules out all three.
_is_jdk() {
    [ -n "$1" ] || return 1
    [ -x "$1/bin/javac" ] || return 1

    local real_javac real_home
    real_javac=$(_resolve_link "$1/bin/javac")
    real_home=$(_resolve_link "$1")
    [ "$real_javac" = "$real_home/bin/javac" ] || return 1

    "$1/bin/javac" -version >/dev/null 2>&1
}

# 17 is the project's release target; anything older cannot build it.
_jdk_is_new_enough() {
    local major
    major=$("$1/bin/javac" -version 2>&1 | sed -n 's/^javac \([0-9]*\).*/\1/p')
    [ -n "$major" ] && [ "$major" -ge 17 ] 2>/dev/null
}

_try_jdk() {
    if _is_jdk "$1" && _jdk_is_new_enough "$1"; then
        JAVA_HOME="$1"
        export JAVA_HOME
        return 0
    fi
    return 1
}

# How good a build JDK this is; lower wins. Newest is NOT best: Gradle's
# support lags new JDK releases, and Debian ships Java 25 as an early-access
# build ("25.0.5-ea") that build tools can mis-probe. A stable LTS is the safer
# default for compiling, whatever else is installed.
#
#   0  Java 21 - LTS, well supported by the Gradle version pinned here
#   1  Java 17 - the project's release target
#   2  any other stable release
#   3  early-access builds, used only when nothing else exists
_jdk_rank() {
    local version_output major
    version_output=$("$1/bin/javac" -version 2>&1)
    major=$(printf '%s' "$version_output" | sed -n 's/^javac \([0-9]*\).*/\1/p')

    case "$version_output" in
        *-ea*|*-EA*) echo 3; return ;;
    esac
    case "$major" in
        21) echo 0 ;;
        17) echo 1 ;;
        *)  echo 2 ;;
    esac
}

# Keeps the best candidate seen so far in _best/_best_rank.
_consider() {
    [ -n "${1:-}" ] || return 0
    _is_jdk "$1" || return 0
    _jdk_is_new_enough "$1" || return 0

    local rank; rank=$(_jdk_rank "$1")
    if [ -z "$_best" ] || [ "$rank" -lt "$_best_rank" ]; then
        _best="$1"; _best_rank="$rank"
    fi
}

_find_jdk() {
    # An explicitly set, valid JAVA_HOME wins outright; the caller meant it.
    _try_jdk "${JAVA_HOME:-}" && return 0

    _best=""; _best_rank=99

    # Whatever owns javac on PATH is usually the intended JDK.
    if command -v javac >/dev/null 2>&1; then
        _consider "$(dirname "$(dirname "$(_resolve_link "$(command -v javac)")")")"
    fi

    # macOS keeps an authoritative registry of installed JDKs.
    if [ -x /usr/libexec/java_home ]; then
        _consider "$(/usr/libexec/java_home -v 17+ 2>/dev/null)"
    fi

    # Common install roots.
    local candidate
    for candidate in \
        /usr/lib/jvm/*/ \
        /usr/java/*/ \
        /opt/java/*/ \
        /Library/Java/JavaVirtualMachines/*/Contents/Home/ \
        "$HOME"/.sdkman/candidates/java/*/ \
        "$HOME"/.jdks/*/
    do
        _consider "${candidate%/}"
    done

    _try_jdk "$_best" && return 0
    return 1
}

if ! _find_jdk; then
    cat >&2 <<'MSG'
No usable JDK found. This project needs a JDK 17 or newer to build - a JRE is
not enough, because Gradle needs a compiler.

Install one:
  Debian / Kali / Ubuntu   sudo apt install default-jdk
  Fedora / RHEL            sudo dnf install java-21-openjdk-devel
  Arch                     sudo pacman -S jdk-openjdk
  macOS (Homebrew)         brew install openjdk@21

Then re-run, or set JAVA_HOME yourself to a directory containing bin/javac.
MSG
    unset -f _is_jdk _jdk_is_new_enough _jdk_rank _consider _try_jdk _resolve_link _find_jdk
unset _best _best_rank
    return 1 2>/dev/null || exit 1
fi

unset -f _is_jdk _jdk_is_new_enough _jdk_rank _consider _try_jdk _resolve_link _find_jdk
unset _best _best_rank
