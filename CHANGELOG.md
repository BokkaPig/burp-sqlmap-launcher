# Changelog

All notable changes to this project are recorded here.
Format loosely follows [Keep a Changelog](https://keepachangelog.com/).

## [1.0.6] — 2026-11-11

### Added

- MIT licence.

### Fixed

- `tools/test-find-jdk.sh` assumed a machine had exactly one JDK, asserting that
  a rejected candidate fell through to one specific path. CI runners carry two
  (a tool-cache Temurin and a distro one), so the amd64 job failed even though
  the lookup behaved correctly. The assertions now require only that the
  fixture was rejected in favour of something that can genuinely build.

## [1.0.5] — 2026-11-11

### Added

- **`build.sh` now recovers from a stale Gradle daemon by itself.** A daemon
  caches what it decided about the JVM it runs on. If it ever probed that JVM
  while the JDK was part-installed, it keeps serving "no compiler", so the build
  fails with *"does not provide the required capabilities: [JAVA_COMPILER]"*
  even after the JDK is repaired — and keeps failing however many times the
  packages are reinstalled. The giveaway is that it fails in milliseconds.

  `build.sh` now spots that error, stops the daemon, and retries once. If it
  still fails, it points at `./gradlew -q javaToolchains`, which prints Gradle's
  own `Is JDK:` verdict for every installation it can find — the diagnostic that
  finally identified this, after four wrong theories about the JDK itself.

- Troubleshooting entries for it in the README and AGENTS.md.

## [1.0.4] — 2026-11-11

### Fixed

- **The JDK lookup checked only `javac`, so it could hand Gradle an
  installation Gradle then refused.** Gradle decides whether an installation
  can compile by checking that `javac`, `javadoc` *and* `jar` are all present —
  it never runs them. An installation with a working `javac` but no `javadoc`
  or `jar` is rejected with *"does not provide the required capabilities:
  [JAVA_COMPILER]"*, which reads as though the compiler is missing when it is
  not. The lookup now requires the same three tools Gradle does.

- When no complete JDK is found but an incomplete one exists, the failure now
  names that installation and which tools it is missing, with the `apt` command
  to repair it. Previously it reported "no JDK found" on machines that visibly
  had a working `javac`, which sent people in circles.

## [1.0.3] — 2026-11-11

### Changed

- **The JDK lookup no longer just takes the newest JDK it finds.** Gradle's
  support lags new JDK releases, and Debian ships Java 25 as an early-access
  build (`25.0.5-ea`) that build tools can mis-probe. Selection now prefers a
  stable LTS — Java 21, then 17, then any other stable release — and falls back
  to an early-access build only when nothing else is installed. An explicitly
  set, valid `JAVA_HOME` still wins outright.

## [1.0.2] — 2026-11-11

### Fixed

- **`build.sh` could select a JRE and hand it to Gradle**, producing
  *"Toolchain installation ... does not provide the required capabilities:
  [JAVA_COMPILER]"* — the very error the script exists to prevent.

  The check asked whether `javac` existed in a candidate directory and ran.
  Debian's `update-alternatives` can leave a `javac` symlink *inside a JRE*
  that points at a different JDK's compiler: it runs perfectly, so the check
  passed, but Gradle still rejected the installation because it has no compiler
  of its own. Gradle keys off the `javac` binary belonging to the installation,
  not off the compiler module — which the JRE package does ship, so testing
  `ToolProvider.getSystemJavaCompiler()` would not have caught this either.

  A candidate's `javac` must now resolve to exactly `<home>/bin/javac`. That
  also stops `/usr` being mistaken for a JDK home, since its `javac` really
  lives under `/usr/lib/jvm`.

### Added

- `tools/test-find-jdk.sh` — six tests for the JDK lookup: a real JDK is
  honoured, a nonexistent path falls through, a JRE borrowing another JDK's
  `javac` is rejected, a failing `javac` stub is rejected, a JDK older than 17
  is rejected, and a machine with no JDK explains itself and exits non-zero.
  Run in CI on every architecture.

## [1.0.1] — 2026-11-11

Portability fix. No change to what the extension does.

### Fixed

- **The documented build command failed on x86_64.** The README hardcoded
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-arm64`, the path from the machine it
  was written on. On amd64 that directory does not exist, so `gradlew` refused to
  start with *"JAVA_HOME is set to an invalid directory"*. No architecture-
  specific path is hardcoded anywhere now.
- Running `./gradlew` on a JRE produced Gradle's own *"does not provide the
  required capabilities: [JAVA_COMPILER]"*, which does not say how to fix it. The
  build now intercepts this and prints the install command for each distribution.

### Added

- `build.sh` — builds on any architecture, locating a JDK first. Takes the same
  arguments as `./gradlew`.
- `tools/find-jdk.sh` — the single place allowed to know where JDKs live. Checks
  `JAVA_HOME`, then `javac` on `PATH`, then `/usr/libexec/java_home` on macOS,
  then the usual install roots. Validates candidates by actually running `javac`,
  so the macOS `/usr/bin/javac` stub and JDKs older than 17 are rejected rather
  than accepted and failing later.
- **Requirements** section in the README: supported platforms, and JDK install
  commands for Debian/Kali/Ubuntu, Fedora/RHEL, Arch and macOS.
- CI now builds on **linux-amd64, linux-arm64 and macos-arm64**, and checks the
  JDK lookup works with `JAVA_HOME` unset.

### Changed

- The jar is now built reproducibly (no file timestamps, fixed entry order), so
  it is byte-identical wherever it is built. CI prints its checksum on every
  architecture, which turns "architecture does not matter" from a claim into
  something the build demonstrates.

## [1.0.0] — 2026-10-23

First working version. Built and tested in one session; the history below is
grouped by what was added rather than by release, since nothing shipped between
the steps.

### Added — launching sqlmap

- Burp suite tab and **Send to sqlmap** context menu item, available anywhere
  Burp offers a context menu (Proxy history, Repeater, Target, Logger).
- Raw request handed to sqlmap with `-r`, byte for byte, so headers, cookies,
  bodies and content types are never reconstructed or re-encoded.
- GUI flag builder: detection and tuning, enumeration, proxy and free-text flags,
  with a live preview of the exact command.
- *Mark injection point (\*)* button for sqlmap's `*` syntax.
- `--force-ssl` ticked automatically for HTTPS requests.
- Terminal launch via a generated `run.sh`, supporting qterminal,
  x-terminal-emulator, gnome-terminal, xfce4-terminal, konsole, alacritty, kitty,
  xterm and tmux (detached session, survives Burp closing).
- Per-run working directory holding the request file, the runner script and
  sqlmap's output.
- Persisted settings for sqlmap path, terminal, working directory and whether to
  hold the terminal open.

### Added — finding what to test

- Passive candidate detector scoring all traffic Burp sees. Sends nothing of its
  own.
- DBMS error signatures for MySQL, PostgreSQL, Microsoft SQL Server, Oracle,
  SQLite, IBM DB2, Sybase and generic SQL, with false-positive guards.
- Heuristics for SQLi-prone parameter names, numeric values, numeric path
  segments and authentication paths.
- Candidates table with severity ranking, hit counts, evidence and a detail pane.
  Filters for scope, proxy-only traffic, free text, and hiding tested endpoints.
- Noise filtering: static assets, parameterless pages, off-scope traffic, and
  requests whose User-Agent contains `sqlmap`.

### Added — not testing the same thing twice

- Endpoint identity of `method + host + normalised path + parameter names`,
  collapsing numeric, UUID and long-hex path segments so one endpoint is not
  listed once per database row.
- Tested registry recording each run with its flags, persisted in the Burp
  **project file** so it follows the engagement.
- Ticks against tested endpoints in both the candidates table and the run queue.
- Confirmation prompt before repeating a run with identical flags; different
  flags proceed without interruption.
- *Mark as tested* and *Clear tested mark* for runs done outside the extension.

### Added — tamper scripts

- Dropdown listing every tamper script in the local sqlmap install, each with the
  description read from its own `tamper()` docstring.
- Appends to the comma-separated `--tamper` field rather than replacing it, so
  scripts can be chained; duplicates ignored, field stays free-text.
- Falls back through well-known install paths to a built-in name list.

### Changed

- Level and risk now default to **3** (previously 1), in both the UI and the
  underlying options model.

### Fixed

- **Copy command** produced a command pointing at a request file that did not
  exist. It now writes the request file first and copies a command that runs
  as-is.
- Candidate samples were captured eagerly, writing a temp file for every scored
  request even when the sample was immediately discarded. Capture is now lazy:
  25 identical requests cause exactly one capture.
- The candidate store notified the Swing thread once per proxied request. The
  table now polls a dirty flag instead, keeping heavy browsing off the EDT.
- `SqlmapOptions` defaults and the `OptionsPanel` spinner defaults had drifted
  apart (model said 1, UI said 3). Both are now 3.

### Testing

- 49 headless JUnit tests covering command building and shell quoting, endpoint
  identity, DBMS signatures, scoring decisions, dedupe and lazy capture, the
  tested-run persistence round trip, tamper discovery, and the generated runner
  script (executed for real against a stand-in sqlmap).
- UI harness (`tools/ui-harness`) for standing the Swing panels up outside Burp,
  with headless screenshot support.
