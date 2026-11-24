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

The build file is the guard: its main dependencies are `:hosanna-theme` and Compose runtime,
foundation and ui. Adding `:theme` or `compose.material3` to them breaks this rule.

## Layout

One folder per component family, the same name in main, test and screenshots:

| Where                                       | What                                                                                                                                                                                                |
|---------------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `src/main/kotlin/.../composables/<family>/` | The composables, package `org.churchpresenter.hosanna.composables.<family>` (`button/StandardButton.kt`)                                                                                            |
| `src/test/kotlin/.../composables/<family>/` | `<Name>Test` (logic and behaviour) and `<Name>ScreenshotTest`, same package                                                                                                                         |
| `src/test/kotlin/.../composables/testing/`  | The shared test kit: `hosannaComponent` (both themes in `HosannaTheme`, `PinnedFonts`, trimmed), `FrozenInteractionSource` (holds a hover, press or focus for a shot), `testIcon`, `HosannaSurface` |
| `screenshots/<family>/<name>/`              | The committed images, `<state>_light.png` / `<state>_dark.png`                                                                                                                                      |

- **A component's sizes and timings are an `internal object <Name>Defaults`** beside it — not public:
  callers choose a hierarchy or intent, never a height.
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
