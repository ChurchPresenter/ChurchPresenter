# `:profiles` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The Profiles settings pages and what they are built from:
- `ProfilesSettingsTab` and its editor: the section list, every page (General, Bible, Songs,
  Background, Captions, Subtitles, Q&A, Dictionary, Stage Monitor, Outputs), the preview column,
  the Adjust handles and the merge card;
- the Background tab (`BackgroundSettingsTab`, its pickers, the quick-background rail) with
  `BackgroundSettingsViewModel`, which only that tab owns;
- the song background panel and the local stock library (`SongBackgroundPanel`, `LocalLibraryDialog`)
  and the bundled stock backgrounds they offer, in `src/main/composeResources/files/backgrounds`;
- the settings card kit the app's own pages use (`SettingsCard`, `SettingsCardBadge`,
  `LocalApplySettings`, `LocalSettingsDevMode`);
- the settings row kit every settings page uses (`SettingsGroup`, `SettingsRowControls`, `RowOption`,
  `SettingsDetail`), and the small pickers beside it (`PreviewOutputPicker`, `TvScreenBox`,
  `SliderNumberField`, `ScanningRow`);
- `FileManager`, which the Bible page lists translations with.

A real Gradle module of this build: `include(":profiles")`, `implementation(projects.profiles)`. Its
consumers are `:composeApp` and `:live-output`. The app keeps the settings pages that need app
services — Projection, Server, System, Companion — and the Options dialog that hosts every page.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`, `:diagnostics`,
`:presenter`, `:canvas`, `:media`, `:slides`, `:bible`, `:bible-tab`, `:lottieGenerator`, `:atem`,
`:stt`, `:dictionary` and `:qa`, and nothing of `:composeApp`'s.

## Seams to the app

- **`LocalDevelopmentBuild`**: whether this is a development build, which decides whether the
  preview-output picker lists the dev windows `main.kt` opens. The app provides it from its
  `BuildConfig` at the root of its composition; unprovided, it answers as an unpackaged run does,
  which is what every test is.

## Package

**`org.churchpresenter.profiles`**; the screenshot suites are in
`org.churchpresenter.profiles.screenshot`. The module's own `Res` (the bundled backgrounds) is
`org.churchpresenter.profiles.generated.resources`, internal.

## Test fixtures

`src/testFixtures` holds the colour-field helpers (`ColorPickerFieldTestSupport`), the Lottie folder
helpers (`LottieFolderTestSupport`) and the settings control locators (`SettingsTabControlLocators`),
which `:composeApp`'s dialog and settings suites use too (`testFixtures(projects.profiles)`).

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suites are `screenshot/ProfilesTabScreenshotTest`,
  `BackgroundSettingsTabScreenshotTest`, `BackgroundMediaDialogsScreenshotTest`,
  `SongBackgroundPanelScreenshotTest` and `LottieBandScreenshotTest`; their images are under
  `profiles/screenshots/`.

## Coverage floor

Complexity **84%**; the other five counters keep the shared 85%. What is left is mostly not untested
behavior: the stock photo, local library and Lottie generator dialogs open real windows a headless
test cannot, the camera and DeckLink format pickers and the ATEM upload need real hardware, and
Compose's per-value change checks on the rows' handlers. Raise the floor as any of that becomes
reachable; never lower it without asking.

## Commands

```bash
./gradlew :profiles:test :profiles:detekt
./gradlew :profiles:jacocoTestCoverageVerification
./gradlew :profiles:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :profiles:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
