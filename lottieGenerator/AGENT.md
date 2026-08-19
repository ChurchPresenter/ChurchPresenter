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

`lottiegen.App` (as `LottieGenApp`) and `lottiegen.editor.StyleEditorApp`, both from
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
- There is no detekt task on this module.
- Tests run with `java.awt.headless=true`; `TextMeasurer` and `FontRegistry` use AWT and must keep
  working headless.

## Dependencies

`compose.desktop.currentOs`, Material 3, the extended icon set, `kotlinx-serialization-json`,
`kotlinx-coroutines-swing`, and `compottie` + `compottie-dot` for previewing the Lottie it writes.
All from `gradle/libs.versions.toml`.
