# Architecture

Why this is built the way it is. `AGENTS.md` covers how to work on it;
this file covers the decisions, including the ones that look odd without the
reasoning.

## Flow

```
 Burp traffic                     Right-click any request
      │                                     │
      ▼                                     │
 CandidateDetector  (networking threads)    │
   score() -> Verdict  [pure]               │
      │                                     │
      ▼                                     │
 CandidateStore  (concurrent, deduped)      │
      │  polls dirty flag                   │
      ▼                                     ▼
 CandidatesPanel ─── "Send to sqlmap" ──► SqlmapTab  (Swing EDT)
      ▲                                     │
      │                                     ├─ writes request.txt  (bytes verbatim)
      │                                     ├─ builds argv  (SqlmapOptions)
      │                                     ├─ writes run.sh  (TerminalLauncher)
      │                                     └─ spawns a terminal
      │                                            │
      └──────── TestedRegistry ◄───────────────────┘
               (Burp project file)
```

## Decisions

### sqlmap is given the raw request with `-r`

The alternative is reconstructing a command line from the URL, headers, cookies
and body. That is where every tool of this kind goes wrong: multipart bodies,
JSON, odd content types and non-UTF-8 encodings all break. Burp already has the
exact bytes; writing them to a file and passing `-r` means nothing is
re-encoded, and sqlmap's own parser does the work.

Corollary: the request file must be **byte-exact**. `SqlmapTask` holds it as an
ISO-8859-1 string, because that charset maps bytes 0–255 one-to-one, so the
request can be shown and edited in a `JTextArea` and written back unchanged. A
UTF-8 round trip would corrupt binary bodies.

A raw request file carries no scheme, which is why `--force-ssl` is ticked
automatically for HTTPS requests.

### sqlmap runs in a terminal, not piped into Burp

sqlmap is interactive — it asks whether to keep testing other parameters, whether
to use a detected technique, and so on. Capturing its output into a Burp pane
would mean reimplementing that dialogue. Spawning a terminal keeps the real
sqlmap experience, including its progress display, and lets the run outlive Burp
(notably under `tmux`).

Everything is funnelled through a generated `run.sh` so quoting is solved once
rather than once per terminal emulator. `TerminalLauncher.TERMINALS` then only
needs to know how each emulator takes a command.

### Scoring is a pure function

`CandidateDetector.score(HttpRequest, String body) -> Verdict` has no side
effects and touches no Burp internals. This is not stylistic: the surrounding
handler calls `HttpRequestResponse.httpRequestResponse(...)`, a static factory
that reaches into Burp's internal object factory and throws outside Burp. Keeping
the logic out of that path is what makes detection testable at all.

Weights are deliberately crude — this ranks a worklist, it does not prove
anything. A leaked DBMS error dominates (60) because it is evidence rather than
inference. The rest is heuristic: known-dangerous parameter names, numeric
values, numeric path segments, auth paths.

### Endpoint identity: `method + host + normalised path + parameter names`

This single key does three jobs: deduplicating the candidate list, identifying
what has been tested, and deciding whether a new sighting is new attack surface.

- **Numeric, UUID and long-hex path segments are collapsed** (`/orders/41` and
  `/orders/42` become `/orders/{id}`). Without this, a list view generates one
  "candidate" per database row and the tested record is useless.
- **The parameter name set is part of the key.** `/search?q=x` and
  `/search?q=x&sort=y` are different endpoints, because a new parameter is new
  injection surface and should resurface as untested.
- **Cookies are excluded.** This is the surprising one. Cookie *names* look
  stable, but analytics and consent cookies appear and disappear between
  requests, so including them splits one endpoint into a dozen keys and the
  tested record stops matching anything. Cookies are still counted and shown, and
  sqlmap still tests them at `--level 2+`.

Changing `EndpointKey.normalisePath` or the key format **invalidates every
previously recorded test result**, since it is the persistence key.

### The tested record lives in the Burp project file

`api.persistence().extensionData()` rather than `preferences()`. A pentest is
scoped to an engagement, and the Burp project file is the artefact that follows
the engagement. Preferences would leak one client's endpoint list into the next
job's Burp session.

The format is flat TSV with escaped tabs and newlines, not JSON, to keep the
extension dependency-free. Records are small and few.

Duplicate detection compares the **flag summary**, not just the endpoint. Running
the same endpoint again at a higher `--level` is a legitimate escalation and goes
through without a prompt; running it with *identical* flags is almost always a
mistake, so that asks first.

### Performance choices that look like over-engineering

Both of these were measured problems, not speculation:

- **Lazy sample capture.** Keeping a sample calls `copyToTempFile()`, which
  writes a file. Browsing a site produces hundreds of repeat sightings whose
  sample is immediately discarded. `CandidateStore.record` therefore takes a
  `Supplier<HttpRequestResponse>` and only calls it for a new or higher-scoring
  sighting. Verified: 25 identical requests cause exactly one capture.
- **Polled refresh.** The store sets an `AtomicBoolean`; `CandidatesPanel` polls
  it every 800 ms. The obvious listener-per-change design queued one Swing event
  per proxied request, on Burp's networking threads.

### Detection is passive, and filtered

The extension never sends a request of its own. It reads what Burp already saw.
Defaults are in-scope-only and proxy-traffic-only, both to cut noise and to avoid
listing Scanner and Intruder traffic. Requests whose User-Agent contains `sqlmap`
are ignored outright, so pointing sqlmap's `--proxy` back at Burp cannot feed its
own traffic into the candidate list.

### Tamper scripts are read from the local sqlmap

Hardcoding the list would drift from whatever sqlmap the operator actually runs.
`TamperScripts` resolves the configured sqlmap binary, follows symlinks
(`/usr/bin/sqlmap` → `/usr/share/sqlmap/sqlmap.py`), and reads the sibling
`tamper/` directory, pulling each description from the docstring on that script's
`tamper()` function. Well-known install paths and a built-in name list are the
fallbacks.

Because `--tamper` takes a comma-separated list, the dropdown **appends** to a
free-text field rather than replacing it. A plain single-select would have made
chaining impossible.

## Layout

| File | Role |
|---|---|
| `SqlmapExtension` | Entry point; registers tabs, context menu, HTTP handler |
| `SqlmapContextMenu` | "Send to sqlmap" menu item |
| `SqlmapTab` | Run pane: queue, request editor, duplicate guard, launch |
| `OptionsPanel` | Flag builder; one widget per sqlmap option |
| `SqlmapOptions` | Flag model, argv building, shell quoting |
| `TamperScripts` | Tamper discovery and descriptions |
| `TerminalLauncher` | Generates `run.sh`, spawns the terminal |
| `SqlmapTask` | One queued request (raw bytes + endpoint key) |
| `Settings` | sqlmap path, terminal, work dir (Burp preferences) |
| `CandidateDetector` | Passive scoring; `score()` is pure |
| `SqlErrors` | DBMS error signatures |
| `EndpointKey` | Endpoint identity and path normalisation |
| `Candidate`, `CandidateStore` | Deduplicated candidate list |
| `TestedRegistry` | Recorded runs; project-file persistence |
| `CandidatesPanel` | Candidates table and detail pane |
| `UiHelpers` | Swing form helper |
