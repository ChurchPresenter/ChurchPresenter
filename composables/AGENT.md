# `:composables` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The new composables, drawn in the app's active theme. A real Gradle module of this build —
`include(":composables")`, `implementation(projects.composables)` — in the package
`org.churchpresenter.hosanna.composables`. Layer: foundation.

## The one rule that matters

**Everything here is drawn from the app's active theme (`:theme`) through `AppTheme`, and Compose
foundation — nothing else.**

- **`AppTheme` (in `:theme`) is the only way in.** It reads whichever of the twelve themes is
  active: `AppTheme.colors` (`ThemeColors`) gives its Material roles and its semantic roles under the
  names components ask by (`live`, `star`, `onSurfaceFaint`, `song`, `verse`, …);
  `AppTheme.typography` is the `TypeScale` in the theme's font; `AppTheme.elevation` the light or dark
  `Shadows`; `AppTheme.shapes`, `AppRadius`, `ElevationLevel`/`elevationShadow` and `StateAlpha` the
  rest. All of it lives in `:theme`; nothing here defines a token.
- **A composable sets no color, corner radius, typography or elevation of its own** and defines no
  per-component color/shape/type table (no `XxxColors`, no theme-like object). It reads the roles
  and derives states from them with `StateAlpha`.
- **A token the design needs and the theme lacks is added to `:theme`** — a color to
  `SemanticColors`, in both its light and its dark set, which every theme picks from, then mapped in
  `ThemeColors`; a size, shape or shadow to its file there. Never as one component's color, never as a literal here. The only literals are the
  output's own black and white (`screen`/`onScreen`), which no theme changes.
- **Not `MaterialTheme` directly, and no Material components** (`Button`, `Text`, `Surface`,
  `TextField`, …): read the theme through `AppTheme`, and build on `androidx.compose.foundation`
  (`BasicText`, `BasicTextField`, `Box`, `clickable`, `indication`, …). Nothing of `:theme`'s other
  than its colors either — no `AppShape`, `raised()`/`sunken()` or its components.
- **Every clickable composable takes `onClick`, `modifier: Modifier = Modifier` and
  `interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }`** — in
  that order, the source right after the modifier. It draws its hover, pressed and focus states
  from that source; a caller passes its own only to watch or drive them.
- **No size or radius literal** that a token already names.
- **No generic functions or classes** (`fun <T>`, `class Foo<T>`): a list is keyed by `String`
  (`MenuEntry.Item(key, …)`), or by the component's own enum, never by a type parameter.
- **Glyphs a composable draws itself** (a check, a ×, a star) come from `:icons`
  (`painterResource(Res.drawable.ic_…)`), never drawn by hand or as text.

The build file's main dependencies are `:theme` (its tokens), `:icons` (drawables only), Compose
runtime, foundation and ui, and Material 3 only because the theme's colors are a `ColorScheme`.

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
shared kit: `hosannaComponent` and `hosannaPopup` (light and dark in `ChurchPresenterTheme`, `PinnedFonts`,
trimmed), `FrozenInteractionSource` and `frozenSource` (hold a hover, press or focus for a shot),
`testIcon`, `HosannaSurface`.

Screenshots are committed under `screenshots/<family>/<name>/` as `<state>_light.png` /
`<state>_dark.png`.

- **A new composable** goes in its family's folder; anything it shares goes to `foundation/`, its
  public types and plain logic to `model/`, its sizes to `defaults/`.
- **Every state is shot**, one image per state, every variant of the component in it.
- A test of a color rule reads a theme's colors with `themeColorsFor(ThemeMode.LIGHT)` (or `DARK`).

## Commands

```bash
./gradlew :composables:test
./gradlew :composables:detekt                            # gate — no baseline, must be clean
./gradlew :composables:jacocoTestCoverageVerification    # the coverage floor
./gradlew :composables:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :composables:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
