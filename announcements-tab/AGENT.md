# `:announcements-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Announcements tab** — on-screen notices and the four timers beside them (Duration, Count-Up,
Specific Time and the live Clock display): the tab the operator drives, the view model holding its
state and running the tick, and the presenter that draws the notice or the digits on the audience
screen.

`AnnouncementsSettingsTab` does not exist — the tab configures its own appearance in place, so
everything this feature has is here. `include(":announcements-tab")`,
`implementation(projects.announcementsTab)`.

The second tab extracted from `:composeApp`, after `:dictionary-tab`, and the first one that had to
be *decoupled* rather than simply moved: `PresenterManager` and the `Presenting` enum stay in the
app, and the tab reaches them through a port.

## `AnnouncementsOutput` — the port, and why there is one

The Dictionary tab was handed callbacks and never knew what a `PresenterManager` was. This one
did: thirteen members of it, plus `Presenting`, which 36 files in the app name. Moving either would
have dragged the app in with them.

So `AnnouncementsOutput` is an interface **owned by this module** describing only what the
announcements feature asks of an output — is the ticker live, put this text up, start a countdown of
N seconds, lock screen 2 to announcements. `:composeApp` implements it in
`viewmodel/PresenterAnnouncementsOutput.kt`, a pass-through over `PresenterManager`, and passes the
implementation in. `Presenting` never crosses the boundary.

**`output` is nullable, and every call site treats null as "no output attached".** That is what lets
the tab be composed in a test, and in the preview panes, without a presenter.

**Do not widen the interface to mirror `PresenterManager`.** It is the list of things the
announcements feature needs, not a view onto the app.

## What `:composeApp` uses from it

| symbol | used by |
|---|---|
| `AnnouncementsTab` | `MainDesktop` |
| `AnnouncementsPresenter` | `PresenterModeContent`, `LivePreviewPanel`, `BrowserSourceVideoRenderer` |
| `AnnouncementsViewModel.formatTimer` | `PresenterManager`, for the text it pushes while a timer runs |
| `AnnouncementsOutput` | `PresenterAnnouncementsOutput`, which implements it |

## Layout

`src/main/kotlin/org/churchpresenter/announcements/` — the package is `org.churchpresenter.announcements`,
**not** `org.churchpresenter.app.churchpresenter.*`.

| file | owns |
|---|---|
| `AnnouncementsTab.kt` | the tab: text, appearance, position, the four timer modes, the preview pane |
| `AnnouncementsViewModel.kt` | its state and the tick — timer arithmetic, the clock, settings sync |
| `AnnouncementsPresenter.kt` | what the audience sees: the plate, its type, its entrance animation |
| `AnnouncementsOutput.kt` | the port above |

## Rules

- **No `:composeApp` types, ever** — that is what `AnnouncementsOutput` exists for.
- **The screenshots are COMMITTED**, under `announcements-tab/screenshots/`, exactly as
  `:composeApp`'s are. The root `AGENT.md` rule applies here in full: never move them under `build/`.
- **`AppPreviewAnnouncementsScreenshotTest` and `AppPreviewTimersScreenshotTest` stayed in
  `:composeApp`** and must stay there — they shoot the whole app window with this tab selected, so
  they need the app, not this module.
- **The clock is never photographed.** Specific Time counts down to a time of day and Clock Display
  *is* a clock, so a shot of either draws different digits on every recording. `AnnouncementsTabScreenshotTest`
  says which states it skips and why; keep that list honest rather than committing a churning image.

## Gates

**`MaxLineLength` is fixed at source, not baselined.** The move surfaced 93 long lines; all 34 that
detekt counts (it excludes raw strings) were wrapped, and **all 78 `MaxLineLength` entries were
deleted from `config/detekt/baseline.xml`**. Three helpers in `AnnouncementsTab` did most of the
work — `saved`, `updateAnn` and `typedDigits`/`typedSeconds` — by stating once what forty call sites
had been restating. **Never add a `MaxLineLength` entry back.**

The baseline now holds **two** entries: `LongMethod` on `AnnouncementsPresenter` and
`TooManyFunctions` on `AnnouncementsViewModel`. `AnnouncementsTab` carries `@Suppress("LongMethod")`
at the declaration instead, which was asked for explicitly.

**Coverage does not clear the 0.85 default yet, and there is no override.** Measured 2026-08-24:

| counter | measured |
|---|---|
| LINE | 0.967 |
| INSTRUCTION | 0.917 |
| CLASS | 0.882 |
| BRANCH | 0.860 |
| COMPLEXITY | 0.749 |
| METHOD | 0.705 |

The gap is the private composables `AnnouncementsTab` is built from: the timer steppers render
`Icon(Icons.Default.Add, contentDescription = null)`, so a test cannot find them to click. That is
an accessibility gap as much as a coverage one, and closing it — content descriptions, then tests —
is the fix. **An `extra["coverageFloors"]` override or a `coverageExcludes` entry is not**, and
neither may be added without asking. Because of this the module has **no coverage-floor step in
`.github/workflows/test.yml`**; the test step is there and gates. Add the floor step in the same
change that closes the gap.

## Commands

```bash
./gradlew :announcements-tab:test                 # 12 test classes, 201 tests
./gradlew :announcements-tab:detekt               # two baselined entries, nothing else
./gradlew :announcements-tab:jacocoTestCoverageVerification   # fails today; see Gates
./gradlew :announcements-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'   # 49 committed images
./gradlew :announcements-tab:recordRoborazziJvm --tests '*ScreenshotTest*'   # re-record after a visual change
```
