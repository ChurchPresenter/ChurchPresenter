# `:theme` — Agent Notes

Rules, structure and commands for this module only. The repo-wide rules are in the root
`AGENT.md`.

## What it is

The app's look: the **nine color schemes**, the semantic color roles, the typography and shape
scales, and the theme-mode plumbing. A real Gradle module of this build —
`include(":theme")`, `implementation(projects.theme)` — in the package
`org.churchpresenter.theme`.

It held `org.churchpresenter.app.churchpresenter.ui.theme` until that was renamed. That package is
**still there in `:composeApp`** and holds `LanguageProvider`/`LocalLanguage` — so a rewrite that
moves `…ui.theme.*` by prefix breaks them. Key on the types this module owns: `ThemeMode`,
`ChurchPresenterTheme`, `AppThemeWrapper`, `ThemeManager`, `SemanticColors`, the
`MaterialTheme.semantic` extension and friends.

**Two tests reach into `Theme.kt` by reflection** — `Class.forName("org.churchpresenter.theme.ThemeKt")`
in `ThemeRenderTest` and `ThemeTest`, to read the private schemes by name. The compiler cannot see
that string, so a future package move has to update it by hand; both tests fail loudly with
`ClassNotFoundException` if it is missed.

## The one rule that matters

**A color literal belongs in this module or nowhere.** The module depends on Compose and on
nothing of the app's own, so nothing here can start reading a setting, a file or a ViewModel — that
is what keeps the palette a palette. A `Color(0xFF…)` anywhere else in the codebase is a finding
(see `DEVELOPMENT_GUIDE.md`'s zero-tolerance list).

Corollaries:

- **Anything `:composeApp` calls has to be public here.** `colorSchemeFor` was `internal` and is
  not any more.
- **`LanguageProvider` stays in `:composeApp`** — it is i18n, not theming, and it resolves layout
  direction from the app's own `Language` catalogue. `ui/theme/` in the app holds that and nothing
  else.

## Layout

`src/main/kotlin/org/churchpresenter/theme/`

| File | Owns |
|---|---|
| `Theme.kt` | The nine `ColorScheme`s (Light, Warm, Ocean, Rose light; Dark, Studio, Midnight, Forest, Mocha dark), `AppTypography`, the shape scale, `colorSchemeFor(mode, systemDark)` and the `ChurchPresenterTheme` composable that also provides the scrollbar style |
| `ThemeManager.kt` | `ThemeMode` (the fixed palettes plus `SYSTEM` and `CUSTOM`), `ThemeManager`, `LocalThemeManager`/`ProvideThemeManager`/`rememberThemeManager`, and `themeFromSettings(saved)` |
| `ThemeCustomization.kt` | `ThemeCustomization` (accent, light/dark base, the optional background/text/secondary/selection/success/warning/error colours, UI font family, UI font scale), `LocalThemeCustomization`, `customColorScheme(custom)` — the HSL-generated palette behind `ThemeMode.CUSTOM` — `customSemanticColorsFor(custom)` and `UI_FONT_SCALES` |
| `UiFontScale.kt` | `ProvideUiFontScale` — the UI text size as a font scale on `LocalDensity`, applied once per window |
| `SemanticColors.kt` | `SemanticColors` — the named roles (success, warning, live, staged…) derived per scheme — `semanticColorsFor`, `isDarkScheme`, and the `MaterialTheme.semantic` accessor |
| `AppThemeWrapper.kt` | The one-call wrapper (`ProvideThemeManager` + `ChurchPresenterTheme`) used by app entry points, previews and screenshot tests |

`ThemeMode.SYSTEM` is **not** a palette of its own — it resolves to Light or Dark, so anything
listing palettes (schedule-label presets, the theme switcher) skips it. `themeFromSettings` matches
against `ThemeMode.entries` rather than a hand-written `when`, so it cannot fall behind the enum:
keep it that way when adding a mode.

`ThemeMode.CUSTOM` is not a fixed palette either: `customColorScheme` generates it from the colours
in `LocalThemeCustomization`, which the app supplies from its settings — this module still reads
none. Anything listing *presets* skips it as it skips `SYSTEM`.

The generator's guarantees are what make an operator's five-minutes-before-service pick safe, and
`ThemeCustomizationTest` holds them across every 15° of hue, muted and saturated, on both bases:
- **Accent, secondary and background** keep only their hue and saturation; every lightness is fixed
  per role in `DarkTones`/`LightTones`, so the settings layers always step apart by the
  `ThemeSurfaceRampTest` margins. Background saturation is capped for the same reason.
- **Text** keeps its hue and is stepped lighter or darker until it clears 4.5:1 on every surface.
- **Selection, success, warning and error** are fills, used exactly as picked, with black or white
  on them — whichever reads. A button's label is picked the same way (light or dark of its hue), since
  a yellow or cyan accent is bright even at the fixed lightness.
- Unpicked error and status colours, and the tooltip pair, come from the Light or Dark preset.

`ChurchPresenterTheme` applies the customization's font family (through the typography) and its font
scale — through `ProvideUiFontScale`, on `LocalDensity`, so hard-coded `sp` sizes scale too — to
every theme. Each window starts from the display's density, so a dialog that opens its own window
without going through the theme wraps its body in `ProvideUiFontScale`; a second call in the same
window is a no-op, so nesting never scales twice. **Output windows never call it**: the audience's
screen does not follow the operator's text size.

Adding a theme means: a `ColorScheme` in `Theme.kt`, an entry in `ThemeMode`, a branch in
`colorSchemeFor`, and whatever `SemanticColorsTest`/`ThemeSurfaceRampTest` assert about ramps and
contrast.

## Commands

```bash
./gradlew :theme:test                              # its suite (headless; includes Compose render tests)
./gradlew :theme:detekt                            # gate — no baseline, must be clean
./gradlew :theme:jacocoTestCoverageVerification    # the coverage floor
```

All three run in CI, gated on this directory or the shared build files changing.

## Gates

- **detekt**: the app's `config/detekt/detekt.yml`, **no baseline**, `src/main/kotlin` and
  `src/test/kotlin` both in scope. Must be clean.
- **Coverage**: the root build's default six counters at 85%, all of them — this module declares
  **no** `coverageFloors` and no `coverageExcludes`. Keep it that way; a new palette is data and is
  covered by the schema tests.
- Tests run with `java.awt.headless=true`; the Compose render tests use `compose.uiTest` and
  `compose.desktop.currentOs` as test-only dependencies.

## Dependencies

`compose.runtime`, `compose.foundation`, `compose.material3`, `compose.ui` — nothing else, ever.
Versions from `gradle/libs.versions.toml`.
