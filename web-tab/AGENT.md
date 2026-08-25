# `:web-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Web tab and the website presenter** — the embedded Chromium browser the operator drives, its
bookmarks, zoom and device emulation, and the presenter that puts a live page on the screen.

`include(":web-tab")`, `implementation(projects.webTab)`.

Two production files. `WebsitePresenter.kt` is bigger than its name: it also holds `CefManager`
(the JCEF singleton), `EmbeddedWebView` and `WebNavController`. They stayed together because they
are one story — starting Chromium, wrapping a browser, and drawing it.

## What `:composeApp` uses from it

| symbol | who |
|---|---|
| `WebTab` | `MainDesktop` |
| `WebsitePresenter` | `PresenterModeContent` |
| `CefManager` | `main.kt` (`init()` and the crash-report tags) and `DeviceInfoReport` |
| `WebOutput` | implemented by `PresenterWebOutput` |

JCEF is declared `api`, not `implementation`, because those call sites handle `CefBrowser` and
`CefManager` types directly.

## The port is wider than the others, deliberately

`WebOutput` has nine members where `:qa-tab`, `:announcements-tab` and `:stt-tab` have two or six.
That is not sloppiness: **when a page is live, the tab and the output share the same `CefBrowser`.**
Typing an address in the tab has to navigate the browser the audience is looking at, so the
reference itself crosses the boundary. Splitting it into something narrower would mean two browsers
and two page loads of the same site.

`isLive` is still a `Boolean` rather than the `Presenting` enum, for the same reason as everywhere
else: the tab never asks what is live when it is not the website.

## The JCEF build setup is duplicated, and has to be

`currentJcefPlatform()` is copied from `:composeApp`. JCEF is **not in the version catalogue**,
because the natives artifact name is built from the host OS and architecture rather than being a
fixed coordinate — `me.friwi:jcef-natives-macosx-arm64:...` and so on. Both modules need the answer:
this one to compile and test against it, `:composeApp` to package it. Keep the version string in
step between the two.

The `--add-exports=java.desktop/sun.awt=...` list on `Test` tasks is the same one `:composeApp`
applies to its `JavaExec` tasks. Without it anything touching `CefBrowserWindowMac` dies with
`IllegalAccessError`.

## Coverage: three floor overrides, and why

Read the long comment above `extra["coverageFloors"]` in `build.gradle.kts` before touching them.

**Only three counters are named.** `INSTRUCTION` (0.852), `METHOD` (0.864) and `CLASS` (0.852) clear
the root build's 0.85 default and are left on it — they must keep clearing it, so do not add them.
The overridden three are `BRANCH` 0.73, `LINE` 0.83 and `COMPLEXITY` 0.70, each the measured value
with a hair of headroom so a regression still fails the gate.

The reason is the same for all three: this module embeds Chromium. Measured, not guessed —
`WebsitePresenter.kt` holds 64 of the 190 missed branches and 924 of the 1359 missed instructions,
all of it `CefManager.init()` starting a browser or code past `EmbeddedWebView`'s
`createClient() ?: return`. Another 39 branches are readiness arms in the input-forwarding block
that need a real, showing AWT render surface.

**Two seams did most of the work getting here, and neither is optional.**

- **`CefHandlers.kt`.** The display, popup and user-agent handlers were `object :` expressions inside
  `EmbeddedWebView`, constructible only once Chromium was running. As named factories they are at
  **100% coverage**, and that is what took `CLASS` from 0.615 over the line. Do not inline them back.
- **The browser stub in `WebInputForwardingTest`.** `findMethod` walks *up* the class hierarchy for
  `sendMouseEvent`, which lives on JCEF's `CefBrowser_N` rather than the `CefBrowser` interface — so
  a plain `mockk<CefBrowser>` never satisfies it, but an abstract subclass declaring it does. That
  took the mouse and scroll lambdas from 0.00 to ~0.85, and the test asserts the mapped coordinates
  rather than that a mock was called.

**What is left is nearly mined out.** The long tail is inside `WebTab.kt`'s 644-line composable, and
the last ten tests written against it bought three branches between them. The suite is 222 tests; do
not expect another 20 to move these numbers.

**The one change that would move them a long way** is putting `EmbeddedWebView`'s
`CefManager.createClient()` behind a parameter, the way the handlers were extracted. That makes most
of those 924 instructions reachable. It touches the live rendering path, so it is a deliberate piece
of work rather than something to slip in — but it is the answer if these floors need to rise.

## A suspected production bug, left alone

`onKeyEvent` on the mirrored image has no `.focusable()` anywhere in its modifier chain, so the node
cannot take focus and the handler looks unreachable in the app as well as in tests. The tab's
type-to-page field is the supported way to send keystrokes to a live page, and it works. Flagged
rather than fixed, because fixing it blind would change what happens during a service.

## detekt

`config/detekt/baseline.xml` carries two entries that came from `:composeApp`'s baseline unchanged —
`LongMethod` on `WebTab` and `TooGenericExceptionCaught` in `CefManager`. Seven `MaxLineLength`
entries came with them and were **fixed by wrapping instead of re-baselined**; their entries are
deleted from the root baseline.

Three lines still exceed 120 characters and detekt does not flag them: they are inside raw-string
JavaScript literals, where wrapping would change the script.

Baseline IDs embed the whole signature, so `WebTab`'s entry was rewritten by hand when the tab took
`output: WebOutput?`. Its parameter KDoc now lives in `@param` tags rather than inside the parameter
list — a comment in there ends up in the ID and makes it fragile.

## Commands

```bash
./gradlew :web-tab:test
./gradlew :web-tab:detekt
./gradlew :web-tab:jacocoTestCoverageVerification
./gradlew :web-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :web-tab:recordRoborazziJvm --tests '*ScreenshotTest*'
```
