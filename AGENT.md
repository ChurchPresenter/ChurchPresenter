# Agent Development Notes

Standards, structure, and commands only. Never add debugging narratives, past-bug write-ups, or
one-off error stories — those belong in git commits and in the tests that encode them.

General style rules (imports, string resources, Material 3, type names, cleanup) live in
`CODING_STANDARDS.md` and `DEVELOPMENT_GUIDE.md`, which CLAUDE.md also loads. This file holds only
the rules those don't cover.

## Code Standards

### Translations — **NEVER** touch non-English locales
- **NEVER** add, update, or look up translations in `values-ru/`, `values-uk/`, `values-pl/`,
  `values-de/`, `values-be/`, `values-cs/`, `values-kk/`, or any other non-English locale file.
- **NEVER** translate strings unless the user **explicitly** says "get translations"/"translate".
- **ONLY** add new strings to the default English `values/strings.xml`.
- Reason: translations are managed separately; machine translations cause quality issues.

### ViewModel ownership — never pass a ViewModel around
- **NEVER** pass a ViewModel into another class/tab/ViewModel, and **NEVER** let one leave the
  composable that owns it (no `onViewModelReady` callbacks, no getters, no external refs).
- Expose data via typed callbacks, state parameters, or a `StateFlow` consumed internally.
- Only acceptable exception: a rendering bridge whose panel lifecycle is tightly coupled to the
  ViewModel (`MediaPresenter`/`VideoPlayer`, `PresentationPlayer`, `LottieFrameStream`) — document
  it explicitly at the site.
- Known standing deviation: `MainDesktop.kt` wires several ViewModels top-down. Not new precedent.

### UI icons
- **NEVER** use text/emoji as icons (`Text("⏸")`). Use `painterResource()` with real icon assets.

### Debugging
- Keep debug logs until the fix is confirmed; ask before removing if unsure. Remove them once done.

## Architecture

All source under `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/`:

| Package          | Owns                                                                |
|------------------|---------------------------------------------------------------------|
| `tabs/`          | UI only — one file per tab, no logic                                |
| `viewmodel/`     | State + business logic; owns its own ViewModel, never passed around |
| `presenter/`     | Output window rendering (what the audience sees)                    |
| `server/`        | Ktor REST/WebSocket server, ATEM client, tunnel, SSL                |
| `data/`          | File I/O, database, song parsing, Bible data                        |
| `data/settings/` | Data classes for all persisted settings                             |
| `models/`        | Shared data models (ScheduleItem, SceneModels, etc.)                |
| `composables/`   | Reusable UI components (VideoPlayer, SceneCanvas, etc.)             |
| `dialogs/`       | All dialogs and settings dialog tabs                                |
| `utils/`         | Stateless helpers (AutoFit, UpdateChecker, CrashReporter, etc.)     |
| `ui/theme/`      | Theme, language provider, Material 3 wrappers                       |

```
main.kt → MainDesktop.kt → tabs/* + PresenterManager → presenter/*
                        ↘ CompanionServer (server/)
                        ↘ StageMonitorScreen.kt
```
- `MainDesktop.kt` is the root composable; `presenter/Presenting.kt` is the live-content enum.
- New user-facing strings go in `composeApp/src/jvmMain/composeResources/values/strings.xml`.
- Per-feature source locations are listed in `FEATURES.md`.

### Sub-builds
Five module sources are mounted into composeApp via `kotlin.srcDir` — they compile as one app but
have their own Gradle builds and test suites, under `src/jvmMain/appResources/common/`:
`ChurchPresenter-PresentationEngine` (committed directly, NOT a git submodule), `-BLE`,
`-LottieGen`, `-Converter`, `-CompanionSatellite`.

- **When touching module code, compile BOTH builds**: `./gradlew compileKotlinJvm` at the repo root
  AND `sh gradlew build` inside the module. The main build is more permissive and will accept code
  the module's own build rejects.
- The Presentation Engine has **zero Compose dependency by construction** — accidental Compose
  imports fail its standalone build.
- The Presentation Engine runs **entirely in-JVM**: never shell out to `osascript`, AppleScript,
  `qlmanage`, `sips`, or `unzip`.

## Dependencies

