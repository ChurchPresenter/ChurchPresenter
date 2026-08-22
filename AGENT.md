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
| `presenter/`     | Output window rendering (what the audience sees)                    |
| `server/`        | Ktor REST/WebSocket server, ATEM *bridge*, tunnel, SSL — the ATEM client is `:atem`, the PCO OAuth callback listener is `:planning-center` |
| `data/`          | File I/O, database, song parsing, Bible data                        |
| `data/settings/` | Only `ObsSceneSelection.kt` — the rest is the `:settings` module    |
| `models/`        | Only what needs the app: ShortcutAction, the two Companion UI states |
| `composables/`   | Reusable UI components (VideoPlayer, SceneCanvas, etc.)             |
| `dialogs/`       | All dialogs and settings dialog tabs                                |
| `utils/`         | Stateless helpers (AutoFit, UpdateChecker, CrashReporter, etc.)     |
| `ui/theme/`      | `LanguageProvider` only — the theme itself is the `:theme` module   |

```
main.kt → MainDesktop.kt → tabs/* + PresenterManager → presenter/*
                        ↘ CompanionServer (server/)
                        ↘ StageMonitorScreen.kt
```
- `MainDesktop.kt` is the root composable; `presenter/Presenting.kt` is the live-content enum.
- New user-facing strings go in `composeApp/src/jvmMain/composeResources/values/strings.xml`.
- Per-feature source locations are listed in `FEATURES.md`.

## Modules

