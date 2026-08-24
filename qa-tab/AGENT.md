# `:qa-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Audience Q&A tab, whole** — the moderation queue people post to from their phones, the QR code
they scan to find it, the sharing dialog beside it, and the presenter that puts a question on the
audience screen. Everything the operator and the congregation see of Q&A is here.

`include(":qa-tab")`, `implementation(projects.qaTab)`.

The third tab extracted from `:composeApp`, after `:dictionary-tab` and `:announcements-tab`. Its
`QAManager` already implements `:companion-server`'s `QaModeration`, so the server side needed no
change at all — the phones were already talking to an interface rather than to the app.

## `QaOutput` — the port

`QATab` used to take a `PresenterManager` and a `(Presenting) -> Unit`. Both are `:composeApp` types,
and `Presenting` alone is named by three dozen files there, so neither could come into this module.

`QaOutput` is the seam: six members — two flags the tab reads and four things it asks for.
`:composeApp` implements it in `viewmodel/PresenterQaOutput.kt` as a pass-through, and `Presenting`
never crosses the boundary.

| what the tab needs | what the app maps it to |
|---|---|
| `outputIsClear` | `presentingMode.value == Presenting.NONE` |
| `lockedToQa` | any screen lock set to `Presenting.QA` |
| `setDisplayedQuestion` / `setShowQrCode` | the same two calls on `PresenterManager` |
| `goLive()` / `clear()` | `presenting(Presenting.QA)` / `presenting(Presenting.NONE)` |

**`outputIsClear` is a boolean, not the enum.** Q&A only cares whether the screen went empty — a move
between two other kinds of content is none of its business — and that is what lets `Presenting` stay
in the app. The `LaunchedEffect` that drops the displayed question keys on it.

**Do not widen this to mirror `PresenterManager`.**

## The file choosers are parameters

Export, import and Export & Clear all used `FileChooser.platformInstance`, a native dialog with
nothing to click headless. The tab now takes `chooseExportFile`/`chooseImportFile` and `MainDesktop`
passes the real chooser; everything around them — building the export text, writing it, parsing an
imported line, and the rule that a cancelled or failed save **aborts** a clear rather than losing the
questions — stayed here and is tested by handing in a temp path.

That replaced a `mockkObject(FileChooser.Companion)` fixture. **Do not put it back**: mocks are a last
resort here, and this one is not needed any more.

## What `:composeApp` uses from it

| symbol | used by |
|---|---|
| `QATab` | `MainDesktop` |
| `QAManager` | `MainDesktop`, `main.kt` — constructed once and handed to the server as its `QaModeration` |
| `QAPresenter`, `QAQRCodePresenter` | `PresenterModeContent`, `StageMonitorScreen`, `LivePreviewPanel`, `BrowserSourceVideoRenderer` |
| `QaOutput` | `PresenterQaOutput`, which implements it |

## Layout

`src/main/kotlin/org/churchpresenter/qa/` — the package is `org.churchpresenter.qa`, **not**
`org.churchpresenter.app.churchpresenter.*`.

| file | owns |
|---|---|
| `QATab.kt` | the tab: the queue, its filters and sorts, the row controls, export/import |
| `QAManager.kt` | the questions, the session, votes, the cooldown, and the state on disk |
| `QAPresenter.kt` | what the audience sees: the question card and the join QR code |
| `QARemoteDialog.kt` | the sharing dialog — the two addresses, the tunnel, and how the QR looks |
| `QaOutput.kt` | the port above |

## What moved to `:ui-components` to make this possible

Three things the Q&A files needed that had no business being app-only, all now shared:

- **`AutoFit.kt`** — `calculateAutoFitFontSize` and `MIN_AUTO_FIT_FONT_SIZE`. Identical to the file
  `:announcements-tab` introduces, so the two changes merge cleanly.
- **`QrCode.kt`** — `generateQRCodeBitmap`, lifted out of `QAPresenter.kt`. `PresentationRemoteDialog`
  in the app draws a QR too, and a presentation dialog importing from `:qa-tab` would make the module
  boundary meaningless. zxing moved into `gradle/libs.versions.toml` at the same time; it had been a
  hand-copied literal in `composeApp/build.gradle.kts`.
