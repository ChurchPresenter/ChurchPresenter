# `:live-output` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

What is on air, and everything that puts it on a screen or a wire:
- `PresenterManager` and the live state it is split into (`LiveSongs`, `LiveBible`, `LiveSlides`,
  `LivePictures`, `LiveAnnouncements`, `LiveLowerThird`, `LiveOverlays`, `LiveScreens`, `LiveWeb`),
  `PreviewBus`, `OutputLocks`, `legacyProgram` and the tabs' output adapters (`Presenter*Output`);
- the output windows' content (`PresenterScreen`, `PresenterOutputContent`, `PresenterModeContent`,
  `OutputLayers`, `CueTextContent`/`CueMediaContent`) and the transitions that drive them
  (`PresenterTransitionEffects`, `TransitionLogic`, `AnnouncementLogic`, `LottieBandEffects`);
- the stage monitor (`StageMonitorScreen`, `StageZoneContent`, `StageZoneStyle`);
- the off-screen outputs on the shared `ComposeScenePump`: `BrowserSourceVideoRenderer`,
  `NdiVideoRenderer`/`NdiManager`, `OmtVideoRenderer`/`OmtManager`, `DeckLinkComposeOutput`, over
  `OffscreenOutputContent`; and `PresentationPlayer`, `LottieFrameStream`, `AppNetworkInputs`;
- `LottieOutputFrames`: the lower third's frames for outputs larger than its desktop frames. Each
  output holds its pixel size from `LowerThirdCue`; a larger size gets frames pre-rendered at the
  size it draws them, at most two sizes at once, closed when no output of that size is left.

A real Gradle module of this build: `include(":live-output")`, `implementation(projects.liveOutput)`.
`:composeApp` is its only consumer: the root screen, the window wiring (`PresenterWindows`,
`VirtualOutputs`), the remote and Instance Link wiring, and the live preview panel. It takes the
feature modules whose presenters it draws, and nothing of `:composeApp`'s.

## Seams to the app

- **`OffscreenOutputContext.appVersion`**: the version an OMT sender reports. The app passes
  `BuildConfig.APP_VERSION`, which this module cannot see.
- **`AppNetworkInputs`**: installed by `main` first thing, as `:canvas`'s `NetworkInputs`.

## Package

**`org.churchpresenter.liveoutput`**, one flat package.

## Rules

- `internal` stops at the module edge. What `:composeApp` calls is public; nothing else is.
- **Tests and screenshots live here.** The screenshot suite is `screenshot/StageMonitorScreenshotTest`;
  its images are under `live-output/screenshots/stageMonitor/`. `LiveOutputTestSupport.kt` holds this suite's copies of the app's
  `withSongsEverywhere`/`withBibleEverywhere` and `TestSingletons.latchSkikoHostOs` — copies, so the
  move did not touch `:shared-ui`.
- What needs the app stays there, with its tests: `PresenterWindows` and the window geometry
  (`MergeTileTest`, `PresenterOverflowTest`), the render benchmark and the soak test, and the
  full-screen screenshot suites (`PresenterFullScreenScreenshotTest` and its portrait twin), which
  draw the other tabs' presenters too.

## Commands

```bash
./gradlew :live-output:test :live-output:detekt
./gradlew :live-output:jacocoTestCoverageVerification
./gradlew :live-output:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :live-output:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
