# Agent Development Notes

Standards, structure, and commands only. Never add debugging narratives, past-bug write-ups, or
one-off error stories — those belong in git commits and in the tests that encode them. A rule may
carry one sentence of *why*; the story of how it was learned stays in git.

General style rules (imports, string resources, Material 3, type names, cleanup) live in
`CODING_STANDARDS.md`, which CLAUDE.md also loads. `DEVELOPMENT_GUIDE.md` (workflow, contributing,
verification greps), `FEATURES.md` (feature → source map) and `README.md` (onboarding) are read on
demand.

## Code Standards

### Translations — **NEVER** touch non-English locales
- **NEVER** add, update, or look up translations in `values-ru/`, `values-uk/`, `values-pl/`,
  `values-de/`, `values-be/`, `values-cs/`, `values-kk/`, or any other non-English locale file.
- **NEVER** translate strings unless the user **explicitly** says "get translations"/"translate".
- **ONLY** add new strings to the default English `values/strings.xml` (in `:strings`).
- Reason: translations are managed separately; machine translations cause quality issues.

### ViewModel ownership — never pass a ViewModel around
- **NEVER** pass a ViewModel into another class/tab/ViewModel, and **NEVER** let one leave the
  composable that owns it (no `onViewModelReady` callbacks, no getters, no external refs).
- Expose data via typed callbacks, state parameters, or a `StateFlow` consumed internally.
- Only acceptable exception: a rendering bridge whose panel lifecycle is tightly coupled to the
  ViewModel (`MediaPresenter`/`VideoPlayer`, `PresentationPlayer`, `LottieFrameStream`) — document
  it explicitly at the site.
- Known standing deviation: `MainDesktop.kt` and the files split out of it pass ViewModels
  top-down — the wiring (`*Wiring.kt`, `MainDesktopEffects.kt`, `RemoteCommandEffects.kt`,
  `PresenterWindows.kt`) and the root screen's layout pieces, which reach them through
  `MainDesktopScope`/`MainDesktopViewModels` (`MainDesktopPanels.kt`, `ScheduleSidebar.kt`,
  `MainTabArea.kt`, `ContentTabPanes.kt`, `PreviewSidebar.kt`). Not new precedent.

### UI icons
- **NEVER** use text/emoji as icons (`Text("⏸")`). Use `painterResource()` with real icon assets.

### Debugging and logging
- Diagnostics go through `Log.info`/`warn`/`error` (`:diagnostics`), never `println` or
  `System.err` — `bash cleanup_check.sh` counts both. A warning or error also lands in the crash
  report's breadcrumb trail.
- Keep debug logs until the fix is confirmed; ask before removing if unsure. Remove them once done.

### Commit authorship — **NEVER** add yourself
- **NEVER** add a `Co-Authored-By:` trailer for an agent, an assistant or a tool — no
  `Co-Authored-By: Claude`, no `Generated with Claude Code`, no bot byline of any kind.
- **NEVER** set yourself as the commit author or committer. Every commit is authored by the person
  running the work, and its message says what changed and why — nothing about who or what typed it.
- This applies to commit messages, merge commits, PR bodies and PR descriptions alike.

## Architecture

All source under `composeApp/src/jvmMain/kotlin/org/churchpresenter/app/churchpresenter/`:

| Package          | Owns                                                                |
|------------------|---------------------------------------------------------------------|
| `tabs/`          | UI only — one file per tab, no logic                                |
| `viewmodel/`     | State + business logic; owns its own ViewModel, never passed around |
| `presenter/`     | Output window rendering (what the audience sees), plus the off-screen outputs (`BrowserSourceVideoRenderer`, `NdiVideoRenderer`, `OmtVideoRenderer`) on the shared `ComposeScenePump` |
| `server/`        | Ktor REST/WebSocket server, ATEM *bridge*, tunnel, SSL — the ATEM client is `:atem`, the PCO OAuth callback listener is `:planning-center` |
| `data/`          | File I/O, database, song parsing, Bible data                        |
| `data/settings/` | Only `ObsSceneSelection.kt` — the rest is the `:settings` module    |
| `models/`        | Only what needs the app: `PresetItems`, the two Companion UI states — `ShortcutAction` is `:shared-ui` |
| `composables/`   | UI components with app or feature ties (SceneCanvas, LivePreviewPanel, etc.) — the shared ones are `:shared-ui`, the video player `:media` |
| `dialogs/`       | All dialogs and settings dialog tabs                                |
| `utils/`         | Stateless helpers (UpdateChecker, etc.) — the shared ones (AutoFit, screen bounds) are `:shared-ui`, crash reporting is `:diagnostics` |
| `ui/theme/`      | `LanguageProvider` and the theme-customization settings — the theme itself is the `:theme` module |