**Each module documents itself.** Every one of these directories holds its own `AGENT.md` — what it
is, what `:composeApp` uses from it, its layout, its commands, its gates and its rules — and a
`CLAUDE.md` that loads it (plus the module's `README.md` where there is one). Read the module's own
file before changing it, and **put module-specific notes there, not here.**

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
| `planning-center/`     | `:planning-center`     | The Planning Center Online client — OAuth, the Services REST calls, the callback  | [AGENT.md](planning-center/AGENT.md)     |
| `bible-formats/`       | `:bible-formats`       | The `.spb` converters and the Bible download catalogues (eBible, Zefania, Beblia)  | [AGENT.md](bible-formats/AGENT.md)       |
| `song-chords/`         | `:song-chords`         | The chord grammar songs are written in — parsing, transposition, chord-sheet import | [AGENT.md](song-chords/AGENT.md)         |

**Every one of them is a real Gradle module of this build** — `include(":theme")`,
`implementation(projects.companionSatellite)`, tested with `./gradlew :<module>:test` on the root
wrapper, with dependency versions from `gradle/libs.versions.toml` rather than hand-copied
literals. Their `version` comes from the `subprojects` block in the root build; don't re-declare it.
There are no mounted sub-builds and no second wrapper left: one `./gradlew` at the repo root builds
and tests everything.

**None of them are git submodules.** All of them are committed directly into this repository, so a
plain `git clone` is enough and a change spanning the app and a module is one commit.

### POI is shared by three modules
`:presentation-engine`, `:converter` and `:composeApp` all pull Apache POI, and **exactly ONE POI
schema jar may be on the classpath**: `poi-ooxml-full`, never `poi-ooxml-lite`, because the
engine's `<p:timing>` parser needs classes (`CTTLTimeNode*`, `CTTLAnimateBehavior`, …) the lite jar
omits. The version lives in `gradle/libs.versions.toml` (`apache-poi`) and nowhere else, and
`:composeApp` excludes the lite module **graph-wide** in a `configurations.configureEach` block so
no transitive path can reintroduce it.

### JaCoCo lives in the root build
Every module of this build shares one shape — a `test` task, `src/main/kotlin`,
`classes/kotlin/main` — so the JaCoCo wiring, `useJUnitPlatform()` and the six-counter floor are
written **once** in the root `build.gradle.kts`, in the `subprojects { plugins.withId(...) }` block.
The default floor is 85% on all six counters.

A module's build file carries only what differs, and both are read when the task is realized, so
they must be set **above everything else** in the file:
- `extra["coverageFloors"]` — a counter→minimum map **merged over** the defaults, so name only the
  counters that need a different number (usually the one or two that cannot reach 85%), never all
  six. `:converter`, `:companion-satellite`, `:bible-engine` and `:presentation-engine` name two
  each; `:theme`, `:core-models`, `:lottieGenerator`, `:crossword`, `:songlibrary`, `:settings`,
  `:diagnostics`, `:atem`, `:planning-center`, `:bible-formats` and `:song-chords` name none.
  Each module's own `AGENT.md` says which, and why.
- `extra["coverageExcludes"]` — class-directory excludes, replacing the default
  `**/ComposableSingletons*` outright. **Read the rule below before adding one.**

### **NEVER exclude code from coverage without asking first**
An exclude does not make code tested; it makes the gate stop asking. It is the one change that can
turn a real coverage failure green while leaving the untested code exactly as it was, and it is
invisible in the number afterwards — a module reporting 98% over a tenth of its classes reads the
same as one reporting 98% over all of them.

So: **do not add a path to `extra["coverageExcludes"]`, and do not widen an existing pattern, on
your own initiative.** Raise it, say what cannot be tested and why, and let the person running the
work decide. The same goes for lowering `extra["coverageFloors"]`.

If the honest answer is "this needs a display / a device / a network", the first move is the split
described under **Tests** below — pull the decisions out of the unreachable call and test those —
not an exclude. A carve-out that survives that exercise is worth stating in the module's own
`AGENT.md`: what is excluded, and what makes it unreachable.

**Measure what an exclude is hiding before believing the number beside it.** `:songlibrary`
reported 94.5% instructions over 15 classes; with its four-path exclude list removed the same suite
measures **2.7% over 48** — 1,102 instructions of 40,871. Nothing about the tests changed. Take the
headline figure from a module that excludes anything as a statement about the excluded set, not
about the module, and say which it is.

**Do not re-declare `jacocoTestReport`/`jacocoTestCoverageVerification` in a module.** Configuring
the task there realizes it during evaluation, before the `extra` above is set, and a second
`violationRules` block adds rules rather than replacing them — so a floor can only ever be raised,
never lowered, and silently.

`:composeApp` is deliberately out of scope: it is Kotlin Multiplatform, with two exec files, a
`jvmMain` source set and a long exclude list, and registers its own task. The `kotlin("jvm")` plugin
id is what separates the two.

## Commands

```bash
./gradlew :composeApp:run              # run the app
./gradlew compileKotlinJvm             # fast compile check
./gradlew :composeApp:detekt           # static analysis — CI's first gate, run it LAST before you stop
./gradlew :theme:test :theme:detekt    # the theme module's own suite and gate
# NEVER run :composeApp:detektBaseline — it rewrites baseline.xml and absorbs your own new findings
./gradlew :composeApp:check            # compile + all unit tests
./gradlew :composeApp:jacocoTestReport # coverage → build/reports/jacoco/jacocoTestReport/html/
bash cleanup_check.sh                  # repo code-quality report

bash test-changed.sh                   # ONLY the suites your change touches — seconds, not minutes
bash test-changed.sh --dry-run         # print the selection and the gradle command, run nothing

# Screenshots → composeApp/screenshots/<section>/ (COMMITTED; one folder per test class)
./gradlew :composeApp:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :composeApp:verifyRoborazziJvm --tests '*ScreenshotTest*'   # gate: fails past 0.1% of pixels
```

**Run `./gradlew :composeApp:detekt` as the last step of any change that touched Kotlin**, before
saying the work is done. It is the first job in `.github/workflows/test.yml`, so anything it catches
fails the PR before a single test runs. It catches what the compiler will not: an unused import left
behind by a refactor is a warning to `compileKotlinJvm` and a **build failure** to detekt, so
"it compiles" and "the tests pass" are both green while CI is red. A clean run is the expected
result and every finding it prints is yours to fix.

`:bible-engine` and `:presentation-engine` each carry their own `config/detekt/baseline.xml` too,
written the day they joined the detekt step above. They had 205 findings between them and had never
been gated: 44 were fixed outright (dead code, a swallowed exception, 26 over-long lines, and two
parameters a public function ignored), the 7 in test sources carry `@Suppress` at the declaration,
and the remaining 141 -- 86 in `:bible-engine` and 55 in `:presentation-engine`, all `src/main`,
and mostly `MagicNumber`, `NestedBlockDepth` and `ReturnCount` against byte-format parsers -- are
baselined so the rules gate new code. Those numbers
are debt, not absolution: the modules are parsers, but a parser is not exempt from a named constant.

`config/detekt/baseline.xml` holds pre-existing findings from the day the size/length rules
(`LongMethod`, `LongParameterList`, `TooManyFunctions`, `LargeClass`, `MaxLineLength`,
`TooGenericExceptionCaught`) were switched on — 1,590 of them, suppressed so those rules gate new
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
(0.1% of an image's pixels — measured: one switch flipping costs 0.19%, a status line 0.66%, a row
appearing 32.6%, so anything looser hides real regressions. It was 0.5%; the churn that needed that
much slack was fixed at source instead, and `ScreenshotSupport` names each cause). A failure writes a reference|diff|new
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

`previewApp/about_*` (the git hash in `BuildConfig.VERSION_DISPLAY`) and
`previewApp/dictionary_light` (a count read mid-load) were both fixed in their suites rather than by
widening the threshold, along with `colour_picker`, `settings_companion_satellite_*` and a stale
`canvas_*`; `ScreenshotSupport` records what each one was.

**Two churn sources are NOT fixed, and they fail 24 of the 914 images on a clean `main`** — measured
2026-08-22 on macOS, `main` and a feature branch producing byte-identical failure sets:

| suite | images | why it changes every run |
|---|---|---|
| `StageMonitorScreenshotTest` | 22 | The stage monitor draws a **live wall clock**. The diff is literally `06:47:19 PM` against `01:56:04 AM`. |
| `AppPreviewSettingsScreenshotTest` → `settings_stage_monitor_*` | 1 | Same clock, inside the settings preview. |
| `CanvasTabScreenshotTest` → `source_camera` | 1 | Enumerates the host's **real capture devices**. Committed as "MacBook Pro Camera"; a machine without one renders "Capture screen 0". |

Both are the same shape as the `about_*` git-hash case that *was* fixed — a value from outside the
composition leaking into the picture — and both want the same remedy: take the value as a parameter
and let the test pin it. Until then `verifyRoborazziJvm` cannot be read as pass/fail; check the
failing names against this table first, and treat **anything else** as a real difference.

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

| re-record                                                  | images that change     |
|------------------------------------------------------------|------------------------|
| same platform, unchanged states (macOS → macOS)            | **21 of 247 — 8.5%**   |
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
- **Some classes cannot run beside anything, and do not.** `jvmTestSerial` is a second `Test` task
  over the same classes and classpath with `maxParallelForks = 1`, holding the classes listed in
  `serialTestClasses` (`composeApp/build.gradle.kts`): the loopback-UDP suites, the one screenshot
  test that binds a literal port, and the `AppPreview*ScreenshotTest` family, which all seed one
  fixed directory that cannot move per fork without rewriting every image. `jvmTest` excludes them
  and is `finalizedBy` it, so a plain `check` runs both. **Passing `--tests` stands that exclusion
  down** and drops `jvmTest` itself to one fork — so `--tests '*SomeSerialClass*'` runs in `jvmTest`,
  not in `jvmTestSerial`. That is deliberate (a named class must keep working), but it does mean the
  serial task is not the one that ran.
- **A fork that stops making progress is killed with a diagnosis.** `HungTestReporter`, a
  `TestExecutionListener`, watches whichever test is running and, once one has been running past its
  threshold (five minutes by default, **150s in CI** — far past anything this suite legitimately
  does; the slowest class is 37.1s for all of its tests together), dumps every thread in the fork
  and `halt`s it with exit code 93. **The hang it exists for is still unexplained**; that class's
  KDoc records what has been ruled out, so the next attempt does not repeat it. The dump goes to stderr *and* to
  `build/test-results/<task>/hung-test-dump.txt`, which is inside what the workflow already uploads
  as `test-reports`, so it survives the halt losing Gradle's buffered output. Chasing a hang, tighten
  it with `./gradlew :composeApp:jvmTest -PhangThresholdMs=30000`. It exists because the suite has
  hung outright with no failing assertion, and a class name alone was not a cause — the recorded
  stack is one thread waiting on another, which only a full dump shows.

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
- **A route that answers before it finishes leaves a coroutine behind — track it, and join it
  before tearing anything down.** `POST /api/atem/still|clip` responds `"uploading"` and transfers
  on `CompanionServer`'s own scope. A test that asserts on the wire and then drops the shared
  connection is racing that coroutine: its next `AtemConnectionManager.use` dials a switcher the
  test has just closed, which holds the manager's mutex for the whole connect timeout and can leave
  a client cached for a dead **ephemeral** port. Nothing detects either, because
  `AtemClient.isAlive()` is `socket != null` — true for a UDP socket with nobody listening — so the
  next test's switcher records no command of any name and times out with "got 0". One leak is enough:
  each poisoned test burns its 5s deadline while leaving more stuck work behind, so the backlog
  outgrows the suite and never drains. **Five `CompanionServerAtemUploadTest` tests failed exactly
  this way, and did so again after a first fix that reached only the still tests and not the clip
  ones.** `AtemBridge.trackUpload`/`cancelUpload` is the handle; `cancelUpload` joins, so it returns
  only once the coroutine has stopped. Route **every** test in such a suite through the one helper —
  a suite where some tests take the safe path and some do not is a suite that still fails.
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
