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
`ChurchPresenter-PresentationEngine`, `-BLE`, `-LottieGen`, `-Converter`, `-CompanionSatellite`.
A sixth, `-Cross`, is not mounted — `syncCrosswordFiles` copies its `encoded/*.xwp` into
composeResources at build time.

**None of these are git submodules.** All six are committed directly into this repository, so a
plain `git clone` is enough and a change spanning the app and a module is one commit.

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
./gradlew :composeApp:detekt           # static analysis — CI's first gate, run it LAST before you stop
# NEVER run :composeApp:detektBaseline — it rewrites baseline.xml and absorbs your own new findings
./gradlew :composeApp:check            # compile + all unit tests
./gradlew :composeApp:jacocoTestReport # coverage → build/reports/jacoco/jacocoTestReport/html/
bash cleanup_check.sh                  # repo code-quality report

bash test-changed.sh                   # ONLY the suites your change touches — seconds, not minutes
bash test-changed.sh --dry-run         # print the selection and the gradle command, run nothing

# Screenshots → composeApp/screenshots/<section>/ (COMMITTED; one folder per test class)
./gradlew :composeApp:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :composeApp:verifyRoborazziJvm --tests '*ScreenshotTest*'   # gate: fails past 0.5% of pixels
```

**Run `./gradlew :composeApp:detekt` as the last step of any change that touched Kotlin**, before
saying the work is done. It is the first job in `.github/workflows/test.yml`, so anything it catches
fails the PR before a single test runs. It catches what the compiler will not: an unused import left
behind by a refactor is a warning to `compileKotlinJvm` and a **build failure** to detekt, so
"it compiles" and "the tests pass" are both green while CI is red. A clean run is the expected
result and every finding it prints is yours to fix.

`config/detekt/baseline.xml` holds pre-existing findings from the day the size/length rules
(`LongMethod`, `LongParameterList`, `TooManyFunctions`, `LargeClass`, `MaxLineLength`,
`TooGenericExceptionCaught`) were switched on — 1,623 of them, suppressed so those rules gate new
code only. **Every entry is `jvmMain` code; `jvmTest` has none and must keep none.** The test suite
was brought to zero findings instead: 616 lines were wrapped, and the 27 that cannot be wrapped
carry `@Suppress` at the declaration — one-line raw-string JSON fixtures (wrapping changes the
literal), one backtick test name too long to break (Kotlin identifiers cannot span lines), and two
test classes over the `LargeClass` threshold. **Suppress at the site in tests; never add a test
entry to the baseline.**

Thresholds are deliberately not detekt's defaults: `LongMethod` 100 (60 flags 191 findings, most
of them Compose UI; 100 is the knee of the curve and still catches `ChurchPresenterApp` at 1,418 lines
and `MainDesktop` at 1,168), `LargeClass` 1000, and `LongParameterList` with
`ignoreDefaultParameters: true` so the `*TestSupport.kt` DSL helpers — long lists of defaulted
parameters, overridden by name per test — are not flagged.

**NEVER run `./gradlew :composeApp:detektBaseline` again.** Not to refresh it, not to re-sort it,
not "just to see". That task rewrites the file from the current tree, so it silently absorbs every
finding you just introduced and the gate stops gating. The file is generated once and edited by
hand from now on. **Never extend it to silence a new finding**, either. Entries are keyed by rule
plus signature, so touching a baselined function can surface its finding — fix the finding and
delete the entry.

**Verify before you commit a UI change, and re-record what it moved.** `verifyRoborazziJvm` compares
the committed images against a fresh render and fails past `ScreenshotSupport.CHANGE_THRESHOLD`
(0.5% of an image's pixels — measured: one switch flipping costs 0.19%, a status line 0.66%, a row
appearing 32.6%, so anything looser hides real regressions). A failure writes a reference|diff|new
image to `composeApp/build/outputs/roborazzi/<name>_compare.png` and names it in the message.

**It runs locally, not in CI.** The committed set is a macOS recording and CI renders on Linux, where
essentially every file differs — see the table below. CI still records and posts the advisory
`reg-actions` comparison; it does not verify.

The pictures in that comment are served from a `reg_actions` branch, one directory per run. **How
long they live is `retention-days` on the reg-actions step in `screenshots.yml` — the only place
that window is set**; the action prunes expired directories itself. It prunes files and not history,
though, so the pack grows regardless, which is what
`.github/workflows/reg-actions-prune.yml` is for: monthly it rebuilds the branch as a single root
commit over whatever the tip already holds, applying no retention of its own. Left alone the branch
outgrew all of `main` in nine days.

That branch is **not** the comparison baseline, despite the name. reg-actions resolves the expected
images from the workflow *artifact* attached to the merge-base commit's run, so nothing done to the
branch can affect whether a comparison works. Nothing under `composeApp/screenshots/` is affected
either; those are the images reviewers approve.

Known red, both pre-existing and neither a regression: `previewApp/about_*` draws
`BuildConfig.VERSION_DISPLAY`, which carries the git hash and so changes on every commit, and
`previewApp/dictionary_light` renders a 14,197-row list whose row heights are not stable between
runs. Both need their suite fixed, not the threshold widened.

Every state is shot in **both themes and stacked into one image**, light above dark — go through
`stackedThemes` (or `captureComponent`, which wraps it) and a state is written once, not twice. One
folder per test class (`screenshots/<section>/`). An
open popup (dropdown, menu, tooltip) is its own compose root: pass `rootIndex = 1` or `onRoot()`
fails with "expected exactly 1 node". Two captures that come out byte-identical mean the state was
never reached — check with `md5 -q composeApp/screenshots/<section>/*.png | sort | uniq -d`.

**Shoot a shared composable in its own suite rather than only through the tab that uses it** —
`DropdownSelector`, `GoLiveButton`, `ActionIconButton` and friends are used by many tabs, so one
image per state beats the same button appearing inside a dozen tab screenshots. `captureComponent`
takes a `drive` block for the interaction (opening a menu) and `rootIndex` for the popup it opens,
and crops what it shoots to the drawn content — a popup root is the whole window, so without that an
open menu arrives as a menu on a screenful of empty background. Tab and presenter shots are **not**
cropped: there the empty space is the layout.
A tab's *own* extracted composables are `private` and stay that way: widening them to `internal` to
photograph them is refactoring production code for testability. Their states are covered through
the tab.

### **NEVER move the screenshot location, and NEVER put screenshots under `build/`**
This is a standing rule, not a preference to re-litigate. `composeApp/screenshots/` is where they
live and they are **committed**. Do not change `SCREENSHOT_ROOT`, `roborazzi.outputDir`, the
workflow's `image-directory-path`, or add `composeApp/screenshots/` to `.gitignore` — not to save
repo size, not because CI renders its own copies, not because the committed images do not feed the
reg-actions comparison. All of that is true and none of it is a reason: **under `build/` the images
are git-ignored and wiped by `clean`, so they exist only on the machine that last recorded them and
no reviewer can ever open them, approve them, or ask for a state to be changed before it merges.**
That review is the entire point of having them.

This has already been flipped three times in two days, each time undoing a merged decision.
`ScreenshotInvariantsTest` now fails if the root moves under `build/`. If you think it should move,
**ask first** — do not change it and explain afterwards.

**Screenshots ARE committed**, under `composeApp/screenshots/`. They are the artifact a human opens
and approves before a UI change is merged: a reviewer looks at the images and asks for changes when
a state is wrong, missing, or badly framed. **Re-record and include the images in the commit
whenever a state you touched changed.** `.github/workflows/screenshots.yml` additionally records
both sides on one runner and posts the before/after as a PR comment — that comment is a convenience,
not the approval. Note the record step overwrites the working copy before the comparison reads it,
so the committed images are for humans and never enter the diff CI computes.

The cost is real and does not go away by being committed. **Skia rasterises text per platform**, and
git keeps every version of a binary for ever. Measured on 2026-08-08 against the 247-image set:

| re-record | images that change |
|---|---|
| same platform, unchanged states (macOS → macOS) | **21 of 247 — 8.5%** |
| across platforms (the committed set vs CI's Linux renders) | **246 of 247 — 99.6%** |

**The two numbers are what matter, not an average of them.** Re-recording on the platform the set
was recorded on is cheap, so *do* re-record after a change and commit what moved. Re-recording on a
different OS rewrites essentially the whole set for no visual change — so **record on ONE platform
per branch**, and never re-record the whole suite out of habit.

> An earlier version of this paragraph said "15 of 16, measured" without saying across what. That
> conflated the two axes and understated the cross-platform case, which is the one the "ONE
> platform" rule exists for. To measure it yourself:
> `gh run download <run-id> -n screenshots -D <dir>` then `cmp -s` file by file. **A local
> re-record does not measure this** — it compares against your own platform and returns a
> comfortable 8%.

**The committed set is currently a macOS recording and CI renders on Linux**, so the two disagree on
almost every file. That is not a fault in either; it is what the table above describes. Which
platform is canonical has not been decided — if you are about to re-record broadly, ask first.

To hand images to the website, download the `screenshots` artifact from a run on `main`: those are
the Linux renders and they are consistent with each other.

**A capture must be written under `SCREENSHOT_ROOT`** (`ScreenshotSupport.kt`, the single definition
of that path). Images are matched between the two sides of the comparison **by their path relative
to that root**, so one written elsewhere is not reported as changed — it quietly has no counterpart
and stops being compared. Go through `stackedThemes`/`captureComponent` and this is handled.

**Name the class `…ScreenshotTest`.** The workflow records with `--tests '*ScreenshotTest*'`; a
class outside that pattern is never rendered in CI, and its images are never compared.

A difference in the comment is advisory, not a failure — a deliberate design change looks exactly
like a regression.

A failure that makes no sense — unresolved references to symbols that exist, unrelated suites
failing, a `NoClassDefFoundError` at runtime — is a stale build. `clean` does not clear it;
`--rerun-tasks` does.

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
CI is `.github/workflows/test.yml` (push/PR); it runs these plus each module's own suite, invoked
one at a time through the module's own wrapper — and only for the modules whose own directory the
change touched (the `Which sub-builds changed` paths-filter step).

### The suite runs in parallel forks — what that costs you

`jvmTest` runs on **4 parallel JVMs** (`maxParallelForks`, half the cores, capped at 4; override with
`-PtestForks=N`). It went from ~12 minutes to ~5. Two rules follow from it, and breaking either one
produces a failure that only appears under load and only sometimes:

- **Never bind a fixed port directly.** Go through `testPort(39_xxx)` (`TestPorts.kt`), which shifts
  the whole range by this fork's band. A bare literal is a `BindException` against another fork. If
  a class stores its port and reuses it for the client URL, the *stored* value must be the
  `testPort` one too — `CompanionServerAtemKeyTest` bound the offset port and then talked to the
  literal, and every one of its tests failed with "connection refused".
- **`user.home` is per fork, not shared.** `PerForkTestHome` (a `LauncherSessionListener`, which is
  the only hook that runs before discovery) points each fork at `build/test-home/worker-N`. Suites
  that deliberately use the shared fake home rather than swapping in a temp dir — and there are a
  couple of dozen, several of which *delete* a directory in `@BeforeTest` — are isolated by that and
  by nothing else.

The tests themselves are still **JUnit 4**, running on junit-vintage under the JUnit Platform
launcher. `useJUnitPlatform()` is there for `PerForkTestHome`, not for JUnit 5 syntax: keep writing
`kotlin.test` annotations, and `@get:Rule`/`@BeforeClass` keep working. The
`capabilitiesResolution` block in `composeApp/build.gradle.kts` is what pins `kotlin-test` to its
JUnit 4 flavour — remove it and the JUnit 5 flavour resolves instead, which quietly stops running
every `@BeforeClass` in the suite.

**`-PfastTest`** turns off JaCoCo instrumentation (~15-25%) for the inner loop; `check` keeps it on.

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
  - What must NOT be done to reach coverage: adding an **ad-hoc** mutable `internal var` seam on a
    singleton and restoring it by hand in each test (leaks between tests — see the flaky-test rule
    below), or asserting that a stub was called instead of that something works. If a test can only
    prove a mock was invoked, don't write it.
  - **The one permitted seam of that shape is the recent-files singletons**, and only through
    `RecentFilesSwap` in `jvmTest`. `RecentPictureFolders`, `RecentMediaFiles` and
    `RecentPresentationFiles` expose `internal var file`/`pinnedFile`; a test repoints them via that
    helper, which restores both paths and both lists in one place, so the restore is structural
    rather than something each author remembers. **Never assign those fields directly.** The
    alternative — keeping them private and reaching in with `getDeclaredField` — is what this
    replaced: it hides renames from the compiler (see the reflection rule below) and, run outside
    Gradle, it deleted the developer's own recents. Adding a *fourth* such seam is not covered by
    this exception; raise it first.
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
  - When the production code's own delay is the cost and is not injectable, **do not write the
    test**; note the gap in the test class's doc comment instead. But first ask whether it has to
    stay non-injectable: `BibleEngineClient`'s reconnect backoff was the standing example here, and
    a defaulted `retryFloorMs` constructor parameter both unlocked the reconnect test and removed a
    flake — a *defaulted constructor parameter* is not the ad-hoc mutable singleton seam banned
    above. The schedule's shape stays pinned by a pure test of `retryDelayMs`.
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
- **Isolate `os.name` the same way** — skiko maps it onto a known OS in a JVM-wide `by lazy` and
  throws `Error: Unknown OS <name>` on anything else, from `org.jetbrains.skia.Surface`'s static
  initializer. So a Compose test composed inside a faked `os.name` permanently breaks every later
  Compose test in that JVM with `NoClassDefFoundError: Could not initialize class
  org.jetbrains.skia.Surface`, blamed on whichever class ran next. Call
  `TestSingletons.latchSkikoHostOs()` before the swap; `withOsName` already does.
- Tests run headless (`java.awt.headless=true`); anything reaching `GraphicsEnvironment` throws.
  `BibleBookAbbreviations.resolveBookId` does so indirectly (Compose string resources) — stub it.
- Assert invariants over exact pixel values — font metrics differ across the three target platforms.
- **Build paths from real directories, not POSIX literals.** `Path("/tmp/x.png").parent` is `\tmp`
  on Windows and does not exist, so `FileChooser` silently falls back to the home directory; and a
  chosen path stored via `absolutePathString()` gains a drive letter. Create a temp dir and derive
  the expectation from it rather than asserting a `/`-rooted string.
