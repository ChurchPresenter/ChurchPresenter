# `:canvas` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Canvas** tab and everything a scene is drawn from:
- `SceneViewModel` and the scene editor (`CanvasTab` and its panels, `SceneCanvas`);
- the source renderer (`SceneSourceRenderer`) and the source property editors;
- the capture sources the renderer draws: cameras (enumeration, formats, diagnostics, the ffmpeg
  frame cache), screen and window capture, the headless-browser source, NDI and OMT input,
  DeckLink input;
- `ScenePresenter`, which the app's outputs draw a scene with.

A real Gradle module of this build: `include(":canvas")`, `implementation(projects.canvas)`. Its
consumers are `:composeApp` and `:live-output`. Besides the tab the app uses `SceneViewModel`,
`ScenePresenter`, the camera background (`CameraBackground`, `CameraDeviceCatalog`, the camera
picker parts), the video cache behind looping backgrounds, `DeckLinkManager` and `liveMerges`, and
`PreviewShape`.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`,
`:diagnostics`, `:ndi`, `:omt`, `:media`, `:slides`, `:bible` and `:bible-tab`, and nothing of
`:composeApp`'s.

## Seams to the app

- **`onPresentScene`**: the tab's Go Live. The app sets the active scene, switches the output to
  the canvas and shows the output window.
- **`NetworkInputs`**: the app's NDI runtime and OMT library, as the canvas receives from them. The
  app owns and loads both (it sends its own outputs over them) and installs `AppNetworkInputs`
  (`:live-output`) once at startup, first thing in `main`. Until then every call answers as an
  unloaded library.
- The Bible source lists translations with its own `bibleFilesInDirectory`, a copy of the app's
  `FileManager.getBibleFilesInDirectory` kept here so the move did not touch `:bible` — every UI
  module depends on it through `:shared-ui`, so a change there reruns nearly every suite in CI.

## Layout

- **`SceneViewModel`** keeps its scenes and the scene-level actions; the source edits are
  extensions in `SceneViewModelSources.kt` — outside the module they need an import
  (`import org.churchpresenter.canvas.addSource`).
- **The browser source** is three objects: `SharedBrowserFrameCache` (the shared frames, one per
  source), `BrowserProcesses` (finding the browser, its port, stopping it) and `CdpPages` (the
  page over DevTools).
- **The renderer** is split by kind of source: `SceneSourceRenderer.kt`, `SceneTextSources.kt`,
  `SceneClockSource.kt`, `SceneDeviceSources.kt`, and `WindowBounds.kt` for window capture.

## Coverage floor

Branches **81%** and complexity **77%**; the other four counters keep the shared 85%. What is left is
mostly not untested behavior: building the libvlc player, killing zombie headless browsers (which on a
developer's machine would kill their real ones), the coroutine plumbing around the ffmpeg pipe, the
`minOf`/`maxOf` empty-list exits in shape math, and Compose's per-value change checks on click
handlers. Raise the floors as any of that becomes reachable; never lower them without asking.

## Package

**`org.churchpresenter.canvas`**; the screenshot suites are in `org.churchpresenter.canvas.screenshot`.
The tab harness (`canvasTab`, `CanvasLabel`) and the source-panel harness are in `src/test`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suites are `screenshot/CanvasTabScreenshotTest`
  and `screenshot/CanvasOmtSourceScreenshotTest`; their images are under `canvas/screenshots/`.
  The website's `previewApp/canvas_*` export is shot by the app.
- What needs the app stays there: the song backgrounds' camera picker and their tests, the OBS
  scene wiring, the off-screen scene pump, and `DeckLinkComposeOutput`.

## Commands

```bash
./gradlew :canvas:test :canvas:detekt
./gradlew :canvas:jacocoTestCoverageVerification
./gradlew :canvas:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :canvas:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
