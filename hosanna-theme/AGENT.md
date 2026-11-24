# `:hosanna-theme` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The Hosanna theme: a palette of its own, used by the composables built on it and by nothing else.
Its only consumer by design is `:composables`. A real Gradle module of this build — `include(":hosanna-theme")`,
`implementation(projects.hosannaTheme)` — in the package `org.churchpresenter.hosanna`. Layer:
foundation.

## Rules

- **A color literal belongs in `:theme`, in this module, or nowhere.** Hosanna's colors live here
  and only here.
- **It depends on Compose and nothing of the app's own** — no setting, file or ViewModel.
- It is separate from `:theme`: it is not a `ThemeMode` and does not appear in the theme switcher.

## Layout

`src/main/kotlin/org/churchpresenter/hosanna/`

| File | Owns |
|---|---|
| `HosannaTheme.kt` | `HosannaTheme { }` (Material 3 colors, typography and shapes, plus the locals below) and the `HosannaTheme.colors` / `.typography` / `.shapes` / `.elevation` accessors |
| `HosannaColors.kt` | `HosannaColors` — every Material role plus success, live, star, faint, link, content types, song sections and the output `screen`/`onScreen` (the same in both themes), `toMaterial(dark)` that builds the Material schemes from it, and `HosannaStateAlpha` (hover, focus, pressed, dragged, the secondary fill a track shows behind its main one, disabled, scrim, content-type container and edge) |
| `HosannaTypography.kt` | The ten styles (Display … Overline), `toMaterial()`, `tabular()` for numbers that line up |
| `HosannaShapes.kt` | The radius scale XS 4 · S 6 · M 8 · L 12 · XL 16 · Full as `HosannaRadius` (dp) and `HosannaShapes` (shapes), and the Material `Shapes` built from it |
| `HosannaElevation.kt` | Levels 0–5, the two-layer shadows per theme, the 1dp edge, `Modifier.hosannaElevation` |

`src/main/composeResources/font/` holds Inter at 300, 400, 600 and 700, static instances of the
variable font in `:shared-ui`'s test fixtures; its license is `Inter-OFL.txt`.

- **Corners are `HosannaTheme.shapes`/`HosannaRadius`, not `AppShape`.** `AppShape` scales by `:theme`'s
  `CORNER_SCALE`; Hosanna's radii are exact.
- **Overline is uppercase in its text**, not its style — `TextStyle` has no text transform; pass the text through `asOverline()`.
- **Elevation is a shadow and an edge, never tonal**: `surfaceTint` equals `surface`.

## Commands

```bash
./gradlew :hosanna-theme:test
./gradlew :hosanna-theme:detekt                            # gate — no baseline, must be clean
./gradlew :hosanna-theme:jacocoTestCoverageVerification    # the coverage floor
```

## Gates

- **detekt**: no baseline, must be clean.
- **Coverage**: `cp.test`'s 85% on five counters; **BRANCH is 0.65**, agreed with the person running
  the work. The module's own branches are all covered; the 5 missed are the Compose compiler's
  skip and default-argument checks in `HosannaTheme`, which no test reaches. Raise it as the
  components built on the theme add branches of their own.

## Dependencies

`compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui` — nothing else.
Versions from `gradle/libs.versions.toml`.
