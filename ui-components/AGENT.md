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

Four counters run on the root build's default 85% and clear it with room. **BRANCH and COMPLEXITY
carry lowered floors, because they cannot reach 85% here:**

| counter | measured | floor | ceiling if every reachable branch were covered |
|---|---|---|---|
| INSTRUCTION | 0.976 | 0.85 | — |
| LINE | 0.977 | 0.85 | — |
| CLASS | 0.955 | 0.85 | — |
| METHOD | 0.950 | 0.85 | — |
| BRANCH | 0.821 | **0.81** | ~0.87 |
| COMPLEXITY | 0.796 | **0.78** | ~0.84 |

Each floor sits about a point under what the module measures, not four: a floor well below reality
stops being a gate. They catch a real regression while absorbing the drift a Compose or Kotlin
version bump causes in generated branch counts.

The gap is the Compose compiler's `$changed` recomposition-skip branches, emitted inside each
composable's own method and reachable by no test. Of the complexity still missed, **138 units sit in
methods whose bodies run in full** (≤15 missed instructions) and only ~39 in methods with real
uncovered code — 21 of those being `FocusLostRescueState`'s AWT window paths, unreachable since this
suite runs headless.

Measured, not assumed: ten tests covering `LabeledCheckbox`/`RadioButton`/`Switch` in every state a
caller can produce moved COMPLEXITY by **one unit** — those three each miss 7–8 complexity against
13 missed instructions. Moving four screenshot suites into this module moved BRANCH by **0.009**.

**There are no `coverageExcludes` and there must never be any.** Every class this module compiles is
measured, so the denominator is honest. To judge whether this module is tested, read INSTRUCTION and
LINE (both ~0.98); BRANCH and COMPLEXITY here measure Compose codegen as much as they measure code.

## Commands

```bash
./gradlew :ui-components:test                 # 49 test classes, 425 tests
./gradlew :ui-components:detekt               # six baselined LongMethod, nothing else
./gradlew :ui-components:verifyRoborazzi      # the committed widget screenshots — local, not CI
./gradlew :ui-components:recordRoborazzi      # re-record after a deliberate visual change
```
