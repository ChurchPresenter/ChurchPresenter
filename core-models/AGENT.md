# `:core-models` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The **shared data models** — the types the app, its server and its tests all pass around. A real
Gradle module of this build: `include(":core-models")`, `implementation(projects.coreModels)`.

**The package is `org.churchpresenter.core.models`**, and this module is the exception to the
"keeps the package it always had" line every other module's notes carry. It held
`org.churchpresenter.app.churchpresenter.models` — a name that repeats itself and claims `app`
about a module the app depends on — alongside a second root, `core.models.songs`, left over from
the Song Library extraction. Both were folded into one proper reverse-domain root.

That rename was only safe because the serial names are pinned (see **Rules**): the on-disk format
no longer follows the package, so these classes can be moved and regrouped without invalidating a
single saved file.

## What lives here, and what cannot

**Every model sits in a feature subpackage of `…churchpresenter.models`** — nothing is left at the
root. A new model goes in the subpackage for the feature it belongs to; add a subpackage rather
than dropping a file at the root.

| Package | Owns |
|---|---|
| `schedule/` | `ScheduleItem.kt` — the sealed hierarchy, every kind of service-plan entry, plus `websiteDisplayText`; and `TimerModes.kt`, the countdown modes `ScheduleItem` reads |
| `songs/` | The song itself and the `.song` format — `SongItem`, `SongFileParser`, `SongLibrary`, `SongGrid`, `SongEdits`, `SongFileName` — plus `LyricSection` (a verse/chorus section) and `SongTuning` |
| `scene/` | `SceneModels.kt` — `SceneSource` (the sealed canvas-layer hierarchy), `Scene`, `SourceTransform`, `PathPoint` |
| `bible/` | `SelectedVerse.kt` — one selected Bible verse |
| `qa/` | `Question.kt` — `Question`, `QuestionStatus`, the wire types `QuestionDto`/`SubmitQuestionRequest`, and `Question.toDto()` |
| `companion/` | `CompanionSurfaceSlot.kt`, `CompanionSurfacePlacement.kt` — where a Companion surface is docked |
| `shortcuts/` | `KeyChord.kt` — a keyboard binding, serialized into settings |
| `presentation/` | `PresentationLoadError.kt`, `AnimationType.kt` |
| `statistics/` | What was sung and read: the two stores on disk (`DisplayStatistics`, `PlayEventLog` and their entry/event types), the computed report rows (`SongSummary`, `VerseSummary`, `ActivityPoint`), the identity keys (`SongKey`, `VerseKey`) and the reporting period (`StatisticsPeriod`, `DateRange`). One type per file. The logic over them is `:statistics` |

The songs package was `core.models.songs` — a second package root left over from the Song Library
extraction — until it was folded in here. **One package root: `org.churchpresenter.app.churchpresenter`.**

**Three models stayed in `:composeApp` because they cannot move**: `ShortcutAction` (60+ generated
`StringResource` references and `tabs.Tabs`), `CompanionButtonState` (`ImageBitmap`) and
`CompanionConnectionUiState`. Don't try again without removing those dependencies first.

`TimerModes` lives here because `ScheduleItem` needs it; `utils.Constants.TIMER_MODE_*` are
aliases of it, so existing call sites are unchanged. `Constants` itself now lives in `:settings`,
which depends on this module for exactly that alias — the composables and AWT screen-device
helpers that used to share its file stayed behind in `:composeApp`.

## Rules

- **The sealed hierarchies' serial names are PINNED, and must stay pinned.** `ScheduleItem` and
  `SceneSource` are polymorphic, so kotlinx.serialization writes a discriminator into every saved
  file — `"type":"org.churchpresenter.app.churchpresenter.models.ScheduleItem.SongItem"` — and by
  default that string is the class's fully-qualified name. Every subclass of both now carries an
  explicit `@SerialName` holding exactly that historical string, which is what lets a model be
  moved between subpackages at all: without it, regrouping the packages silently invalidates every
  saved schedule and scene on every user's machine, and the Companion/instance-link wire format
  with them. **Never delete one, never "tidy" one to a shorter name, and give any new subclass the
  same treatment.**

  The long form names a package that no longer exists, which reads like a mistake and is not one —
  it is a *stable identifier*, and the only thing that keeps a file readable across versions. It
  was shortened to `"song"`/`"color"` once, on 2026-08-21, and reverted the same day: the pins are
  compatible in **both** directions (an older build computes the same default name, so files move
  freely between versions), and `CompanionServerScheduleMappingTest`'s `a body in the legacy
  sealed-class format is still accepted` shows the string is on the wire too — an instance-link
  peer or Companion client on an older version sends it. Shortening costs all of that and buys
  nothing but a tidier literal. `ScheduleItemSerializationTest`/`SceneModelsSerializationTest` assert the exact
  strings and are what catches a slip.
- **`ModelInvariantsTest` finds models by walking the package tree from its own package**, so it
  covers every subpackage automatically and a new model is tested without editing it. It is
  anchored on the test class rather than on a model class for exactly that reason — anchored on
  `KeyChord` it followed that class into `shortcuts/` and quietly discovered one model instead of
  thirty.
- **Anything `:composeApp` calls has to be public here.** `websiteDisplayText` was `internal` and
  is not any more.
- **No Compose runtime, no composables, no Compose compiler plugin.** The module depends on
  `compose.ui` for exactly one reason: `KeyChord` is a keyboard binding and speaks Compose's
  `Key`/`KeyEvent`/`KeyShortcut`. If a change here needs the Compose compiler, the type belongs in
  `:composeApp`, not here.
- Nothing in this module may read a setting, touch the filesystem or reach the network. It is
  types and their serialization.
- Wire types (`QuestionDto`, anything the Companion server or Instance Link sends) are a
  **contract with other builds and other running instances** — changing a field name or its
  `@SerialName` breaks remote clients that are already deployed. `QuestionWireFormatTest` and
  `ScheduleItemSerializationTest` exist to make that break loud.

## Test fixtures

The module applies `java-test-fixtures` and publishes
`testFixtures/kotlin/…/utils/KeyEventFixture.kt` — the way to build a Compose `KeyEvent` in a test
without a window. Consume it as `testImplementation(testFixtures(projects.coreModels))`; put a new
shared fixture here rather than copying one into a second suite.

## Commands

```bash
./gradlew :core-models:test
./gradlew :core-models:detekt                            # gate — no baseline, must be clean
./gradlew :core-models:jacocoTestCoverageVerification
```

All three run in CI, gated on this directory or the shared build files changing.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, no baseline, main and test both in scope.
- **Coverage**: the root build's default six counters at 85%, all of them — **no**
  `coverageFloors`, **no** `coverageExcludes`. `ModelInvariantsTest` uses `kotlin-reflect` to sweep
  the hierarchy, which is what keeps a newly added model from silently arriving uncovered.

## Dependencies

`compose.ui` (for `KeyChord` only) and `kotlinx-serialization-json`. Nothing of the app's own,
ever — this module is the bottom of the dependency graph.
