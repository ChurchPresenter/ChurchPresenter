# `:atem` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Blackmagic ATEM protocol client**: the UDP conversation with the switcher — handshake,
reliable delivery, state dump, keyer cuts and media-pool upload. A real Gradle module of this
build: `include(":atem")`, `implementation(projects.atem)`.

It keeps the package it always had — `…churchpresenter.server` — so **no import in the app changed
when it moved**, the same way `:core-models`, `:settings` and `:diagnostics` kept theirs. Two
modules sharing a package name is fine on a plain classpath; do not "tidy" it into a new package.

## What lives here

| Path | Owns |
|---|---|
| `server/AtemClient.kt` | `class AtemClient` — the socket, the packet layer, every command builder and parser; `AtemState`, `AtemMediaSlot`, `AtemProtocolException`, and the `Companion` one-shots (`cutKey`, `cutUpstreamKeyer`, `isReachable`) |
| `server/AtemConnectionManager.kt` | `object AtemConnectionManager` — one shared client, serialised by a `Mutex`, reconnected lazily when the ATEM expires an idle session |
| `server/AtemFrameEncoder.kt` | The media-pool frame encoding (10-bit YUV + RLE) |
| `server/AtemUploadStatus.kt` | `object AtemUploadStatus` — the upload progress `StateFlow` the UI observes |
| `src/testFixtures/…/FakeAtemSwitcher.kt` | The loopback switcher every suite here drives, and the app's ATEM suites borrow |

## What deliberately stayed in `:composeApp`

`server/AtemBridge.kt` — the wiring: it reads `AtemSettings` and asks `viewmodel.isLottieFile` what
it is uploading. Those are the two app-side dependencies the client itself does not have, and
keeping them out is the whole reason this module cuts cleanly. `AtemBridgeTest`,
`CompanionServerAtemUploadTest`, `CompanionServerAtemKeyTest`, `AtemUploadTracedTest`,
`LowerThirdSequencerKeyTest`, `LowerThirdAtemUploadTest` and the `AtemSettingsTab*` suites stay with
it; they drive `FakeAtemSwitcher` through `testFixtures(projects.atem)`.

**If a change here needs a setting or a ViewModel, it belongs in the bridge, not in this module.**

## Rules

- **Anything `:composeApp` calls has to be public here.** `internal` no longer reaches the app. The
  `internal` members of `AtemClient` are the byte builders and parsers, called only by this
  module's own tests — keep them that way.
- **`FakeAtemSwitcher` is derived from a capture, never from `AtemClient`.** Its doc comment says
  why at length: a fake written by reading the client encodes the client's own misreadings, so the
  test passes and the bug is pinned in place. When the client turns out to be wrong about the
  protocol, fix the client; only change the fake against a new capture.
- **Nothing here may read a setting or reach a ViewModel.** The only app-side dependency is
  `:diagnostics`, for `CrashReporter`.
- **No Compose, ever.** The encoder takes bytes, not an `ImageBitmap`; rendering happens app-side.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, `src/main/kotlin` and
  `src/test/kotlin` in scope — **not `src/testFixtures/kotlin`**, matching every other module here.
  That scope is load-bearing: detekt's default excludes name test source sets (`**/test/**`,
  `**/jvmTest/**`, …) for 15 rules but do not name `testFixtures`, so adding it reports 42
  `MagicNumber` findings against `FakeAtemSwitcher`'s captured byte layouts that the same file never
  produced in `:composeApp`'s `jvmTest`. Findings created by a directory name are not findings.

  Six findings that `:composeApp`'s baseline used to absorb had to be dealt with, since no module
  here carries a baseline. **Five were fixed, not suppressed:**
  - `MaxLineLength` in a `require` message — the interpolation moved to a local.
  - `LongParameterList` ×2 — `AtemKey` groups the three values every keyer call site already passed
    together, taking `cutKey` to four parameters; `AtemClient.Transfer` groups the four that
    describe one in-progress upload, taking `sendGrantedChunks` to three.
  - `TooGenericExceptionCaught` ×3 — `connect`, `use` and `tryRun` each ran
    `catch (e: Exception) { teardown(); throw e }`, which named an exception only to rethrow it
    untouched and covered nothing outside `Exception`. All three are now `try`/`finally` on a
    success flag, so the teardown also runs for cancellation and `Error`, and the cause travels on
    unchanged. `AtemConnectionManager.runInvalidatingOnFailure` is the shared form.

  **One is suppressed, and only because the maintainer approved it:** `TooManyFunctions` on
  `AtemClient` — 46 functions against a threshold of 11, and extracting every pure byte
  builder/parser into a separate object still leaves ~29, so no refactor reaches it.

  **Do not add a baseline file to this module, and do not add a `@Suppress` without asking.**
