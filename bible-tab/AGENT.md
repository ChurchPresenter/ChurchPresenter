# `:bible-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Bible** tab: the book, chapter and verse browser, search and smart references, history,
multi-verse passages, hold mode, the cross-reference panel and chips, and the detection panel that
shows what the speech engine heard. `BibleViewModel` lives here, along with what only the tab reads:
the cross references (`CrossReferenceRepository`), the verse-sequence log, the book names and
abbreviations, and the long-verse split.

A real Gradle module of this build: `include(":bible-tab")`, `implementation(projects.bibleTab)`.
Its consumers are `:composeApp` and `:live-output`. Besides the tab the app uses `BibleViewModel`
(presenter wiring, remote commands, Instance Link), `VerseSequenceLog`, `BibleBookAbbreviations`
(Planning Center scripture detection, the calendar) and the long-verse constants (Profiles → Bible).

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`, `:bible`,
`:bible-formats`, `:diagnostics` and `:stt`, and nothing of `:composeApp`'s.

## Seams to the app

- **`BibleOutput`**: the verses on screen and hold mode. `LiveBible` extends it, so the app
  passes `PresenterManager` directly. Tests use `FakeBibleOutput`.
- **`BibleEngineStatus`**: whether the detection engine and its speech feed are up.
  `BibleEngineClient` implements it. The client itself stays in the app; it needs Ktor and the
  engine.
- **`BibleVerseStatistics`**: where a verse that went live is counted. The app adapts `:statistics`'
  `StatisticsManager` to it in `MainTabArea.kt`.
- **`onVerseWentLive`**: the app records its usage telemetry there (`recordBibleWentLive`).
- **`crossReferences`** is required. The app passes `sharedCrossReferences`, which reads the dataset
  from the app's resources. Tests pass their own repository.
- **`BibleViewModel(remoteSyncLog = …)`**: where the Instance Link follower's sync events go:
  `InstanceLinkLogger` in the app.

## Layout

- **`BibleViewModel`** keeps its state, lifecycle and a few small actions. Everything else is an
  extension on it, one file per concern: `…Browse`, `…Clicks`, `…Verses`, `…Refs`, `…Navigation`,
  `…Search`, `…SmartSearch`, `…Selection`, `…Loading`, `…Detection`, `…Canonical`,
  `…TrainingLog`.
  - Outside the module the extensions need an import (`import org.churchpresenter.bibletab.selectBook`).
- **Functions with long argument lists take one value instead.** Examples:
  `onEngineScripture(EngineScripture(…))`, `logLiveReference(LiveReference(…))`,
  `bibleSttStatus(BibleSttSignals(…))`. That keeps them under detekt's parameter limit without a
  baseline entry.

## Package

**`org.churchpresenter.bibletab`**. The tab harness (`bibleTab`, `BibleLabel`, `FakeBibleOutput`,
`withBibleEverywhere`, the finders) is in `src/testFixtures`. It is public so `:composeApp`'s suites
can drive the tab.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suite is `screenshot/BibleTabScreenshotTest`,
  and its images are under `bible-tab/screenshots/bibleTab/`.
- The went-live statistics and telemetry test (`BibleTabGoLiveTelemetryTest`) stays in the app,
  because it needs the real `StatisticsManager` (`:statistics`).

## Commands

```bash
./gradlew :bible-tab:test :bible-tab:detekt
./gradlew :bible-tab:jacocoTestCoverageVerification
./gradlew :bible-tab:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :bible-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
