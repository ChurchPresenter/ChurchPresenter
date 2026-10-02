# `:media` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Media** tab and the playback under it: the tab itself, `MediaViewModel`, the VLC players
(`VideoPlayer`, `SoftwareVideoPlayer`, the shared frame buffer and the VLC availability checks), the
`EmbeddedVideoDecoder` a presentation's video layers play through, `MediaPresenter` and the subtitle
overlay, the subtitle parser, the media settings tab, the recent media files and the Pexels/Pixabay
stock search. A real Gradle module of this build — `include(":media")`,
`implementation(projects.media)`. `:composeApp` is its only consumer.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme` and
`:diagnostics`, plus vlcj and the Ktor client — and nothing of `:composeApp`'s.

## What stays in the app

- **Who plays what where** — the output windows, the live preview, the stage monitor, scenes and
  `PresentationPlayer` use `VideoPlayer`/`EmbeddedVideoDecoder`/`MediaPresenter` from here.
- **The remote-control side** — `MediaRemoteWiring` and the media routes, which reach the server.
- **The preview-output picker** — it reads the app's outputs; the tab draws it through its
  `previewOutputPicker` slot, which the app supplies.
- **JavaFX start-up** (`JavaFxInit.kt`) — it serves the website view, not media.

## The one interface

**`MediaOutput`** is everything the tab needs from the live output: presenting mode, whether the
presenter window is up, and the calls that put media on screen or clear it. The app's
`PresenterMediaOutput` implements it by passing every call to `PresenterManager` (reached as
`presenterManager.mediaOutput`); the tab takes it, never the manager. Tests use `FakeMediaOutput`.
A new need from the output is a new member here, not a reference to the app.

## Package

**`org.churchpresenter.media`**, with `.tabs`, `.viewmodel`, `.presenter`, `.composables`, `.data`,
`.dialogs`, `.subtitles` and `.utils` — the same split the code had in the app.

## Rules

- `internal` stops at the module edge: what `:composeApp` calls is public, everything else is not.
- **Tests and screenshots live here.** Unit tests beside the code in `src/test/kotlin`; the
  `…ScreenshotTest` suites in `.screenshot` with their committed images in `screenshots/<section>/`.
  Shared test helpers come from `:shared-ui`'s test fixtures, never a local copy.
- **`user.home` is the module's own** (`build/test-home`): the recent media files write under it.
- The tab takes `vlcAvailable`/`vlcArchMismatch`/`vlcLoadFailed` and `LocalMediaVlcPlayers` so a
  test never depends on whether the machine running it has VLC installed.

## Commands

```bash
./gradlew :media:test :media:detekt
./gradlew :media:jacocoTestCoverageVerification
./gradlew :media:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
