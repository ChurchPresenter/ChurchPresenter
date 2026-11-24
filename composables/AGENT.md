# `:composables` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The composables built on the Hosanna theme. A real Gradle module of this build —
`include(":composables")`, `implementation(projects.composables)` — in the package
`org.churchpresenter.hosanna.composables`. Layer: foundation.

## The one rule that matters

**Everything here is drawn from `:hosanna-theme`'s tokens and Compose foundation — nothing else.**

- **Only the Hosanna theme, never a theme of a component's own.** A composable sets no color,
  corner radius, typography or elevation of its own and defines no per-component color/shape/type
  table (no `XxxColors`, no theme-like object) — not here and not in `:hosanna-theme`. It reads
  the theme's roles (`HosannaTheme.colors.primary`, `.onSurface`, …) and derives states from them
  with `HosannaStateAlpha`. A role or token the design needs and the theme lacks is added to the
  theme as a general token, never as one component's.
- **Never `MaterialTheme` directly** — not its `colorScheme`, `typography` or `shapes`. Every
  Material role is a field of `HosannaColors`; the Material scheme is built from it, not read.
- **Every clickable composable takes `onClick`, `modifier: Modifier = Modifier` and
  `interactionSource: MutableInteractionSource = remember { MutableInteractionSource() }`** — in
  that order, the source right after the modifier. It draws its hover, pressed and focus states
  from that source; a caller passes its own only to watch or drive them.

- **Tokens only from `:hosanna-theme`**: colors, typography, shapes, elevation and state alphas
  through `HosannaTheme.*`, `HosannaRadius`, `HosannaLevel`/`hosannaElevation` and
  `HosannaStateAlpha`.
- **Nothing from the old theme**: no `:theme` dependency — no `ChurchPresenterTheme`,
  `MaterialTheme.semantic`, `AppShape`, `raised()`/`sunken()` or any `org.churchpresenter.theme`
  component.
- **Nothing from Material**: no `compose.material3` dependency — no `MaterialTheme.*`, no Material
  components (`Button`, `Text`, `Surface`, `TextField`, …). Build on `androidx.compose.foundation`
  (`BasicText`, `BasicTextField`, `Box`, `clickable`, `indication`, …).
- **No color, size or radius literal** that a token already names. A value the theme lacks is added
  to `:hosanna-theme` first, then used here.

- **No generic functions or classes** (`fun <T>`, `class Foo<T>`): a list is keyed by `String`
  (`MenuEntry.Item(key, …)`), or by the component's own enum, never by a type parameter.
- **Glyphs a composable draws itself** (a check, a ×, a star) come from `:icons`
  (`painterResource(Res.drawable.ic_…)`), never drawn by hand or as text.

The build file is the guard: its main dependencies are `:hosanna-theme`, `:icons` (drawables only)
and Compose runtime, foundation and ui. Adding `:theme` or `compose.material3` to them breaks this
rule.

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
- `:theme` is on the **test** classpath only, for `ThemeMode`, which `:shared-ui`'s `bothThemes`
  labels its renders with; nothing here renders with it.

## Commands

```bash
./gradlew :composables:test
./gradlew :composables:detekt                            # gate — no baseline, must be clean
./gradlew :composables:jacocoTestCoverageVerification    # the coverage floor
./gradlew :composables:recordRoborazziJvm --tests '*ScreenshotTest*'
./gradlew :composables:verifyRoborazziJvm --tests '*ScreenshotTest*'
```
