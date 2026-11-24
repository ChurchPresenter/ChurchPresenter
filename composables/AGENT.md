# `:composables` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The composables built on the app theme's Light and Dark (Hosanna) palette. A real Gradle module of this build —
`include(":composables")`, `implementation(projects.composables)` — in the package
`org.churchpresenter.hosanna.composables`. Layer: foundation.

## The one rule that matters

**Colors, type, shapes, elevation and state alphas all come from `:theme`. Nothing is set per component.**

- **Colors are the app theme's**, read through `ThemeColors.current` (`foundation/ThemeColors.kt`):
  the current `MaterialTheme.colorScheme` roles and `MaterialTheme.semantic` roles, under the app
  theme's names (`primary`, `faint`, `favorite`, `contentSongs`, `chordVerse`, `screen`…). The app's
  Light and Dark schemes are the Hosanna palette, so the composables follow whichever scheme the user
  picks. A role the design needs and `:theme` lacks is added to `:theme` (`SemanticColors`), never here.
- **Type, shapes, elevation and state alphas are `:theme`'s Hosanna tokens**: `HosannaTheme.typography`
  (the scale, set in the app theme's font family), `.shapes`, `.elevation` (shadows per light/dark
  scheme, the edge from `SemanticColors.elevationEdge`), `HosannaRadius`, `HosannaLevel`/`hosannaElevation`,
  `HosannaStateAlpha` (`org.churchpresenter.theme`).
- **No color, size or radius literal** that a token already names, and no per-component color, shape
  or type table outside `foundation/`'s state functions, which only combine theme roles.
- **Nothing from Material's components**: build on `androidx.compose.foundation` (`BasicText`,
  `BasicTextField`, `Box`, `clickable`, …). `compose.material3` is a dependency only for
  `MaterialTheme`'s color scheme.
- **Every clickable composable takes `onClick`, `modifier: Modifier = Modifier` and
  `interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }`** — in
  that order, the source right after the modifier. It draws its hover, pressed and focus states
  from that source; a caller passes its own only to watch or drive them.

- **No generic functions or classes** (`fun <T>`, `class Foo<T>`): a list is keyed by `String`
  (`MenuEntry.Item(key, …)`), or by the component's own enum, never by a type parameter.
- **Glyphs a composable draws itself** (a check, a ×, a star) come from `:icons`
  (`painterResource(Res.drawable.ic_…)`), never drawn by hand or as text.

The build file is the guard: its main dependencies are `:theme` (colors), `:icons` (drawables only), Compose runtime, foundation and ui, and
`compose.material3` for `MaterialTheme` alone.

## Layout

Code is split by role, and every family puts its pieces in the same places. Packages are
`org.churchpresenter.hosanna.composables.<folder>`, the folder under `src/main/kotlin/.../composables/`:

| Folder | Holds |
|---|---|
| `button/`, `chip/`, `field/`, … | The composables of one family, and nothing else (`button/StandardButton.kt`) |
| `foundation/` | What several composables are built from: state, state colors, frames, focus ring, spinner, the icon-segment group, hold-to-repeat |
| `model/` | Public types and pure logic: enums and option classes (`ButtonHierarchy`, `ChipColor`, `MenuEntry`, `ScreenPosition`…), the caller-supplied text bundles in `Labels.kt`, and the `…Model.kt` / `…Key.kt` files of plain functions (parsing, stepping, formatting, key mapping) |
| `defaults/` | Every `internal object <Name>Defaults` and its metrics class — sizes and timings, never public: callers choose a hierarchy or intent, never a height |

Tests mirror it: `src/test/kotlin/.../composables/<family>/` holds `<Name>Test` and
`<Name>ScreenshotTest`; `model/` and `foundation/` hold the tests of those folders; `testing/` is the
shared kit: `hosannaComponent` and `hosannaPopup` (both themes in `HosannaTheme`, `PinnedFonts`,
trimmed), `FrozenInteractionSource` and `frozenSource` (hold a hover, press or focus for a shot),
`testIcon`, `HosannaSurface`.

Screenshots are committed under `screenshots/<family>/<name>/` as `<state>_light.png` /
`<state>_dark.png`.

- **A new composable** goes in its family's folder; anything it shares goes to `foundation/`, its
  public types and plain logic to `model/`, its sizes to `defaults/`.
- **Every state is shot**, one image per state, every variant of the component in it.
- The test kit renders inside `ChurchPresenterTheme` (Light or Dark) with `HosannaTheme` for the type,
  and `testing/ThemeColorsFixture.kt` gives `LightThemeColors`/`DarkThemeColors` for color tests.

## Commands

```bash
./gradlew :composables:test
./gradlew :composables:detekt                            # gate — no baseline, must be clean
./gradlew :composables:jacocoTestCoverageVerification    # the coverage floor
./gradlew :composables:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :composables:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
