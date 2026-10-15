# `:canvas` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Canvas** tab: `SceneViewModel` and the scene editor — `CanvasTab` and its panels, the size
menu and layouts (`CanvasSizeMenu`, `CanvasPlacement`), the add-source menu, and the source property
editors (`SourcePropertiesPanel` and the per-kind editors). What a scene is drawn *from* — the
canvas, the renderer, the capture sources and `ScenePresenter` — is `:canvas-engine`, which this
module takes with `api`.

A feature-layer module (root AGENT.md, "Layers"): `include(":canvas")`. Its consumer is
`:composeApp`, which uses the tab and `SceneViewModel`. It takes `:canvas-engine`, `:shared-ui`,
`:strings`, `:icons`, `:core-models`, `:settings`, `:theme`, `:ndi`, `:omt` and `:bible`, and
nothing of `:composeApp`'s.

## Seams to the app

- **`onPresentScene`**: the tab's Go Live. The app sets the active scene, switches the output to
  the canvas and shows the output window.
- **`cameraHost`**: the machine the camera panel describes. The app passes none, so the panel reads
  `CameraDeviceCatalog` and probes ffmpeg itself; a test pins one.
- The Bible source pages through `:bible` directly (`BibleSourceBrowser`), and lists translations
  with its own `bibleFilesInDirectory`, a copy of the app's `FileManager.getBibleFilesInDirectory`.

## Layout

- **`SceneViewModel`** keeps its scenes and the scene-level actions; the source edits are
  extensions in `SceneViewModelSources.kt` — outside the module they need an import
  (`import org.churchpresenter.canvas.addSource`).
- **The tab** is `CanvasTab.kt` over `CanvasTabScope.kt`, with `CanvasLeftPanel.kt` (scenes and
  layers), `CanvasCenterPanel.kt` (the canvas, its toolbar and the properties column) and
  `CanvasAddSourceMenu.kt`.
- **The editors** are split by kind of source: `SceneMediaEditors.kt`, `SceneGraphicEditors.kt`,
  `SceneDeviceEditors.kt`, `SceneOmtEditor.kt` and `BibleSourceProperties.kt`.

## Coverage floor

The shared 85% on all six counters; no `coverageFloors` entry. Complexity and branches clear it by
about a point, so a new branch needs a test with it. What is left uncovered is mostly not untested
behavior: Compose's remembered-lambda checks on the editors' handlers (the "nothing changed but the
slot is empty" exit, which no recomposition reaches), the text editor's pop-out window (a real
`DialogWindow`, which a headless test cannot open), the style rows' shadow handler (the canvas offers
no shadow button), and defensive exits a picker cannot reach.

## Package

**`org.churchpresenter.canvas`**; the screenshot suites are in `org.churchpresenter.canvas.screenshot`.
The tab harness (`canvasTab`, `CanvasLabel`) and the source-panel harness are in `src/test`.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suites are `screenshot/CanvasTabScreenshotTest`
  and `screenshot/CanvasOmtSourceScreenshotTest`; their images are under `canvas/screenshots/`.
  The website's `previewApp/canvas_*` export is shot by the app.
- What needs the app stays there: the OBS scene wiring, the off-screen scene pump, and
  `DeckLinkComposeOutput`. What the outputs and the settings pages draw with is `:canvas-engine`'s,
  never this module's.

## Commands

```bash
./gradlew :canvas:test :canvas:detekt
./gradlew :canvas:jacocoTestCoverageVerification
./gradlew :canvas:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :canvas:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
