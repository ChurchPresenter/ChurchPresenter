# `:lower-third-settings-tab` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**One page of the options dialog** — the one that configures the Lower Third tab: the Lottie
animation library found in a folder on the left, a live preview of the selected animation on the
right, and beneath it the four window insets the band is placed with.

`include(":lower-third-settings-tab")`, `implementation(projects.lowerThirdSettingsTab)`.

**Unlike `:dictionary-settings-tab`, it does depend on its tab module.** That pair split cleanly
because the settings page there only reads and writes `DictionarySettings` and never touches a
Strong's number. This one is different: the preview pane renders the chosen animation, and it must
render it **exactly as the audience will see it** — the same compottie configuration and the same
bundled `LottieFonts` the presenter uses. A preview that used a different font stack would be a
preview of something else. So `implementation(projects.lowerThirdTab)` is named rather than routed
around, and `isLottieFile` — the one definition of what counts as an animation on disk — arrives
through that module's `api(projects.companionServer)`.

Do not try to remove that edge by threading a `LottieFontManager` parameter in from the caller.
There is no second implementation to swap, so it would buy indirection and no seam.

## What `:composeApp` uses from it

One symbol: `LowerThirdSettingsTab`, drawn by `OptionsDialog`. It takes an `AppSettings`, an
`((AppSettings) -> AppSettings) -> Unit`, and an `onOpenLottieGen` callback, and nothing else — no
view model, no presenter, no app type. **Keep it that way.**

`LowerThirdSettingsViewModel` came with it and is internal to this module's concerns; nothing
outside constructs one.

## Layout

`src/main/kotlin/org/churchpresenter/lowerthird/settings/` — a sibling package of
`:lower-third-tab`'s `org.churchpresenter.lowerthird`.

Two production files, `LowerThirdSettingsTab.kt` and `LowerThirdSettingsViewModel.kt`, and three
test classes beside them. Screenshots are in `screenshots/lowerThirdSettingsTab/`, committed, seven
of them.

## The shared Lottie fixtures live in `:lower-third-tab`, not here

`lottieJson`, `NOT_LOTTIE_JSON`, `withLottieFolder` and `awaitFolderScan` are **test fixtures of
`:lower-third-tab`** (`src/testFixtures/`), consumed here and by `:composeApp`'s
`ServerSettingsTabTriggersTest`. Three suites put animations on disk and point a tab at them, and
what counts as an animation is decided by one function — `companionserver.isLottieFile`. A fixture
copied per module drifts from it silently, so there is one copy, beside the module that owns the
rendering.

**Do not add a fourth copy.** If a suite needs a new folder shape, add it to
`LottieFolderFixtures.kt`.

`awaitLowerThirdRows` is *not* there: it waits on the server tab's list, which has no "Scanning
folder" marker, so it belongs with that tab in `:composeApp`.

## Coverage

No `extra["coverageFloors"]` and no `extra["coverageExcludes"]` — the module clears the root
build's 0.85 default on all six counters with room to spare. Measured:

| counter | value |
|---|---|
| INSTRUCTION | 0.997 |
| BRANCH | 0.957 |
| LINE | 0.993 |
| COMPLEXITY | 0.957 |
| METHOD | 0.979 |
| CLASS | 1.000 |

That is far above what `:lower-third-tab` manages, and the reason is worth keeping in mind: this
page has no switcher, no network, no modal and no offscreen renderer in it. It is a folder listing
and some settings fields. **If a number here starts slipping, something has been added that does
not belong on a settings page.**

## detekt: one baselined entry, and five that were fixed instead

`config/detekt/baseline.xml` holds **one** entry — `LongMethod` on `LowerThirdSettingsTab` itself,
307 lines against a threshold of 100. It came across from `:composeApp`'s baseline, where it had
been since the size rules were switched on; it is pre-existing debt, not something this module
introduced. `:dictionary-settings-tab` carries exactly one entry of the same shape for the same
reason, so the two settings pages are consistent.

**Five `MaxLineLength` entries came across with it and were fixed rather than re-baselined** — the
four margin fields whose `onValueChange` ran the whole `copy(streamingSettings = …)` onto one line,
and the selection bar's `drawRect`. All five are now wrapped, and the entries are deleted from the
root `config/detekt/baseline.xml`. A moved file's baseline entries are dead the moment it moves, so
they must be deleted there rather than left to rot.

Note the interaction: wrapping those five lines *raised* the `LongMethod` count from 256 to 307.
That is expected and is not a reason to leave lines long.

## Commands

```bash
./gradlew :lower-third-settings-tab:test
./gradlew :lower-third-settings-tab:detekt
./gradlew :lower-third-settings-tab:jacocoTestCoverageVerification
./gradlew :lower-third-settings-tab:verifyRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :lower-third-settings-tab:recordRoborazziJvm --tests '*ScreenshotTest*'
```