```
main.kt → MainDesktop.kt → tabs/* + PresenterManager → presenter/*
                        ↘ CompanionServer (server/)
                        ↘ StageMonitorScreen.kt
```
- `MainDesktop.kt` is the root composable; `Presenting` (in `:shared-ui`) is the live-content enum.
- New user-facing strings go in `strings/src/main/composeResources/values/strings.xml` — the
  `:strings` module.
- Per-feature source locations are listed in `FEATURES.md`.

## Modules

**Each module documents itself.** Every one of these directories holds its own `AGENT.md` — what it
is, what `:composeApp` uses from it, its layout, its commands, its gates and its rules — and a
`CLAUDE.md` that loads it. Read the module's own file before changing it, and **put
module-specific notes there, not here.**

| Module                 | Gradle                 | What it is                                                                        | Notes                                    |
|------------------------|------------------------|-----------------------------------------------------------------------------------|------------------------------------------|
| `converter/`           | `:converter`           | Song/Bible format converter, also a standalone app                                | [AGENT.md](converter/AGENT.md)           |
| `companion-satellite/` | `:companion-satellite` | Bitfocus Companion Satellite protocol client                                      | [AGENT.md](companion-satellite/AGENT.md) |
| `theme/`               | `:theme`               | The nine color schemes, semantic colors, type and shape scales                    | [AGENT.md](theme/AGENT.md)               |
| `core-models/`         | `:core-models`         | The shared data models                                                            | [AGENT.md](core-models/AGENT.md)         |
| `bible-engine/`        | `:bible-engine`        | Bible Lookup Engine — speech-to-reference detection                               | [AGENT.md](bible-engine/AGENT.md)        |
| `lottieGenerator/`     | `:lottieGenerator`     | Animated lower-third generator, also a standalone app                             | [AGENT.md](lottieGenerator/AGENT.md)     |
| `crossword/`           | `:crossword`           | Crossword authoring tool + the encoded puzzles the app ships                      | [AGENT.md](crossword/AGENT.md)           |
| `presentation-engine/` | `:presentation-engine` | PPTX/PPT/Keynote/PDF parsing, timing and animation                                | [AGENT.md](presentation-engine/AGENT.md) |
| `songlibrary/`         | `:songlibrary`         | The Song Library Manager window, opened from the Help menu                        | [AGENT.md](songlibrary/AGENT.md)         |
| `settings/`            | `:settings`            | Everything the app persists: the settings classes, `SettingsManager`, `Constants` | [AGENT.md](settings/AGENT.md)            |
| `diagnostics/`         | `:diagnostics`         | Crash reporting: the crash log on disk and the Sentry bridge behind it            | [AGENT.md](diagnostics/AGENT.md)         |
| `atem/`                | `:atem`                | The Blackmagic ATEM protocol client — UDP, state, keyers, media-pool upload       | [AGENT.md](atem/AGENT.md)                |
| `ndi/`                 | `:ndi`                 | NDI in and out — runtime discovery, the send calls and the receive calls, behind one interface | [AGENT.md](ndi/AGENT.md)                 |
| `omt/`                 | `:omt`                 | OMT (Open Media Transport) in and out, over the libomt the app bundles, behind one interface | [AGENT.md](omt/AGENT.md)                 |
| `planning-center/`     | `:planning-center`     | The Planning Center Online client — OAuth, the Services REST calls, the callback  | [AGENT.md](planning-center/AGENT.md)     |
| `bible-formats/`       | `:bible-formats`       | The `.spb` converters and the Bible download catalogues (eBible, Zefania, Beblia)  | [AGENT.md](bible-formats/AGENT.md)       |
| `song-chords/`         | `:song-chords`         | The chord grammar songs are written in — parsing, transposition, chord-sheet import | [AGENT.md](song-chords/AGENT.md)         |
| `bible/`               | `:bible`               | The Bible itself: a loaded `.spb` translation, its books, verses and search        | [AGENT.md](bible/AGENT.md)               |
| `calendar/`            | `:calendar`            | The Calendar Manager — planned services on a month grid, each with a run of show   | [AGENT.md](calendar/AGENT.md)            |
| `strings/`             | `:strings`             | The app's user-facing strings, every locale, and the `Res` class generated from them | [AGENT.md](strings/AGENT.md)             |
| `icons/`               | `:icons`               | The UI drawables and the window-icon frames — not the installer icons               | [AGENT.md](icons/AGENT.md)               |
| `shared-ui/`           | `:shared-ui`           | The composables and helpers more than one feature uses — fields, pickers, buttons, text styling | [AGENT.md](shared-ui/AGENT.md)           |
| `slides/`              | `:slides`              | The Pictures and Presentation tabs: their viewmodels, presenters, picture decoding and recent files | [AGENT.md](slides/AGENT.md)              |
| `media/`               | `:media`               | The Media tab and the VLC playback under it: player, decoder, subtitles, stock media search | [AGENT.md](media/AGENT.md)               |
| `web/`                 | `:web`                 | The Web tab and the embedded Chromium (JCEF) it and the output window browse with | [AGENT.md](web/AGENT.md)                 |
| `crossword-tab/`       | `:crossword-tab`       | The hidden Crossword tab and the decoder for the puzzles it plays                  | [AGENT.md](crossword-tab/AGENT.md)       |

