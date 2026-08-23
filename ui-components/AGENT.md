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

**Five of the six counters clear the root build's 0.85 default outright.** Measured 2026-08-23:

| counter | measured | floor |
|---|---|---|
| INSTRUCTION | 0.981 | 0.85 (default) |
| LINE | 0.981 | 0.85 (default) |
| CLASS | 0.970 | 0.85 (default) |
| METHOD | 0.963 | 0.85 (default) |
| BRANCH | 0.861 | 0.85 (default) |
| COMPLEXITY | 0.8499 | **0.84** |

BRANCH used to carry a lowered floor and no longer needs one. COMPLEXITY is **one unit of 1126
short** of the default, and the floor under it is the only override this module has.

**What the remaining 169 missed units are**, measured rather than assumed:

- **31 in `FocusLostRescueState`** — the AWT window-activation healing. `FocusLostRescueTest`
  records the decision not to exercise those: real hardware timing, no injectable delay, and this
  repo's rule against tests that race one. Reaching them would mean mocking `java.awt.Window` to
  assert that a stub was called. **Do not.**
- **28 in methods whose bodies run in full** (zero missed instructions) — pure Compose codegen.
- **104 in methods missing ≤15 instructions** — overwhelmingly the same codegen, in each
  composable's own `fun X(` and `) {` lines: the `$changed` recomposition-skip and `$default`
  bitmask branches, which no call from a test can drive both ways.

**There are no `coverageExcludes` and there must never be any.** Every class this module compiles is
measured, so the denominator is honest. To judge whether this module is tested, read INSTRUCTION and
LINE (both 0.98).

### How the last four points were won, in case another module needs the same

Composing a widget once and asserting on it covers its first draw and nothing else. Three shapes
moved the number, in order of yield:

1. **Compose it with only its required arguments.** Real call sites take the defaults; the suite was
   passing every one of them explicitly, so no default branch ever fired. `WidgetDefaultsTest`.
2. **Change its arguments at one call site**, driven from a step counter — enabled, label, size and
   the optional slots together, so the widget is re-evaluated with genuinely different inputs.
   `WidgetParameterChangesTest` and its `*MoreTest` half. This is what the settings tabs actually do
   to these widgets.
3. **Find the composables nothing composes.** `rememberSystemFonts` had *zero* covered instructions
   — every other test called the blocking `SystemFonts.families()` directly — and the colour field's
   dialog had never been opened. Those two alone moved INSTRUCTION by a point.

What did **not** move it: recomposing the parent while the widgets' own inputs stay identical, and
re-driving gestures another suite already covered.

## Commands

```bash
./gradlew :ui-components:test                 # 74 test classes, 620 tests
./gradlew :ui-components:detekt               # six baselined LongMethod, nothing else
# recordRoborazziJvm / verifyRoborazziJvm are the Test-derived tasks -- the un-suffixed ones are
# lifecycle aggregates and reject --tests.
./gradlew :ui-components:verifyRoborazziJvm --tests '*ScreenshotTest*'   # local, not CI
./gradlew :ui-components:recordRoborazziJvm --tests '*ScreenshotTest*'   # after a visual change
```
