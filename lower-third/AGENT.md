# `:lower-third` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**Lower thirds**:
- the tab (`LowerThirdTab`), which lists the lottie presets in a folder, previews them, and puts them
  live or onto an ATEM;
- what an ATEM upload needs: the render cache, the off-screen renderer and the key sequencer;
- `LowerThirdPresenter` (`presenter/`), the animation on an output. It takes the frame to draw as
  a plain `ImageBitmap`; `:live-output`'s `LottieFrameStream` owns the native bitmap behind it.
  `LowerThirdLayout` stays in the app, since it draws the app's camera and video backgrounds;
- `LottieFonts` and the bundled fonts it reads, under `src/main/resources/fonts`. LottieGen's
  `FontRegistry` reads the same resources off the classpath;
- the ATEM page of the Options dialog (`AtemSettingsTab`, with `AtemSettingsFields`), which edits
  `AtemSettings` and tests the connection.

A real Gradle module of this build: `include(":lower-third")`,
`implementation(projects.lowerThird)`. Its consumers are `:composeApp` and `:live-output`. The
outputs (`:live-output`), the app's ATEM bridge and routes, and the Bible lottie band all use the
render cache, the sequencer and `LottieFonts` from here.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`, `:diagnostics`,
`:atem`, `:lottieGenerator` and `:presentation-engine`, and nothing of `:composeApp`'s.

## Seams to the app

- **`previewOutput` and `outputPicker`**: which output the preview stands for, and the app's picker
  for it. `AppLowerThirdTab` works them out.
- **`confirmRemove`**: the "delete this preset?" question. A Swing dialog by default; tests answer it
  themselves, since a real one cannot open headless.
- **`queryAtemState` and `probeAtemReachable`**: the ATEM calls, which are UDP with a 5s timeout,
  so tests pass their own.

## Layout

- **The render cache** (`render/`): `LottieRenderCache` prepares and reads cache entries. Its JSON
  and size policy is `LottieRenderSizes`, reached through the `LottieRenderPolicy` interface by
  delegation. Its files, keys and eviction are `LottieCacheFiles`, and its frame codec is `ArgbRle`.
- **The sequencer**: `LowerThirdSequencer.run` takes:
  - a `LowerThirdClip`: what runs;
  - a `LowerThirdKey`: which ATEM key takes it on air;
  - an `AtemKeyDriver`: by default the switcher's keepalive connection. A test passes its own to
    make a key fail without the client's 5s timeout.
- **`LottieRenderCache.ensureForFile`** returns its background job. Nothing in the app waits on it;
  a test does.
- **The tab** builds a remembered, `@Stable` `LowerThirdTabScope` from three holders:
  - `LowerThirdTabInputs`: what the app hands it;
  - `LowerThirdTabState`: what the composition holds;
  - `LowerThirdWindow`: the window.

  Scope properties forward with plain getters and setters, never `by x::y`.
- **The ATEM settings page** (`AtemSettingsTab.kt`, `AtemSettingsFields.kt`): the connection card
  with its Test Connection probe, the lower-third and background upload cards, the key fields.
  `OptionsDialog` in the app draws it.

## Package

**`org.churchpresenter.lowerthird`**, with `.render` and `.presenter`. The test helpers (`lowerThirdTab`,
`LowerThirdLabel`, the folder fixtures and the finders) are in `src/testFixtures`. They are public,
so the app's screenshot suite can use them. That suite passes the app's real preview output and
picker through `previewFor` and `pickerFor`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests live here**, beside the code. The tab's and the ATEM page's screenshots stay in the app
  (`LowerThirdTabScreenshotTest`, `AtemSettingsTabScreenshotTest`), where they were recorded.
- The suite runs on JUnit 5, so a class-level hook is `@BeforeAll`/`@AfterAll`. JUnit 4's
  `@BeforeClass` is silently never run here.
- A test that swaps `user.home` loads a skia class first, so skiko unpacks into the suite's own home
  (see `LowerThirdAtemUploadTest`).

## Commands

```bash
./gradlew :lower-third:test :lower-third:detekt
./gradlew :lower-third:jacocoTestCoverageVerification
```