Every one is a real Gradle module of this build and is committed directly (no git submodules, no
second wrapper): tested with `./gradlew :<module>:test` on the root wrapper, dependency versions
from `gradle/libs.versions.toml`, `version` from the root `subprojects` block — don't re-declare it.

### POI is shared by three modules
`:presentation-engine`, `:converter` and `:composeApp` all pull Apache POI, and **exactly ONE POI
schema jar may be on the classpath**: `poi-ooxml-full`, never `poi-ooxml-lite` — the engine's
`<p:timing>` parser needs classes the lite jar omits. The version lives in
`gradle/libs.versions.toml` (`apache-poi`) and nowhere else, and `:composeApp` excludes the lite
module graph-wide in a `configurations.configureEach` block.

### JaCoCo lives in the root build
The JaCoCo wiring, `useJUnitPlatform()` and the six-counter floor (85% on all six) are written
**once** in the root `build.gradle.kts`, in the `subprojects { plugins.withId(...) }` block. A
module's build file carries only what differs, set **above everything else** in the file:
- `extra["coverageFloors"]` — a counter→minimum map **merged over** the defaults; name only the
  counters that need a different number. `:converter`, `:companion-satellite`, `:bible-engine`,
  `:presentation-engine` and `:slides` name two each; every other module names none.
  Each module's own `AGENT.md` says which, and why.
- `extra["coverageExcludes"]` — class-directory excludes, replacing the default
  `**/ComposableSingletons*` outright. **Read the rule below before adding one.**

**Do not re-declare `jacocoTestReport`/`jacocoTestCoverageVerification` in a module** — configuring
it there realizes the task before the `extra` above is set, and a second `violationRules` block adds
rules rather than replacing them. `:composeApp` (Kotlin Multiplatform) registers its own task and is
out of scope of the shared block.

### **NEVER exclude code from coverage without asking first**
An exclude does not make code tested; it makes the gate stop asking, and it is invisible in the
number afterwards. **Do not add a path to `extra["coverageExcludes"]`, widen an existing pattern, or
lower `extra["coverageFloors"]` on your own initiative.** Raise it, say what cannot be tested and
why, and let the person running the work decide.

If the answer is "this needs a display / a device / a network", the first move is the split under
**Tests** below — not an exclude. A carve-out that survives it is stated in the module's own
`AGENT.md`. When a module excludes anything, its headline percentage describes the included set
only — measure with the excludes removed before quoting it.

## Commands

