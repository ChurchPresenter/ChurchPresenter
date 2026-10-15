# `:lower-third` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Lower Third tab and the ATEM settings page**:
- the tab (`LowerThirdTab`), which lists the lottie presets in a folder, previews them, and puts them
  live or onto an ATEM;
- the ATEM page of the Options dialog (`AtemSettingsTab`, with `AtemSettingsFields`), which edits
  `AtemSettings` and tests the connection.

What they drive — the render cache, the off-screen renderer, `LowerThirdPresenter`, the key
sequencer, `LottieFonts` and the bundled fonts — is `:lower-third-engine`, which this module takes
with `api`. `LowerThirdLayout` stays in the app, since it draws the app's camera and video
backgrounds.

A feature-layer module (root AGENT.md, "Layers"): `include(":lower-third")`. Its consumer is
`:composeApp`. It takes `:lower-third-engine`, `:shared-ui`, `:strings`, `:icons`, `:core-models`,
`:settings`, `:theme`, `:diagnostics`, `:atem`, `:lottieGenerator` and `:presentation-engine`, and
nothing of `:composeApp`'s.

## Seams to the app

- **`previewOutput` and `outputPicker`**: which output the preview stands for, and the app's picker
  for it. `AppLowerThirdTab` works them out.
- **`confirmRemove`**: the "delete this preset?" question. A Swing dialog by default; tests answer it
  themselves, since a real one cannot open headless.
- **`queryAtemState` and `probeAtemReachable`**: the ATEM calls, which are UDP with a 5s timeout,
  so tests pass their own.

## Layout

- **The tab** builds a remembered, `@Stable` `LowerThirdTabScope` from three holders:
  - `LowerThirdTabInputs`: what the app hands it;
  - `LowerThirdTabState`: what the composition holds;
  - `LowerThirdWindow`: the window.

  Scope properties forward with plain getters and setters, never `by x::y`.
- **The ATEM settings page** (`AtemSettingsTab.kt`, `AtemSettingsFields.kt`): the connection card
  with its Test Connection probe, the lower-third and background upload cards, the key fields.
  `OptionsDialog` in the app draws it.

## Package

**`org.churchpresenter.lowerthird`** (`.render` and `.presenter` are `:lower-third-engine`'s). The test helpers (`lowerThirdTab`,
`LowerThirdLabel`, the folder fixtures and the finders) are in `src/testFixtures`. They are public,
so the app's screenshot suite can use them. That suite passes the app's real preview output and
picker through `previewFor` and `pickerFor`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here**, beside the code: `LowerThirdTabScreenshotTest`, `AtemSettingsTabScreenshotTest`, images under
  `lower-third/screenshots/`.
- The suite runs on JUnit 5, so a class-level hook is `@BeforeAll`/`@AfterAll`. JUnit 4's
  `@BeforeClass` is silently never run here.
- A test that swaps `user.home` loads a skia class first, so skiko unpacks into the suite's own home
  (see `LowerThirdAtemUploadTest`).

## Commands

```bash
./gradlew :lower-third:test :lower-third:detekt
./gradlew :lower-third:jacocoTestCoverageVerification
./gradlew :lower-third:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :lower-third:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
