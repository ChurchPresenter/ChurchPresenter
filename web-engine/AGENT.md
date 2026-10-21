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

**Off-screen rendering** (`offscreen/`, on unless *Smooth web output* is turned off in System
settings) renders the live page once in a windowless Chromium and draws the same frames on every
output, the previews and the Web tab: `OffscreenPage` (one page, built off composition),
`OffscreenFrames` (the copy out of Chromium's buffer and the level pyramid for small surfaces),
`OffscreenWebView`/`OffscreenWebControl` (drawing, and the operator's mouse and keys), the per-platform
key fixes (`PageKeys`, `LinuxKeys`, `LinuxClipboard`, Windows' `SentMessagePump`) and the Sentry
reporting (`OffscreenReporter`). Chromium reads the windowless flag once, at start-up
(`CefManager.init(offscreen)`), so the setting needs a restart.
- `src/main/java/org/cef/browser/` holds `CefBrowserHeadless` and `HeadlessSink`, in JCEF's own
  package: they extend the package-private `CefBrowser_N`, which works only while JCEF runs from the
  class path. Re-check them on every JCEF upgrade.
- On macOS a windowed browser in a windowless engine crashes Chromium as it is shown, so with the
  engine off screen `EmbeddedWebView` is an `OwnOffscreenPage` too.
- `JcefShutdownHook` takes out jcefmaven's shutdown hook and `QuitThroughApp` takes Cmd+Q: both would
  dispose JCEF on the event thread, which crashes on the way out.

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
`build()`, `CefManager.init`'s install lambda, and the `SwingPanel` itself; for off-screen rendering,
`SentMessagePump`'s Windows timer, the pointer forwarding's native-event branch (a test's pointer
events carry no AWT event), and a Linux clipboard message's write to the system clipboard. Keep a
new native call behind one of the seams above rather than in a composable.

## Rules

- **`user.home` is the module's own** (`build/test-home`): `CefManager` installs JCEF under it.
- **The suite runs with `--add-opens=java.base/java.lang`**, as the app does, so `JcefShutdownHook`
  reads the JDK's real hook table.
- **Test fixtures:** `stubOffscreenPage()` and `paintStub()` give `:live-output`'s and `:web`'s suites
  a real page on a stub client; JCEF's native calls are no-ops without its library.

## Commands

```bash
./gradlew :web-engine:test :web-engine:detekt
./gradlew :web-engine:jacocoTestCoverageVerification
```