```bash
./gradlew :composeApp:run              # run the app
./gradlew compileKotlinJvm             # fast compile check
./gradlew :composeApp:detekt           # static analysis — CI's first gate, run it LAST before you stop
./gradlew :theme:test :theme:detekt    # a module's own suite and gate
# NEVER run :composeApp:detektBaseline — it rewrites baseline.xml and absorbs your own new findings
./gradlew :composeApp:check            # compile + all unit tests
./gradlew :composeApp:jacocoTestReport # coverage → build/reports/jacoco/jacocoTestReport/html/
bash cleanup_check.sh                  # repo code-quality report

bash test-changed.sh                   # ONLY the suites your change touches — seconds, not minutes
bash test-changed.sh --dry-run         # print the selection and the gradle command, run nothing

# Screenshots → <module>/screenshots/<section>/ (COMMITTED; one folder per test class)
./gradlew :composeApp:recordRoborazziJvm :shared-ui:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :composeApp:verifyRoborazziJvm :shared-ui:verifyRoborazziJvm --tests '*ScreenshotTest*'   # gate: fails past 0.1% of pixels
```

A failure that makes no sense — unresolved references to symbols that exist, unrelated suites
failing, a `NoClassDefFoundError` at runtime — is a stale build. `clean` does not clear it;
`--rerun-tasks` does.

### detekt
**Run `./gradlew :composeApp:detekt` (and the detekt task of every module you touched) as the last
step of any change that touched Kotlin.** It is the first job in `.github/workflows/test.yml`, and it
fails on what the compiler only warns about (an unused import). Every finding it prints is yours to
fix.

### **NEVER add to a detekt baseline file**
`config/detekt/baseline.xml`, `bible-engine/config/detekt/baseline.xml` and
`presentation-engine/config/detekt/baseline.xml` hold findings from before the size/length rules
(`LongMethod`, `LongParameterList`, `TooManyFunctions`, `LargeClass`, `MaxLineLength`,
`TooGenericExceptionCaught`) were switched on. They are debt to be paid down, and they only ever
shrink:
- **The only permitted edit to a baseline file is deleting entries.** Never add a line to one — not a
  new entry, not a re-keyed copy of an old one, not a "temporary" one. No exceptions, and no asking
  whether this one is fine: fix the finding instead.
- **NEVER run `detektBaseline`** (or any task that writes a baseline) — it regenerates the file from
  the current tree and silently absorbs every finding you just introduced.
