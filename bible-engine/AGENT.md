# `:bible-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`. This directory's `README.md` is the full engine documentation — architecture, pipeline,
configuration, event schema, SPB format — and `TRAINING_PLAN.md` covers the tuning/evaluation
workflow. Read those before changing detection behavior.

## What it is

The **Bible Lookup Engine (BLE)**: speech-to-reference detection. It listens to a live
speech-to-text feed, decides which Bible verse is being spoken — stated outright, continued from a
passage being read, or matched by verse text — and emits `scripture.*` events over a Ktor
WebSocket. It is what drives the app's auto-follow.

A real Gradle module of this build: `include(":bible-engine")`, `implementation(projects.bibleEngine)`.
Package `engine`, unchanged from when it was a separate build, so no import in the app changed.

## How the app uses it

`:composeApp` runs the engine **in-process**, from `viewmodel/BibleEngineClient.kt`:

| Symbol | Role |
|---|---|
| `org.churchpresenter.bibleengine.EngineServer.start(sttUrl, bibleRoot, port, bibleFiles)` | starts the engine when STT connects |
| `org.churchpresenter.bibleengine.EngineHandle` | the returned handle — `boundPort`, stop |
| `org.churchpresenter.bibleengine.engine.DetectionLogger` | the per-session detection log the app also writes to |

The app is then just another WebSocket client of `ws://<host>:<port>/bible-engine`, and pushes the
aggressiveness chip over that same socket as `set_tuning {level}`. **BLE connects to the STT server
itself** — the app does not relay the transcript stream.

Its `jar` was a **fat jar** while it was a separate build. As a module of this build `:composeApp`
consumes the jar directly, so it is a plain library jar now and ktor/logback/socket.io arrive
through the dependency graph. Nothing packages or runs it standalone any more, but the
`application` plugin stays so `:bible-engine:run` still works.

## Layout

**The package is `org.churchpresenter.bibleengine`** (subpackages `bible`, `detection`, `engine`,
`socket`, `tools`, `version` unchanged). It was a bare top-level `engine`.

Renaming it is not a search-and-replace: **`engine` is also a variable name all over this module**
— `engine.push(...)`, `engine.sessions`, `engine.enabled`, `engine.requestHistory` — and
`bible-engine.properties` contains the same eight characters. Rewrite only `engine.<known
subpackage or top-level type>`, outside string literals, or you will quietly retarget member
calls and a config filename. The runtime config keys `engine.verbose` and `engine.logCandidates`
are likewise not packages and must not move.


`src/main/kotlin/org/churchpresenter/bibleengine/` — see the README's "Project structure" for the per-file map:

| Package | Owns |
|---|---|
| (root) | `Main.kt` (standalone entry), `EngineServer.kt` (in-process start/stop), `Config.kt` (runtime tunables + `applyLevel`), `AppConfig.kt` (config file + settings discovery) |
| `bible/` | `SpbLoader` (SPB parser + book-manifest scanner), `BibleIndex` (BM25 inverted index), `BibleModels` |
| `detection/` | `ReferenceWatcher` (explicit + sticky references, evidence tiers), `BookResolver`, `NumberWords`, `ReverseLookup`, `ContinuationEngine` |
| `engine/` | `DetectionEngine` (pipeline orchestration), `UtteranceState`, `AgreementScorer`, `Stabilizer`, `DetectionLogger` |
| `socket/` | `SttSocketClient` (Socket.IO input), `SocketHandler` (Ktor WS route), `Broadcaster`, `SttPayload` |
| `version/` | Translation detection — `VersionDetector`, `VersionScorer`, `SpbVersionIndex`/`SpbVersionCorpus` |
| `tools/` | `StickyAudit` (the `stickyAudit` task); `tools/` alongside the source holds the Python correlation script |

## Commands

```bash
./gradlew :bible-engine:test                              # the unit suite
./gradlew :bible-engine:jacocoTestCoverageVerification    # the coverage floor
./gradlew :bible-engine:replayEval                        # score a recorded service against ground truth
./gradlew :bible-engine:stickyAudit                       # audit a sticky-log-*.jsonl for risky jumps
./gradlew :bible-engine:run                               # standalone, for manual poking
```

`test` runs with `bible.root` pointed at `bible-engine/Bibles` and forwards the `replay.*` system
properties (`replay.db`, `replay.fixture`, `replay.bibles`, `replay.level`, `replay.updateGolden`)
so a replay can be driven from the command line. The replay suite is inert without them.

## Gates

- **Coverage**: the root build's six counters, with `extra["coverageFloors"]` lowering BRANCH and
  COMPLEXITY — a detection pipeline is branch-dense — and `extra["coverageExcludes"]` dropping
  `**/ui/**`, `**/MainKt*` and `**/tools/**` (CLI diagnostics). Both `extra` blocks must stay
  **above everything else** in the build file; never re-declare the JaCoCo tasks here.
- There is no detekt task on this module.

## Rules

- **A behavior change is a golden-file change.** `DbReplayTest` scores a recorded service against
  a committed golden; regenerate it with `-Dreplay.updateGolden` only for an *intentional* change,
  and commit the regenerated golden in the same commit.
- Tunables live in `Config.kt` and are set at runtime by aggressiveness level — add a knob there
  rather than hard-coding a threshold at its use site, and keep `ConfigTuningTest` honest.
- The engine must keep working with **no** STT server: the direct-WebSocket input mode is what the
  tests and `wscat` use.
- `maxHeapSize = "2g"` on the test task is deliberate — the suite loads real SPB indexes.

## Dependencies

Ktor server (core/netty/websockets), `kotlinx-serialization-json`, `kotlinx-coroutines-core`,
`logback-classic`, `socket.io-client`; tests add the Ktor CIO client, the WS client and
`sqlite-jdbc` (replay databases). All from `gradle/libs.versions.toml`.
