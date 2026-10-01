# `:shared-ui` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The composables and helpers that more than one feature uses — settings fields, sliders, segmented
buttons, the color and font pickers, the text outline and backdrop controls, tooltips, auto-fit,
screen bounds, keyboard-shortcut labels. A real Gradle module of this build —
`include(":shared-ui")`, `implementation(projects.sharedUi)`. `:composeApp` is its only consumer.

It takes `:strings` and `:icons` (as `api`, so a caller sees the same `Res`), `:core-models`,
`:settings`, `:theme`, `:song-chords` and `:bible` — and nothing of `:composeApp`'s.

## What belongs here

**Code that two or more features use and that depends on nothing in `:composeApp`.** A component
used by one feature belongs with that feature; a component that needs a ViewModel, a presenter or
the app shell stays in `:composeApp` until that tie is cut. Do not move something here to make it
reachable — move it here because it is shared.

## Package

**`org.churchpresenter.sharedui`**, with the same sub-packages the code had in the app:
`.composables`, `.utils`, `.models`. `models` also holds the two enums every layer names —
`Tabs` and `Presenting` (the live-content enum).

`utils/Constants.kt` holds top-level functions, so its file class is
`org.churchpresenter.sharedui.utils.ConstantsKt` — the `SceneViewModel` suites in `:composeApp`
stub it by that string.

## Rules

- **`internal` stops at the module edge.** What `:composeApp` calls is public; keep everything else
  `internal` or `private`. Widen a declaration only for a real caller, not for a test in another
  module — move that test here instead.
- **Tests live beside the code they test**, in `src/test/kotlin`. A test that also needs app code
  stays in `:composeApp`.
- **`user.home` is the module's own** (`build/test-home`, set in `build.gradle.kts`) — several
  classes here read and write under it, and a test must never touch the real `~/.churchpresenter`.
- **Screenshots of these components live here**: the `…ScreenshotTest` suites in
  `src/test/kotlin/…/screenshot`, their committed images in `screenshots/<section>/`. The harness
  they shoot through (`ScreenshotSupport`) is this module's test fixtures, shared with
  `:composeApp`'s suites. The root `AGENT.md` Screenshots rules apply unchanged.
- The tests are `kotlin.test` on the JUnit Platform; no JUnit 4 rules (`@get:Rule` does not run).

## Commands

```bash
./gradlew :shared-ui:test :shared-ui:detekt
./gradlew :shared-ui:jacocoTestCoverageVerification   # the six-counter floor from the root build
./gradlew :shared-ui:verifyRoborazziJvm --tests '*ScreenshotTest*'
```

CI runs the suite and the floor when `shared-ui/` or a module it depends on changes; detekt runs on
every change.
