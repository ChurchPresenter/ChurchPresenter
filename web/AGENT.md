# `:web` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Web** tab and the embedded Chromium it browses with: the tab itself (`WebTab` and its pieces
`WebTabScope`, `WebToolbar`, `WebPreview`), and `WebsitePresenter.kt` — JCEF's install and start-up
(`JcefInstall`, `CefManager`), the embedded browser (`EmbeddedWebView`, `WebNavController`) and the
website output an output window draws. A real Gradle module of this build — `include(":web")`,
`implementation(projects.web)`. `:composeApp` is its only consumer.

It takes `:shared-ui`, `:strings`, `:icons`, `:core-models`, `:settings`, `:theme` and `:diagnostics`
— and nothing of `:composeApp`'s. JCEF itself is an `api` dependency, because a `CefBrowser` crosses
`WebOutput`; the platform's Chromium binaries stay the app's runtime dependency.

## What stays in the app

- **The preview output** — which output the preview stands in for, and its picker. `AppWebTab` reads
  the shape from `rememberPreviewOutput` and draws `PreviewOutputPicker` into the tab's `outputPicker`
  slot; the tab only takes `previewAspectRatio` and the slot.
- **`PresenterManager`** — reached only through `WebOutput`, below.

## The one interface

**`WebOutput`** is everything the tab needs from the live output: presenting mode, the URL and title
on screen, the live browser and its snapshot. The app's `PresenterWebOutput` implements it by passing
every call to `PresenterManager` (reached as `presenterManager.webOutput`). Tests use `FakeWebOutput`,
which only remembers what it was told. A new need from the output is a new member here, not a
reference to the app.

## Package

**`org.churchpresenter.web`**, with `.tabs` and `.presenter` — the split the code had in the app.

## Rules

- `internal` stops at the module edge: what `:composeApp` calls is public, everything else is not.
- **Tests live here**, beside the code in `src/test/kotlin`. The tab's screenshots stay in the app
  (`WebTabScreenshotTest`), because they are shot through `AppWebTab` with the app's preview picker.
- **`user.home` is the module's own** (`build/test-home`): `CefManager` installs JCEF under it.

## Coverage floor

**The shared 85% on all six counters, enforced in CI** (`Web coverage floor`), with no overrides and
no excludes. What needs a real Chromium is kept to a thin edge behind a seam, and everything that
decides sits in front of it:

- **`CefEngine`** holds the engine's state and decisions — whether to install, what an outcome
  leaves, recovering from a dead client. `CefManager` keeps its API as getters over the one the app
  runs on; a test builds its own, so the Web tab's defaults never see a test's engine.
- **`EmbeddedBrowser`** is `EmbeddedWebView`'s body, taking the client source, the screen capture
  and the `SwingPanel` as parameters. Its CEF handlers are the named classes in
  `EmbeddedWebViewHandlers.kt`.
- **`BrowserInput`** is all the mirrored preview needs of the live browser; `CefBrowserInput` is the
  reflective sender behind it.
- **The toolbar's handlers** are `WebTabScope` extensions (`WebTabNavigation.kt`,
  `WebTabPageActions.kt`), tested on a scope directly; the composables only call them.

Left uncovered, and why: the reflective `patchJcefModuleAccess` paths, `pactl` audio routing (Linux
only), `buildCefApp`'s `build()`, `CefManager.init`'s install lambda, and the `SwingPanel` itself.
Keep a new native call behind one of the seams above rather than in a composable.

## Commands

```bash
./gradlew :web:test :web:detekt
./gradlew :web:jacocoTestReport :web:jacocoTestCoverageVerification   # the report, and the floor CI checks
```
