# `:lottieGenerator` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`. This directory carries three more documents, and they are the ones to read before
touching animation output:

- `README.md` — what the tool does, how to run and package it, the file-by-file map.
- `ADDING_ANIMATIONS.md` — the technical checklist for implementing a new style (code path **and**
  spec path).
- `ANIMATION_DESIGN_BRIEF.md` — the code-free brief for proposing a new animation *concept*.

## What it is

A Compose Desktop app that generates **animated lower thirds as Lottie JSON**. It ships twice: as
its own installable app, and as a window the running app opens — from the Lower Third settings
(where it knows the presenter resolution and the configured output folder) and from the Help menu.

A real Gradle module of this build: `include(":lottieGenerator")`,
`implementation(projects.lottieGenerator)`. It is **not** mounted through `kotlin.srcDir` any more;
`:composeApp` depends on the project like any other.

## What `:composeApp` uses from it

`org.churchpresenter.lottiegen.App` (as `LottieGenApp`) and `org.churchpresenter.lottiegen.editor.StyleEditorApp`, both from
`dialogs/AboutDialog.kt`, wrapped in `AppThemeWrapper` so the generator follows the app's theme.
The app passes `outputDir` and the canvas size and gets an `onFileSaved` callback; with
`embedded = true` it stays inside the app's window chrome. Keep those entry points public and their
parameters defaulted — they are the whole API surface.

The app renders the generated files itself (`presenter/LowerThirdPresenter.kt`,
`LowerThirdOffscreenRenderer`, `LottieFrameStream`); nothing in this module is involved at
presentation time.

## Layout

`src/main/kotlin/lottiegen/`

| Package | Owns |
|---|---|
| (root) | `Main.kt` (`mainClass = "lottiegen.MainKt"`), `App.kt`, `LottieGenState.kt` |
| `model/` | `LottieGenConfig`, `Preset`, `ColorTheme`, `CanvasPreset`, `StyleCatalog`, enums |
| `lottie/` | The generation engine: `LottieGenerator.generate()`, `LottieBuilder`, `KeyframeUtils`, `ShapeHelpers`, `TextHelpers`, `ColorUtils`, `TextMeasurer` (AWT), `FontRegistry` |
| `lottie/styles/` | The twelve hand-written style generators plus `StyleGenerator` |
| `spec/` | The data-driven styles: `StyleSpec`, `SpecJson`, `SpecLayout`, `SpecStyleGenerator`, `StyleRegistry` |
| `editor/` | The Animation Style Editor — `StyleEditorApp`, `EditorViewModel`, `SpecEditOps`, `BuildRegistrar`, `ImageImport`, and its own `ui/` |
| `viewmodel/` | `LottieGenViewModel` — debounced regeneration |
| `ui/` | The generator's Compose UI: control panel, preview, components, theme, `Strings` |
| `persistence/` | Preset, color-theme, logo and spec file I/O |

**Two ways to add a style.** A code style is a new `styles/Style*.kt` plus registration; a spec
style is authored in the editor and needs **no code edit** at all. `ADDING_ANIMATIONS.md` decides
between them and lists the registration checklist and verification steps — follow it rather than
inventing a third path.

## Theme

**The Material layer is `:theme`'s.** `LottieGenTheme` wraps `ChurchPresenterTheme`, so the
standalone window gets the same colour schemes, typography, shapes and semantic colours as every
other ChurchPresenter screen. It used to build its own `ColorScheme` from the palette plus a
`Typography` that restated Material's defaults — both duplicates, and the colour one let the tool's
dialogs and dropdown menus drift from the app's whenever a theme changed on one side only.

**`LottieGenPalette` and `Tokens` stay.** Those 51 roles are the hand-drawn panel chrome — canvas
checkerboard, transport track, live dot, badge and logo chips — which Material has no equivalent
for. They are not duplication, and they are what `ProvideLottieGenPalette` supplies on the embedded
path, where the host already owns the MaterialTheme.

The scrollbar style is deliberately provided *inside* the shared theme, overriding it: these
scrollbars sit on panel chrome rather than on Material surfaces.

## Commands

```bash
./gradlew :lottieGenerator:test                              # its suite (headless)
./gradlew :lottieGenerator:run                               # the generator alone
./gradlew :lottieGenerator:jacocoTestCoverageVerification    # the coverage floor
./gradlew :lottieGenerator:packageDmg                        # installer (Msi/Deb also available)
```

Both CI steps are gated on this directory or the shared build files changing.

## Gates