- **Coverage**: `BRANCH` 0.80 and `COMPLEXITY` 0.78 are the only two counters named. `AtemClient`'s
  receive loop branches on every malformed packet a switcher could send, and the fake only replays
  what real hardware sent, so those two cannot reach 85% from a loopback fake alone.
  Measured: BRANCH 81.3%, COMPLEXITY 79.5%. The other four clear the 85% default unaided —
  INSTRUCTION 91.5%, LINE 94.0%, METHOD 96.4%, CLASS 100%. **No `coverageExcludes`.**

  Raise the floors when the number moves, rather than leaving slack: they sit just under the
  measured value on purpose, so a regression trips them.

## What is not tested here, and why

Listed in `AtemClientSocketTest`'s doc comment, which is the place to keep this current:

- `isReachable`'s failure path and `connect` against a **silent** host. Both end only when a socket
  timeout expires (2s and 5s), neither is injectable, so a test of them would cost its whole
  timeout — the shape the root `AGENT.md` rules out.
- **The keepalive loop**, and with it `drainAndAck` and `closeSocketOnly`, which nothing else
  calls. Its cadence is a hard-coded 1.5s `delay` and its liveness window a hard-coded 5s, so any
  assertion about it is an assertion about two durations. If it ever needs covering, the move is a
  pair of *defaulted constructor parameters* — the `BibleEngineClient.retryFloorMs` shape — not a
  mutable seam. Worth roughly 85 instructions and 17 branches, the largest single gap left.
- **`retransmitFrom`**, both paths. Reaching it means `FakeAtemSwitcher` sending a retransmit
  request, and there is no capture of one — writing the bytes by reading `AtemClient` is precisely
  what the fake's doc comment forbids. Get a capture first.
- The in-flight eviction at `MAX_IN_FLIGHT` (2048 packets), which no upload of a testable size
  reaches.
- The exceptional arm of each `runCatching { socket?.soTimeout = prev }` restore. JaCoCo puts the
  whole duplicated exception path on that one line — 34 instructions in `awaitRealSession`, 53 in
  `drainAndAck` — and it only runs if setting a socket timeout throws. Read those two line numbers
  as noise, not as untested logic.

## Commands

```bash
./gradlew :atem:test
./gradlew :atem:detekt                            # gate — no baseline, must be clean
./gradlew :atem:jacocoTestCoverageVerification
```

All three run in CI, gated on this directory or the shared build files changing.

`DeckLinkHardwareTest`'s opt-in pattern does **not** apply here: nothing in this suite touches a
switcher. Every test binds a loopback `DatagramSocket` on an ephemeral port through
`FakeAtemSwitcher`, so there is no fixed port to collide on and no `testPort()` equivalent needed.

## Dependencies

`kotlinx-coroutines-core` and `projects.diagnostics`, and nothing else. `implementation` rather than
`api` on both: no ATEM signature mentions a coroutines-only or diagnostics type that a caller has to
name. No Compose, no `:settings`, no `:core-models`, no Ktor.