- **Touching a baselined function can surface its finding.** Entries are keyed by rule plus
  signature (a parameter's KDoc included), so changing a signature or a long line makes detekt
  report it again. That finding is now yours: wrap the line, split the function, narrow the catch —
  and delete the old entry, which no longer matches anything.
- **When you fix a baselined finding, delete its entry** in the same change.
- **Every entry is `jvmMain` code; `jvmTest` has none and must keep none.** In tests, suppress at the
  declaration (`@Suppress`) for what genuinely cannot be wrapped.
- detekt cannot fix these rules itself (none of them is auto-correctable, and the build has no
  `detekt-formatting` plugin), so deleting a baseline file does not clean anything up — it turns every
  entry back into a CI failure. Pay the debt down file by file instead.

Thresholds are deliberately not detekt's defaults: `LongMethod` 100, `LargeClass` 1000, and
`LongParameterList` with `ignoreDefaultParameters: true` so the `*TestSupport.kt` DSL helpers are
not flagged.

### Screenshots
- **Committed, beside the module that shoots them** — `composeApp/screenshots/` for the app's tabs,
  dialogs and outputs, `<module>/screenshots/` for each module's own (`shared-ui`, `slides`, `media`).
  The harness (`ScreenshotSupport`, `captureComponent`, `stackedThemes`) is `:shared-ui`'s test
  fixtures, used by all of them. They are what
  a reviewer opens and approves before a UI change merges. **Re-record and commit the images
  whenever a state you touched changed.**
- **NEVER move them, and NEVER put them under `build/`** — not `SCREENSHOT_ROOT`,
  `roborazzi.outputDir`, the workflow's `image-directory-path`, nor `.gitignore`. Under `build/` they
  are wiped by `clean` and no reviewer can open them. `ScreenshotInvariantsTest` enforces this. If
  you think it should move, **ask first**.
- **Verify locally, not in CI.** The committed set is a macOS recording; CI renders on Linux, where
  almost every file differs, so CI records and posts an advisory `reg-actions` comparison only.
  **Record on ONE platform per branch** and never re-record the whole suite out of habit — across
  platforms it rewrites nearly every file for no visual change. Which platform is canonical is
  undecided; ask before re-recording broadly.
- `verifyRoborazziJvm` fails past `ScreenshotSupport.CHANGE_THRESHOLD` (0.1% of pixels) and writes a
  reference|diff|new image to `<module>/build/outputs/roborazzi/<name>_compare.png`. **Open it
  before calling anything churn** — a whole suite failing is usually a re-record nobody did.
- There is **no known churn**: a clean `main` verifies with zero failures on macOS, so any failure is
  a real difference. When a value from outside the composition leaks into a picture (a clock, the
  host's devices, installed fonts — see `PinnedFaces`/`LocalFontPreviewFace`), the remedy is always
  the same: take it as a parameter or a composition local and let the test pin it — never widen the
  threshold.
- Every state is shot in **both themes stacked into one image** — go through `stackedThemes` or
  `captureComponent`, which also write under `SCREENSHOT_ROOT` (a capture written elsewhere is never
  compared). One folder per test class. **Name the class `…ScreenshotTest`** or CI never renders it.
- An open popup is its own compose root: pass `rootIndex = 1`. Byte-identical captures mean a state
  was never reached (`md5 -q <module>/screenshots/<section>/*.png | sort | uniq -d`).
- Shoot a shared composable in its own suite via `captureComponent` — in `:shared-ui` when the
  composable lives there, so its pictures sit with its code; a tab's own `private` composables stay
  private and are covered through the tab.
- `<module>/screenshots/.parts` (per-theme halves) is git-ignored and cleaned in a `finally`;
  never commit it.
- The `reg_actions` branch holds the PR-comment images; retention is `retention-days` in
  `screenshots.yml`, and `reg-actions-prune.yml` squashes the branch monthly. It is not the
  comparison baseline.

### Other commands
Presentation Engine tooling (from that module's root):
```bash
./gradlew dumpTiming  -Pfile=/path/deck.pptx [-Pout=/dir]   # parse audit + PNG renders
./gradlew dumpKeynote -Pfile=/path/deck.key                 # IWA object-graph probe
./gradlew makeSampleDeck -Pout=/path/sample.pptx
```

Run two instances on one machine (Instance Link testing):
```bash
JAVA_TOOL_OPTIONS="-Dchurchpresenter.singleInstancePort=47633 -Duser.home=$HOME/cp-follower"
```

## Tests

### **NEVER write tests or re-record screenshots before the UI is approved**
On a change that touches the UI, **stop when it compiles and the existing suites still pass, show
what it looks like, and wait.** No new unit tests, no new screenshot tests, no
`recordRoborazziJvm` until the person running the work has said it is right ("looks good", "ship
it", "add tests now"). Tests written against the first version get thrown away. Existing tests must
keep passing, and a test your change invalidated is fixed or deleted as part of the change.

`composeApp/src/jvmTest/` — run with `./gradlew :composeApp:check`. CI (`.github/workflows/test.yml`)
runs these plus each module's suite, only for the modules whose directory the change touched.

### The suite runs in parallel forks
`jvmTest` runs on up to 4 parallel JVMs (`-PtestForks=N` to override). Breaking these produces
failures that only appear under load:
- **Never bind a fixed port directly.** Go through `testPort(39_xxx)` (`TestPorts.kt`), and store
  the `testPort` value if the port is reused for the client URL.
- **`user.home` is per fork** (`PerForkTestHome` points each at `build/test-home/worker-N`).
- **Some classes cannot run beside anything**: `jvmTestSerial` (`maxParallelForks = 1`) holds those
  listed in `serialTestClasses` (`composeApp/build.gradle.kts`). `jvmTest` excludes them and is
  `finalizedBy` it. Passing `--tests` stands the exclusion down and runs the named class in `jvmTest`.
- **A hung fork is killed with a diagnosis.** `HungTestReporter` dumps every thread and halts the
  fork (exit 93) once a test runs past its threshold (5 min, 150s in CI); the dump also goes to
  `build/test-results/<task>/hung-test-dump.txt`. Tighten it with `-PhangThresholdMs=30000`.
  Off-screen Compose scenes (`LowerThirdOffscreenRenderer`, `ComposeScenePump`) confine themselves
  to the event queue to avoid a snapshot-observer lock inversion — keep any new one that way.

The tests are **JUnit 4** on junit-vintage; `useJUnitPlatform()` exists for `PerForkTestHome`. Keep
writing `kotlin.test` annotations. The `capabilitiesResolution` block in
`composeApp/build.gradle.kts` pins `kotlin-test` to its JUnit 4 flavour — removing it silently stops
every `@BeforeClass`.

**`-PfastTest`** turns off JaCoCo instrumentation for the inner loop; `check` keeps it on.

### Writing tests
- **Unreachable code is a refactor, not a dead end.** Pull each decision out of the call that needs
  a display/device/network and test it directly; shrink the unreachable call to one step taken as a
  **single** function parameter (JaCoCo scores every lambda as a method, so more parameters can score
  worse). `SwingFileChooser.openWith`/`saveWith`/`showOwned` is the worked example. Measure before and
  after.
- **No ad-hoc mutable `internal var` seams on singletons**, restored by hand per test. The one
  permitted seam is the recent-files singletons (`RecentPictureFolders`, `RecentMediaFiles`,
  `RecentPresentationFiles`), and only through `RecentFilesSwap` (`:shared-ui`'s test fixtures) —
  never assign their fields directly.
  A fourth such seam needs asking first. A *defaulted constructor parameter* is fine.
- **Prefer `internal` over reflection** to reach non-public code (`jvmTest` is a friend of
  `jvmMain`). Reflection is the fallback for what genuinely cannot be widened, and only to READ
  private state in an assertion, never to WRITE it.
- **NEVER use `Thread.sleep` or `delay` as the wait.** Wait for the condition itself — a bounded poll
  on observable state, or a callback/flag/`Job` the code under test exposes. The timeout exists only
  to fail the test. For "must NOT happen", find the signal that the deciding code path finished (a
  token it bumps, the job it ran) and assert after it.
- **No unit test may cost more than ~1s of wall clock.** No retry/backoff delays, idle windows,
  timeouts as the success path, or warm-up pauses. When the production delay is the cost, make it an
  injectable defaulted parameter; if that is impossible, don't write the test and note the gap in the
  class doc. Check with the `time=` attributes in `composeApp/build/test-results/jvmTest/TEST-*.xml`.
- **A route that answers before it finishes leaves a coroutine behind — track it and join it before
  teardown.** `POST /api/atem/still|clip` transfers after responding; route every test in such a suite
  through `AtemBridge.trackUpload`/`cancelUpload` (which joins).
- **No flaky tests.** A new or changed test must pass three consecutive
  `./gradlew :composeApp:jvmTest --tests '<pattern>' --rerun-tasks` runs. Never fix a flake with a
  wider timeout, a retry or a looser assertion; if it cannot be made deterministic, delete it and note
  the gap. (`NoSuchFileException: .../in-progress-results-*.bin` is Gradle losing its scratch file —
  re-run.)
- **`mockk`/`spyk` are a last resort.** Prefer a real fixture, a plain fake or widening to `internal`.
  A mock is justified only when the object genuinely cannot be built or driven (e.g. an animated
  `Deck` for `PresenterManager.presentationShowSlide`). Even then **assert the real outcome**, not
  only `verify { … }`. First use of `mockk` on a final class costs ~1s of instrumentation.
- **Isolate `user.home` before constructing a ViewModel.** `CrashReporter`, `InstanceLinkLogger` and
  `TrainingDataLogger` resolve their path once per JVM — touch them before any swap.
- **Isolate `os.name` the same way**: call `TestSingletons.latchSkikoHostOs()` before any swap
  (`withOsName` does), or skiko's JVM-wide lazy breaks every later Compose test with
  `NoClassDefFoundError: … org.jetbrains.skia.Surface`.
- Tests run headless (`java.awt.headless=true`); anything reaching `GraphicsEnvironment` throws.
  `BibleBookAbbreviations.resolveBookId` does so indirectly — stub it.
- Assert invariants over exact pixel values — font metrics differ across the three target platforms.
- **Build paths from real temp directories, not POSIX literals** — `/tmp/x` does not exist on
  Windows and a stored `absolutePathString()` gains a drive letter.
