# `:lower-third-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Lower Third tab** — the animated Lottie band the audience sees over the picture: the preset
list, the preview, the playback transport, and the Blackmagic ATEM media-pool upload beside it. Plus
the presenter that draws the band on the output and the offscreen renderer the Browser Source overlay
and the ATEM upload both rasterise through.

`include(":lower-third-tab")`, `implementation(projects.lowerThirdTab)`.

The fourth tab extracted from `:composeApp`, and by far the cheapest: **it needed no port.**
`LowerThirdTab` was already callback-driven before it moved — `onGoLive`, `onAddToSchedule`,
`onOpenLottieGen`, and even the two ATEM calls arrive as parameters — so `PresenterManager` never
reached it and nothing had to be invented to keep it out.

`LowerThirdSettingsTab` is **deliberately still in `:composeApp`**, with the other pages of the
options dialog, and so is its `LowerThirdSettingsViewModel`, which nothing else uses. Do not move
them without asking.

## The one thing that did have to change

`LocalMainWindowState` — a `:composeApp` composition local — was read for exactly one thing: whether
the window is maximised, which decides *which* saved panel width the tab uses. That became
`isWindowMaximized: Boolean = true`, the same parameter `AnnouncementsTab` takes. No `WindowUtils`
move was needed, so this module depends on nothing pending in another branch.

## What `:composeApp` uses from it

| symbol | used by |
|---|---|
| `LowerThirdTab` | `MainDesktop` |
| `LowerThirdPresenter` | `PresenterModeContent`, `LivePreviewPanel`, `BrowserSourceVideoRenderer` |
| `LottieFrame`, `LottieFrameStream` | `PresenterManager`, `PresentationPlayer` |
| `SkiaLottieFrameRenderer` | `main.kt`, `CompanionHostWiring` — the real renderer handed to the render cache |
| `LottieFonts` | `main.kt`, and `LowerThirdSettingsTab`, which stayed behind |

## Layout

`src/main/kotlin/org/churchpresenter/lowerthird/` — package `org.churchpresenter.lowerthird`, **not**
`org.churchpresenter.app.churchpresenter.*`.

| file | owns |
|---|---|
| `LowerThirdTab.kt` | the tab: presets, preview, transport, the ATEM dialog and quick-upload row |
| `LowerThirdPresenter.kt` | what the audience sees — the band, its window insets, the key filter |
| `LowerThirdOffscreenRenderer.kt` | rasterising a Lottie without a window, for the cache and the ATEM |
| `LottieFrameStream.kt` | the frames the output windows draw, one publisher for all of them |
| `SkiaLottieFrameRenderer.kt` | the real `LottieFrameRenderer` `:companion-server` renders through |
| `LottieFonts.kt` | resolving the families a Lottie names, bundled first then system |

## What moved elsewhere to make this possible

- **`ScreenGeometry.kt` → `:ui-components`** — `presenterScreenBounds`, `presenterAspectRatio`,
  `formatAspectRatio`, `rememberScreenDevices`, `findScreenIndexByBounds`. Five tabs ask these and
  none of them needs a settings type. `assignedDisplayBounds` stayed in `:composeApp` because it
  takes a `ScreenAssignment`, and `:ui-components` must not gain a production dependency on
  `:settings`.
- **`formatAtemFps` → `:atem`** — the ATEM settings tab and this one both format the switcher's
  frame rate, and they are now in different modules.
- **`latchSkikoNativeLibrary` → `:ui-components` test fixtures** — every module whose tests swap
  `user.home` and touch Compose needs it.
- **The 19 bundled TTFs → `:resources`** (`resources/src/main/resources/fonts/`). They were a
  `:composeApp` classpath resource, and `LottieFonts.getResourceAsStream("/fonts/…")` returns null
  from anywhere else.

## Rules

- **No `:composeApp` types, ever.** Nothing here needs one; keep it that way.
- **The screenshots are COMMITTED**, under `lower-third-tab/screenshots/`, exactly as `:composeApp`'s
  are. The root `AGENT.md` rule applies here in full: never move them under `build/`.
- **`AppPreviewLowerThirdScreenshotTest` stayed in `:composeApp`** and must stay — it shoots the whole
  app window. So did `presenterLowerThird/`, which is **Bible and Song in lower-third band mode**
  (`BiblePresenter`/`SongPresenter`), not this feature. This presenter has no screenshots of its own.
