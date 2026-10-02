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

**Not enforced yet.** The module measures 74% of instructions and 59% of branches against the shared
85%, and CI runs its tests but not its floor. The gap is the code that needs a real Chromium:
`CefManager`/`JcefInstall` installing and starting JCEF, `WebsitePresenter` embedding a live browser,
and `WebPreview`'s mouse, wheel and key forwarding into a `CefBrowser`. The tests that reach it —
the native calls split from the decisions, as the root `AGENT.md`'s Tests section describes — are
still to be written; the `Web coverage floor` step goes into `test.yml` with them, at the shared 85%.

## Commands

```bash
./gradlew :web:test :web:detekt
./gradlew :web:jacocoTestReport   # what the floor will check
```
