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

## Coverage: six floor overrides, and why

Read the long comment above `extra["coverageFloors"]` in `build.gradle.kts` before touching them.
The short version: this module embeds a browser, and three kinds of code here need a running one —
`CefManager`'s native install and JVM module patching, `EmbeddedWebView`'s CEF handler callbacks,
and the key-forwarding lambda. The numbers are the measured values with almost no headroom, so the
gate still bites on a regression.

**The mouse and scroll forwarding are NOT in that category any more.** They went from 0.00 to 0.82
and 0.87 by giving the test a browser stub that *declares* the reflected methods — `findMethod`
walks up the class hierarchy for `sendMouseEvent`, which lives on JCEF's `CefBrowser_N` rather than
the `CefBrowser` interface, so a plain `mockk<CefBrowser>` never satisfies it but an abstract
subclass declaring it does. `WebInputForwardingTest` is the worked example, and it asserts the
mapped coordinates rather than that a mock was called.

**The honest remaining target is `WebTabKt` itself** — 430 instructions and 102 decision points
inside one 644-line composable, all reachable through ordinary interaction. Raise the floors as that
comes down.

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
