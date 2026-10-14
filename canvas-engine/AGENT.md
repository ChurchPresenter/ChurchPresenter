# `:canvas-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**What a scene is drawn from, without the tab**:
- the canvas and the renderer: `SceneCanvas`, `SceneSourceRenderer` (with `SceneTextSources`,
  `SceneClockSource`, `SceneDeviceSources`), `ScenePresenter`, which the outputs draw a scene with,
  `PreviewShape`, and the timers behind clock sources (`TimerStateManager`);
- the camera kit: enumeration (`CameraEnumeration`, `CameraDeviceCatalog`, `MacCameras`,
  `WindowsCameras`, `AvfoundationDevices`), formats, the ffmpeg frame cache
  (`SharedCameraFrameCache`, `CameraCaptureLoop`), the failure text, hints and reports, the
  picker's decisions (`CameraSelection`) and `CameraPrivacyHint`, which the tab's camera panel and
  the background camera picker share;
- screen and window capture (`ScreenCaptureCache`, `WindowBounds`, the Win32 and X11 helpers);
- the headless-browser source (`SharedBrowserFrameCache`, `BrowserProcesses`, `CdpPages`);
- NDI and OMT in (`NdiFrameCache`, `OmtFrameCache`, `ReceivedFrameCache`, `NetworkInputs`);
- DeckLink in (`DeckLinkManager`, `DeckLinkCapture`, `DeckLinkMerges`) and its JNI shim,
  `org.churchpresenter.app.churchpresenter.composables.DeckLinkManager`, whose name the native
  library binds to;
- the video cache behind looping backgrounds (`SceneVideoCache`) and `ResourceCensus`.

An engine-layer module (root AGENT.md, "Layers"): `:canvas` (the tab, with `api`), `:presenter`,
`:live-output`, `:profiles` and `:telemetry` use it. It takes `:shared-ui`, `:strings`, `:theme`,
`:core-models`, `:settings`, `:diagnostics`, `:ndi`, `:omt`, `:media-engine` and `:slides-engine`.

**The package is `:canvas`' own** (`org.churchpresenter.canvas`): the files moved here unchanged, so
no import moved with them.

## Seams

- **`NetworkInputs`**: the app's NDI runtime and OMT library, as the canvas receives from them. The
  app installs `AppNetworkInputs` (`:live-output`) first thing in `main`; until then every call
  answers as an unloaded library.
- **The browser launch is the one step a test replaces**: `SharedBrowserFrameCache.acquireWith`
  takes it, and `acquire` passes the real one. `holdLiveBrowserSource` (test fixtures) holds a
  source live with no browser behind it, for suites that read a running source.

## Tests

The suites of everything above moved with it; the tab's suites stay in `:canvas`. The test
fixtures are what both share: `TestSingletons` and `withOsName` (an `os.name` swap that latches
skiko first), `clearSceneTimers`, `recolor`, `shellCommand`, the font-search helpers and
`holdLiveBrowserSource`.

`user.home` is the module's own (`build/test-home`): `DeckLinkManager` and the capture caches
write under it.

## Coverage floor

The shared 85% on all six counters; no `coverageFloors` entry. What is left uncovered is mostly not
untested behavior: building the libvlc player, the headless browser's own process and DevTools
traffic, the coroutine plumbing around the ffmpeg pipe, the native DeckLink/X11/Win32 calls, the
`minOf`/`maxOf` empty-list exits in shape math, and Compose's remembered-lambda checks.

## Commands

```bash
./gradlew :canvas-engine:test :canvas-engine:detekt
./gradlew :canvas-engine:jacocoTestCoverageVerification
```