- **`WindowUtils.kt`** — `LocalMainWindowState`, `centeredOnMainWindow`, `primaryScreenSizeDp`,
  `dialogSizeWithin`, and its test. Twenty-nine files in the app import them; every dialog that ever
  moves out of `:composeApp` will need them.

## Rules

- **No `:composeApp` types, ever** — that is what `QaOutput` and the chooser parameters exist for.
- **The screenshots are COMMITTED**, under `qa-tab/screenshots/`, exactly as `:composeApp`'s are. The
  root `AGENT.md` rule applies here in full: never move them under `build/`.
- **`AppPreviewQAScreenshotTest` stayed in `:composeApp`** and must stay there — it shoots the whole
  app window with this tab selected, so it needs the app.
- **`QARemoteDialog` is shot through `QARemoteContent`.** A `DialogWindow` is an OS window and cannot
  be photographed headless, which is why the body is `internal` and separate. Keep it that way.
- **`CompanionServerQaTest`/`CompanionServerQaModerationTest` stayed in `:composeApp`** — they drive
  the real server against a real `QAManager`, which is the app's wiring rather than this module's.

## Gates

**All six counters clear the root build's 0.85 default. This module has no `coverageFloors` override
and no `coverageExcludes`, and must not gain either.** Measured 2026-08-24:

| counter | measured |
|---|---|
| INSTRUCTION | 0.953 |
| LINE | 0.949 |
| METHOD | 0.905 |
| BRANCH | 0.858 |
| COMPLEXITY | 0.852 |
| CLASS | 0.893 |

BRANCH started at 0.841 and COMPLEXITY at 0.826 — the two the move left short. Four things closed it,
and the first is the one to reach for first in the next tab that moves out:

1. **A defaults test, and its opposite.** `QATab` takes eleven defaulted parameters and `MainDesktop`
   passes every one, so the defaults were never taken. `QATabDefaultsTest` composes the tab with only
   its three required arguments *and* with every argument supplied — between them the two call shapes
   cover both sides of each `$default` branch.
2. **Dead defaults on private composables were deleted.** `QuestionRow`'s one call site always passed
   `deviceName`, `isHistory` and `onMarkDone`; `QAIconButton`'s thirteen call sites never passed
   `modifier` or `enabled`, so both parameters went and the size became a named constant.
3. **The states nothing drove were driven** — every empty-list message, a denied question's own
   controls, the two confirmation prompts, the vote that reverses, the cooldown that has expired, and
   putting a second question up while the first is live.
4. **The two opacity sliders, by coordinate.** `SlimSlider` publishes no semantics — it is a `Canvas`
   behind a tap detector — so each is found through its readout, which sits exactly 10.dp off the
   track's right edge. The two are told apart by giving them different percentages in the fixture.

**Still uncovered, and honestly so:** `QARemoteDialog` itself — the `DialogWindow` call and the
grow-to-fit effect, neither of which runs headless. That is ~160 instructions the module carries
rather than excludes.

**Three `LongMethod` suppressions, no baseline.** `QATab`, `QuestionRow` and `QARemoteContent` each
carry `@Suppress("LongMethod")` at the declaration with a note. All three were **already baselined in
`:composeApp`** before the move — this is the same debt re-keyed, not new suppression. `@Suppress` at
the site rather than a baseline entry because a detekt ID embeds the signature it was written against,
and all three signatures changed in the move. `config/detekt/baseline.xml` is **empty and must stay
empty.**

**`MaxLineLength` is fixed at source.** The move surfaced 98 long lines and **all 98 were wrapped**;
none is baselined. An `updateQa` helper did most of the work in `QARemoteDialog`, standing in for
twenty-one restatements of `onSettingsChange { s -> s.copy(qaSettings = s.qaSettings.copy(…)) }`.
The three `TooGenericExceptionCaught` findings around file I/O were fixed by narrowing to
`IOException` rather than suppressed. The 94 stale entries these files left behind in
`config/detekt/baseline.xml` were deleted.

## Commands

```bash
./gradlew :qa-tab:test                 # 21 test classes, 303 tests
./gradlew :qa-tab:detekt               # clean — an empty baseline, three @Suppress at the sites
./gradlew :qa-tab:jacocoTestCoverageVerification   # all six counters over 0.85
./gradlew :qa-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'   # 34 committed images
./gradlew :qa-tab:recordRoborazziJvm --tests '*ScreenshotTest*'   # re-record after a visual change
```
