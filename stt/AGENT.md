# `:stt` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The STT tab** (`STTTab`), with its connection bar, status line and live preview in
`STTTabParts.kt`, and `captionSettingsOnScreen`, the caption look the preview draws with.

- `STTManager`, the caption server's client the tab drives, is `:stt-engine`, which this module
  takes with `api`; the Bible tab and the outputs read the same manager from there.
- `STTPresenter`, the captions on an output, and the caption text, look and reveal pace it is built
  from (`Caption*.kt`, `SttDripFeed.kt`, `SttRevealPace.kt`) are `:presenter`'s, in this module's
  `presenter` package.

A feature-layer module (root AGENT.md, "Layers"): `include(":stt")`. Its consumer is `:composeApp`.
It takes `:stt-engine`, `:shared-ui`, `:core-models`, `:strings`, `:settings` and `:theme`, and
nothing of `:composeApp`'s.

## Seams to the app

- **`captionsLive`**: the tab needs only to know whether captions are on air, so it takes that as
  a `State<Boolean>`. `AppSTTTab` passes one derived from `presenterManager.isLive(Presenting.STT)`.
- **`settingsDialog`**: the caption settings dialog edits the output profiles the app owns, so the
  app draws it into this slot.

## Package

**`org.churchpresenter.stt`** (`.presenter` is `:presenter`'s). The test helpers (`sttTab`, `STTLabel`
and the finders) are in `src/testFixtures`, over `:stt-engine`'s feeders and `SILENT_STT_URL`. They
are public, so the module's own screenshot suites and `:composeApp`'s can use them.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here**, beside the code: `STTTabScreenshotTest`, `CaptionBackdropScreenshotTest` and
  `CaptionPresentationScreenshotTest`, images under
  `stt/screenshots/`.

## Commands

```bash
./gradlew :stt:test :stt:detekt
./gradlew :stt:jacocoTestCoverageVerification
./gradlew :stt:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :stt:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
