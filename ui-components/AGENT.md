# `:ui-components` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root `AGENT.md`.

## What it is

**The app's own widget library** — the custom composables every tab and dialog is built from:
dropdowns and settings fields, segmented buttons, the colour picker, tooltips, the selection list,
the panel resize handle. Generic by construction: it knows the theme and the resources, and nothing
else about the app. `include(":ui-components")`, `implementation(projects.uiComponents)`.

**What is NOT here**: anything that knows a feature. `VideoPlayer`, `SceneCanvas`, `DeckLinkManager`,
`LivePreviewPanel`, the `Scene*Editors`, `CompanionSurfacePanel` and `BibleSourceProperties` all
stayed in `:composeApp` — they live in the same folder there but depend on view models, presenters
or app utils. **A widget that needs one of those does not belong here.**

**The font picker and the colour field are here now.** `ColorPickerField`, `ShadowDetailRow`,
`FontSettingsDropdown` and the `FontPickerPanel`/`FontPickerModel`/`FontPreviewLines` behind it
followed `Utils`, `SystemFonts` and `FontCatalog` in, which is what unblocked them. The one thing
that had to stay behind is the Bible: `FontPreviewText.update` takes `List<String>` rather than
`List<Bible>`, and `previewLinesFrom` — the only function that knows a translation — lives in
`:composeApp`. A widget library must not depend on `:bible` to preview a font.

## Rules

- **Depends on `:theme` and `:resources` only.** `:settings` appears as a *test* dependency for the
  alignment screenshots and must not become a production one.
- **`api(projects.resources)`, not `implementation`** — a widget's own signature can carry a
  resource type, and every call site reads `Res.string.x` through the one shared accessor.
- **`ScreenshotSupport` lives in `src/testFixtures`, not `src/test`.** `:composeApp` consumes it as
  `testFixtures(projects.uiComponents)`; it is the lower module, so this is the one copy of the
  theme-stacking, trimming and capture machinery. Do not fork a second copy up in `:composeApp`.
- **So does every helper that drives one of these widgets.** `ColorPickerFieldTestSupport`,
  `SettingsFieldTestSupport` (the font, number and style-button helpers, plus `unlabelledControls`
  and `renderedPixels`), `StepperArrowsTestSupport` and `RenderedTextTestSupport` are all fixtures,
  because a settings tab in `:composeApp` and one in a feature module of its own both drive the same
  widgets. **A helper that names a tab is not one of these** — `songTab`, the `*Group` ordinals and
  `chooseShowOption` stayed in `SongSettingsTabTestSupport`.
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
| INSTRUCTION | 0.971 | 0.85 | — |
| LINE | 0.976 | 0.85 | — |
| CLASS | 0.940 | 0.85 | — |
| METHOD | 0.941 | 0.85 | — |
| BRANCH | 0.826 | **0.81** | ~0.87 |
| COMPLEXITY | 0.804 | **0.78** | ~0.84 |

Re-measured 2026-08-23, after the font picker and colour field moved in: every counter held, and
BRANCH and COMPLEXITY each rose about half a point.

Each floor sits about a point under what the module measures, not four: a floor well below reality
stops being a gate. They catch a real regression while absorbing the drift a Compose or Kotlin
version bump causes in generated branch counts.

The gap is the Compose compiler's `$changed` recomposition-skip branches, emitted inside each
composable's own method and reachable by no test. Measured at extraction, when the module held 177
missed complexity: **138 units sat in methods whose bodies run in full** (≤15 missed instructions)
and only ~39 in methods with real uncovered code — 21 of those being `FocusLostRescueState`'s AWT
window paths, unreachable since this suite runs headless. The module has grown since; that
breakdown is the shape of the gap, not a current count.

Measured, not assumed: ten tests covering `LabeledCheckbox`/`RadioButton`/`Switch` in every state a
caller can produce moved COMPLEXITY by **one unit** — those three each miss 7–8 complexity against
13 missed instructions. Moving four screenshot suites into this module moved BRANCH by **0.009**.

**There are no `coverageExcludes` and there must never be any.** Every class this module compiles is
measured, so the denominator is honest. To judge whether this module is tested, read INSTRUCTION and
LINE (0.97 and 0.98); BRANCH and COMPLEXITY here measure Compose codegen as much as they measure code.

## Commands

```bash
./gradlew :ui-components:test                 # 60 test classes, 544 tests
./gradlew :ui-components:detekt               # six baselined LongMethod, nothing else
# recordRoborazziJvm / verifyRoborazziJvm are the Test-derived tasks -- the un-suffixed ones are
# lifecycle aggregates and reject --tests.
./gradlew :ui-components:verifyRoborazziJvm --tests '*ScreenshotTest*'   # local, not CI
./gradlew :ui-components:recordRoborazziJvm --tests '*ScreenshotTest*'   # after a visual change
```
