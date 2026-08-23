# `:dictionary-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The Dictionary tab** — what the operator and the audience see of Strong's: the browser with its
search, language filter and history, the "In Scripture" panel beside it, the view model holding all
of that state, and the presenter that draws an entry on the screen.

The page of the options dialog that *styles* that output is its own module, `:dictionary-settings-tab`
— it reads `DictionarySettings` and draws it with the `:ui-components` fields, and knows nothing
about a Strong's number, so it needs neither `:dictionary` nor a view model.

The data underneath is `:dictionary` (14,197 entries and the interlinear index over them, 18 MB of
JSON). **This module is the picture; that one is the numbers.** `include(":dictionary-tab")`,
`implementation(projects.dictionaryTab)`.

It is the first tab extracted from `:composeApp`, and it is the shape the others should follow: tab
+ view model + presenter in one module, with the app left holding only the wiring, and the settings
page beside it in a module of its own.

**The settings tab moved in two steps**, and the second one is the lesson: it came here first,
because that is where the Dictionary lives, and then straight back out into
`:dictionary-settings-tab` because it shares nothing with the tab but a name. A settings page reads
a settings class and draws widgets; it does not need the feature's data or its view model. Check
that before assuming the next tab's settings page belongs with its tab.

## What `:composeApp` uses from it

Six imports, and nothing else:

| symbol | used by |
|---|---|
| `DictionaryTab`, `DictionaryViewModel` | `MainDesktop` — constructs the one and draws the other |
| `DictionaryPresenter` | `PresenterModeContent`, `StageMonitorScreen`, `LivePreviewPanel`, `BrowserSourceVideoRenderer` |

**Both public composables take callbacks, not app types.** `DictionaryTab` is handed
`onGoLive`/`onAddToSchedule`/`getVerseText`/`getBookName`/`onWordClick`/`onVerseClick` as functions,
and `DictionaryPresenter` takes a `StrongsEntry` and a `DictionarySettings`. Neither has ever seen
`PresenterManager` or a schedule, which is what made this module a clean cut rather than a
disentangling. **Keep it that way** — a parameter of an app type here would pull `:composeApp` back
in and there is no way to depend on it.

## Layout

`src/main/kotlin/org/churchpresenter/dictionary/ui/` — note the package is `…dictionary.ui`, a
sibling of `:dictionary`'s own `…dictionary`, and **not** `org.churchpresenter.app.churchpresenter.*`.

| file | owns |
|---|---|
| `DictionaryTab.kt` | the tab: list pane, detail pane, "In Scripture", the two scripture filters |
| `DictionaryViewModel.kt` | its state — search, filters, history, the picked Bible, interlinear paging |
| `DictionaryPresenter.kt` | what the audience sees: the card, its type and its transitions |

## Rules

- **No `:composeApp` types, ever** — see above. `MAX_BIBLE_SCAN_DEPTH` moved to `:bible` rather than
  this module importing `FileManager` for one constant.
- **The screenshots are COMMITTED**, under `dictionary-tab/screenshots/`, exactly as `:composeApp`'s
  are. The root `AGENT.md` rule applies here in full: never move them under `build/`.
- **`AppPreviewDictionaryScreenshotTest` stayed in `:composeApp`** and must stay there — it shoots
  the whole app window with the Dictionary tab selected, so it needs the app, not this module.

## Gates

**All six counters clear the root build's 0.85 default. This module has no `coverageFloors`
override and must not gain one.** Measured 2026-08-23:

| counter | measured |
|---|---|
| INSTRUCTION | 0.947 |
| LINE | 0.953 |
| CLASS | 0.933 |
| METHOD | 0.918 |
| BRANCH | 0.930 |
| COMPLEXITY | 0.887 |

Re-measured 2026-08-23, after the settings tab left for `:dictionary-settings-tab`. BRANCH and
COMPLEXITY both sit higher than when this module was extracted (0.886 and 0.858) — the work that
lifted them is below, and the split did not undo it.

BRANCH and COMPLEXITY started at 0.824 and 0.793 — below the default, and the same Compose
`$changed` codegen ceiling `:ui-components` runs into. Two things lifted them, and both are worth
repeating in the next tab that moves out:

1. **Dead defaults on private composables were deleted.** `DictionaryDetailPane` had twenty
   parameters carrying a default value that `DictionaryTab` — its only caller — always passed. Each
   one is a branch that nothing can reach and that JaCoCo counts against the module for ever.
   Removing them is dead-code removal, not a coverage trick: **check `mb=1` on a defaulted parameter
   of a `private` composable before assuming its branches are unreachable.**
2. **The states no test drove were driven** — the tab composed with nothing but a view model
   (`DictionaryTabDefaultsTest`), the presenter at preview and stage-monitor sizes rather than only
   1920x1080, the chapter and verse filters that appear only once the one above them is narrowed,
   and an entry with no KJV usage.

**There are no `coverageExcludes` and there must never be any.** Most of what is still missed is in
`DictionaryTabKt`, where Compose codegen and the states no test drives sit together. That is the
honest number; do not hide it behind an exclude.

**The detekt baseline holds five entries and nothing else** — three `LongMethod` and two
`TooManyFunctions`, all carried across verbatim from `:composeApp`'s baseline when the files moved.
**Nothing here was newly suppressed.** Two of them had to be re-keyed when the dead default values
went, because a detekt ID embeds the signature it was written against. Never add a sixth.

## Commands

```bash
./gradlew :dictionary-tab:test                  # 17 test classes, 222 tests
./gradlew :dictionary-tab:detekt                 # six baselined entries, nothing else
./gradlew :dictionary-tab:jacocoTestCoverageVerification
./gradlew :dictionary-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'   # 19 committed images
./gradlew :dictionary-tab:recordRoborazziJvm --tests '*ScreenshotTest*'   # re-record after a visual change
```
