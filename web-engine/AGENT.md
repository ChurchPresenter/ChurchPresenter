# `:web-engine` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The embedded Chromium, without the Web tab** (`presenter/`): JCEF's install and start-up
(`JcefInstall`, `CefEngine`, `CefManager`), the embedded browser (`EmbeddedWebView`,
`WebNavController`, the CEF handlers in `EmbeddedWebViewHandlers.kt`) and `WebsitePresenter`, the
website an output window draws.

An engine-layer module (root AGENT.md, "Layers"): `:web` (the tab, with `api`), `:live-output` and
`:telemetry` use it, and `:composeApp` starts the engine (`CefManager.init`). It takes `:shared-ui`,
`:settings` and `:diagnostics`. JCEF itself is an `api` dependency, because a `CefBrowser` crosses
`WebOutput`; the platform's Chromium binaries stay the app's runtime dependency.

**The package is `:web`'s own** (`org.churchpresenter.web.presenter`): the files moved here
unchanged, so no import moved with them.

## Seams

- **`CefEngine`** holds the engine's state and decisions — whether to install, what an outcome
  leaves, recovering from a dead client. `CefManager` keeps its API as getters over the one the app
  runs on; a test builds its own, so the Web tab's defaults never see a test's engine.
- **`EmbeddedBrowser`** is `EmbeddedWebView`'s body, taking the client source, the screen capture
  and the `SwingPanel` as parameters.
- **`WebNavController`** takes its starting browser as a defaulted constructor parameter, so the
  tab's suites can hand it one; only the view sets it after that.

## Coverage floor

The shared 85% on all six counters, with no overrides and no excludes. Left uncovered, and why: the
reflective `patchJcefModuleAccess` paths, `pactl` audio routing (Linux only), `buildCefApp`'s
`build()`, `CefManager.init`'s install lambda, and the `SwingPanel` itself. Keep a new native call
behind one of the seams above rather than in a composable.

## Rules

- **`user.home` is the module's own** (`build/test-home`): `CefManager` installs JCEF under it.

## Commands

```bash
./gradlew :web-engine:test :web-engine:detekt
./gradlew :web-engine:jacocoTestCoverageVerification
```
