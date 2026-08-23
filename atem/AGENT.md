# `:atem` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Blackmagic ATEM protocol client**: the UDP conversation with the switcher — handshake,
reliable delivery, state dump, keyer cuts and media-pool upload. A real Gradle module of this
build: `include(":atem")`, `implementation(projects.atem)`.

**The package is `org.churchpresenter.atem`**, across all three source sets — `main`, `test` and
the `testFixtures` that ship `FakeAtemSwitcher` to `:composeApp`'s suites.

It held `…churchpresenter.server`, which has since become `:companion-server` in its own right
(`org.churchpresenter.companionserver` — `CompanionServer`, the routes, the tunnel, `AtemBridge`
itself). Sharing that name cost nothing at extraction time and one thing afterwards: `AtemBridge`
and the ATEM routes resolved `AtemClient` with no import at all, so nothing in those files said
they depended on this module. They say it now.

**Never rewrite a package by prefix** — key on the ten types this module declares
(`AtemClient`, `AtemConnectionManager`, `AtemFrameEncoder`, `AtemKey`, `AtemMediaSlot`,
`AtemProtocolException`, `AtemState`, `AtemUploadStatus`, `EncodedFrame`, `FakeAtemSwitcher`), and
match on a word boundary: `AtemKey` is a prefix of nothing here today, but `Constants` matching
inside `ConstantsKt` is exactly how a sibling rename silently rewrote a `mockkStatic` string.

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
- **Coverage**: **no `coverageFloors` override and no `coverageExcludes`.** The root build's
  default six counters at 85%, as written, and all six pass:
  INSTRUCTION 95.8%, BRANCH 88.0%, LINE 99.1%, COMPLEXITY 85.8%, METHOD 99.3%, CLASS 100%.

  COMPLEXITY is the tight one — it clears by under a point, so a new branch in `AtemClient` will
  need a test with it. **Do not answer that by writing a floor into this file.** The root
  `AGENT.md` requires asking before lowering one, and a floor written before the number is measured
  is a carve-out for a problem nobody has shown exists.

  What made the timeout paths reachable is the four defaulted constructor parameters on
  `AtemClient` — see the rule below.

## The four timeout parameters — how the deadline paths are tested

`AtemClient` takes `connectTimeoutMs`, `commandTimeoutMs`, `keepAliveIntervalMs` and
`silenceTimeoutMs` as **defaulted constructor parameters**. Production passes none of them; the
defaults are the values this client has always used (5s, 8s, 1.5s, 5s).

They exist because every failure path here ends when a deadline expires — a switcher that never
answers the hello, a command that is never ACKed, a session the ATEM has silently dropped. At the
shipped values each of those tests would cost 5–8 seconds, which the root `AGENT.md` rules out. At
60ms they cost 60ms, and they still assert against a *real* deadline rather than a stubbed one.
`AtemClientTimeoutTest` is the whole set: 12 tests, ~1.5s.

This is the `BibleEngineClient.retryFloorMs` shape the root `AGENT.md` blesses, **not** the ad-hoc
mutable singleton seam it bans. Do not turn any of them into a `var`.

`FakeAtemSwitcher` has two knobs for the same purpose — `ackCommands = false` and
`ftdeFatalCode` — which withhold or vary a reply the captures already showed. Neither invents a
byte layout, which is the line that matters (see the fake's doc comment).

## What is not tested here, and why

- **`retransmitFrom` through the wire.** The function itself is tested directly — it is `internal`
  for that — but nothing drives `receiveAndProcess`'s retransmit-request branch, because that needs
  `FakeAtemSwitcher` to *send* one and there is no capture of it. Writing those bytes by reading
  `AtemClient` is precisely what the fake's doc comment forbids. Get a capture first.
- The in-flight eviction at `MAX_IN_FLIGHT` (2048 packets), which no upload of a testable size
  reaches.
- One dead sub-branch of `isCoveredByAck`: `(shortlyBefore || beforeWrap) && shortlyAfter`.
  `shortlyAfter` is mutually exclusive with both of the others by construction, so the guard cannot
  be false there. Do not write a test for it; there is no input that reaches it.
- The exceptional arm of each `runCatching { socket?.soTimeout = prev }` restore. JaCoCo puts the
  whole duplicated exception path on that one line, and it only runs if *setting a socket timeout*
  throws. Read those line numbers as noise, not as untested logic.

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
