# `:slides` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Pictures** and **Presentation** tabs: the tabs themselves, their viewmodels, the presenters
that draw a picture or a deck on an output, picture decoding (HEIC included), the hidden-slide store
and the recent folders and files. A real Gradle module of this build — `include(":slides")`,
`implementation(projects.slides)`. `:composeApp` is its only consumer.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme`, `:diagnostics`
and `:presentation-engine` (as `api`: a caller passes `Deck`s) — and nothing of `:composeApp`'s.

## What stays in the app

- **`PresentationPlayer`** — the animated-deck runtime. `PresenterManager` owns it, and it drives
  VLC (`EmbeddedVideoDecoder`) and the Lottie stream, which belong to the app. It produces the
  [`PresentationFrame`] this module's presenter draws.
- **The remote-control side** — `PresentationRoutes`, `PresentationRemoteRoutes`,
  `PresentationStore`, `PictureLibrary` and `PresentationRemoteDialog`, which reach the server and the
  tunnel. The tab draws the dialog through its `remoteDialog` slot; the app supplies it.
- **VLC** — the tab's `vlcAvailable`/`vlcArchMismatch`/`vlcLoadFailed` are passed in by the app.

## The one interface

**`SlidesOutput`** is everything the tabs need from the live output: presenting mode, screen locks,
the live deck frame, and the calls that put a picture or a slide on screen — split into
`LiveOutput`, `PictureOutput` and `DeckOutput` so none outgrows detekt's function limit. The app's
`PresenterSlidesOutput` implements it by passing every call to `PresenterManager` (reached as
`presenterManager.slidesOutput`); the tabs and viewmodels take it, never the manager. Tests use
`FakeSlidesOutput`, which records what it was told. A new need from the output is a new member here,
not a reference to the app.

## Package

**`org.churchpresenter.slides`**, with `.tabs`, `.viewmodel`, `.presenter`, `.data` and `.utils` — the
same split the code had in the app.

## Rules

- `internal` stops at the module edge: what `:composeApp` calls is public, everything else is not.
- **Tests and screenshots live here.** Unit tests beside the code in `src/test/kotlin`; the
  `…ScreenshotTest` suites in `.screenshot` with their committed images in `screenshots/<section>/`.
  Shared test helpers come from `:shared-ui`'s test fixtures, never a local copy.
- **`user.home` is the module's own** (`build/test-home`): the recent files, hidden items and slide
  cache all write under it.
- The viewmodels switch to `Dispatchers.Main`, which on the desktop is `kotlinx-coroutines-swing` —
  without it every load dies in the background and a test only sees it time out.

## Coverage floor

Branches **79%** and complexity **75%**; the other four counters keep the shared 85%. The gap is not
untested behaviour: it is the coroutine plumbing the compiler generates around slide rendering (45
branches on one `finally` line), Compose's per-value change checks on click handlers and effects,
the shift-drag reorder in the picture grid (a test cannot hold Shift on a mouse event), and the native
file pickers. Raise the floors when any of that becomes reachable; never lower them further without
asking.

## Commands

```bash
./gradlew :slides:test :slides:detekt
./gradlew :slides:jacocoTestCoverageVerification
./gradlew :slides:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
