# `:stt-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Live Captions tab** — the connection to a speech-to-text server, the transcript and the
translation it streams, and the presenter that draws them on the screen.

`include(":stt-tab")`, `implementation(projects.sttTab)`.

## What `:composeApp` uses from it

More than one symbol, unlike the settings-page modules, because the live transcript is read in five
places:

| symbol | who reads it |
|---|---|
| `STTTab` | `MainDesktop`, as a tab |
| `STTManager` | `main.kt` builds the single instance; the Bible tab's auto-follow, `PresenterModeContent`, `PresenterWindows`, `PresenterOutputContent`, `LivePreviewPanel` and `BrowserSourceVideoRenderer` all read from it |
| `STTPresenter` | the presenter windows, the live preview and the Browser Source overlay |
| `SttOutput` | implemented by `PresenterSttOutput` in the app |

`STTManager` being a public type of this module is deliberate and mirrors `:qa-tab`'s `QAManager`:
one manager, constructed once, read from wherever the captions are drawn.

## The port

`SttOutput` is two members — `isLive` and `goLive()`. The tab used to take `PresenterManager` and a
`(Presenting) -> Unit`, both `:composeApp` types. `isLive` is a `Boolean` rather than the
`Presenting` enum on purpose: the tab never asks *what* is live when it is not the captions, so
exporting the enum would widen the port to the app's whole content model for nothing.
`PresenterSttOutput` implements it over `PresenterManager`.

## The settings dialog is NOT here

`STTSettingsDialog` stays in `:composeApp`, with the other pages of the options dialog. It positions
itself against the main window and is a peer of the app's other settings screens, not part of the
tab. The tab takes an `onOpenSettings: () -> Unit` and `MainDesktop` owns both the flag and the
dialog. **Do not move it in**, and do not give the tab its own copy of the state.

## Two seams that exist because the module boundary does

- **`STTManager(cleanupSharedLogs)`** — a defaulted constructor parameter. The `.db` snapshots the
  "Help Dev" capture writes land in the Bible training log's folder and share its 30-day retention.
  That log is Bible reference data and stays in `:composeApp`, so `main.kt` passes
  `TrainingDataLogger::cleanupOldLogsOnce` in. Defaulted to a no-op so this module's suite needs no
  such folder. A defaulted constructor parameter, not the ad-hoc singleton seam the root `AGENT.md`
  bans.
- **`applyConnected()` is `public`**, not `internal`. It is the transition the socket's own `connect`
  callback runs, and the Bible tab's auto-follow states are only reachable in a test by calling it —
  `jvmTest` is no longer a friend of this module's `main`. Nothing in production calls it except the
  callback.

## `SILENT_STT_URL` is a test fixture of this module

`src/testFixtures/` holds a loopback port that accepts TCP and then says nothing, which is what a
test needs when it wants an STT server that never answers. `:composeApp`'s Bible auto-follow suite
borrows it from here rather than keeping a second copy — getting this wrong once (a refused port,
then `setReconnectionAttempts(Int.MAX_VALUE)`) pushed `jvmTest` past CI's step budget, so there is
one definition and it is documented at the site.

## `Dispatchers.Main` must be on the classpath

`STTManager`'s scope is `Dispatchers.Main`. Without `kotlinx-coroutines-swing` every socket callback
is silently dropped — no error, just nothing happening — and the suite fails as a wall of 5-second
timeouts. The dependency is declared for exactly this reason; do not remove it because "nothing
imports it".

## The socket seam, and why coverage is high

`STTManager.connect()` used to register all six event handlers inline on a socket.io `Socket`, which
can only be obtained by dialling a server. That put every one of them out of reach: seven lambdas
with **zero** coverage, and the module measured `CLASS 0.708`, `COMPLEXITY 0.758`, `BRANCH 0.825`.

`installHandlers(s: SttSocket, url: String)` takes the socket as this class actually uses it — two
methods, `on` and `emit`. `connect()` keeps only what genuinely needs a network: building the real
socket and dialling it. `IoSttSocket` is the production implementation and the suite passes a fake
that records subscriptions and fires events at them.

**This is the refactor the root `AGENT.md` prescribes** — shrink the unreachable call to one step and
take the sequence around it as a function. It is not a mock: the assertions are on what the manager
did with the event, never on the fake being called.

`sttPositionToAlignment` was widened from `private` to `internal` for the same reason: a thirteen-arm
`when` distinguishable only by where text landed in a full render.

No `extra["coverageFloors"]` and no `extra["coverageExcludes"]` — the module clears the root build's
0.85 default on all six counters:

| counter | before the seam | now |
|---|---|---|
| INSTRUCTION | 0.939 | 0.984 |
| BRANCH | 0.825 | 0.906 |
| LINE | 0.941 | 0.995 |
| COMPLEXITY | 0.758 | 0.869 |
| METHOD | 0.874 | 0.974 |
| CLASS | 0.708 | 1.000 |

**Do not inline the handlers back into `connect()`.** Every number above depends on that split.

## detekt: four baselined entries

`config/detekt/baseline.xml` carries four findings that came across from `:composeApp`'s baseline
unchanged — `LongMethod` on `STTTab` and `STTPresenter`, `LongParameterList` on `buildDisplayText`,
and `TooManyFunctions` on `STTManager`. Pre-existing debt, not introduced here.

**Seven `MaxLineLength` entries came across with them and were fixed rather than re-baselined**, and
their entries are deleted from the root `config/detekt/baseline.xml`. Note that wrapping those lines
*raised* `STTTab`'s `LongMethod` count; that is expected and not a reason to leave lines long.

Baseline IDs embed the full signature, so `STTTab`'s entry had to be rewritten by hand when the tab
took `output: SttOutput`. A KDoc block sitting *inside* the parameter list broke the match — the
parameter docs are `@param` tags on the function now, which is better style anyway.

## Commands

```bash
./gradlew :stt-tab:test
./gradlew :stt-tab:detekt
./gradlew :stt-tab:jacocoTestCoverageVerification
./gradlew :stt-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :stt-tab:recordRoborazziJvm --tests '*ScreenshotTest*'
```
