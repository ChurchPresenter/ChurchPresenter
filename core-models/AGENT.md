# `:core-models` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The **shared data models** — the types the app, its server and its tests all pass around — in the
package they always had (`org.churchpresenter.app.churchpresenter.models`), so no import in the app
changed when they moved. A real Gradle module of this build: `include(":core-models")`,
`implementation(projects.coreModels)`.

## What lives here, and what cannot

| File | Owns |
|---|---|
| `ScheduleItem.kt` | The `ScheduleItem` sealed hierarchy — every kind of service-plan entry — plus `websiteDisplayText` |
| `SceneModels.kt` | `SceneSource` (the sealed canvas-layer hierarchy), `SourceTransform`, `PathPoint` and friends |
| `Question.kt` | `Question`, `QuestionStatus`, and the Q&A wire types `QuestionDto`/`SubmitQuestionRequest` |
| `LyricSection.kt` | A song's verse/chorus section |
| `SelectedVerse.kt` | One selected Bible verse |
| `KeyChord.kt` | A keyboard binding, serialized into settings |
| `SongTuning.kt`, `AnimationType.kt`, `PresentationLoadError.kt` | Small value types and enums |
| `CompanionSurfaceSlot.kt`, `CompanionSurfacePlacement.kt` | Where a Companion surface is docked |
| `TimerModes.kt` | The countdown modes |

**Three models stayed in `:composeApp` because they cannot move**: `ShortcutAction` (60+ generated
`StringResource` references and `tabs.Tabs`), `CompanionButtonState` (`ImageBitmap`) and
`CompanionConnectionUiState`. Don't try again without removing those dependencies first.

`TimerModes` lives here because `ScheduleItem` needs it; `utils.Constants.TIMER_MODE_*` are
aliases of it, so existing call sites are unchanged. `Constants` itself now lives in `:settings`,
which depends on this module for exactly that alias — the composables and AWT screen-device
helpers that used to share its file stayed behind in `:composeApp`.

## Rules

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