- **`@BeforeClass` does not run here.** `:composeApp` pins `kotlin-test` to its JUnit 4 flavour with a
  `capabilitiesResolution` block; the modules take the platform default, where `org.junit.BeforeClass`
  still *compiles* and is simply never called. `LowerThirdAtemUploadTest` arrived using it and its
  render warm-up silently did nothing, so the frame it measured came out at zero bytes. Use
  `@BeforeAll` with `@TestInstance(PER_CLASS)`.
- **Clear `AtemUploadStatus` after any test that uploads.** It is a process-wide object shared with
  the Companion endpoints, and a test that tears down its composition mid-transfer leaves a failed
  entry behind — which the next class draws into its screenshot.
- **Settle the progress bar before capturing it.** Material 3's `LinearProgressIndicator` animates
  toward its value, so a capture taken straight after `progress(…)` catches the bar part-way and how
  far depends on how warm the JVM is. `LowerThirdTabScreenshotTest.settleProgressBar` does it.

## Gates

**Five of six counters clear the root build's 0.85 default. COMPLEXITY has a floor of 0.82**, set in
`build.gradle.kts` with the reasoning written out there. There are **no `coverageExcludes` and must
never be any.** Measured 2026-08-24:

| counter | measured | floor |
|---|---|---|
| INSTRUCTION | 0.944 | 0.85 |
| LINE | 0.944 | 0.85 |
| CLASS | 0.933 | 0.85 |
| METHOD | 0.912 | 0.85 |
| BRANCH | 0.880 | 0.85 |
| COMPLEXITY | 0.829 | **0.82** |

It arrived at INSTRUCTION 0.831 / BRANCH 0.729 / COMPLEXITY 0.662 / METHOD 0.777 / CLASS 0.800 and
105 tests. Four things moved it, and the first is the one to reach for first in the next tab:

1. **A defaults test, and its opposite.** `LowerThirdTab` takes eleven defaulted parameters and
   `MainDesktop` passes nine, so most defaults were never taken. `LowerThirdTabDefaultsTest` composes
   the tab with only `appSettings` *and* with every argument supplied; between them the two call
   shapes cover both sides of each `$default` branch.
2. **The modal was made into a parameter.** Removing a preset sat behind `JOptionPane` — a modal AWT
   dialog no headless test can click — with the delete, the selection drop and the list refresh all
   on the far side of it. `confirmRemoval` now defaults to `confirmRemovalWithSwing`, leaving the
   modal as the only uncovered step. This is the `SwingFileChooser.openWith` shape the root
   `AGENT.md` prescribes, and it is the right move whenever a blocking call hides real behaviour.
3. **The controls nothing drove were driven** — the ATEM slot dropdown and its hand-typed fallback,
   the generator, the panel drag handle in both window states, the key toggle, quick upload, an
   upload that cannot land, the folder watcher, and a switcher that stops answering mid-service.
4. **Coordinates where semantics run out.** The panel handle publishes none, so it is found from the
   Generate button's right edge plus the panel padding — derived, not guessed at a fraction of the
   width.

**What the 0.82 buys, precisely.** Sixty-two decision points remain and none of them is untested
logic — LINE is 0.944. They are: the `JOptionPane` modal; the `queryAtemState` / `probeAtemReachable`
defaults, which are the real `AtemClient` and cost a 5s UDP timeout each (the seams exist to avoid
that); the upload dialog's mid-transfer progress bars; a `canvasSize == null` arm reachable only from
a Lottie that also fails to parse; `LowerThirdSequencer` against an unreachable switcher, which does
not return inside 30s; and the `onClick` bodies of disabled buttons, whose false arms the UI prevents
by construction. **Every one is written down at the site in the test that stops short of it.**

**The detekt baseline is empty and must stay empty.** The move surfaced 37 `MaxLineLength` findings
and all 37 were fixed at source; the 41 stale entries these files left behind in
`config/detekt/baseline.xml` were deleted. Two pre-existing findings carry `@Suppress` at the
declaration with a note — `LongMethod` on `LowerThirdTab`, and `TooGenericExceptionCaught` on the two
catches guarding *injected* calls, where narrowing to `IOException` would let a caller's other
failure escape into the composition.

## Commands

```bash
./gradlew :lower-third-tab:test                 # 18 test classes, 155 tests
./gradlew :lower-third-tab:detekt               # clean — empty baseline, two @Suppress at the sites
./gradlew :lower-third-tab:jacocoTestCoverageVerification
./gradlew :lower-third-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'   # 24 committed images
./gradlew :lower-third-tab:recordRoborazziJvm --tests '*ScreenshotTest*'   # re-record after a visual change
```
