# AGENTS.md

Orientation for an AI agent (or a human) picking this project up cold. Read this
before changing anything; `ARCHITECTURE.md` explains *why* the design is the way
it is, and the decisions recorded there are load-bearing.

## What this is

A Burp Suite extension (Java, Montoya API) that:

1. passively scores traffic to find endpoints worth testing for SQL injection,
2. hands a chosen request to **sqlmap in a real terminal** via `sqlmap -r`, and
3. records what has already been run, against which endpoint, with which flags,
   so the same test is not repeated.

It is a pentest tool, used against systems the operator is authorised to test.

## Build, test, run

```sh
./build.sh               # compiles + tests + produces build/burp-sqlmap-launcher.jar
./build.sh test          # tests only (49 of them, all headless)
./build.sh clean build
```

`build.sh` is a thin wrapper that sources `tools/find-jdk.sh` to locate a JDK,
then calls `./gradlew`. Use `./gradlew` directly if your environment is already
set up; the arguments are identical.

**Known environment trap:** Gradle compiles with the JVM it is running on, so
that JVM must be a JDK. A JRE fails with *"does not provide the required
capabilities: [JAVA_COMPILER]"*. The build now intercepts this and prints what to
do, but if you are debugging it directly, set `JAVA_HOME` to any directory
containing `bin/javac`.

**Never hardcode a JAVA_HOME path**, in docs, scripts or CI. The directory name
varies by distribution *and* architecture (`java-21-openjdk-amd64` on x86_64,
`java-21-openjdk-arm64` on ARM). An earlier README hardcoded the ARM path and the
documented build command failed outright for anyone on x86_64. Derive it:

```sh
export JAVA_HOME="$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")"
```

**Architecture is not a build variable.** The jar is ordinary Java bytecode and
runs anywhere; there is no amd64 or arm64 artefact. CI builds on both only to
prove the toolchain works on both, not because the output differs.

Install the jar in Burp: **Extensions → Installed → Add → type Java →**
`build/burp-sqlmap-launcher.jar`.

Visual check of the UI without a licensed Burp:

```sh
tools/ui-harness/run.sh candidates
tools/ui-harness/run.sh tamper --screenshot /tmp/out.png   # headless, needs Xvfb
```

## The one thing that will trip you up

**Montoya is an interface-only API, so nearly all of it can be stubbed with
dynamic proxies — that is how the tests run without Burp.** See
`src/test/java/uk/co/periculo/burpsqlmap/TestStubs.java`.

The exception is **static factory methods on Montoya interfaces**, such as
`HttpRequestResponse.httpRequestResponse(...)`. Those reach into Burp's internal
object factory and throw outside Burp. They cannot be unit tested.

Consequence for design: **keep logic out of code paths that touch those
factories.** `CandidateDetector.score()` is a pure static function returning a
`Verdict` precisely so the scoring can be tested; the handler method around it
does the untestable plumbing and holds no logic worth testing. Preserve that
split when adding detection features.

## Threading

Three threads matter, and mixing them up causes real bugs:

| Code | Runs on | Rules |
|---|---|---|
| `CandidateDetector.handleHttpResponseReceived` | Burp's **networking** threads | Must be fast, must never throw (it is wrapped in try/catch for this reason), must never touch Swing. Slowness here slows the proxy. |
| `CandidateStore` | written from networking threads, read from Swing | Concurrent map + an `AtomicBoolean` dirty flag. Do not add listener callbacks that fire on the networking thread. |
| Everything in `*Panel` / `*Tab` | **Swing EDT** | `addTasks()` already marshals with `invokeLater`; keep it that way. |

`CandidatesPanel` **polls** `store.consumeDirty()` on a timer rather than being
pushed to. This is deliberate: under heavy browsing a push-per-request queued one
EDT event per proxied request. Do not "simplify" it back to a listener.

Similarly, candidate samples are captured through a `Supplier`, not eagerly:
capturing calls `copyToTempFile()`, which writes a file. Most sightings are
repeats whose sample is discarded. Do not make it eager.

## Conventions

- Plain Java 17, no frameworks, no dependency injection. Montoya is
  `compileOnly` (Burp provides it at runtime) and `testImplementation`.
- Swing built by hand; `UiHelpers.Form` is the labelled-row helper. Use it rather
  than hand-rolling another `GridBagLayout`.
- Comments explain *why*, not *what*. Several comments in this codebase record
  decisions that look wrong without the reason — leave them in place.
- British spelling in identifiers and comments (`normalisePath`, `tokenise`).
- No logging framework: `api.logging().logToOutput` / `logToError`.

## Testing expectations

Tests live in `src/test/java` and are plain JUnit 5. They must stay **headless**
and must not need a Burp, a network or a target. The suite covers:

- command building and shell quoting (`SqlmapOptionsTest`)
- endpoint identity, the dedupe/tested key (`EndpointKeyTest`)
- DBMS error signatures, including false-positive guards (`SqlErrorsTest`)
- candidate scoring decisions (`CandidateDetectorTest`)
- dedupe and lazy sample capture (`CandidateStoreTest`)
- the tested-run record and its persistence round trip (`TestedRegistryTest`)
- tamper script discovery (`TamperScriptsTest`, skips if sqlmap is absent)
- the generated runner script, executed for real (`TerminalLauncherTest`)

`TerminalLauncherTest` uses `"echo"` as the terminal so the script is still
written and run but no window opens. Keep that trick if you add cases.

If you add a feature, add tests at the same level: pure logic gets a unit test;
UI gets a mode in `tools/ui-harness`.

## What is NOT verified

Be honest about this in any handoff:

- The extension has **never been loaded into a licensed Burp by an automated
  run.** Everything is compiler-checked against the real Montoya API and the
  logic is unit tested, but the Burp integration itself is unverified by CI.
- `HttpRequestResponse.httpRequestResponse(...).copyToTempFile()` in
  `CandidateDetector.analyse` is the one line that can only fail at runtime
  inside Burp. If candidates list but "Send to sqlmap" does nothing, look there
  first, then at **Extensions → Errors**.
- The UI harness renders with the default Swing look and feel, not Burp's theme.
  Layout is representative; colours are not.

## Safe places to extend

- **New detection signal:** add to `CandidateDetector.score()` and a case to
  `CandidateDetectorTest`. Keep it pure.
- **New DBMS error signature:** add to `SqlErrors`, and add a false-positive
  guard case — the benign-text tests exist because signatures over-match easily.
- **New sqlmap flag:** add a field to `SqlmapOptions`, emit it in `toArgs()`, add
  a widget in `OptionsPanel` and wire it into `snapshot()` **and** the `onChange`
  list, or the command preview will not update.
- **New terminal:** add one entry to `TerminalLauncher.TERMINALS`. Anything that
  takes `-e <argv...>` needs no special case.

## Traps

- `SqlmapOptions` defaults and the `OptionsPanel` spinner defaults must agree.
  They drifted once (model said 1, UI said 3) and a test caught it.
- `SqlmapTask` holds the raw request as an **ISO-8859-1** string so every byte
  round-trips through an editable text area. Do not "fix" this to UTF-8; it will
  corrupt binary bodies.
- The endpoint key deliberately **excludes cookies**. See `ARCHITECTURE.md`.
- `EndpointKey.normalisePath` collapses numeric/UUID/hex segments. Changing it
  invalidates every previously recorded test result, because it is the
  persistence key.
- Any path that names a JDK, an architecture or a distribution layout belongs in
  `tools/find-jdk.sh`, not in a script, a doc or a workflow file. That file is
  the single place allowed to know where JDKs live.