- **Coverage**: the root build's default six counters at 85% — this module declares **no**
  `coverageFloors`. `extra["coverageExcludes"]` drops `**/ui/**` and `**/MainKt*`, which need a
  display. The `spec/` and `lottie/` packages are where the coverage lives, and the `SpecPort*Test`
  suites exist so a spec style stays byte-comparable with the code style it replaced.

  **What that 94% is a statement about.** It is the module's `spec/`, `lottie/`, `model/`,
  `viewmodel/` and `persistence/` core -- not the module. `extra["coverageExcludes"]` drops
  `**/ui/**` and `**/MainKt*`, and the root build filters `classDirectories`, so the excluded
  classes leave both the numerator and the denominator. Measured 2026-08-22 by re-pointing the
  report at all of `classes/kotlin/main`:

  | counter | reported | with nothing excluded |
  |---|---|---|
  | INSTRUCTION | 94.4% | **47.3%** |
  | BRANCH | 90.8% | 51.7% |
  | LINE | 95.0% | 51.9% |
  | COMPLEXITY | 87.0% | 48.0% |
  | METHOD | 88.7% | 47.9% |
  | CLASS | 96.8% | 73.3% |

  The exclude hides **50.2% of the module's bytecode** -- 47,639 of 94,813 instructions, 62 of
  247 classes by the CLASS counter. Quote that rather than the source-line figure (6,418 of
  15,411 lines, 27 of 81 files = 41.6%), which understates it: the UI files are dense.

  The excluded half is **0.5% covered** -- 248 instructions, all of them the `Strings` object in
  `ui/`; `editor/ui` is 0 of 24,061 and `ui/components` 0 of 8,249. There are no Compose UI tests
  in the module at all, so nothing is lost by the filter beyond the honesty of the headline.

  This is milder than `:songlibrary`'s 94.5% -> 2.7%, because the non-excluded half genuinely is
  tested at 94-98%. **It is still not a 94% module.** Both numbers are real; they answer different
  questions, and only the second one answers "how much of this module is tested".

  Worth knowing per package: `editor` is at **70.2%**, below the 0.85 floor, and passes only
  because the floor is checked bundle-wide rather than per package. The root package
  `org.churchpresenter.lottiegen` is at 0% over 8 classes.

  **No exclude should be added or removed here on the strength of this note** -- it is a wording
  fix, not a licence to change the gate.

- **Detekt**: `./gradlew :lottieGenerator:detekt`. The module has the plugin, **no baseline** and
  **zero findings**, down from 382, and is in `test.yml`'s Detekt step like every other module --
  so those 381 fixes are protected from regression rather than merely done.
  **Do not add a baseline file**, and do not reach for a second `@Suppress` without asking.

  There is exactly one suppression: `LongParameterList` on `LottieGenPalette`'s 51-role
  constructor, at the declaration with its reasoning (see **Theme** above for why those 51 roles
  exist). `constructorThreshold` is 7 and no shallower grouping reaches it -- the nine natural
  banner groups are 8-11 members each and would each be flagged in turn -- so satisfying the rule
  needs three levels of nesting and turns `palette.appBg` into `palette.chrome.surfaces.appBg`.
  The suppression was the deliberate call: one standing finding kept the whole module off the
  gate, which cost far more than the rule bought.
- Tests run with `java.awt.headless=true`; `TextMeasurer` and `FontRegistry` use AWT and must keep
  working headless.

## The logo plate and gutter follow the logo's aspect

Two different things are called "the logo size", and mixing them up is what made wide logos
overhang their plate:

- **`Style1Bar` fits the longest side** — `scale = logoSizePx / max(logoW, logoH)` — so the logo
  always draws inside a `logoSizePx` square and a square gutter is exactly right for it.
- **Styles 5, 6, 7, 9 and 10 scale by HEIGHT** — `scale = logoSizePx / cfg.logoH` — so `logoSize`
  is the logo's *height* and its drawn width is `logoSizePx * aspect`. A logo wider than it is
  tall draws wider than the space reserved for it.

Those five used to reserve a square gutter regardless (and Style 5 drew a square
`makeRect(logoBgSize, logoBgSize)` plate), so a wide logo overhung the plate and could collide
with the text bar. They now size both from `logoAspect(cfg.logoW, cfg.logoH)` (`ColorUtils.kt`),
which returns exactly `1.0` for a square logo — so **the generated JSON is bit-for-bit unchanged
for square logos**, and only non-square ones move. That invariant is what the `SpecPort*Test`
matrices rely on: they were switched to a square logo, where the compiled style and the spec port
agree exactly.

Note the consequence for a *tall* logo: its plate is now narrower than it is high, hugging the
logo with even padding, where before it was square with extra room at the sides.

**The spec ports cannot follow this**, and that is a known gap rather than an oversight: `SizeSpec`
has no logo-derived variant and the layout slot gaps are static em, so neither the plate nor the
reserved width can track an aspect. Each affected `SpecPort*Test` therefore covers a wide logo for
layer *structure* only, and says so at the call site. Closing it needs a logo-derived `SizeSpec`
plus logo-aware slot gaps.

## Dependencies

`compose.desktop.currentOs`, Material 3, the extended icon set, `kotlinx-serialization-json`,
`kotlinx-coroutines-swing`, and `compottie` + `compottie-dot` for previewing the Lottie it writes.
All from `gradle/libs.versions.toml`.

Plus **`projects.theme`** — the shared Material layer (see **Theme** above). It is the only module
dependency here, and it brings nothing but Compose.
