# `:ui-components` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The app's own widget library** — the custom composables every tab and dialog is built from:
dropdowns and settings fields, segmented buttons, the colour picker, tooltips, the selection list,
the panel resize handle. Generic by construction: it knows the theme and the resources, and nothing
else about the app. `include(":ui-components")`, `implementation(projects.uiComponents)`.

**What is NOT here**: anything that knows a feature. `VideoPlayer`, `SceneCanvas`, `DeckLinkManager`,
`LivePreviewPanel`, the `Scene*Editors`, `FontPickerPanel`, `CompanionSurfacePanel` and
`BibleSourceProperties` all stayed in `:composeApp` — they live in the same folder there but depend
on view models, presenters or app utils. **A widget that needs one of those does not belong here.**

`ColorPickerField` and `ShadowDetailRow` are the two near misses: both are generic, but
`ColorPickerField` needs `Utils.parseHexColor` from `:composeApp`. Moving that helper into `:theme`
(where the colour code lives) would let both follow.

## Rules

- **Depends on `:theme` and `:resources` only.** `:settings` appears as a *test* dependency for the
  alignment screenshots and must not become a production one.
- **`api(projects.resources)`, not `implementation`** — a widget's own signature can carry a
  resource type, and every call site reads `Res.string.x` through the one shared accessor.
- **`ScreenshotSupport` lives in `src/testFixtures`, not `src/test`.** `:composeApp` consumes it as
  `testFixtures(projects.uiComponents)`; it is the lower module, so this is the one copy of the
  theme-stacking, trimming and capture machinery. Do not fork a second copy up in `:composeApp`.
- **The screenshots are COMMITTED**, under `ui-components/screenshots/`, exactly as `:composeApp`'s
  are under `composeApp/screenshots/`. The root `AGENT.md` rule applies here in full: they are what
  a reviewer opens and approves, so they must never move under `build/`.
- **The detekt baseline holds six `LongMethod` entries and nothing else.** All 59 `MaxLineLength`
  findings the extraction surfaced were fixed, including 42 in `AlignmentButtons` (worst line: 359
  characters). Two of the six became long *because* of that wrapping. Never add another rule to it.

## Gates

`BRANCH` and `COMPLEXITY` do not reach the default 85%, and **cannot**:

| counter | measured | ceiling if every reachable branch were covered |
|---|---|---|
| INSTRUCTION | 0.959 | — |
| LINE | 0.961 | — |
| METHOD | 0.889 | — |
| CLASS | 0.913 | — |
| BRANCH | 0.766 | ~0.87 |
| COMPLEXITY | 0.720 | ~0.82 |

The gap is the Compose compiler's `$changed` recomposition-skip branches, emitted inside each
composable and reachable by no test. Measured: `LabeledCheckbox`, `LabeledRadioButton` and
`LabeledSwitch` each miss 7–8 complexity against **13 missed instructions** — the bodies run in
full. Ten tests covering checked/unchecked/disabled/supporting/trailing-control moved the number by
one. Moving four screenshot suites in moved BRANCH by 0.009.

**So do not read a low BRANCH/COMPLEXITY here as untested code** — read INSTRUCTION and LINE, which
are at 0.96. Whether to declare `coverageFloors` for those two counters is an open decision; there
are **no `coverageExcludes` and there should never be any**, since the denominator is honest.

## Commands

```bash
./gradlew :ui-components:test                 # 43 test classes
./gradlew :ui-components:detekt               # six baselined LongMethod, nothing else
./gradlew :ui-components:verifyRoborazzi      # the committed widget screenshots — local, not CI
./gradlew :ui-components:recordRoborazzi      # re-record after a deliberate visual change
```
