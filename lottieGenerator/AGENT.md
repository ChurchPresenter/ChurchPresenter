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

`org.churchpresenter.lottiegen.band.BibleLottieGenApp` is the second entry point: the **Bible
lower-third band** generator, opened from Settings → Bible → Lower Third Animation and from the
Background tab's Bible Lower Third surface (`dialogs/tabs/BibleLottieBandPicker.kt`). It takes an
`outputDir`, an `onFileSaved: (File) -> Unit` and a `BibleLottieGenConfig` seed, and shares the
engine, `PreviewPanel` and palette with the main generator. The app plays its files through
`presenter/BibleLottieBand.kt`, which fills the named text layers at run time — see the contract
under `band/` below.

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
| `band/` | The Bible band generator: `BibleLottieGenConfig` + enums, `BandTimeline` (the five marked segments), `BandLayout` (slot boxes), `BandBackgroundLayers`, `BandTextLayers`, `BibleLottieGenerator`, `BibleLottieGenViewModel`, `BibleLottieGenApp`, and its own `ui/` |
| `viewmodel/` | `LottieGenViewModel` — debounced regeneration |
| `ui/` | The generator's Compose UI: control panel, preview, components, theme, `Strings` |
| `persistence/` | Preset, color-theme, logo and spec file I/O |
| `tools/` | `DumpStyleReview` — the `dumpStyleReview` Gradle task's `main()`, batch review PNGs |

**The band template contract.** A band file is an ordinary Lottie plus what the player relies
on: `markers` named `bg_in`, `text_in`, `hold`, `text_out`, `bg_out`; text layers named `Text1`,
`Reference1`, `Text2`, `Reference2` with a wrap box (`sz`/`ps`) in their document; a hidden
`<Slot>Shadow` twin under each; `<Slot>Matte` and `Band*` shape layers; and a top-level `cp`
object carrying `textAnimation` and `tickerPxPerSecond`. Fonts, sizes, colours, tracking and the
strings themselves are replaced by the player from the Bible settings, so the `preview*` fields of
the config never reach the output. Keyframes are the entrance mirrored into the exit
(`buildKeyframes(startFrame = …)`); `TYPEWRITER`, `TYPEWRITER_WORDS` and `TICKER` are emitted
static because the Compottie runtime has no text range selectors — the player reveals or scrolls
the string itself.

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
./gradlew :lottieGenerator:dumpStyleReview -Pstyles=14,15    # before/after (Detail off/on) x 3 alignments,
                                                              # one PNG per style, in build/style-review/
                                                              # (-Pstyles=all for every style; -Pout=/dir to redirect)
```

Both CI steps are gated on this directory or the shared build files changing.

## Gates

- **Coverage**: the root build's default six counters at 85% — only **branch (0.83) and
  complexity (0.81)** are named in `extra["coverageFloors"]`, added 2026-09 when wiring the
  optional Detail line into the 12 compiled styles turned each one's `if(isName)` into a 3-way
  `when(kind)` with no automated test exercising the new branches (compiled/Path A styles are
  verified by live preview only — see `ADDING_ANIMATIONS.md`). A ratchet, not a target: raise it
  as generator tests are added, never lower it to make a change fit. `extra["coverageExcludes"]`
  drops `**/ui/**` and `**/MainKt*`, which need a display. The `spec/` and `lottie/` packages are
  where the coverage lives, and the `SpecPort*Test` suites exist so a spec style stays
  byte-comparable with the code style it replaced.
- **Detekt**: `./gradlew :lottieGenerator:detekt`. The module has the plugin, **no baseline**, and
  is clean -- gated in `test.yml`'s Detekt step alongside every other module. It went from 382
  findings down to one lingering `LongParameterList` on `LottieGenPalette`'s 51-role constructor
  (see **Theme** above for why those 51 exist), which `constructorThreshold: 7` kept flagging no
  matter how the roles were grouped at one level. The fix was the three levels of nesting the
  previous note said it would need: `LottieGenPalette` takes 3 top-level groups (`Panel`,
  `Content`, `Interaction`), each of those holds 3-5 leaf classes of 2-6 `Color` roles apiece, and
  every constructor in the tree stays at or under 6 parameters. The grouping is filing, not a
  design change -- every role keeps its original name and its OKLCH comment, and `Tokens` (the
  only thing the rest of the module reads) is unchanged at every call site.
- Tests run with `java.awt.headless=true`; `TextMeasurer` and `FontRegistry` use AWT and must keep
  working headless.

## Dependencies

`compose.desktop.currentOs`, Material 3, the extended icon set, `kotlinx-serialization-json`,
`kotlinx-coroutines-swing`, and `compottie` + `compottie-dot` for previewing the Lottie it writes.
All from `gradle/libs.versions.toml`.

Plus **`projects.theme`** — the shared Material layer (see **Theme** above). It is the only module
dependency here, and it brings nothing but Compose.
