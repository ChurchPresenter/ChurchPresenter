# `:web` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

The **Web** tab (`WebTab` and its pieces `WebTabScope`, `WebToolbar`, `WebPreview`,
`WebBrowserInput`). The embedded Chromium it browses with — JCEF's install and start-up, the
embedded browser and `WebsitePresenter` — is `:web-engine`, which this module takes with `api`.

A feature-layer module (root AGENT.md, "Layers"): `include(":web")`. Its consumer is
`:composeApp`. It takes `:web-engine`, `:output-api`, `:shared-ui`, `:strings`, `:icons`,
`:core-models`, `:settings` and `:theme`, and nothing of `:composeApp`'s.

## What stays in the app

- **The preview output** — which output the preview stands in for, and its picker. `AppWebTab` reads
  the shape from `rememberPreviewOutput` and draws `PreviewOutputPicker` into the tab's `outputPicker`
  slot; the tab only takes `previewAspectRatio` and the slot.
- **`PresenterManager`** — reached only through `WebOutput`, below.

## The one interface

**`WebOutput`** (`:output-api`, in this package) is everything the tab needs from the live output: presenting mode, the URL and title
on screen, the live browser and its snapshot. `:live-output`'s `PresenterWebOutput` implements it by
passing every call to `PresenterManager` (reached as `presenterManager.webOutput`). Tests use
`FakeWebOutput`, which only remembers what it was told. A new need from the output is a new member
here, not a reference to the app.

## Package

**`org.churchpresenter.web`**, with `.tabs` (`.presenter` is `:web-engine`'s).

## Rules

- `internal` stops at the module edge: what `:composeApp` calls is public, everything else is not.
- **Tests live here**, beside the code in `src/test/kotlin`. The tab's screenshots stay in the app
  (`WebTabScreenshotTest`), because they are shot through `AppWebTab` with the app's preview picker.
- **`user.home` is the module's own** (`build/test-home`), as the engine's is.

## Coverage floor

**The shared 85% on all six counters, enforced in CI**, with no overrides and no excludes. What
needs a real Chromium is `:web-engine`'s, behind its seams; what the tab decides sits in front:

- **`BrowserInput`** is all the mirrored preview needs of the live browser; `CefBrowserInput` is the
  reflective sender behind it.
- **The toolbar's handlers** are `WebTabScope` extensions (`WebTabNavigation.kt`,
  `WebTabPageActions.kt`), tested on a scope directly; the composables only call them.

## Commands

```bash
./gradlew :web:test :web:detekt
./gradlew :web:jacocoTestReport :web:jacocoTestCoverageVerification   # the report, and the floor CI checks
```