Presentation deps in `composeApp/build.gradle.kts` are mirrored in
`ChurchPresenter-PresentationEngine/build.gradle.kts` — **keep versions in sync**:
- `pdfbox:2.0.33`, `poi:5.3.0`, `poi-scratchpad:5.3.0`
- `poi-ooxml:5.3.0` **with `poi-ooxml-lite` excluded** + `poi-ooxml-full:5.3.0` — the animation
  timing parser needs `<p:timing>` schema classes lite omits. **Exactly ONE schema jar may be on
  the classpath.**
- `io.airlift:aircompressor` — pure-Java snappy for the Keynote IWA reader.
- All POI/PDFBox access is typed, no reflection.

## Commands

```bash
./gradlew :composeApp:run              # run the app
./gradlew compileKotlinJvm             # fast compile check
./gradlew :composeApp:check            # compile + all unit tests
./gradlew :composeApp:jacocoTestReport # coverage → build/reports/jacoco/jacocoTestReport/html/
bash cleanup_check.sh                  # repo code-quality report
```

Presentation Engine tooling (from that module's root):
```bash
./gradlew test
./gradlew dumpTiming  -Pfile=/path/deck.pptx [-Pout=/dir]   # parse audit + PNG renders
./gradlew dumpKeynote -Pfile=/path/deck.key                 # IWA object-graph probe
./gradlew makeSampleDeck -Pout=/path/sample.pptx
```

Run two instances on one machine (Instance Link testing):
```bash
JAVA_TOOL_OPTIONS="-Dchurchpresenter.singleInstancePort=47633 -Duser.home=$HOME/cp-follower"
```

## Tests

`composeApp/src/jvmTest/` — run with `./gradlew :composeApp:check`.
CI is `.github/workflows/test.yml` (push/PR); it runs these plus the module suites and requires
`submodules: true`, or the app won't compile.

When writing tests here:
- **Unreachable code is a refactor, not a dead end.** When a class is uncovered because it needs a
  display, a bus, a network or a device, do not conclude it cannot be tested. Almost none of such a
  class is actually the unreachable call — the rest is ordinary logic sitting around it. Split it:
  - Pull each decision into its own function — what the dialog/request is configured with, what
    its answer is turned into — and test those directly.
  - Then shrink the unreachable call itself to **one step of its own**, and take the sequence
    around it as a function with that step as a **parameter**. Tests pass a stand-in and exercise
    the real order, the real wiring and the real helpers; only the one call stays uncovered.
    `SwingFileChooser.openWith`/`saveWith`/`showOwned` is the worked example.
  - **Count the lambdas.** JaCoCo scores every lambda as its own method, so a helper taking four
    function parameters adds four uncovered methods at each call site and can score *worse* than
    the inline code it replaced. Take one function parameter — the unreachable step — and call the
    rest directly. Measure before and after; the report is the arbiter, not the intent.
  - What must NOT be done to reach coverage: adding a mutable `internal var` seam on a singleton
    (leaks between tests — see the flaky-test rule below), or asserting that a stub was called
    instead of that something works. If a test can only prove a mock was invoked, don't write it.
- **Prefer `internal` over reflection to reach non-public code.** `jvmTest` is a friend of
  `jvmMain`, so an `internal` member is callable straight from a test. Reflection costs a lookup
  per call, throws at runtime instead of failing to compile when a signature changes, loses the
  types (casts, `Array<Any?>`, `javaPrimitiveType`), and hides renames from the IDE. Widen the
  member to `internal` and call it. Reflection is the fallback for what genuinely cannot be
  widened — a private top-level function, or code that must stay private for a reason worth
  stating. Existing reflection in `PlatformFileChooserTest`/`CrashReporterTest` predates this rule
  and is not precedent for new tests.
- **NEVER use `Thread.sleep` or `delay` to wait for async work.** A fixed pause asserts on timing,
  not behaviour, and flakes on a loaded CI machine. Wait for the condition itself — a bounded
  poll on observable state with a timeout that throws, or a callback/flag the code under test
  sets. Never assert "nothing happened" after an arbitrary pause; wait for a positive signal that
  the operation finished, then assert what did or didn't change.
- **No unit test may cost more than ~1s of wall clock.** The suite is run constantly; a test that
  waits is a tax on every future change. This rules out anything whose cost is a duration rather
  than the work itself: retry/backoff delays, "wait for silence" idle windows, timeouts used as
  the success path, polling loops that expect to time out, and fixed warm-up pauses. Concretely:
  - A wait must end on a **positive signal** — the state you expect, a frame you can identify, a
    callback firing. It must never end by the timeout expiring; the timeout exists only to fail
    the test. A `waitFor { ... } == false` result costing the full timeout is the bug this rule
    is about.
  - For "must NOT happen", find the signal that the deciding code path **finished** and assert
    against it. Example: `BibleViewModel.navigateToReference` bumps `verseSelectionToken` and then
    — same coroutine, no suspension between — bumps `autoFollowLiveToken` only if the match
    qualified, so waiting on the first and then asserting the second is race-free and instant.
  - When the production code's own delay is the cost and is not injectable (e.g.
    `BibleEngineClient`'s 2s reconnect backoff floor), **do not write the test**; note the gap in
    the test class's doc comment instead.
  - Where an idle window genuinely is the only terminator (a snapshot with no final frame), keep
    it in the low hundreds of ms — it only has to outlast a loopback gap, not a network.
  - Check the cost of what you added: `./gradlew :composeApp:jvmTest` then read the `time=`
    attributes in `composeApp/build/test-results/jvmTest/TEST-*.xml`.
- **No flaky tests.** A test that passes only most of the time is worse than no test: it trains
  everyone to re-run instead of to read the failure. A new or changed test must pass on repeated
  consecutive runs before it is committed — `./gradlew :composeApp:jvmTest --tests '<pattern>'
  --rerun-tasks`, three times. If it cannot be made deterministic, delete it and note the gap in
  the test class's doc comment. Never "fix" a flake by widening a timeout, adding a retry, or
  loosening the assertion — those hide the race instead of removing it. The usual causes here:
  racing a coroutine instead of waiting on a positive signal, depending on another test's leftover
  state (`user.home`, a MockK object mock, a shared singleton), depending on test execution order,
  or asserting on a real clock, a real port, or the filesystem outside a per-test temp dir.
  (Not a flaky test: `:composeApp:jvmTest` itself sometimes fails with
  `NoSuchFileException: .../test-results/jvmTest/binary/in-progress-results-*.bin` — that is
  Gradle losing its own scratch file, unrelated to any assertion. Re-run.)
- **`mockk`/`spyk`/reflection are a LAST RESORT, not a first reach.** Reach for a real fixture, a
  plain fake (a stand-in lambda, a constructed data class), or widening a member to `internal`
  first — those exercise real behaviour and don't rot when signatures change. Only when the branch
  needs an object that genuinely cannot be built or driven any other way is a mock justified:
  - A real example worth it: `PresenterManager.presentationShowSlide`'s animated branches need an
    animated `Deck` (a real one means a POI-built PPTX with a `<p:transition>` and a `DeckRasterizer`
    that renders — slow, graphics, flaky headless) and a `PresentationPlayer` that cannot be
    constructed from a mock deck (its ctor rasterizes). `mockk<Deck>`/`mockk<PresentationPlayer>`
    injected via the `internal presentationPlayer` field is the only way in.
  - A counter-example NOT worth it: `SongsViewModel`'s remote follower path looks mock-shaped but is
    fully reachable with plain fakes — a real `SongCatalogResponse` and a `fetchDetail` lambda
    returning a constructed `SongDetailDto`, driven through `setInstanceLinkSource` → `selectSong`.
    No mock. Prefer this.
  - Even with a mock, **assert the real outcome** — which object ends up live, the resulting state,
    an identity (`assertSame`) — over `verify { mock.method() }`. A lone "a stub was called"
    assertion tests nothing; a supporting `verify` alongside a real-state assertion is fine.
  - Use **reflection to READ** private `_` backing state in an assertion when it has no getter —
    never to WRITE it to force a state the public API cannot produce (that just exercises dead
    defensive code). Widening to `internal` is preferred over reflection where the field can be
    widened; keep `_`-prefixed backing fields private.
  - `mockk` of a final class pays a one-time JVM instrumentation cost (~1s) on first use; it is
    amortised across the suite but can push an *isolated* first run near the 1s bar — check it, and
    prefer non-mock approaches partly for this reason.
- **Isolate `user.home` before constructing a ViewModel** — several resolve file paths from it at
  construction and then write or delete there. `CrashReporter`, `InstanceLinkLogger` and
  `TrainingDataLogger` resolve theirs once per JVM, so touch them before any swap or they latch
  onto a temp dir that gets deleted.
- Tests run headless (`java.awt.headless=true`); anything reaching `GraphicsEnvironment` throws.
  `BibleBookAbbreviations.resolveBookId` does so indirectly (Compose string resources) — stub it.
- Assert invariants over exact pixel values — font metrics differ across the three target platforms.
